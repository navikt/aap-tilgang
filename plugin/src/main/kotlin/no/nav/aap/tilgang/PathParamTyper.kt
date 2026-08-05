package no.nav.aap.tilgang

import no.nav.aap.tilgang.plugin.kontrakt.BehandlingreferanseResolver
import no.nav.aap.tilgang.plugin.kontrakt.JournalpostIdResolver

@JvmInline
public value class SakPathParam(val param: String)

public data class BehandlingPathParam(
    val param: String,
    val resolver: BehandlingreferanseResolver = DefaultBehandlingreferanseResolver()
)

public data class JournalpostPathParam(
    val param: String,
    val resolver: JournalpostIdResolver = DefaultJournalpostIdResolver()
)

public data class PersonIdentPathParam(val param: String)