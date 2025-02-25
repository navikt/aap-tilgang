package no.nav.aap.tilgang

import no.nav.aap.tilgang.plugin.kontrakt.BehandlingsreferanseResolver
import no.nav.aap.tilgang.plugin.kontrakt.JournalpostIdResolver

@JvmInline
value class SakPathParam(val param: String)

data class BehandlingPathParam(val param: String, val resolver: BehandlingsreferanseResolver = DefaultBehandlingreferanseResolver())

data class JournalpostPathParam(val param: String, val resolver: JournalpostIdResolver = DefaultJournalpostIdResolver())
