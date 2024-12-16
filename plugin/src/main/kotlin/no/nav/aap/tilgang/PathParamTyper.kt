package no.nav.aap.tilgang

import no.nav.aap.tilgang.plugin.kontrakt.JournalpostIdResolver

@JvmInline
value class SakPathParam(val param: String)

@JvmInline
value class BehandlingPathParam(val param: String)

data class JournalpostPathParam(val param: String, val resolver: JournalpostIdResolver = DefaultJournalpostIdResolver())
