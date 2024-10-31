package no.nav.aap.tilgang

fun interface JournalpostIdResolver<TParams: Any, TRequest: Any> {
    fun resolve(parameters: TParams, tBody: TRequest): Long
}