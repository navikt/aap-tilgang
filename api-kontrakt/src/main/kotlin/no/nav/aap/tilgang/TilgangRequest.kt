package no.nav.aap.tilgang

import java.util.UUID

sealed interface TilgangRequest

data class SakTilgangRequest (
    val saksnummer: String,
    val operasjon: Operasjon
): TilgangRequest

data class BehandlingTilgangRequest(
    val behandlingsreferanse: UUID,
    val avklaringsbehovKode: String?,
    val operasjon: Operasjon,
    val operasjonerIKontekst: List<Operasjon>? = emptyList(),
): TilgangRequest

data class JournalpostTilgangRequest(
    val journalpostId: Long,
    val avklaringsbehovKode: String?,
    val operasjon: Operasjon
): TilgangRequest

data class PersonTilgangRequest(
    val personIdent: String,
): TilgangRequest


