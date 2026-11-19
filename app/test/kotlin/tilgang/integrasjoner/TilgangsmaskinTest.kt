package tilgang.integrasjoner

import io.getunleash.FakeUnleash
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import tilgang.AzureTokenGen
import tilgang.auth.ITokenProvider
import tilgang.fakes.Fakes
import tilgang.fakes.WithFakes
import tilgang.integrasjoner.tilgangsmaskin.BrukerOgRegeltype
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistGrunn
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinHttpClient
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.serialize
import tilgang.unleash.FeatureToggle
import tilgang.unleash.UnleashService
import kotlin.test.assertFailsWith

@WithFakes
class TilgangsmaskinTest {
    private val redis = Fakes.getRedisServer()
    private val httpClient = Fakes.getHttpClient()
    private val prometheus = Fakes.getPrometheus()

    @ParameterizedTest
    @CsvSource(
        "person, true",
        "person, false",
        "kjerne, true",
        "kjerne, false",
        "komplett, true",
        "komplett, false",
        "bulk, true",
        "bulk, false",
    )
    fun `switches client without restart for every access check`(endpoint: String, allowed: Boolean) = runTest {
        val calls = mutableListOf<String>()
        val status = if (allowed) HttpStatusCode.OK else HttpStatusCode.Forbidden
        val cache = mockk<Redis>()
        coEvery { cache[any()] } returns null
        coEvery { cache.set(any(), any(), any()) } returns Unit
        val fakeUnleash = FakeUnleash()
        val feature = FeatureToggle.raskRobustTilgangsmaskin
        val token = OidcToken(AzureTokenGen("tilgangazure", "tilgang").generate())
        val tokenProvider = mockk<ITokenProvider>()
        coEvery { tokenProvider.oboToken(any(), any()) } returns "test-token"

        UnleashService(fakeUnleash).use { unleash ->
            MockEngine.create {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler {
                    calls.add("standard")
                    respond("", status)
                }
            }.use { standardEngine ->
                MockEngine.create {
                    dispatcher = StandardTestDispatcher(testScheduler)
                    addHandler {
                        calls.add("robust")
                        respond("", status)
                    }
                }.use { robustEngine ->
                    TilgangsmaskinHttpClient.create(standardEngine).use { standardClient ->
                        TilgangsmaskinHttpClient.create(robustEngine).use { robustClient ->
                            val gateway = TilgangsmaskinGateway(
                                cache, standardClient, prometheus, tokenProvider,
                                httpClientProvider = {
                                    if (unleash.isEnabled(feature)) robustClient else standardClient
                                },
                            )

                            for (enabled in listOf(false, true, false)) {
                                if (enabled) fakeUnleash.enable(feature.key()) else fakeUnleash.disable(feature.key())
                                val result = when (endpoint) {
                                    "person" -> gateway.harTilgangTilPerson("123", token)
                                    "kjerne" -> gateway.harTilgangTilPersonKjerne("123", token, "ansatt").harTilgang
                                    "komplett" -> gateway.harTilgangTilPersonKomplett("123", token, "ansatt").harTilgang
                                    "bulk" -> gateway.harTilganger(listOf(BrukerOgRegeltype("123", "KJERNE")), token)
                                    else -> error("Unknown endpoint: $endpoint")
                                }
                                assertThat(result).isEqualTo(allowed)
                            }
                        }
                    }
                }
            }
        }

        assertThat(calls).containsExactly("standard", "robust", "standard")
    }

    @Test
    fun `Kan parse harTilgangTilPersonKjerne`() = runTest {
        val token = AzureTokenGen("tilgangazure", "tilgang").generate()
        val tilgangsmaskinGateway = TilgangsmaskinGateway(redis, httpClient, prometheus)
        val harTilgangResponse = tilgangsmaskinGateway.harTilgangTilPersonKjerne("123", OidcToken(token), "799")
        val harIkkeTilgangResponse = tilgangsmaskinGateway.harTilgangTilPersonKjerne("456", OidcToken(token), "799")

        assertThat(harTilgangResponse.harTilgang).isTrue()
        assertThat(harIkkeTilgangResponse.harTilgang).isFalse()
        assertThat(harIkkeTilgangResponse.tilgangsmaskinAvvistResponse?.title)
            .isEqualTo(TilgangsmaskinAvvistGrunn.AVVIST_HABILITET.toString())
    }

