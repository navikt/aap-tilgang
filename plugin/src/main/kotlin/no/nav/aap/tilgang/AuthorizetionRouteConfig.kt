package no.nav.aap.tilgang

import io.ktor.http.Parameters
import io.ktor.server.util.getOrFail
import no.nav.aap.tilgang.plugin.kontrakt.Behandlingsreferanse
import no.nav.aap.tilgang.plugin.kontrakt.JournalpostIdResolver
import no.nav.aap.tilgang.plugin.kontrakt.Journalpostreferanse
import no.nav.aap.tilgang.plugin.kontrakt.Saksreferanse
import no.nav.aap.tilgang.plugin.kontrakt.TilgangReferanse
import tilgang.BehandlingTilgangRequest
import tilgang.JournalpostTilgangRequest
import tilgang.Operasjon
import tilgang.SakTilgangRequest
import tilgang.TilgangRequest

data class AuthorizationParamPathConfig(
    val operasjon: Operasjon = Operasjon.SE,
    val avklaringsbehovKode: String? = null,
    val approvedApplications: Set<String> = emptySet(),
    val applicationsOnly: Boolean = false,
    val sakPathParam: SakPathParam? = null,
    val behandlingPathParam: BehandlingPathParam? = null,
    val journalpostPathParam: JournalpostPathParam? = null
) : AuthorizetionRouteConfig {

    internal fun tilTilgangRequest(parameters: Parameters): AuthorizedRequest {
        require(operasjon != Operasjon.SAKSBEHANDLE || avklaringsbehovKode != null) {
            "Avklaringsbehovkode må være satt for operasjon SAKSBEHANDLE"
        }

        if (sakPathParam != null) {
            return AuthorizedRequest(
                applicationsOnly,
                approvedApplications,
                SakTilgangRequest(saksnummer = parameters.getOrFail(sakPathParam.param), operasjon = operasjon)
            )
        }
        if (behandlingPathParam != null) {
            return AuthorizedRequest(
                applicationsOnly,
                approvedApplications, BehandlingTilgangRequest(
                    behandlingsreferanse = parameters.getOrFail(behandlingPathParam.param),
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
                approvedApplications, JournalpostTilgangRequest(
                    journalpostId = journalpostId,
                    operasjon = operasjon,
                    avklaringsbehovKode = avklaringsbehovKode
                )
            )
        }
        return AuthorizedRequest(
            applicationsOnly = applicationsOnly,
            approvedApplications = approvedApplications,
            tilgangRequest = null
        )
    }
}

data class AuthorizationBodyPathConfig(
    val operasjon: Operasjon,
    val approvedApplications: Set<String> = emptySet(),
    val applicationsOnly: Boolean = false,
    val journalpostIdResolver: JournalpostIdResolver? = DefaultJournalpostIdResolver()
) : AuthorizetionRouteConfig {

    fun tilTilgangRequest(request: Any): AuthorizedRequest {
        when (request) {
            is TilgangReferanse ->
                when (request) {
                    is Saksreferanse -> {
                        val referanse = request.hentSaksreferanse()
                        return AuthorizedRequest(
                            applicationsOnly,
                            approvedApplications,
                            SakTilgangRequest(referanse, operasjon)
                        )
                    }

                    is Behandlingsreferanse -> {
                        val referanse = request.hentBehandlingsreferanse()
                        val avklaringsbehovKode = request.hentAvklaringsbehovKode()
                        return AuthorizedRequest(
                            applicationsOnly,
                            approvedApplications,
                            BehandlingTilgangRequest(referanse, avklaringsbehovKode, operasjon)
                        )
                    }

                    is Journalpostreferanse -> {
                        val referanse =
                            requireNotNull(journalpostIdResolver).resolve(request.journalpostIdResolverInput())
                        val avklaringsbehovKode = request.hentAvklaringsbehovKode()
                        return AuthorizedRequest(
                            applicationsOnly,
                            approvedApplications,
                            JournalpostTilgangRequest(referanse, avklaringsbehovKode, operasjon)
                        )
                    }
                }

            else -> return AuthorizedRequest(
                applicationsOnly = applicationsOnly,
                approvedApplications = approvedApplications,
                tilgangRequest = null
            )

        }
    }
}

interface AuthorizetionRouteConfig


data class AuthorizedRequest(
    val applicationsOnly: Boolean,
    val approvedApplications: Set<String> = emptySet(),
    val tilgangRequest: TilgangRequest?
)
