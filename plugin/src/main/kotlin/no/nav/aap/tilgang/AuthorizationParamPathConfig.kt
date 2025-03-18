package no.nav.aap.tilgang

import io.ktor.http.Parameters
import io.ktor.server.util.getOrFail

/**
 * Informasjon som tilgang trenger for å utlede kontekst fra path til requesten.
 *
 * @param avklaringsbehovKode Om operasjonen er [Operasjon.SAKSBEHANDLE], trenger tilgang også å vite hvilket avklaringsbehov som skal saksbehandles.
 */
data class AuthorizationParamPathConfig(
    val operasjon: Operasjon = Operasjon.SE,
    val avklaringsbehovKode: String? = null,
    val applicationRole: String? = null,
    val applicationsOnly: Boolean = false,
    val sakPathParam: SakPathParam? = null,
    val behandlingPathParam: BehandlingPathParam? = null,
    val journalpostPathParam: JournalpostPathParam? = null
) : AuthorizationRouteConfig {
    init {
        require(operasjon != Operasjon.SAKSBEHANDLE || avklaringsbehovKode != null) {
            "Avklaringsbehovkode må være satt for operasjon SAKSBEHANDLE"
        }
        if (applicationsOnly) {
            requireNotNull(applicationRole) {
                "applicationRole må være satt dersom applicationsOnly = true"
            }
        }
    }

    fun tilTilgangRequest(parameters: Parameters): AuthorizedRequest {
        if (sakPathParam != null) {
            return AuthorizedRequest(
                applicationsOnly,
                applicationRole,
                SakTilgangRequest(
                    saksnummer = parameters.getOrFail(sakPathParam.param),
                    operasjon = operasjon
                )
            )
        }
        if (behandlingPathParam != null) {
            return AuthorizedRequest(
                applicationsOnly,
                applicationRole,
                BehandlingTilgangRequest(
                    behandlingsreferanse = behandlingPathParam.resolver.resolve(parameters.getOrFail(behandlingPathParam.param)),
                    operasjon = operasjon,
                    avklaringsbehovKode = avklaringsbehovKode
                )
            )
        }
        if (journalpostPathParam != null) {
            val journalpostId =
                journalpostPathParam.resolver.resolve(parameters.getOrFail(journalpostPathParam.param))
            return AuthorizedRequest(
                applicationsOnly,
                applicationRole,
                JournalpostTilgangRequest(
                    journalpostId = journalpostId,
                    operasjon = operasjon,
                    avklaringsbehovKode = avklaringsbehovKode
                )
            )
        }
        return AuthorizedRequest(
            applicationsOnly = applicationsOnly,
            applicationRole = applicationRole,
            tilgangRequest = null
        )
    }
}