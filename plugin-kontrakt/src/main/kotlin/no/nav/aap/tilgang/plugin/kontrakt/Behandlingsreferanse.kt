package no.nav.aap.tilgang.plugin.kontrakt

interface Behandlingsreferanse : TilgangReferanse {
    /**
     * Output her er input i [BehandlingsreferanseResolver].
     */
    fun behandlingsreferanseResolverInput(): String
    fun hentAvklaringsbehovKode(): String?
}