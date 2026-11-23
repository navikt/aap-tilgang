package no.nav.aap.tilgang.config

import no.nav.aap.tilgang.Rolle

/**
 * Konteksten det bes om tilgang til. Hentes ut fra request body med [AuthorizationBodyPathConfig.kontekst].
 */
public sealed interface Tilgangskontekst {
    public data class Sak(val saksnummer: String) : Tilgangskontekst

    public data class Person(val personIdent: String) : Tilgangskontekst

    /**
     * @property referanse Input til [AuthorizationBodyPathConfig.behandlingreferanseResolver].
     * @property påkrevdRolle Brukes når [AuthorizationBodyPathConfig.påkrevdRolle] er tom.
     */
    public data class Behandling(
        val referanse: String,
        val påkrevdRolle: List<Rolle> = emptyList(),
    ) : Tilgangskontekst

    /**
     * @property referanse Input til [AuthorizationBodyPathConfig.journalpostIdResolver].
     * @property påkrevdRolle Brukes når [AuthorizationBodyPathConfig.påkrevdRolle] er tom.
     */
    public data class Journalpost(
        val referanse: String,
        val påkrevdRolle: List<Rolle> = emptyList(),
    ) : Tilgangskontekst
}
