package tilgang.integrasjoner

import kotlinx.coroutines.test.runTest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tilgang.AzureTokenGen
import tilgang.fakes.Fakes
import tilgang.fakes.WithFakes
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistGrunn
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway

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
        assertThat(harIkkeTilgangResponse.tilgangsmaskinAvvistResponse?.title == TilgangsmaskinAvvistGrunn.AVVIST_HABILITET.toString()).isTrue()
    }

    @Test
    fun `Kan parse harTilgangTilPersonKomplett`() = runTest {
        val token = AzureTokenGen("tilgangazure", "tilgang").generate()
        val tilgangsmaskinGateway = TilgangsmaskinGateway(redis, httpClient, prometheus)
        val harTilgangResponse = tilgangsmaskinGateway.harTilgangTilPersonKomplett("123", OidcToken(token), "799")
        val harIkkeTilgangResponse = tilgangsmaskinGateway.harTilgangTilPersonKomplett("456", OidcToken(token), "799")

        assertThat(harTilgangResponse.harTilgang).isTrue()
        assertThat(harIkkeTilgangResponse.harTilgang).isFalse()
        assertThat(harIkkeTilgangResponse.tilgangsmaskinAvvistResponse?.title == TilgangsmaskinAvvistGrunn.AVVIST_GEOGRAFISK.toString()).isTrue()
    }
}