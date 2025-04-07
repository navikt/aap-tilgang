package no.nav.aap.tilgang

import no.nav.aap.komponenter.config.requiredConfigForKey

/**
 * Kun spesifiserte roller i claims får tilgang. Ingen ytterligere tilgangssjekk
 */
data class RollerConfig(
    val roller: List<AdGruppe>
): AuthorizationRouteConfig

interface AdGruppe {
    /**
     * Id-en til ad-gruppen.
     */
    val id: String
}

object SaksbehandlerNasjonal : AdGruppe {
    override val id: String = requiredConfigForKey("AAP_SAKSBEHANDLER_NASJONAL")
}
object SaksbehandlerOppfolging : AdGruppe {
    override val id: String = requiredConfigForKey("AAP_SAKSBEHANDLER_OPPFOLGING")
}
object Kvalitetssikrer : AdGruppe {
    override val id: String = requiredConfigForKey("AAP_KVALITETSSIKRER")
}
object Beslutter : AdGruppe {
    override val id: String = requiredConfigForKey("AAP_BESLUTTER")
}
object Drift : AdGruppe {
    override val id: String = requiredConfigForKey("AAP_DRIFT")
}
object Produksjonsstyring : AdGruppe {
    override val id: String = requiredConfigForKey("AAP_PRODUKSJONSSTYRING")
}
object Les : AdGruppe {
    override val id: String = requiredConfigForKey("AAP_LES")
}