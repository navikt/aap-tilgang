package no.nav.aap.tilgang.resolver

import no.nav.aap.tilgang.RelevanteIdenter

public fun interface RelevanteIdenterResolver {
    public suspend fun resolve(referanse: String): RelevanteIdenter
}