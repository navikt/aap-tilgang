package no.nav.aap.tilgang

import io.ktor.http.*

fun interface JournalpostIdResolver {
    fun resolve(parameters: Parameters): Long
}