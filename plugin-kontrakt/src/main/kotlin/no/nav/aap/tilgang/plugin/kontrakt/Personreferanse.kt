package no.nav.aap.tilgang.plugin.kontrakt

public interface Personreferanse : TilgangReferanse {
    public fun hentPersonreferanse(): String
}