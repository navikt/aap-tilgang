package tilgang.kontroll


data class SakstilgangInput(
    val saksnummer: String,
    val påkrevdRolle: List<Rolle>,
    val operasjon: Operasjon,
    val relevanteIdenter: RelevanteIdenter?,
)
