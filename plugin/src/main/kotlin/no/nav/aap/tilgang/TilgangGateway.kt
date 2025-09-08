package no.nav.aap.tilgang

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.OnBehalfOfTokenProvider
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.tokenx.OnBehalfOfTokenProvider as TexasOnBehalfOfTokenProvider
import java.net.URI
import no.nav.aap.komponenter.miljo.Miljø

object TilgangGateway {
    private val baseUrl = URI.create(requiredConfigForKey("integrasjon.tilgang.url"))
    private val config = ClientConfig(scope = requiredConfigForKey("integrasjon.tilgang.scope"))

    private val client = RestClient.withDefaultResponseHandler(
        config = config,
        tokenProvider = (if (Miljø.erProd()) OnBehalfOfTokenProvider else TexasOnBehalfOfTokenProvider(identityProvider = "azuread")),
    )

    fun harTilgangTilSak(body: SakTilgangRequest, currentToken: OidcToken): TilgangResponse {
        val httpRequest = PostRequest(
            body = body,
            currentToken = currentToken
        )
        val respons = requireNotNull(
            client.post<_, TilgangResponse>(
                uri = baseUrl.resolve("/tilgang/sak"),
                request = httpRequest
            )
        )
        return respons
    }

    fun harTilgangTilBehandling(body: BehandlingTilgangRequest, currentToken: OidcToken): TilgangResponse {
        val httpRequest = PostRequest(
            body = body,
            currentToken = currentToken
        )
        val respons = requireNotNull(
            client.post<_, TilgangResponse>(
                uri = baseUrl.resolve("/tilgang/behandling"),
                request = httpRequest
            )
        )
        return respons
    }

    fun harTilgangTilJournalpost(
        body: JournalpostTilgangRequest,
        currentToken: OidcToken
    ): TilgangResponse {
        val httpRequest = PostRequest(
            body = body,
            currentToken = currentToken
        )
        val respons = requireNotNull(
            client.post<_, TilgangResponse>(
                uri = baseUrl.resolve("/tilgang/journalpost"),
                request = httpRequest
            )
        )
        return respons
    }

    fun harTilgangTilPerson(body: PersonTilgangRequest, currentToken: OidcToken): TilgangResponse {
        val httpRequest = PostRequest(
            body = body,
            currentToken = currentToken
        )
        val respons = requireNotNull(
            client.post<_, TilgangResponse>(
                uri = baseUrl.resolve("/tilgang/person"),
                request = httpRequest
            )
        )
        return respons
    }
}
