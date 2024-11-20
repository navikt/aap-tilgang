package no.nav.aap.tilgang

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
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
import io.ktor.server.util.getOrFail
import io.ktor.util.AttributeKey
import no.nav.aap.komponenter.httpklient.auth.AzpName
import no.nav.aap.komponenter.httpklient.auth.token
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import no.nav.aap.tilgang.plugin.kontrakt.Behandlingsreferanse
import no.nav.aap.tilgang.plugin.kontrakt.Journalpostreferanse
import no.nav.aap.tilgang.plugin.kontrakt.Saksreferanse
import no.nav.aap.tilgang.plugin.kontrakt.TilgangReferanse
import org.slf4j.LoggerFactory
import tilgang.BehandlingTilgangRequest
import tilgang.JournalpostTilgangRequest
import tilgang.Operasjon
import tilgang.SakTilgangRequest

const val TILGANG_PLUGIN = "TilgangPlugin"

val log = LoggerFactory.getLogger(TILGANG_PLUGIN)

inline fun <reified T : Behandlingsreferanse> Route.installerTilgangTilBehandlingPostPlugin(
    operasjon: Operasjon,
) {
    if ((this as RoutingNode).pluginRegistry.getOrNull(AttributeKey(TILGANG_PLUGIN)) != null) {
        throw IllegalStateException("Fant allerede registeret tilgang plugin")
    }
    install(DoubleReceive)
    install(buildTilgangTilBehandlingPlugin { call: ApplicationCall -> call.parseBehandlingFraRequestBody<T>(operasjon) })
}

inline fun <reified T : Saksreferanse> Route.installerTilgangTilSakPostPlugin(
    operasjon: Operasjon,
) {
    if ((this as RoutingNode).pluginRegistry.getOrNull(AttributeKey(TILGANG_PLUGIN)) != null) {
        throw IllegalStateException("Fant allerede registeret tilgang plugin")
    }
    install(DoubleReceive)
    install(buildTilgangTilSakPlugin { call: ApplicationCall -> call.parseSakFraRequestBody<T>(operasjon) })
}

inline fun <reified T : Journalpostreferanse> Route.installerTilgangTilJournalpostPlugin(
    operasjon: Operasjon,
) {
    if ((this as RoutingNode).pluginRegistry.getOrNull(AttributeKey(TILGANG_PLUGIN)) != null) {
        throw IllegalStateException("Fant allerede registeret tilgang plugin")
    }
    install(DoubleReceive)
    install(buildTilgangTilJournalpostPlugin { call: ApplicationCall -> call.parseJournalpostFraRequestBody<T>(operasjon) })
}

fun Route.installerTilgangGetPlugin(
    behandlingPathParam: BehandlingPathParam
) {
    if ((this as RoutingNode).pluginRegistry.getOrNull(AttributeKey(TILGANG_PLUGIN)) != null) {
        throw IllegalStateException("Fant allerede registeret tilgang plugin")
    }
    install(buildTilgangTilBehandlingPlugin { call: ApplicationCall ->
        BehandlingTilgangRequest(
            call.parameters.getOrFail(
                behandlingPathParam.param
            ), null, Operasjon.SE
        )
    })
}

inline fun <reified T: TilgangReferanse> Route.installerTilgangBodyPlugin(
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
        config.tilTilgangRequest(Operasjon.SE, call.parameters)
    })
}

fun Route.installerTilgangGetPlugin(
    sakPathParam: SakPathParam
) {
    if ((this as RoutingNode).pluginRegistry.getOrNull(AttributeKey(TILGANG_PLUGIN)) != null) {
        throw IllegalStateException("Fant allerede registeret tilgang plugin")
    }
    install(buildTilgangTilSakPlugin { call: ApplicationCall ->
        SakTilgangRequest(
            call.parameters.getOrFail(sakPathParam.param),
            Operasjon.SE
        )
    })
}

inline fun <reified TParams : Any, reified TRequest : Any> Route.installerTilgangPlugin(
    journalpostIdResolver: JournalpostIdResolver<TParams, TRequest>,
    avklaringsbehovResolver: AvklaringsbehovResolver<TRequest>? = null,
    operasjon: Operasjon = Operasjon.SE
) {
    if ((this as RoutingNode).pluginRegistry.getOrNull(AttributeKey(TILGANG_PLUGIN)) != null) {
        throw IllegalStateException("Fant allerede registeret tilgang plugin")
    }
    install(DoubleReceive)
    install(buildTilgangTilJournalpostPlugin { call: ApplicationCall ->
        JournalpostTilgangRequest(
            journalpostIdResolver.resolve(parseParams<TParams>(call.parameters), call.parseGeneric()),
            avklaringsbehovResolver?.resolve(call.parseGeneric()),
            operasjon
        )
    })
}

