package tilgang.integrasjoner

import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import org.junit.jupiter.api.Test
import tilgang.AzureTokenGen
import tilgang.fakes.WithFakes
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistGrunn
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinClient
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TilgangsmaskinTest : WithFakes {
    @Test
    fun `Kan parse harTilgangTilPersonKjerne`() {
        val token = AzureTokenGen("tilgangazure", "tilgang").generate()
        val harTilgangResponse = TilgangsmaskinClient().harTilgangTilPersonKjerne("123", OidcToken(token))
        val harIkkeTilgangResponse = TilgangsmaskinClient().harTilgangTilPersonKjerne("456", OidcToken(token))

        assertTrue(harTilgangResponse.harTilgang)
        assertFalse(harIkkeTilgangResponse.harTilgang)
        assertTrue(harIkkeTilgangResponse.TilgangsmaskinAvvistResponse?.title == TilgangsmaskinAvvistGrunn.AVVIST_HABILITET.toString())
    }
}