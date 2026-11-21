package tilgang.kontroll

import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.RelevanteIdenter
import no.nav.aap.tilgang.Rolle

data class SakstilgangInput(
    val saksnummer: String,
    val påkrevdRolle: List<Rolle>,
    val operasjon: Operasjon,
    val relevanteIdenter: RelevanteIdenter?,
)
