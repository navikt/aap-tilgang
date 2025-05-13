package no.nav.aap.tilgang

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
import no.nav.aap.komponenter.httpklient.exception.IkkeTillattException
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
import java.util.*

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

fun Route.installerTilgangRollePlugin(
    config: RollerConfig
) {
    if ((this as RoutingNode).pluginRegistry.getOrNull(AttributeKey(TILGANG_PLUGIN)) != null) {
        throw IllegalStateException("Fant allerede registeret tilgang plugin")
    }

    install(createRouteScopedPlugin(name = TILGANG_PLUGIN) {
        on(AuthenticationChecked) { call ->
            val roller = call.groupsClaim()

            if (roller.any { adRolle -> adRolle in config.roller.map { it.id } }) {
                return@on
            }
            call.respondWithError(IkkeTillattException("Ingen tilgang"))
        }
    })
}

fun Route.installerTilgangMachineToMachinePlugin(
    config: AuthorizationMachineToMachineConfig,
    auditLogConfig: AuditLogConfig?,
) {
    if ((this as RoutingNode).pluginRegistry.getOrNull(AttributeKey(TILGANG_PLUGIN)) != null) {
        throw IllegalStateException("Fant allerede registeret tilgang plugin")
    }

    require(auditLogConfig == null) {
        "kan ikke installere audit-logger for maskin-til-maskin tokens uten on-behalf-of tokens (m2m obo tokens)"
    }

    install(createRouteScopedPlugin(name = TILGANG_PLUGIN) {
        on(AuthenticationChecked) { call ->
            val principal = call.principal<JWTPrincipal>() ?: error("mangler principal")

            if (config.authorizedAzps.isNotEmpty()) {
                val azpName = principal.getClaim("azp_name", String::class)
                val azp = principal.getClaim("azp", UUID::class) ?: error("token uten azp-claim")

                if (azp in config.authorizedAzps) {
                    return@on
                }

                log.error("azp $azpName ($azp) har ikke tilgang dette endepunktet")
                call.respondWithError(IkkeTillattException("Ingen tilgang"))
                return@on
            }

            val roles = principal.getListClaim("roles", String::class)

            if (roles.any { it in config.authorizedRoles }) {
                log.info("bruker deprecated mekanisme `roles`")
                return@on
            }

            /* Default: deny */
            call.respondWithError(IkkeTillattException("Ingen tilgang"))
        }
    })
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
                call.respondWithError(IkkeTillattException("Ingen tilgang"))
                return@on
            }

            if (auditLogConfig != null && !token.isClientCredentials()) {
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

fun ApplicationCall.groupsClaim(): List<String> {
    return principal<JWTPrincipal>()?.getListClaim("groups", String::class) ?: emptyList()
}

suspend fun ApplicationCall.respondWithError(exception: IkkeTillattException) {
    respond(
        exception.status,
        exception.tilApiErrorResponse()
    )
}