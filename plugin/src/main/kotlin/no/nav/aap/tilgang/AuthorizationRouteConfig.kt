package no.nav.aap.tilgang

interface AuthorizationRouteConfig


data class AuthorizedRequest(
    val applicationsOnly: Boolean,
    val applicationRole: String?,
    val tilgangRequest: TilgangRequest?
)
