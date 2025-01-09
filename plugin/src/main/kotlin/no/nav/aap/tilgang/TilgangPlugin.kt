package no.nav.aap.tilgang

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.RouteScopedPlugin
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.pluginRegistry
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.doublereceive.DoubleReceive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingNode
import io.ktor.util.AttributeKey
import no.nav.aap.komponenter.httpklient.auth.token
import no.nav.aap.komponenter.json.DefaultJsonMapper
import org.slf4j.LoggerFactory

const val TILGANG_PLUGIN = "TilgangPlugin"

val log = LoggerFactory.getLogger(TILGANG_PLUGIN)

inline fun <reified T: Any> Route.installerTilgangBodyPlugin(
    pathConfig: AuthorizationBodyPathConfig,
) {
    if ((this as RoutingNode).pluginRegistry.getOrNull(AttributeKey(TILGANG_PLUGIN)) != null) {
        throw IllegalStateException("Fant allerede registeret tilgang plugin")
    }
    install(DoubleReceive)
    install(buildTilgangPlugin { call: ApplicationCall -> pathConfig.tilTilgangRequest(call.parseGeneric<T>()) })
}

fun Route.installerTilgangParamPlugin(
    config: AuthorizationParamPathConfig
) {
    if ((this as RoutingNode).pluginRegistry.getOrNull(AttributeKey(TILGANG_PLUGIN)) != null) {
        throw IllegalStateException("Fant allerede registeret tilgang plugin")
    }
    install(buildTilgangPlugin { call: ApplicationCall ->
        config.tilTilgangRequest(call.parameters)
    })
}

inline fun buildTilgangPlugin(crossinline parse: suspend (call: ApplicationCall) -> AuthorizedRequest): RouteScopedPlugin<Unit> {
    return createRouteScopedPlugin(name = TILGANG_PLUGIN) {
        on(AuthenticationChecked) { call ->
            val token = call.token()
            val input = parse(call)
            val harTilgang = TilgangService.harTilgang(input, call, token)

            if (!harTilgang) {
                call.respond(HttpStatusCode.Forbidden, "Ingen tilgang")
            }
        }
    }
}

suspend inline fun <reified T : Any> ApplicationCall.parseGeneric(): T {
    if (T::class == Unit::class) return Unit as T
    return DefaultJsonMapper.fromJson<T>(receiveText())
}

fun ApplicationCall.rolesClaim(): List<String> {
    return principal<JWTPrincipal>()?.getListClaim("roles", String::class) ?: emptyList()
}
