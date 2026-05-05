package no.nav.aap.tilgang

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
    val applicationsOnly: Boolean = false,
    val påkrevdRolle: List<Rolle> = emptyList(),
    val relevanteIdenterResolver: RelevanteIdenterResolver? = null,
    val journalpostIdResolver: JournalpostIdResolver = DefaultJournalpostIdResolver(),
    val behandlingreferanseResolver: BehandlingreferanseResolver = DefaultBehandlingreferanseResolver()
) : AuthorizationRouteConfig {
    fun tilTilgangRequest(request: Any): AuthorizedRequest {
        when (request) {
            is Saksreferanse -> {
                val referanse = request.hentSaksreferanse()
                val relevanteIdenter = relevanteIdenterResolver?.resolve(referanse)
                return AuthorizedRequest(
                    applicationsOnly,
                    applicationRole,
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
                val avklaringsbehovKode = request.hentAvklaringsbehovKode()
                return AuthorizedRequest(
                    applicationsOnly,
                    applicationRole,
                    JournalpostTilgangRequest(
                        journalpostId = referanse,
                        avklaringsbehovKode = avklaringsbehovKode,
                        påkrevdRolle = påkrevdRolle,
                        operasjon = operasjon
                    )
                )
            }

            else -> return AuthorizedRequest(
                applicationsOnly = applicationsOnly,
                applicationRole = applicationRole,
                tilgangRequest = null
            )
        }
    }
}