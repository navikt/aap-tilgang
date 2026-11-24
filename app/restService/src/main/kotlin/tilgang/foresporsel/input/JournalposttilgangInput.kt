package tilgang.foresporsel.input

import tilgang.kontroll.Operasjon
import tilgang.kontroll.Rolle

data class JournalposttilgangInput(
    val journalpostId: Long,
    val avklaringsbehovKode: String?,
    val påkrevdRolle: List<Rolle>,
    val operasjon: Operasjon,
)
