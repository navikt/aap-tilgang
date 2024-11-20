package no.nav.aap.tilgang.plugin.kontrakt

interface Saksreferanse : TilgangReferanse {
    fun hentSaksreferanse(): String
}
