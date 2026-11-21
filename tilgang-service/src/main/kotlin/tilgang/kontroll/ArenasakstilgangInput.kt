package tilgang.kontroll

import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.Rolle

data class ArenasakstilgangInput(
    val personIdent: String,
    val påkrevdRolle: List<Rolle>,
    val operasjon: Operasjon,
)
