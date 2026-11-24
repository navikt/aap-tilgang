package tilgang.kontroll


class Tilgangsgrunnlag(
    val kontekst: Tilgangskontekst,
    val søkerIdenter: RelevanteIdenter,
    // Alternative roller per avklaringsbehov; alle behov må være oppfylt.
    val avklaringsbehovRoller: List<List<Rolle>> = emptyList(),
    val påkrevdRolle: List<Rolle> = emptyList(),
    val operasjoner: List<Operasjon>,
)
