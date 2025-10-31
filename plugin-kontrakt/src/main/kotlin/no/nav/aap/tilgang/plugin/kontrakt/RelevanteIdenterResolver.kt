package no.nav.aap.tilgang.plugin.kontrakt
import no.nav.aap.tilgang.RelevanteIdenter

fun interface RelevanteIdenterResolver {
    fun resolve(referanse: String): RelevanteIdenter
}