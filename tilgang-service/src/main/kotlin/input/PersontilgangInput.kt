package tilgang.kontroll


data class PersontilgangInput(
    val personIdent: String,
    val påkrevdRolle: List<Rolle>,
    val operasjon: Operasjon?,
)
