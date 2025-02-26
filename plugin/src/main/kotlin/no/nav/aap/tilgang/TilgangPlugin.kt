package no.nav.aap.tilgang

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import no.nav.aap.komponenter.httpklient.auth.token
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.tilgang.auditlog.AuditLogBodyConfig
import no.nav.aap.tilgang.auditlog.AuditLogConfig
import no.nav.aap.tilgang.auditlog.AuditLogPathParamConfig
import no.nav.aap.tilgang.auditlog.AuditLoggerImpl
import no.nav.aap.tilgang.auditlog.cef.AuthorizationDecision
import no.nav.aap.tilgang.auditlog.cef.CefMessage
import no.nav.aap.tilgang.plugin.kontrakt.AuditlogResolverInput
import org.slf4j.Logger
import org.slf4j.LoggerFactory

const val TILGANG_PLUGIN = "TilgangPlugin"

val log: Logger = LoggerFactory.getLogger(TILGANG_PLUGIN)

inline fun <reified T : Any> Route.installerTilgangBodyPlugin(
    pathConfig: AuthorizationBodyPathConfig,
    auditLogConfig: AuditLogConfig?
) {
    if ((this as RoutingNode).pluginRegistry.getOrNull(AttributeKey(TILGANG_PLUGIN)) != null) {
        throw IllegalStateException("Fant allerede registeret tilgang plugin")
    }

    install(DoubleReceive)
    install(
        buildTilgangPlugin(
            auditLogConfig,
            { call: ApplicationCall -> pathConfig.tilTilgangRequest(call.parseGeneric<T>()) },
            { call: ApplicationCall ->
                when (auditLogConfig) {
                    is AuditLogBodyConfig -> auditLogConfig.tilIdent(call.parseGeneric<T>() as AuditlogResolverInput)
                    is AuditLogPathParamConfig -> auditLogConfig.tilIdent(call.parameters)
                    else -> throw IllegalStateException("AuditLogConfig mangler")
                }
            }
        )
    )
}

fun Route.installerTilgangParamPlugin(
    config: AuthorizationParamPathConfig,
    auditLogConfig: AuditLogPathParamConfig?
) {
    if ((this as RoutingNode).pluginRegistry.getOrNull(AttributeKey(TILGANG_PLUGIN)) != null) {
        throw IllegalStateException("Fant allerede registeret tilgang plugin")
    }

    install(
        buildTilgangPlugin(
            auditLogConfig,
            { call: ApplicationCall -> config.tilTilgangRequest(call.parameters) },
            { call: ApplicationCall -> auditLogConfig!!.tilIdent(call.parameters) }
        )
    )
}

inline fun buildTilgangPlugin(
    auditLogConfig: AuditLogConfig?,
    crossinline parse: suspend (call: ApplicationCall) -> AuthorizedRequest,
    crossinline resolveIdent: suspend (call: ApplicationCall) -> String
): RouteScopedPlugin<Unit> {
    return createRouteScopedPlugin(name = TILGANG_PLUGIN) {
        on(AuthenticationChecked) { call ->
            val token = call.token()
            val input = parse(call)
            val harTilgang = TilgangService.harTilgang(input, call, token)

            if (!harTilgang) {
                call.respond(HttpStatusCode.Forbidden, "Ingen tilgang")
            }

            if (auditLogConfig != null && !token.isClientCredentials()) {
                requireNotNull(auditLogConfig) {
                    "AuditLogConfig mangler logger"
                }
                AuditLoggerImpl(auditLogConfig.logger()).log(
                    CefMessage.konstruer(
                        app = auditLogConfig.app(),
                        brukerIdent = resolveIdent(call),
                        ansattIdent = call.ident(),
                        decision = AuthorizationDecision.PERMIT,
                        path = call.request.path(),
                        callId = if (auditLogConfig.inkluderCallId()) call.callId else null
                    )
                )
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