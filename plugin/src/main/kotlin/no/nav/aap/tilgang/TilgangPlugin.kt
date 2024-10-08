package no.nav.aap.tilgang

import tilgang.Operasjon
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import no.nav.aap.komponenter.httpklient.auth.AzpName
import no.nav.aap.komponenter.httpklient.auth.token
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import org.slf4j.LoggerFactory
import tilgang.BehandlingRequest
import tilgang.SakRequest

val log = LoggerFactory.getLogger("TilgangPlugin")

inline fun <reified T : Behandlingsreferanse> Route.installerTilgangTilBehandlingPostPlugin(
    operasjon: Operasjon,
) {
    install(DoubleReceive)
    install(buildTilgangTilBehandlingPlugin { call: ApplicationCall -> call.parseBehandlingFraRequestBody<T>(operasjon) })
}

inline fun <reified T : Saksreferanse> Route.installerTilgangTilSakPostPlugin(
    operasjon: Operasjon,
) {
    install(DoubleReceive)
    install(buildTilgangTilSakPlugin { call: ApplicationCall -> call.parseSakFraRequestBody<T>(operasjon) })
}

fun Route.installerTilgangGetPlugin(
    behandlingPathParam: BehandlingPathParam
) {
    install(buildTilgangTilBehandlingPlugin { call: ApplicationCall ->
        BehandlingRequest(
            call.parameters.getOrFail(
                behandlingPathParam.param
            ), null, Operasjon.SE
        )
    })

}

fun Route.installerTilgangGetPlugin(
    sakPathParam: SakPathParam
) {
    install(buildTilgangTilSakPlugin { call: ApplicationCall ->
        SakRequest(
            call.parameters.getOrFail(sakPathParam.param),
            Operasjon.SE
        )
    })
}

fun Route.installerTilgangPluginWithApprovedList(
    approvedList: List<String>
) {
    install(buildTilgangPluginWithApprovedList(approvedList))
}


fun buildTilgangPluginWithApprovedList(approvedList: List<String>): RouteScopedPlugin<Unit> {
    return createRouteScopedPlugin(name = "ApprovedListPlugin") {
        on(AuthenticationChecked) { call ->
            val azn = call.azn()

            if (azn.name !in approvedList) {
                call.respond(HttpStatusCode.Forbidden, "Ingen tilgang, $azn er ikke i approvedList")
            } else {
                log.info("Tilgang gitt til $azn, er i godkjentListe $approvedList")
            }
        }
    }
}

inline fun buildTilgangTilBehandlingPlugin(crossinline parse: suspend (call: ApplicationCall) -> BehandlingRequest): RouteScopedPlugin<Unit> {
    return createRouteScopedPlugin(name = "TilgangTilBehandlingPlugin") {
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

inline fun buildTilgangTilSakPlugin(crossinline parse: suspend (call: ApplicationCall) -> SakRequest): RouteScopedPlugin<Unit> {
    return createRouteScopedPlugin(name = "TilgangPlugin") {
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

suspend inline fun <reified T : Behandlingsreferanse> ApplicationCall.parseBehandlingFraRequestBody(
    operasjon: Operasjon
): BehandlingRequest {
    val referanseObject: T = DefaultJsonMapper.fromJson<T>(receiveText())
    val referanse = referanseObject.hentBehandlingsreferanse()
    val avklaringsbehovKode = referanseObject.hentAvklaringsbehovKode()
    return BehandlingRequest(referanse, avklaringsbehovKode, operasjon)
}

suspend inline fun <reified T : Saksreferanse> ApplicationCall.parseSakFraRequestBody(
    operasjon: Operasjon
): SakRequest {
    val referanseObject: T = DefaultJsonMapper.fromJson<T>(receiveText())
    val referanse = referanseObject.hentSaksreferanse()
    return SakRequest(referanse, operasjon)
}

fun ApplicationCall.azn(): AzpName {
    val azp = principal<JWTPrincipal>()?.getClaim("azp_name", String::class)
    if (azp == null) {
        error("azp mangler i AzureAD claims")
    }
    return AzpName(azp)
}

interface Saksreferanse {
    fun hentSaksreferanse(): String
}

interface Behandlingsreferanse {
    fun hentBehandlingsreferanse(): String
    fun hentAvklaringsbehovKode(): String?
}