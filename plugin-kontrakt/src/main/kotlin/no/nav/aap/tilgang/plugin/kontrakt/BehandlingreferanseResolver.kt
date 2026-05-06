package no.nav.aap.tilgang.plugin.kontrakt

import java.util.UUID

fun interface BehandlingreferanseResolver {
    suspend fun resolve(referanse: String): UUID
}
