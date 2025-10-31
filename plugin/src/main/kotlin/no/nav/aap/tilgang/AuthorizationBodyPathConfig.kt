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
                    SakTilgangRequest(referanse, operasjon, relevanteIdenter)
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
                val avklaringsbehovKode = request.hentAvklaringsbehovKode()
                val relevanteIdenter = relevanteIdenterResolver?.resolve(referanse.toString())
                return AuthorizedRequest(
                    applicationsOnly,
                    applicationRole,
                    BehandlingTilgangRequest(referanse, avklaringsbehovKode, operasjon, relevanteIdenter)
                )
            }

            is Journalpostreferanse -> {
                val referanse =
                    journalpostIdResolver.resolve(request.journalpostIdResolverInput())
                val avklaringsbehovKode = request.hentAvklaringsbehovKode()
                return AuthorizedRequest(
                    applicationsOnly,
                    applicationRole,
                    JournalpostTilgangRequest(referanse, avklaringsbehovKode, operasjon)
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