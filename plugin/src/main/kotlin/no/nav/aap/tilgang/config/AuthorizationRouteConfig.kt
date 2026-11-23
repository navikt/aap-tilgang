package no.nav.aap.tilgang.config

import java.util.UUID
import no.nav.aap.tilgang.TilgangRequest

public interface AuthorizationRouteConfig


public data class AuthorizedRequest(
    val applicationsOnly: Boolean,
    val applicationRole: String?,
    val authorizedAzps: List<UUID>?,
    val tilgangRequest: TilgangRequest?
)
