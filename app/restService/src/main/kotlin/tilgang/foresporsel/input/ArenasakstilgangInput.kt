package tilgang.foresporsel.input

import tilgang.kontroll.Operasjon
import tilgang.kontroll.Rolle

data class ArenasakstilgangInput(
    val personIdent: String,
    val påkrevdRolle: List<Rolle>,
    val operasjon: Operasjon,
)
