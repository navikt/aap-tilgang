package tilgang.kontroll


data class JournalposttilgangInput(
    val journalpostId: Long,
    val avklaringsbehovKode: String?,
    val påkrevdRolle: List<Rolle>,
    val operasjon: Operasjon,
)
