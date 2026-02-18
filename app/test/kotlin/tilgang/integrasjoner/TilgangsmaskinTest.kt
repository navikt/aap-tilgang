package tilgang.integrasjoner

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import org.junit.jupiter.api.Test
import tilgang.AzureTokenGen
import tilgang.fakes.Fakes
import tilgang.fakes.WithFakes
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistGrunn
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway

@WithFakes
class TilgangsmaskinTest {

    @Test
    fun `Kan parse harTilgangTilPersonKjerne`() {
        val token = AzureTokenGen("tilgangazure", "tilgang").generate()
        val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val tilgangsmaskinGateway = TilgangsmaskinGateway(Fakes.redis.server, prometheus)
        val harTilgangResponse = tilgangsmaskinGateway.harTilgangTilPersonKjerne("123", OidcToken(token), "799")
        val harIkkeTilgangResponse = tilgangsmaskinGateway.harTilgangTilPersonKjerne("456", OidcToken(token), "799")

        assertTrue(harTilgangResponse.harTilgang)
        assertFalse(harIkkeTilgangResponse.harTilgang)
        assertTrue(harIkkeTilgangResponse.tilgangsmaskinAvvistResponse?.title == TilgangsmaskinAvvistGrunn.AVVIST_HABILITET.toString())
    }
}