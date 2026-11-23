package no.nav.aap.tilgang.config

import java.util.UUID
import no.nav.aap.tilgang.BehandlingTilgangRequest
import no.nav.aap.tilgang.JournalpostTilgangRequest
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.PersonTilgangRequest
import no.nav.aap.tilgang.Rolle
import no.nav.aap.tilgang.SakTilgangRequest
import no.nav.aap.tilgang.TilgangRequest
import no.nav.aap.tilgang.resolver.BehandlingreferanseResolver
import no.nav.aap.tilgang.resolver.DefaultBehandlingreferanseResolver
import no.nav.aap.tilgang.resolver.DefaultJournalpostIdResolver
import no.nav.aap.tilgang.resolver.JournalpostIdResolver
import no.nav.aap.tilgang.resolver.RelevanteIdenterResolver

/**
 * Tilgangskontroll basert på request body.
 *
 * @property kontekst Henter ut konteksten det bes om tilgang til fra body. Må settes for OBO-tokens;
 * kan være `null` når ruten bare skal brukes med client credentials.
 */
public data class AuthorizationBodyPathConfig<T : Any>(
    val operasjon: Operasjon,
    val kontekst: ((T) -> Tilgangskontekst)? = null,
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

    public suspend fun tilTilgangRequest(request: T): AuthorizedRequest =
        AuthorizedRequest(
            applicationsOnly = applicationsOnly,
            applicationRole = applicationRole,
            authorizedAzps = authorizedAzps,
            tilgangRequest = kontekst?.invoke(request)?.let { byggTilgangRequest(it) },
        )

    private suspend fun byggTilgangRequest(kontekst: Tilgangskontekst): TilgangRequest =
        when (kontekst) {
            is Tilgangskontekst.Sak -> SakTilgangRequest(
                saksnummer = kontekst.saksnummer,
                påkrevdRolle = påkrevdRolle,
                operasjon = operasjon,
                relevanteIdenter = relevanteIdenterResolver?.resolve(kontekst.saksnummer)
            )

            is Tilgangskontekst.Person -> PersonTilgangRequest(
                personIdent = kontekst.personIdent,
                påkrevdRolle = påkrevdRolle,
                operasjon = operasjon,
            )

            is Tilgangskontekst.Behandling -> {
                val referanse = behandlingreferanseResolver.resolve(kontekst.referanse)
                BehandlingTilgangRequest(
                    behandlingsreferanse = referanse,
                    påkrevdRolle = påkrevdRolle.ifEmpty { kontekst.påkrevdRolle },
                    operasjon = operasjon,
                    relevanteIdenter = relevanteIdenterResolver?.resolve(referanse.toString())
                )
            }

            is Tilgangskontekst.Journalpost -> JournalpostTilgangRequest(
                journalpostId = journalpostIdResolver.resolve(kontekst.referanse),
                påkrevdRolle = påkrevdRolle.ifEmpty { kontekst.påkrevdRolle },
                operasjon = operasjon
            )
        }
}
