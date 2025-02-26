package no.nav.aap.tilgang.plugin.kontrakt

import java.util.UUID

fun interface BehandlingsreferanseResolver {
    fun resolve(referanse: String): UUID
}
