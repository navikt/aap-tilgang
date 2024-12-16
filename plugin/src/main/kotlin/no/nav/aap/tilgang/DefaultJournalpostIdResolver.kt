package no.nav.aap.tilgang

import no.nav.aap.tilgang.plugin.kontrakt.JournalpostIdResolver

class DefaultJournalpostIdResolver : JournalpostIdResolver {
    override fun resolve(referanse: String): Long {
        return referanse.toLong()
    }
}