package tilgang

interface JournalpostOppslag {
    suspend fun hentJournalpostInfo(journalpostId: Long, callId: String): JournalpostInfo
}
