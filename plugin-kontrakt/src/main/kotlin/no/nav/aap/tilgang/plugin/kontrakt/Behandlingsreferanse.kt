package no.nav.aap.tilgang.plugin.kontrakt

import no.nav.aap.tilgang.Rolle

public interface Behandlingsreferanse : TilgangReferanse {
    /**
     * Output her er input i [BehandlingreferanseResolver].
     */
    public fun behandlingsreferanseResolverInput(): String
    public fun hentPåkrevdRolle(): List<Rolle>
}