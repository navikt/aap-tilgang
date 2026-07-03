package no.nav.aap.tilgang

import no.nav.aap.komponenter.config.requiredConfigForKey

/**
 * Kun spesifiserte roller i claims får tilgang. Ingen ytterligere tilgangssjekk
 */
public data class RollerConfig(
    val roller: List<AdGruppe>
): AuthorizationRouteConfig

public interface AdGruppe {
    /**
     * Id-en til ad-gruppen.
     */
    public val id: String
}

public object SaksbehandlerNasjonal : AdGruppe {
    override val id: String = requiredConfigForKey("AAP_SAKSBEHANDLER_NASJONAL")
}
public object SaksbehandlerOppfolging : AdGruppe {
    override val id: String = requiredConfigForKey("AAP_SAKSBEHANDLER_OPPFOLGING")
}
public object Kvalitetssikrer : AdGruppe {
    override val id: String = requiredConfigForKey("AAP_KVALITETSSIKRER")
}
public object Beslutter : AdGruppe {
    override val id: String = requiredConfigForKey("AAP_BESLUTTER")
}
public object Drift : AdGruppe {
    override val id: String = requiredConfigForKey("AAP_DRIFT")
}
public object Produksjonsstyring : AdGruppe {
    override val id: String = requiredConfigForKey("AAP_PRODUKSJONSSTYRING")
}
public object Les : AdGruppe {
    override val id: String = requiredConfigForKey("AAP_LES")
}