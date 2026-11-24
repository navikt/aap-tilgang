package no.nav.aap.tilgang.resolver

public class DefaultJournalpostIdResolver : JournalpostIdResolver {
    override suspend fun resolve(referanse: String): Long {
        return referanse.toLong()
    }
}