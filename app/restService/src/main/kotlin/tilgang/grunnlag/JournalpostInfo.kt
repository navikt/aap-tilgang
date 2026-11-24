package tilgang.grunnlag

data class JournalpostInfo(
    val journalpostId: Long,
    val søkerIdent: String?,
    val gjelderOrganisasjon: Boolean,
    val saksnummer: String?,
)
