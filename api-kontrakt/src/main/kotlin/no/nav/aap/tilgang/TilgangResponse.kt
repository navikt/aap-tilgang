package no.nav.aap.tilgang

data class TilgangResponse(val tilgang: Boolean, val tilgangIKontekst: Map<Operasjon, Boolean>? = null)