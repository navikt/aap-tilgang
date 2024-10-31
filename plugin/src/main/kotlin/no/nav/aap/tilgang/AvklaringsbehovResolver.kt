package no.nav.aap.tilgang

fun interface AvklaringsbehovResolver<TRequest> {
    fun resolve(request: TRequest): String
}