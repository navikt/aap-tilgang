package no.nav.aap.tilgang.plugin.kontrakt

fun interface JournalpostIdResolver {
    fun resolve(referanse: String): Long
}