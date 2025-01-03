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
    val applicationRole: String? = null,
    val approvedApplications: Set<String>? = null,
    val applicationsOnly: Boolean = false,
    val sakPathParam: SakPathParam? = null,
    val behandlingPathParam: BehandlingPathParam? = null,
    val journalpostPathParam: JournalpostPathParam? = null
) : AuthorizetionRouteConfig {
    init {
        require(operasjon != Operasjon.SAKSBEHANDLE || avklaringsbehovKode != null) {
            "Avklaringsbehovkode må være satt for operasjon SAKSBEHANDLE"
        }

        if (applicationRole != null) {
            require(approvedApplications == null) {
                "approvedApplications skal ikke settes når applicationRole er satt"
            }
        }
    }

    internal fun tilTilgangRequest(parameters: Parameters): AuthorizedRequest {
        if (sakPathParam != null) {
            return AuthorizedRequest(
                applicationsOnly,
                applicationRole,
                approvedApplications,
                SakTilgangRequest(saksnummer = parameters.getOrFail(sakPathParam.param), operasjon = operasjon)
            )
        }
        if (behandlingPathParam != null) {
            return AuthorizedRequest(
                applicationsOnly,
                applicationRole,
                approvedApplications,
                BehandlingTilgangRequest(
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
                applicationRole,
                approvedApplications,
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
            approvedApplications = approvedApplications,
            tilgangRequest = null
        )
    }
}

data class AuthorizationBodyPathConfig(
    val operasjon: Operasjon,
    val applicationRole: String? = null,
    val approvedApplications: Set<String>? = null,
    val applicationsOnly: Boolean = false,
    val journalpostIdResolver: JournalpostIdResolver? = DefaultJournalpostIdResolver()
) : AuthorizetionRouteConfig {
    init {
        if (applicationRole != null) {
            require(approvedApplications == null) {
                "approvedApplications skal ikke settes når applicationRole er satt"
            }
        }
    }

    fun tilTilgangRequest(request: Any): AuthorizedRequest {
        when (request) {
            is TilgangReferanse ->
                when (request) {
                    is Saksreferanse -> {
                        val referanse = request.hentSaksreferanse()
                        return AuthorizedRequest(
                            applicationsOnly,
                            applicationRole,
                            approvedApplications,
                            SakTilgangRequest(referanse, operasjon)
                        )
                    }

                    is Behandlingsreferanse -> {
                        val referanse = request.hentBehandlingsreferanse()
                        val avklaringsbehovKode = request.hentAvklaringsbehovKode()
                        return AuthorizedRequest(
                            applicationsOnly,
                            applicationRole,
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
                            applicationRole,
                            approvedApplications,
                            JournalpostTilgangRequest(referanse, avklaringsbehovKode, operasjon)
                        )
                    }
                }

            else -> return AuthorizedRequest(
                applicationsOnly = applicationsOnly,
                applicationRole = applicationRole,
                approvedApplications = approvedApplications,
                tilgangRequest = null
            )

        }
    }
}

interface AuthorizetionRouteConfig


data class AuthorizedRequest(
    val applicationsOnly: Boolean,
    val applicationRole: String?,
    val approvedApplications: Set<String>?,
    val tilgangRequest: TilgangRequest?
)
