package no.nav.aap.tilgang

import java.util.UUID

sealed interface TilgangRequest

data class SakTilgangRequest(
    val saksnummer: String,
    val påkrevdRolle: List<Rolle> = emptyList(),
    val operasjon: Operasjon,
    /**
     * Valgfritt felt for å spesifisere relevante identer knyttet til saken.
     * Dersom feltet ikke er satt, hentes identer automatisk basert på saksnummeret
     */
    val relevanteIdenter: RelevanteIdenter? = null,
) : TilgangRequest

data class BehandlingTilgangRequest(
    val behandlingsreferanse: UUID,
    val avklaringsbehovKode: String?,
    val påkrevdRolle: List<Rolle> = emptyList(),
    val operasjon: Operasjon,
    /**
     * Valgfritt felt for å spesifisere relevante identer knyttet til saken.
     * Dersom feltet ikke er satt, hentes identer automatisk basert på behandlingsreferansen
     */
    val relevanteIdenter: RelevanteIdenter? = null,
    val operasjonerIKontekst: List<Operasjon> = emptyList(),
) : TilgangRequest

data class JournalpostTilgangRequest(
    val journalpostId: Long,
    val avklaringsbehovKode: String?,
    val påkrevdRolle: List<Rolle> = emptyList(),
    val operasjon: Operasjon
) : TilgangRequest

data class PersonTilgangRequest(
    val personIdent: String,
) : TilgangRequest

data class TilbakekrevingTilgangRequest(
    val saksnummer: String,
    val behandlingsreferanse: UUID,
    @Deprecated("Trengs for bakoverkompabilitet da denne allerede er i bruk")
    val påkrevdRolle: Rolle? = null,
    val påkrevdRoller: List<Rolle> = emptyList(),
    val operasjon: Operasjon,
) : TilgangRequest {
    val effektivePåkrevdRoller: List<Rolle>
        get() = påkrevdRoller.ifEmpty { listOfNotNull(påkrevdRolle) }

    init {
        if (operasjon == Operasjon.SAKSBEHANDLE) {
            require(effektivePåkrevdRoller.isNotEmpty()) { "Påkrevd rolle må være satt for operasjon SAKSBEHANDLE" }
        }
    }
}
