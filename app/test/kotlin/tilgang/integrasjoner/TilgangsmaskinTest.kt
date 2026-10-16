package tilgang.integrasjoner

import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.test.runTest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tilgang.AzureTokenGen
import tilgang.fakes.Fakes
import tilgang.fakes.WithFakes
import tilgang.integrasjoner.tilgangsmaskin.BrukerOgRegeltype
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistGrunn
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import kotlin.test.assertFailsWith

@WithFakes
class TilgangsmaskinTest {
    private val redis = Fakes.getRedisServer()
    private val httpClient = Fakes.getHttpClient()
    private val prometheus = Fakes.getPrometheus()

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
}