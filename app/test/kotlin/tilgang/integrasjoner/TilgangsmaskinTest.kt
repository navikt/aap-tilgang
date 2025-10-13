package tilgang.integrasjoner

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import tilgang.AzureTokenGen
import tilgang.fakes.Fakes
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistGrunn
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinClient
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TilgangsmaskinTest {
    companion object {
        private val FAKES = Fakes()

        @AfterAll
        @JvmStatic
        fun afterall() {
            FAKES.close()
        }
    }

    @Test
    fun `Kan parse harTilgangTilPersonKjerne`() {
        val token = AzureTokenGen("tilgangazure", "tilgang").generate()
        val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val client = TilgangsmaskinClient(FAKES.redis, prometheus)
        val harTilgangResponse = client.harTilgangTilPersonKjerne("123", OidcToken(token), "799")
        val harIkkeTilgangResponse = client.harTilgangTilPersonKjerne("456", OidcToken(token), "799")

        assertTrue(harTilgangResponse.harTilgang)
        assertFalse(harIkkeTilgangResponse.harTilgang)
        assertTrue(harIkkeTilgangResponse.tilgangsmaskinAvvistResponse?.title == TilgangsmaskinAvvistGrunn.AVVIST_HABILITET.toString())
    }
}