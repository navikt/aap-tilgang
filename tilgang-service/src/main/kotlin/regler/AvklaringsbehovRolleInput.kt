package tilgang.kontroll.regler

import tilgang.kontroll.Rolle

data class AvklaringsbehovRolleInput(
    val avklaringsbehovRoller: List<List<Rolle>>,
    val påkrevdRolle: List<Rolle> = emptyList(),
    val roller: List<Rolle>,
)
