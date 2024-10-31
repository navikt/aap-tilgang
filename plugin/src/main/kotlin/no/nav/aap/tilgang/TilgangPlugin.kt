package no.nav.aap.tilgang

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
import tilgang.BehandlingTilgangRequest
import tilgang.JournalpostTilgangRequest
import tilgang.Operasjon
import tilgang.SakTilgangRequest

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

inline fun <reified T : Journalpostreferanse> Route.installerTilgangTilJournalpostPlugin(
    operasjon: Operasjon,
) {
    install(DoubleReceive)
    install(buildTilgangTilJournalpostPlugin { call: ApplicationCall -> call.parseJournalpostFraRequestBody<T>(operasjon) })
}

fun Route.installerTilgangGetPlugin(
    behandlingPathParam: BehandlingPathParam
) {
    install(buildTilgangTilBehandlingPlugin { call: ApplicationCall ->
        BehandlingTilgangRequest(
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
        SakTilgangRequest(
            call.parameters.getOrFail(sakPathParam.param),
            Operasjon.SE
        )
    })
}

inline fun <reified TParams: Any, reified TRequest: Any>Route.installerTilgangPlugin(
    journalpostIdResolver: JournalpostIdResolver<TParams, TRequest>, avklaringsbehovResolver: AvklaringsbehovResolver<TRequest>? = null, operasjon: Operasjon = Operasjon.SE
) {
    install(DoubleReceive)
    install(buildTilgangTilJournalpostPlugin { call: ApplicationCall ->
        JournalpostTilgangRequest(
            journalpostIdResolver.resolve(parseParams<TParams>(call.parameters), call.pareseGeneric()),
            avklaringsbehovResolver?.resolve(call.pareseGeneric()),
            operasjon
        )
    })
}

fun Route.installerTilgangGetPlugin(
    journalpostPathParam: JournalpostPathParam
) {
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

inline fun buildTilgangTilBehandlingPlugin(crossinline parse: suspend (call: ApplicationCall) -> BehandlingTilgangRequest): RouteScopedPlugin<Unit> {
    return createRouteScopedPlugin(name = "TilgangPlugin") {
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

inline fun buildTilgangTilJournalpostPlugin(crossinline parse: suspend (call: ApplicationCall) -> JournalpostTilgangRequest): RouteScopedPlugin<Unit> {
    return createRouteScopedPlugin(name = "TilgangPlugin") {
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

inline fun <reified T: Any> parseParams(params: Parameters) =
    DefaultJsonMapper.objectMapper().convertValue(params.entries().associate { it.key to it.value.first() }, T::class.java)

suspend inline fun <reified T : Any> ApplicationCall.pareseGeneric(): T {
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

interface Journalpostreferanse {
    fun hentJournalpostreferanse(): Long
    fun hentAvklaringsbehovKode(): String?
}