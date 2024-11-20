package no.nav.aap.tilgang.plugin.kontrakt

interface Journalpostreferanse : TilgangReferanse {
    fun hentJournalpostreferanse(): Long
    fun hentAvklaringsbehovKode(): String?
}