fun Route.installerTilgangGetPlugin(
    journalpostPathParam: JournalpostPathParam
) {
    if ((this as RoutingNode).pluginRegistry.getOrNull(AttributeKey(TILGANG_PLUGIN)) != null) {
        throw IllegalStateException("Fant allerede registeret tilgang plugin")
    }
    install(buildTilgangTilJournalpostPlugin { call: ApplicationCall ->
        JournalpostTilgangRequest(
            call.parameters.getOrFail(journalpostPathParam.param).toLong(),
            null,
            Operasjon.SE
        )
    })
}

fun Route.installerTilgangPluginWithApprovedList(
    approvedList: List<String>
) {
    if ((this as RoutingNode).pluginRegistry.getOrNull(AttributeKey(TILGANG_PLUGIN)) != null) {
        throw IllegalStateException("Fant allerede registeret tilgang plugin")
    }
    install(buildTilgangPluginWithApprovedList(approvedList))
}

fun buildTilgangPluginWithApprovedList(approvedList: List<String>): RouteScopedPlugin<Unit> {
    return createRouteScopedPlugin(name = TILGANG_PLUGIN) {
        on(AuthenticationChecked) { call ->
            val azp = call.azp()

            if (azp.name !in approvedList) {
                call.respond(HttpStatusCode.Forbidden, "Ingen tilgang, $azp er ikke i approvedList")
            } else {
                log.info("Tilgang gitt til $azp, er i godkjentListe $approvedList")
            }
        }
    }
}

inline fun buildTilgangTilBehandlingPlugin(crossinline parse: suspend (call: ApplicationCall) -> BehandlingTilgangRequest): RouteScopedPlugin<Unit> {
    return createRouteScopedPlugin(name = TILGANG_PLUGIN) {
        on(AuthenticationChecked) { call ->
            val input = parse(call)
            val harTilgang =
                TilgangGateway.harTilgangTilBehandling(
                    input,
                    currentToken = call.token()
                )
            if (!harTilgang) {
                call.respond(HttpStatusCode.Forbidden, "Ingen tilgang")
            }
        }
    }
}

inline fun buildTilgangTilSakPlugin(crossinline parse: suspend (call: ApplicationCall) -> SakTilgangRequest): RouteScopedPlugin<Unit> {
    return createRouteScopedPlugin(name = TILGANG_PLUGIN) {
        on(AuthenticationChecked) { call ->
            val input = parse(call)
            val harTilgang =
                TilgangGateway.harTilgangTilSak(
                    input,
                    currentToken = call.token()
                )
            if (!harTilgang) {
                call.respond(HttpStatusCode.Forbidden, "Ingen tilgang")
            }
        }
    }
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

inline fun buildTilgangTilJournalpostPlugin(crossinline parse: suspend (call: ApplicationCall) -> JournalpostTilgangRequest): RouteScopedPlugin<Unit> {
    return createRouteScopedPlugin(name = TILGANG_PLUGIN) {
        on(AuthenticationChecked) { call ->
            val input = parse(call)
            val harTilgang =
                TilgangGateway.harTilgangTilJournalpost(
                    input,
                    currentToken = call.token()
                )
            if (!harTilgang) {
                call.respond(HttpStatusCode.Forbidden, "Ingen tilgang")
            }
        }
    }
}

suspend inline fun <reified T : Behandlingsreferanse> ApplicationCall.parseBehandlingFraRequestBody(
    operasjon: Operasjon
): BehandlingTilgangRequest {
    val referanseObject: T = DefaultJsonMapper.fromJson<T>(receiveText())
    val referanse = referanseObject.hentBehandlingsreferanse()
    val avklaringsbehovKode = referanseObject.hentAvklaringsbehovKode()
    return BehandlingTilgangRequest(referanse, avklaringsbehovKode, operasjon)
}

inline fun <reified T : Any> parseParams(params: Parameters) =
    DefaultJsonMapper.objectMapper()
        .convertValue(params.entries().associate { it.key to it.value.first() }, T::class.java)

suspend inline fun <reified T : Any> ApplicationCall.parseGeneric(): T {
    if (T::class == Unit::class) return Unit as T
    return DefaultJsonMapper.fromJson<T>(receiveText())
}


suspend inline fun <reified T : Saksreferanse> ApplicationCall.parseSakFraRequestBody(
    operasjon: Operasjon
): SakTilgangRequest {
    val referanseObject: T = DefaultJsonMapper.fromJson<T>(receiveText())
    val referanse = referanseObject.hentSaksreferanse()
    return SakTilgangRequest(referanse, operasjon)
}

suspend inline fun <reified T : Journalpostreferanse> ApplicationCall.parseJournalpostFraRequestBody(
    operasjon: Operasjon
): JournalpostTilgangRequest {
    val referanseObject: T = DefaultJsonMapper.fromJson<T>(receiveText())
    val referanse = referanseObject.hentJournalpostreferanse()
    val avklaringsbehovKode = referanseObject.hentAvklaringsbehovKode()
    return JournalpostTilgangRequest(referanse, avklaringsbehovKode, operasjon)
}

fun ApplicationCall.azp(): AzpName {
    val azp = principal<JWTPrincipal>()?.getClaim("azp_name", String::class)
    if (azp == null) {
        error("azp mangler i AzureAD claims")
    }
    return AzpName(azp)
}