    @Test
    fun `Kan parse harTilgangTilPersonKomplett`() = runTest {
        val token = AzureTokenGen("tilgangazure", "tilgang").generate()
        val tilgangsmaskinGateway = TilgangsmaskinGateway(redis, httpClient, prometheus)
        val harTilgangResponse = tilgangsmaskinGateway.harTilgangTilPersonKomplett("123", OidcToken(token), "799")
        val harIkkeTilgangResponse = tilgangsmaskinGateway.harTilgangTilPersonKomplett("456", OidcToken(token), "799")

        assertThat(harTilgangResponse.harTilgang).isTrue()
        assertThat(harIkkeTilgangResponse.harTilgang).isFalse()
        assertThat(harIkkeTilgangResponse.tilgangsmaskinAvvistResponse?.title)
            .isEqualTo(TilgangsmaskinAvvistGrunn.AVVIST_GEOGRAFISK.toString())
    }

    @Test
    fun `Kan hente tilgang for en person`() = runTest {
        val token = OidcToken(AzureTokenGen("tilgangazure", "tilgang").generate())
        val gateway = TilgangsmaskinGateway(redis, httpClient, prometheus)

        assertThat(gateway.harTilgangTilPerson("123", token)).isTrue()
        assertThat(gateway.harTilgangTilPerson("456", token)).isFalse()
    }

    @Test
    fun `Kan hente flere tilganger`() = runTest {
        val token = OidcToken(AzureTokenGen("tilgangazure", "tilgang").generate())
        val gateway = TilgangsmaskinGateway(redis, httpClient, prometheus)

        assertThat(gateway.harTilganger(listOf(BrukerOgRegeltype("123", "KJERNE")), token)).isTrue()
        assertThat(gateway.harTilganger(listOf(BrukerOgRegeltype("456", "KJERNE")), token)).isFalse()
    }

    @Test
    fun `Propagerer andre klientfeil enn 403`() = runTest {
        val token = OidcToken(AzureTokenGen("tilgangazure", "tilgang").generate())
        val gateway = TilgangsmaskinGateway(redis, httpClient, prometheus)

        assertFailsWith<ClientRequestException> {
            // trigger en 4xx-respons utover 403 fra tilgangsmaskinen-fake
            gateway.harTilgangTilPerson("400", token)
        }
    }

    @Test
    fun `Cacher kjerneavgjørelser per ansatt`() = runTest {
        val token = OidcToken(AzureTokenGen("tilgangazure", "tilgang").generate())
        val gateway = TilgangsmaskinGateway(redis, httpClient, prometheus)
        val brukerIdent = "456"
        val ansattIdent = "ansatt-1"
        val cacheHitFør = prometheus.counter("cache_hit", "service", "tilgangsmaskinKjerne").count()
        val cacheMissFør = prometheus.counter("cache_miss", "service", "tilgangsmaskinKjerne").count()

        val første = gateway.harTilgangTilPersonKjerne(brukerIdent, token, ansattIdent)
        val gjentatt = gateway.harTilgangTilPersonKjerne(brukerIdent, token, ansattIdent)
        val annenAnsatt = gateway.harTilgangTilPersonKjerne(brukerIdent, token, "ansatt-2")

        assertThat(første.harTilgang).isFalse()
        assertThat(gjentatt).isEqualTo(første)
        assertThat(annenAnsatt).isEqualTo(første)
        assertThat(prometheus.counter("cache_hit", "service", "tilgangsmaskinKjerne").count() - cacheHitFør)
            .isEqualTo(1.0)
        assertThat(prometheus.counter("cache_miss", "service", "tilgangsmaskinKjerne").count() - cacheMissFør)
            .isEqualTo(2.0)
    }

    @ParameterizedTest
    @CsvSource(
        "true, 123, true, 21600",
        "true, 456, false, 1800",
        "false, 123, true, 21600",
        "false, 456, false, 1800",
    )
    fun `Cacher godkjenninger i seks timer og avslag i tretti minutter`(
        kjerneregler: Boolean,
        brukerIdent: String,
        forventetTilgang: Boolean,
        forventetLevetidSekunder: Long,
    ) = runTest {
        val cache = mockk<Redis>()
        coEvery { cache[any()] } returns null
        coEvery { cache.set(any(), any(), any()) } returns Unit
        val token = OidcToken(AzureTokenGen("tilgangazure", "tilgang").generate())
        val gateway = TilgangsmaskinGateway(cache, httpClient, prometheus)
        val ansattIdent = "ansatt-cachetid"
        val cachePrefix = if (kjerneregler) "tilgangsmaskinKjerne" else "tilgangsmaskinKomplett"

        val resultat = if (kjerneregler) {
            gateway.harTilgangTilPersonKjerne(brukerIdent, token, ansattIdent)
        } else {
            gateway.harTilgangTilPersonKomplett(brukerIdent, token, ansattIdent)
        }

        assertThat(resultat.harTilgang).isEqualTo(forventetTilgang)
        coVerify(exactly = 1) {
            cache.set(
                Key(cachePrefix, brukerIdent + ansattIdent),
                resultat.serialize(),
                forventetLevetidSekunder,
            )
        }
    }
}