package no.nav.aap.tilgang.plugin.kontrakt

interface Personreferanse : TilgangReferanse {
    fun hentPersonreferanse(): String
}