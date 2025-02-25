package no.nav.aap.tilgang.plugin.kontrakt

fun interface BehandlingsreferanseResolver {
    fun resolve(referanse: String): String
}
