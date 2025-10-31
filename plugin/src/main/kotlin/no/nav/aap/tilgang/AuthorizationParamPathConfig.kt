package no.nav.aap.tilgang

import io.ktor.http.Parameters
import io.ktor.server.util.getOrFail
import no.nav.aap.tilgang.plugin.kontrakt.RelevanteIdenterResolver

/**
 * Informasjon som tilgang trenger for å utlede kontekst fra path til requesten.
 *
 * @param avklaringsbehovKode Om operasjonen er [Operasjon.SAKSBEHANDLE], trenger tilgang også å vite hvilket avklaringsbehov som skal saksbehandles.
 */
data class AuthorizationParamPathConfig(
    val operasjon: Operasjon = Operasjon.SE,
    val operasjonerIKontekst: List<Operasjon> = emptyList(),
    val avklaringsbehovKode: String? = null,
    val applicationRole: String? = null,
    val applicationsOnly: Boolean = false,
    val sakPathParam: SakPathParam? = null,
    val relevanteIdenterResolver: RelevanteIdenterResolver? = null,
    val behandlingPathParam: BehandlingPathParam? = null,
    val journalpostPathParam: JournalpostPathParam? = null,
    val personIdentPathParam: PersonIdentPathParam? = null,
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
            val saksnummer = parameters.getOrFail(sakPathParam.param)
            val relevanteIdenter = relevanteIdenterResolver?.resolve(saksnummer)
            return AuthorizedRequest(
                applicationsOnly,
                applicationRole,
                SakTilgangRequest(
                    relevanteIdenter = relevanteIdenter,
                    saksnummer = saksnummer,
                    operasjon = operasjon
                )
            )
        }
        if (behandlingPathParam != null) {
            val behandlingsreferanse = behandlingPathParam.resolver.resolve(parameters.getOrFail(behandlingPathParam.param))
            val relevanteIdenter = relevanteIdenterResolver?.resolve(behandlingsreferanse.toString())
            return AuthorizedRequest(
                applicationsOnly,
                applicationRole,
                BehandlingTilgangRequest(
                    relevanteIdenter = relevanteIdenter,
                    behandlingsreferanse = behandlingsreferanse,
                    operasjon = operasjon,
                    avklaringsbehovKode = avklaringsbehovKode,
                    operasjonerIKontekst = operasjonerIKontekst
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

        if (personIdentPathParam != null) {
            return AuthorizedRequest(
                applicationsOnly,
                applicationRole,
                PersonTilgangRequest(
                    personIdent = parameters.getOrFail(personIdentPathParam.param),
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