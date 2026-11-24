package tilgang.foresporsel.input

import tilgang.kontroll.Operasjon
import tilgang.kontroll.RelevanteIdenter
import tilgang.kontroll.Rolle

data class SakstilgangInput(
    val saksnummer: String,
    val påkrevdRolle: List<Rolle>,
    val operasjon: Operasjon,
    val relevanteIdenter: RelevanteIdenter?,
)
