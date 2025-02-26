package no.nav.aap.tilgang

import no.nav.aap.tilgang.plugin.kontrakt.BehandlingreferanseResolver
import java.util.UUID

class DefaultBehandlingreferanseResolver : BehandlingreferanseResolver {
    override fun resolve(referanse: String): UUID {
        return UUID.fromString(referanse)
    }
}
