package no.nav.aap.tilgang

import java.util.UUID

interface AuthorizationRouteConfig


data class AuthorizedRequest(
    val applicationsOnly: Boolean,
    val applicationRole: String?,
    val authorizedAzps: List<UUID>?,
    val tilgangRequest: TilgangRequest?
)
