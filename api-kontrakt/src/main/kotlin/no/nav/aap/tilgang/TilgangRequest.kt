package no.nav.aap.tilgang

data class SakTilgangRequest (
    val saksnummer: String,
    val operasjon: Operasjon
): TilgangRequest

data class BehandlingTilgangRequest(
    val behandlingsreferanse: String,
    val avklaringsbehovKode: String?,
    val operasjon: Operasjon
): TilgangRequest

data class JournalpostTilgangRequest(
    val journalpostId: Long,
    val avklaringsbehovKode: String?,
    val operasjon: Operasjon
): TilgangRequest

sealed interface TilgangRequest
