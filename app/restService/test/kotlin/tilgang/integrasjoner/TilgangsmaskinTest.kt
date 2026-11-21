package tilgang.integrasjoner

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import tilgang.TilgangsmaskinConfig
import tilgang.auth.ITokenProvider
import tilgang.integrasjoner.tilgangsmaskin.BrukerOgRegeltype
import tilgang.integrasjoner.tilgangsmaskin.HarTilgangFraTilgangsmaskinen
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistGrunn
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistResponse
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import tilgang.integrasjoner.tilgangsmaskin.createTilgangsmaskinClient
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertFailsWith

class TilgangsmaskinTest {
    private val tokenProvider = mockk<ITokenProvider>()
    private val redis = mockk<Redis>()
    private val tokenA = mockk<OidcToken>()
    private val tokenB = mockk<OidcToken>()
    private val cache = ConcurrentHashMap<Key, ByteArray>()
    private val mottatteKall = ConcurrentLinkedQueue<MottattKall>()
    private var responseStatus = HttpStatusCode.NoContent

    @ParameterizedTest
    @ValueSource(strings = ["", "/", "/prefiks", "/prefiks/"])
    fun `bruker konfigurert url og obo-token for alle fire kall`(basePath: String) = runTest {
        medGateway(basePath) { gateway ->
            for (metode in listOf("person", "bulk", "kjerne", "komplett")) {
                assertThat(kall(gateway, metode)).isTrue()
            }

            assertThat(mottatteKall.map { it.endepunkt })
                .containsExactly("komplett", "bulk", "kjerne", "komplett")
            assertThat(mottatteKall.map { it.authorization }).containsOnly("Bearer obo-a")
            assertThat(mottatteKall.map { it.callId }).containsOnly("call-id")
            assertThat(mottatteKall.map { it.contentType }).containsExactly(
                ContentType.Text.Plain,
                ContentType.Application.Json,
                ContentType.Application.Json,
                ContentType.Application.Json
            )
            val bodies = mottatteKall.map { it.body }
            assertThat(bodies[0]).isEqualTo("123")
            assertThat(bodies[2]).isEqualTo("123")
            assertThat(bodies[3]).isEqualTo("123")
            val bulk = Redis.mapper.readTree(bodies[1])
            assertThat(bulk.size()).isEqualTo(1)
            assertThat(bulk[0]["brukerId"].asText()).isEqualTo("123")
            assertThat(bulk[0]["type"].asText()).isEqualTo("KJERNE")
            coVerify(exactly = 4) { tokenProvider.oboToken("tilgangsmaskin-test-scope", tokenA) }
        }
    }

