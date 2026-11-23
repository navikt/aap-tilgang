package tilgang.kontroll


data class ArenasakstilgangInput(
    val personIdent: String,
    val påkrevdRolle: List<Rolle>,
    val operasjon: Operasjon,
)
