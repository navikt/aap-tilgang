package tilgang.kontroll

interface JournalpostOppslag {
    suspend fun hentJournalpostInfo(journalpostId: Long, callId: String): JournalpostInfo
}
