package tilgang.kontroll

import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.Rolle

data class JournalposttilgangInput(
    val journalpostId: Long,
    val avklaringsbehovKode: String?,
    val påkrevdRolle: List<Rolle>,
    val operasjon: Operasjon,
)
