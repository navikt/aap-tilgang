package no.nav.aap.tilgang.plugin.kontrakt

import no.nav.aap.tilgang.Rolle

interface Journalpostreferanse : TilgangReferanse {
    fun journalpostIdResolverInput(): String
    fun hentPåkrevdRolle(): List<Rolle>
}
