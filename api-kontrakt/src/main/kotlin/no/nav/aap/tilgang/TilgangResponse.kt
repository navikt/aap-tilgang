package no.nav.aap.tilgang

data class TilgangResponse(val tilgang: Boolean, val tilgangIKontekst: Map<Operasjon, Boolean>? = null)
data class BegrunnetTilgangsResponse(
    val tilgang: Boolean,
    val avvistResponse: Avvisning? = null,
    val tilgangIKontekst: Map<Operasjon, Avvisning>? = null
)

public data class Avvisning(
    val title: String,
    val begrunnelse: String,
    val kanOverstyres: Boolean,
)
