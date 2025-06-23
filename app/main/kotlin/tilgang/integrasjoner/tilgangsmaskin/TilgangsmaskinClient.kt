package tilgang.integrasjoner.tilgangsmaskin

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.error.ManglerTilgangException
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import java.net.URI
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.ContentType
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.OnBehalfOfTokenProvider
import org.slf4j.LoggerFactory

interface ITilgangsmaskinClient {
    fun harTilgangTilPerson(brukerIdent: String, token: OidcToken): Boolean
    fun harTilganger(brukerIdenter: List<BrukerOgRegeltype>, token: OidcToken): Boolean
}

private val log = LoggerFactory.getLogger(TilgangsmaskinClient::class.java)

/**
 * Se Confluence for dukumentasjon.
 * https://confluence.adeo.no/spaces/TM/pages/628888614/Intro+til+Tilgangsmaskinen
 */
class TilgangsmaskinClient() : ITilgangsmaskinClient {
    private val config = ClientConfig(
        scope = requiredConfigForKey("integrasjon.tilgangsmaskin.scope")
    )

    private val baseUrl = URI.create(requiredConfigForKey("integrasjon.tilgangsmaskin.url"))
    private val httpClient = RestClient.withDefaultResponseHandler(
        tokenProvider = OnBehalfOfTokenProvider,
        config = config,
    )

    override fun harTilgangTilPerson(
        brukerIdent: String,
        token: OidcToken
    ): Boolean {
        val url = baseUrl.resolve("/api/v1/komplett")
        val request = PostRequest(
            body = brukerIdent,
            currentToken = token,
            contentType = ContentType.TEXT_PLAIN
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

    override fun harTilganger(
        brukerIdenter: List<BrukerOgRegeltype>,
        token: OidcToken
    ): Boolean {
        val url = baseUrl.resolve("/api/v1/bulk")
        val request = PostRequest(
            body = brukerIdenter,
            currentToken = token
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




