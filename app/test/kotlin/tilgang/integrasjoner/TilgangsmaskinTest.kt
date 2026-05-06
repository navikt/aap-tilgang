package tilgang.integrasjoner

import kotlinx.coroutines.test.runTest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tilgang.AzureTokenGen
import tilgang.fakes.Fakes
import tilgang.fakes.WithFakes
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistGrunn
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway

@WithFakes
class TilgangsmaskinTest {
    private val redis = Fakes.getRedisServer()
    private val prometheus = Fakes.getPrometheus()

    @Test
    fun `Kan parse harTilgangTilPersonKjerne`() = runTest {
        val token = AzureTokenGen("tilgangazure", "tilgang").generate()
        val tilgangsmaskinGateway = TilgangsmaskinGateway(redis, prometheus)
        val harTilgangResponse = tilgangsmaskinGateway.harTilgangTilPersonKjerne("123", OidcToken(token), "799")
        val harIkkeTilgangResponse = tilgangsmaskinGateway.harTilgangTilPersonKjerne("456", OidcToken(token), "799")

        assertTrue(harTilgangResponse.harTilgang)
        assertFalse(harIkkeTilgangResponse.harTilgang)
        assertTrue(harIkkeTilgangResponse.tilgangsmaskinAvvistResponse?.title == TilgangsmaskinAvvistGrunn.AVVIST_HABILITET.toString())
    }
}