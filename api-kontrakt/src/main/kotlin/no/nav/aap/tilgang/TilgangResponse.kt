package no.nav.aap.tilgang

data class TilgangResponse(val tilgang: Boolean, val tilgangIKontekst: Map<Operasjon, Boolean>? = null)
data class BegrunnetTilgangsResponse(
    val tilgang: Boolean,
    val avvistResponse: Avisning? = null,
    val tilgangIKontekst: Map<Operasjon, Avisning>? = null
)

public data class Avisning(
    val title: String,
    val begrunnelse: String,
    val kanOverstyres: Boolean,
)
