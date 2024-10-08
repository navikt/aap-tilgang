package tilgang

data class SakTilgangRequest (
    val saksnummer: String,
    val operasjon: Operasjon
)

data class BehandlingTilgangRequest(
    val behandlingsreferanse: String,
    val avklaringsbehovKode: String?,
    val operasjon: Operasjon
)

data class JournalpostRequest(
    val journalpostId: Long,
    val avklaringsbehovKode: String?,
    val operasjon: Operasjon
)