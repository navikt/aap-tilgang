package no.nav.aap.tilgang

import io.ktor.http.*

fun interface JournalpostIdResolver<TParams: Any, TRequest: Any> {
    fun resolve(parameters: TParams, tBody: TRequest?): Long
}