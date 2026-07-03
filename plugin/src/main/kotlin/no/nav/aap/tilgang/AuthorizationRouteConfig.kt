package no.nav.aap.tilgang

import java.util.UUID

public interface AuthorizationRouteConfig


public data class AuthorizedRequest(
    val applicationsOnly: Boolean,
    val applicationRole: String?,
    val authorizedAzps: List<UUID>?,
    val tilgangRequest: TilgangRequest?
)
