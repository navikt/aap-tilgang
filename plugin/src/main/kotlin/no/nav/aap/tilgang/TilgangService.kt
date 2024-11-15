package no.nav.aap.tilgang

import io.ktor.server.application.ApplicationCall
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import tilgang.BehandlingTilgangRequest
import tilgang.JournalpostTilgangRequest
import tilgang.SakTilgangRequest

object TilgangService {
    fun harTilgang(parse: AuthorizedRequest,
                            call: ApplicationCall,
                            token: OidcToken): Boolean {
        if (token.isClientCredentials()) {
            val azpName = call.azp()
            return parse.approvedApplications.contains(azpName.name)
        } else {
            val request = parse.tilgangRequest
            return when (request) {
                is SakTilgangRequest -> TilgangGateway.harTilgangTilSak(request, token)
                is BehandlingTilgangRequest -> TilgangGateway.harTilgangTilBehandling(request, token)
                is JournalpostTilgangRequest -> TilgangGateway.harTilgangTilJournalpost(request, token)
            }
        }
    }
}
