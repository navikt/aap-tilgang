package tilgang.kontroll


class Tilgangskontekst(
    val ansattIdent: String,
    val roller: List<Rolle>,
    val brukerToken: BrukerToken,
    val callId: String,
)
