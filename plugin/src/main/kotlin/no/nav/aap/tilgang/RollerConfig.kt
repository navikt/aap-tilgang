package no.nav.aap.tilgang

interface AdGruppe {
    /**
     * Id-en til ad-gruppen.
     */
    val id: String
}

/**
 * Kun spesifiserte roller i claims får tilgang. Ingen ytterligere tilgangssjekk
 */
data class RollerConfig(
    val roller: List<AdGruppe>
): AuthorizationRouteConfig