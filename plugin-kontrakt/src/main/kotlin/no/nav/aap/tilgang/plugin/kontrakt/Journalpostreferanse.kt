package no.nav.aap.tilgang.plugin.kontrakt

interface Journalpostreferanse : TilgangReferanse {
    fun journalpostIdResolverInput(): String
    fun hentAvklaringsbehovKode(): String?
}
