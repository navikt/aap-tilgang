package no.nav.aap.tilgang.plugin.kontrakt

fun interface JournalpostIdResolver {
    suspend fun resolve(referanse: String): Long
}