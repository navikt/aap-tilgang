package no.nav.aap.tilgang

import io.ktor.server.application.ApplicationCall
import java.util.UUID
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken

object TilgangService {
    suspend fun harTilgang(
        authorizedRequest: AuthorizedRequest,
        call: ApplicationCall,
        token: OidcToken
    ): TilgangResponse {

        val azp = call.getClaimOrNull<UUID>("azp")
        val isAuthorizedAzp = azp != null &&
                authorizedRequest.authorizedAzps != null &&
                authorizedRequest.authorizedAzps.contains(azp)

        if (token.isClientCredentials()) {
            val isAuthorizedRole = authorizedRequest.applicationRole != null &&
                    call.rolesClaim().contains(authorizedRequest.applicationRole)
            return TilgangResponse(isAuthorizedAzp || isAuthorizedRole)
        }

        if (authorizedRequest.authorizedAzps != null && !isAuthorizedAzp) {
            return TilgangResponse(false)
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
            is TilbakekrevingTilgangRequest -> TilgangGateway.harTilgangTilTilbakekreving(request, token)
        }
    }
}
