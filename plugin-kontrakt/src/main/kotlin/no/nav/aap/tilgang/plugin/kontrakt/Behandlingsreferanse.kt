package no.nav.aap.tilgang.plugin.kontrakt

import no.nav.aap.tilgang.Rolle

interface Behandlingsreferanse : TilgangReferanse {
    /**
     * Output her er input i [BehandlingreferanseResolver].
     */
    fun behandlingsreferanseResolverInput(): String
    fun hentPåkrevdRolle(): List<Rolle>
}