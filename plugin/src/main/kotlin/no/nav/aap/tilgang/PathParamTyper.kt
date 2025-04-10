package no.nav.aap.tilgang

import no.nav.aap.tilgang.plugin.kontrakt.BehandlingreferanseResolver
import no.nav.aap.tilgang.plugin.kontrakt.JournalpostIdResolver

@JvmInline
value class SakPathParam(val param: String)

data class BehandlingPathParam(val param: String, val resolver: BehandlingreferanseResolver = DefaultBehandlingreferanseResolver())

data class JournalpostPathParam(val param: String, val resolver: JournalpostIdResolver = DefaultJournalpostIdResolver())

data class PersonIdentPathParam(val param: String)