package no.nav.aap.tilgang

import no.nav.aap.tilgang.plugin.kontrakt.BehandlingsreferanseResolver
import java.util.UUID

class DefaultBehandlingreferanseResolver : BehandlingsreferanseResolver {
    override fun resolve(referanse: String): UUID {
        return UUID.fromString(referanse)
    }
}
