package no.nav.aap.tilgang.plugin.kontrakt

import java.util.UUID

public fun interface BehandlingreferanseResolver {
    public suspend fun resolve(referanse: String): UUID
}
