package no.nav.aap.tilgang

import io.ktor.server.application.ApplicationCall
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken

object TilgangService {
    fun harTilgang(
        authorizedRequest: AuthorizedRequest,
        call: ApplicationCall,
        token: OidcToken
    ): TilgangResponse {
        if (token.isClientCredentials()) {
            return TilgangResponse(authorizedRequest.applicationRole != null &&
                    call.rolesClaim().contains(authorizedRequest.applicationRole))
        }
        if (authorizedRequest.applicationsOnly) {
            return TilgangResponse(false)
        }
        val request = requireNotNull(authorizedRequest.tilgangRequest) {
            "Kan ikke utlede tilgangRequest for OBO-token."
        }
        return when (request) {
            is SakTilgangRequest -> TilgangGateway.harTilgangTilSak(request, token)
            is BehandlingTilgangRequest -> TilgangGateway.harTilgangTilBehandling(request, token)
            is JournalpostTilgangRequest -> TilgangGateway.harTilgangTilJournalpost(request, token)
            is PersonTilgangRequest -> TilgangGateway.harTilgangTilPerson(request, token)
        }
    }
}
