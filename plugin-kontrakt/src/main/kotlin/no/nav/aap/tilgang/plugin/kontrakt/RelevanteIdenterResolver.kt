package no.nav.aap.tilgang.plugin.kontrakt
import no.nav.aap.tilgang.RelevanteIdenter

public fun interface RelevanteIdenterResolver {
    public suspend fun resolve(referanse: String): RelevanteIdenter
}