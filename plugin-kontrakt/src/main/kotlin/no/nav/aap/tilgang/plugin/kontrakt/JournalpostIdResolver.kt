package no.nav.aap.tilgang.plugin.kontrakt

public fun interface JournalpostIdResolver {
    public suspend fun resolve(referanse: String): Long
}