    @ParameterizedTest
    @CsvSource("kjerne, true", "kjerne, false", "komplett, true", "komplett, false")
    fun `cacher positive og negative vurderinger i seks timer`(metode: String, harTilgang: Boolean) = runTest {
        responseStatus = if (harTilgang) HttpStatusCode.NoContent else HttpStatusCode.Forbidden

        medGateway("") { gateway ->
            repeat(2) {
                assertThat(kall(gateway, metode)).isEqualTo(harTilgang)
            }
            val prefix = if (metode == "kjerne") "tilgangsmaskinKjerne" else "tilgangsmaskinKomplett"
            val key = Key(prefix, "123Z1")
            val cached = requireNotNull(cache[key]).deserialize<HarTilgangFraTilgangsmaskinen>()
            assertThat(cached.harTilgang).isEqualTo(harTilgang)
            assertThat(cached.tilgangsmaskinAvvistResponse)
                .isEqualTo(if (harTilgang) null else avvisning(metode))
            assertThat(mottatteKall).hasSize(1)
            coVerify(exactly = 1) { tokenProvider.oboToken("tilgangsmaskin-test-scope", tokenA) }
            coVerify(exactly = 1) { redis.set(key, any(), 21600) }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["person", "bulk"])
    fun `returnerer false ved 403 for ukachede kall`(metode: String) = runTest {
        responseStatus = HttpStatusCode.Forbidden

        medGateway("") { gateway ->
            assertThat(kall(gateway, metode)).isFalse()
            coVerify(exactly = 0) { redis.set(any(), any(), any()) }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["person", "bulk", "kjerne", "komplett"])
    fun `lar andre http-feil forplante seg uten aa cache svaret`(metode: String) = runTest {
        responseStatus = HttpStatusCode.BadRequest

        medGateway("") { gateway ->
            val exception = assertFailsWith<ClientRequestException> { kall(gateway, metode) }
            assertThat(exception.response.status).isEqualTo(HttpStatusCode.BadRequest)
            coVerify(exactly = 0) { redis.set(any(), any(), any()) }
        }
    }

    @Test
    fun `bruker ukjent som correlation-id naar call-id mangler`() = runTest {
        medGateway("") { gateway ->
            assertThat(kall(gateway, "person", callId = null)).isTrue()
            assertThat(mottatteKall.single().callId).isEqualTo("ukjent")
        }
    }

    @Test
    fun `holder obo-token adskilt for samtidige brukere`() = runTest {
        medGateway("") { gateway ->
            coroutineScope {
                val svarA = async { kall(gateway, "person", token = tokenA) }
                val svarB = async { kall(gateway, "person", token = tokenB) }
                assertThat(svarA.await()).isTrue()
                assertThat(svarB.await()).isTrue()
            }
            assertThat(mottatteKall.map { it.authorization })
                .containsExactlyInAnyOrder("Bearer obo-a", "Bearer obo-b")
        }
    }

    private suspend fun kall(
        gateway: TilgangsmaskinGateway,
        metode: String,
        callId: String? = "call-id",
        token: OidcToken = tokenA,
    ): Boolean = when (metode) {
        "person" -> gateway.harTilgangTilPerson("123", token, callId)
        "bulk" -> gateway.harTilganger(listOf(BrukerOgRegeltype("123", "KJERNE")), token, callId)
        "kjerne" -> gateway.harTilgangTilPersonKjerne("123", token, "Z1", callId).harTilgang
        "komplett" -> gateway.harTilgangTilPersonKomplett("123", token, "Z1", callId).harTilgang
        else -> error("Ukjent testmetode")
    }

    private fun avvisning(endepunkt: String) = TilgangsmaskinAvvistResponse(
        type = "avvist",
        title = if (endepunkt == "kjerne") {
            TilgangsmaskinAvvistGrunn.AVVIST_HABILITET.name
        } else {
            TilgangsmaskinAvvistGrunn.AVVIST_GEOGRAFISK.name
        },
        status = 403,
        navIdent = "Z1",
        begrunnelse = "Ikke tilgang",
        kanOverstyres = false
    )

    private suspend fun medGateway(basePath: String, test: suspend (TilgangsmaskinGateway) -> Unit) {
        val server = embeddedServer(Netty, port = 0) {
            install(ContentNegotiation) {
                jackson()
            }
            routing {
                for (endepunkt in listOf("kjerne", "komplett", "bulk")) {
                    post("${basePath.trimEnd('/')}/api/v1/$endepunkt") {
                        assertThat(call.request.header("Nav-Consumer-Id")).isEqualTo("test-consumer")
                        mottatteKall.add(
                            MottattKall(
                                endepunkt,
                                call.receiveText(),
                                ContentType.parse(requireNotNull(call.request.header(HttpHeaders.ContentType)))
                                    .withoutParameters(),
                                call.request.header(HttpHeaders.Authorization),
                                call.request.header(HttpHeaders.XCorrelationId)
                            )
                        )
                        if (responseStatus == HttpStatusCode.Forbidden) {
                            call.respond(responseStatus, avvisning(endepunkt))
                        } else {
                            call.respond(responseStatus)
                        }
                    }
                }
            }
        }.start()
        val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

        try {
            val port = server.engine.resolvedConnectors().single().port
            val config = TilgangsmaskinConfig(
                URI("http://localhost:$port$basePath"),
                "tilgangsmaskin-test-scope",
                "test-consumer"
            )
            coEvery { tokenProvider.oboToken("tilgangsmaskin-test-scope", tokenA) } returns "obo-a"
            coEvery { tokenProvider.oboToken("tilgangsmaskin-test-scope", tokenB) } returns "obo-b"
            coEvery { redis[any()] } answers { cache[firstArg()] }
            coEvery { redis.set(any(), any(), any()) } answers {
                cache[firstArg()] = secondArg()
            }

            createTilgangsmaskinClient(config, tokenProvider).use { client ->
                test(TilgangsmaskinGateway(redis, client, prometheus))
            }
        } finally {
            server.stop(0, 0)
            prometheus.close()
        }
    }

    private data class MottattKall(
        val endepunkt: String,
        val body: String,
        val contentType: ContentType,
        val authorization: String?,
        val callId: String?,
    )
}