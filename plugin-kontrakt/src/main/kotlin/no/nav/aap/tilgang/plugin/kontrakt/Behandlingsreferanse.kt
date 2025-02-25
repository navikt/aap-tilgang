package no.nav.aap.tilgang.plugin.kontrakt

interface Behandlingsreferanse : TilgangReferanse {
    fun behandlingsreferanseResolverInput(): String
    fun hentAvklaringsbehovKode(): String?
}
