package no.nav.aap.tilgang

import java.util.UUID
import no.nav.aap.tilgang.plugin.kontrakt.BehandlingreferanseResolver
import no.nav.aap.tilgang.plugin.kontrakt.Behandlingsreferanse
import no.nav.aap.tilgang.plugin.kontrakt.JournalpostIdResolver
import no.nav.aap.tilgang.plugin.kontrakt.Journalpostreferanse
import no.nav.aap.tilgang.plugin.kontrakt.Personreferanse
import no.nav.aap.tilgang.plugin.kontrakt.RelevanteIdenterResolver
import no.nav.aap.tilgang.plugin.kontrakt.Saksreferanse

data class AuthorizationBodyPathConfig(
    val operasjon: Operasjon,
    val applicationRole: String? = null,
    val authorizedAzps: List<UUID>? = null,
    val applicationsOnly: Boolean = false,
    val påkrevdRolle: List<Rolle> = emptyList(),
    val relevanteIdenterResolver: RelevanteIdenterResolver? = null,
    val journalpostIdResolver: JournalpostIdResolver = DefaultJournalpostIdResolver(),
    val behandlingreferanseResolver: BehandlingreferanseResolver = DefaultBehandlingreferanseResolver()
) : AuthorizationRouteConfig {

    init {
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

    suspend fun tilTilgangRequest(request: Any): AuthorizedRequest {
        when (request) {
            is Saksreferanse -> {
                val referanse = request.hentSaksreferanse()
                val relevanteIdenter = relevanteIdenterResolver?.resolve(referanse)
                return AuthorizedRequest(
                    applicationsOnly,
                    applicationRole,
                    authorizedAzps,
                    SakTilgangRequest(
                        saksnummer = referanse,
                        påkrevdRolle = påkrevdRolle,
                        operasjon = operasjon,
                        relevanteIdenter = relevanteIdenter
                    )
                )
            }

            is Personreferanse -> {
                val referanse = request.hentPersonreferanse()
                return AuthorizedRequest(
                    applicationsOnly,
                    applicationRole,
                    authorizedAzps,
                    PersonTilgangRequest(referanse)
                )
            }

            is Behandlingsreferanse -> {
                val referanse = behandlingreferanseResolver.resolve(request.behandlingsreferanseResolverInput())
                val relevanteIdenter = relevanteIdenterResolver?.resolve(referanse.toString())
                val påkrevdeRoller = påkrevdRolle.ifEmpty {
                    request.hentPåkrevdRolle()
                }

                return AuthorizedRequest(
                    applicationsOnly,
                    applicationRole,
                    authorizedAzps,
                    BehandlingTilgangRequest(
                        behandlingsreferanse = referanse,
                        påkrevdRolle = påkrevdeRoller,
                        operasjon = operasjon,
                        relevanteIdenter = relevanteIdenter
                    )
                )
            }

            is Journalpostreferanse -> {
                val referanse =
                    journalpostIdResolver.resolve(request.journalpostIdResolverInput())

                val påkrevdeRoller = påkrevdRolle.ifEmpty {
                    request.hentPåkrevdRolle()
                }
                return AuthorizedRequest(
                    applicationsOnly,
                    applicationRole,
                    authorizedAzps,
                    JournalpostTilgangRequest(
                        journalpostId = referanse,
                        påkrevdRolle = påkrevdeRoller,
                        operasjon = operasjon
                    )
                )
            }

            else -> return AuthorizedRequest(
                applicationsOnly = applicationsOnly,
                applicationRole = applicationRole,
                authorizedAzps = authorizedAzps,
                tilgangRequest = null
            )
        }
    }
}