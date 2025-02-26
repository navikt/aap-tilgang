package no.nav.aap.tilgang.plugin.kontrakt

import java.util.UUID

fun interface BehandlingreferanseResolver {
    fun resolve(referanse: String): UUID
}
