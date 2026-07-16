package no.nav.aap.tilgang.plugin.kontrakt

public interface Saksreferanse : TilgangReferanse {
    public fun hentSaksreferanse(): String
}
