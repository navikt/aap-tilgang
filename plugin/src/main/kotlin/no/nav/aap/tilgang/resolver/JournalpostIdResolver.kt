package no.nav.aap.tilgang.resolver

public fun interface JournalpostIdResolver {
    public suspend fun resolve(referanse: String): Long
}