package no.nav.aap.tilgang.plugin.kontrakt

interface Behandlingsreferanse : TilgangReferanse {
    fun hentBehandlingsreferanse(): String
    fun hentAvklaringsbehovKode(): String?
}
