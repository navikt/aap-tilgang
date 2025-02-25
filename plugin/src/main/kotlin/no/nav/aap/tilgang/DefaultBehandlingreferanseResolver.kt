package no.nav.aap.tilgang

import no.nav.aap.tilgang.plugin.kontrakt.BehandlingsreferanseResolver

class DefaultBehandlingreferanseResolver : BehandlingsreferanseResolver {
    override fun resolve(referanse: String): String {
        return referanse
    }
}
