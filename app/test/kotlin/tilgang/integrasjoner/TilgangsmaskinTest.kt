package tilgang.integrasjoner

import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import org.junit.jupiter.api.Test
import tilgang.AzureTokenGen
import tilgang.fakes.WithFakes
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistGrunn
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinClient
import kotlin.test.assertTrue

class TilgangsmaskinTest : WithFakes {
    @Test
    fun `Kan parse harTilgangTilPersonKjerne`() {
        val token = AzureTokenGen("tilgangazure", "tilgang").generate()
        val harTilgang = TilgangsmaskinClient().harTilgangTilPersonKjerne("123", OidcToken(token))
        val harIkkeTilgang = TilgangsmaskinClient().harTilgangTilPersonKjerne("456", OidcToken(token))

        assertTrue(harTilgang.harTilgang)
        assertTrue(harIkkeTilgang.harTilgang)
        assertTrue { harTilgang.TilgangsmaskinAvvistResponse?.title == TilgangsmaskinAvvistGrunn.AVVIST_HABILITET.toString() }
    }
}