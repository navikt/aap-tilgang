package tilgang.kontroll

data class JournalpostInfo(
    val journalpostId: Long,
    val søkerIdent: String?,
    val gjelderOrganisasjon: Boolean,
    val saksnummer: String?,
)
