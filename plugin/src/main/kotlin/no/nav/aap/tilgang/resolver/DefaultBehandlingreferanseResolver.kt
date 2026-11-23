package no.nav.aap.tilgang.resolver

import java.util.UUID

public class DefaultBehandlingreferanseResolver : BehandlingreferanseResolver {
    override suspend fun resolve(referanse: String): UUID {
        return UUID.fromString(referanse)
    }
}
