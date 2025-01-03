package no.nav.aap.tilgang

import io.ktor.server.application.ApplicationCall
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import tilgang.BehandlingTilgangRequest
import tilgang.JournalpostTilgangRequest
import tilgang.SakTilgangRequest

object TilgangService {
    fun harTilgang(
        authorizedRequest: AuthorizedRequest,
        call: ApplicationCall,
        token: OidcToken
    ): Boolean {
        if (token.isClientCredentials()) {
            return if (authorizedRequest.applicationRole != null) {
                call.rolesClaim().contains(authorizedRequest.applicationRole)
            } else {
                val azpName = call.azp()
                authorizedRequest.approvedApplications?.contains(azpName.name) == true
            }
        }
        if (authorizedRequest.applicationsOnly) {
            return false
        }
        val request = requireNotNull(authorizedRequest.tilgangRequest) {
            "Kan ikke utlede tilgangRequest for OBO-token."
        }
        return when (request) {
            is SakTilgangRequest -> TilgangGateway.harTilgangTilSak(request, token)
            is BehandlingTilgangRequest -> TilgangGateway.harTilgangTilBehandling(request, token)
            is JournalpostTilgangRequest -> TilgangGateway.harTilgangTilJournalpost(request, token)
        }
    }
}
