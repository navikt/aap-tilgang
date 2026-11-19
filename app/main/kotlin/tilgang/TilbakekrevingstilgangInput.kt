package tilgang

import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.Rolle
import java.util.UUID

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
