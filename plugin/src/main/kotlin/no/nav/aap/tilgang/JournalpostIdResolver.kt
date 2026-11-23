package no.nav.aap.tilgang

public fun interface JournalpostIdResolver {
    public suspend fun resolve(referanse: String): Long
}