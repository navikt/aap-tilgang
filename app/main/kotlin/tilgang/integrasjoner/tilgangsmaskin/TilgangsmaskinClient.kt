package tilgang.integrasjoner.tilgangsmaskin

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.error.ManglerTilgangException
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import java.net.URI
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.OnBehalfOfTokenProvider
import org.slf4j.LoggerFactory

interface ITilgangsmaskinClient {
    fun harTilganger(brukerIdenter: List<BrukerOgRegeltype>): Boolean
}

private val log = LoggerFactory.getLogger(TilgangsmaskinClient::class.java)

class TilgangsmaskinClient() : ITilgangsmaskinClient {

    private val baseUrl = URI.create(requiredConfigForKey("integrasjon.tilgangsmaskin.url"))
    private val httpClient = RestClient.withDefaultResponseHandler(
        tokenProvider = OnBehalfOfTokenProvider,
        config = ClientConfig(),
    )

    override fun harTilganger(
        brukerIdenter: List<BrukerOgRegeltype>
    ): Boolean {
        val url = baseUrl.resolve("/api/v1/bulk")
        val request = PostRequest(
            body = TilgangsmaskinRequest(brukerIdenter)
        )

        try {
            log.info("Kaller tilgangsmaskin med url: $url")
            httpClient.post<_, Unit>(url, request)
            return true
        } catch (e: ManglerTilgangException) {
            log.info("Kall til tilgangsmaskin returnerte 403")
            return false
        }

    }


}




