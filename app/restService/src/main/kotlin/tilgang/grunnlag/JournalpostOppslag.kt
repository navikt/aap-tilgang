package tilgang.grunnlag

interface JournalpostOppslag {
    suspend fun hentJournalpostInfo(journalpostId: Long, callId: String): JournalpostInfo
}
