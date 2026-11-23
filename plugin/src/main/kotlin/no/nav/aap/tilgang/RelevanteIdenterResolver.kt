package no.nav.aap.tilgang

public fun interface RelevanteIdenterResolver {
    public suspend fun resolve(referanse: String): RelevanteIdenter
}