package tilgang.integrasjoner.tilgangsmaskin

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.error.ManglerTilgangException
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import java.net.URI

interface ITilgangsmaskinClient {
    fun harTilgang(ansattIdent: String, søkerIdent: String): Boolean
    fun harTilganger(ansattIdent: String, brukerIdenter: List<BrukerOgRegeltype>): Boolean
}

class TilgangsmaskinClient() : ITilgangsmaskinClient {

    private val baseUrl = URI.create(requiredConfigForKey("integrasjon.tilgangsmaskin.url"))
    private val httpClient = RestClient.withDefaultResponseHandler(
        tokenProvider = ClientCredentialsTokenProvider,
        config = ClientConfig(),
    )

    override fun harTilgang(
        ansattIdent: String,
        søkerIdent: String,
    ): Boolean {
        val url = baseUrl.resolve("/dev/kjerne/$ansattIdent/$søkerIdent") // dev og prod har ikke samme url
        val response = httpClient.get<TilgangsmaskinResponse>(url, GetRequest())

        return false
    }

    override fun harTilganger(
        ansattIdent: String,
        brukerIdenter: List<BrukerOgRegeltype>
    ): Boolean {
        TODO("Not yet implemented")
        val url = baseUrl.resolve("/dev/bulk/$ansattIdent") // dev
        val request = PostRequest(
            body = TilgangmaskinBulkRequest(brukerIdenter)
        )

        try {
            httpClient.post<_, Unit>(url, request)
            return true
        } catch (e: ManglerTilgangException) {
            return false
        }

    }


}

data class BrukerOgRegeltype(val brukerId: String, val type: String)

data class TilgangmaskinBulkRequest(val input: List<BrukerOgRegeltype>)


