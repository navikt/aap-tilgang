package no.nav.aap.tilgang

import io.ktor.http.Parameters
import io.ktor.server.util.getOrFail
import java.util.UUID
import no.nav.aap.tilgang.plugin.kontrakt.RelevanteIdenterResolver

/**
 * Informasjon som tilgang trenger for å utlede kontekst fra path til requesten.
 *
 * @param avklaringsbehovKode Om operasjonen er [Operasjon.SAKSBEHANDLE], trenger tilgang også å vite hvilket avklaringsbehov som skal saksbehandles.
 */
public data class AuthorizationParamPathConfig(
    val operasjon: Operasjon = Operasjon.SE,
    val operasjonerIKontekst: List<Operasjon> = emptyList(),
    val avklaringsbehovKode: String? = null,
    val påkrevdRolle: List<Rolle> = emptyList(),
    val authorizedAzps: List<UUID>? = null,
    val applicationRole: String? = null,
    val applicationsOnly: Boolean = false,
    val sakPathParam: SakPathParam? = null,
    val relevanteIdenterResolver: RelevanteIdenterResolver? = null,
    val behandlingPathParam: BehandlingPathParam? = null,
    val journalpostPathParam: JournalpostPathParam? = null,
) : AuthorizationRouteConfig {
    init {
        require(operasjon != Operasjon.SAKSBEHANDLE || avklaringsbehovKode != null || påkrevdRolle.isNotEmpty()) {
            "Avklaringsbehovkode eller påkrevdRolle må være satt for operasjon SAKSBEHANDLE"
        }
        val hasApplicationRole = applicationRole != null
        val hasAuthorizedAzps = authorizedAzps != null

        require(!(hasApplicationRole && hasAuthorizedAzps)) {
            "Kan ikke sette både applicationRole og authorizedAzps"
        }

        if (applicationsOnly) {
            require(hasApplicationRole || hasAuthorizedAzps) {
                "applicationRole eller authorizedAzps må være satt dersom applicationsOnly = true"
            }
        }
    }

    public suspend fun tilTilgangRequest(parameters: Parameters): AuthorizedRequest {
        if (sakPathParam != null) {
            val saksnummer = parameters.getOrFail(sakPathParam.param)
            val relevanteIdenter = relevanteIdenterResolver?.resolve(saksnummer)
            return AuthorizedRequest(
                applicationsOnly,
                applicationRole,
                authorizedAzps,
                SakTilgangRequest(
                    relevanteIdenter = relevanteIdenter,
                    saksnummer = saksnummer,
                    operasjon = operasjon,
                    påkrevdRolle = påkrevdRolle,
                )
            )
        }
        if (behandlingPathParam != null) {
            val behandlingsreferanse = behandlingPathParam.resolver.resolve(parameters.getOrFail(behandlingPathParam.param))
            val relevanteIdenter = relevanteIdenterResolver?.resolve(behandlingsreferanse.toString())
            return AuthorizedRequest(
                applicationsOnly,
                applicationRole,
                authorizedAzps,
                BehandlingTilgangRequest(
                    relevanteIdenter = relevanteIdenter,
                    behandlingsreferanse = behandlingsreferanse,
                    operasjon = operasjon,
                    avklaringsbehovKode = avklaringsbehovKode,
                    påkrevdRolle = påkrevdRolle,
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
                authorizedAzps,
                JournalpostTilgangRequest(
                    journalpostId = journalpostId,
                    operasjon = operasjon,
                    avklaringsbehovKode = avklaringsbehovKode,
                    påkrevdRolle = påkrevdRolle
                )
            )
        }

        return AuthorizedRequest(
            applicationsOnly = applicationsOnly,
            applicationRole = applicationRole,
            authorizedAzps = authorizedAzps,
            tilgangRequest = null
        )
    }
}