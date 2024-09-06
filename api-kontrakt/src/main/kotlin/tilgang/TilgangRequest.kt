package tilgang

data class TilgangRequest(
    val saksnummer: String?,
    val behandlingsreferanse: String?,
    val avklaringsbehovKode: String?,
    val operasjon: Operasjon
)