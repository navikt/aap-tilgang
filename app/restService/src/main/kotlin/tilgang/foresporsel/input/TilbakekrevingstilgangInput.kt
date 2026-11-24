package tilgang.foresporsel.input

import java.util.UUID
import tilgang.kontroll.Operasjon
import tilgang.kontroll.Rolle

data class TilbakekrevingstilgangInput(
    val saksnummer: String,
    val behandlingsreferanse: UUID,
    val påkrevdRolle: List<Rolle>,
    val operasjon: Operasjon,
) {
    init {
        if (operasjon == Operasjon.SAKSBEHANDLE) {
            require(påkrevdRolle.isNotEmpty()) { "Påkrevd rolle må være satt for operasjon SAKSBEHANDLE" }
        }
    }
}
