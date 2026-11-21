package tilgang.kontroll.regler

import no.nav.aap.tilgang.Rolle

data class AvklaringsbehovRolleInput(
    val avklaringsbehovRoller: List<List<Rolle>>,
    val påkrevdRolle: List<Rolle> = emptyList(),
    val roller: List<Rolle>,
)
