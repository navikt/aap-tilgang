package no.nav.aap.tilgang.plugin.kontrakt

import no.nav.aap.tilgang.Rolle

public interface Journalpostreferanse : TilgangReferanse {
    public fun journalpostIdResolverInput(): String
    public fun hentPåkrevdRolle(): List<Rolle>
}
