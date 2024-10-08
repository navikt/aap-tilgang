package tilgang

data class SakRequest (
    val saksnummer: String,
    val operasjon: Operasjon
)

data class BehandlingRequest(
    val behandlingsreferanse: String,
    val avklaringsbehovKode: String?,
    val operasjon: Operasjon
)

data class JournalpostRequest(
    val journalpostId: Long,
    val avklaringsbehovKode: String?,
    val operasjon: Operasjon
)