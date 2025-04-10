package tilgang.routes

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.*
import io.ktor.server.request.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.httpklient.auth.token
import no.nav.aap.tilgang.*
import tilgang.LOGGER
import tilgang.Role
import tilgang.TilgangService
import tilgang.auth.ident
import tilgang.auth.roller
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinRequest
import tilgang.metrics.httpCallCounter
import tilgang.metrics.nektetTilgangTeller
import tilgang.regler.parseRoller

private val log = org.slf4j.LoggerFactory.getLogger("tilgang.routes.TilgangRoute")

fun NormalOpenAPIRoute.tilgang(
    tilgangService: TilgangService,
    roles: List<Role>,
    prometheus: PrometheusMeterRegistry
) {
    route("/tilgang") {
        route("/sak") {
            post<Unit, TilgangResponse, SakTilgangRequest> { _, req ->
                prometheus.httpCallCounter("/tilgang/sak").increment()

                val callId = pipeline.call.request.header("Nav-CallId") ?: "ukjent"
                val token = token()
                val roller = parseRoller(rolesWithGroupIds = roles, roller())

                val harTilgang =
                    tilgangService.harTilgangTilSak(ident(), req, roller, token, callId)

                if (!harTilgang) {
                    prometheus.nektetTilgangTeller("sak").increment()
                }

                respond(TilgangResponse(harTilgang))
            }
        }
        route("/behandling") {
            post<Unit, TilgangResponse, BehandlingTilgangRequest> { _, req ->
                prometheus.httpCallCounter("/tilgang/behandling").increment()

                val callId = pipeline.call.request.header("Nav-CallId") ?: "ukjent"
                val token = token()
                val roller = parseRoller(rolesWithGroupIds = roles, roller())

                val harTilgang =
                    tilgangService.harTilgangTilBehandling(ident(), req, roller, token, callId)

                if (!harTilgang) {
                    prometheus.nektetTilgangTeller("behandling").increment()
                }

                respond(TilgangResponse(harTilgang))
            }
        }
        route("/journalpost") {
            post<Unit, TilgangResponse, JournalpostTilgangRequest> { _, req ->
                prometheus.httpCallCounter("/tilgang/journalpost").increment()

                if (req.operasjon == Operasjon.SAKSBEHANDLE && req.avklaringsbehovKode == null) {
                    LOGGER.info("Kan ikke saksbehandle uten avklaringsbehov $req")
                    respondWithStatus(HttpStatusCode.BadRequest)
                }

                val callId = pipeline.call.request.header("Nav-CallId") ?: "ukjent"
                val token = token()
                val roller = parseRoller(rolesWithGroupIds = roles, roller())

                val harTilgang =
                    tilgangService.harTilgangTilJournalpost(ident(), req, roller, token, callId)

                if (!harTilgang) {
                    prometheus.nektetTilgangTeller("journalpost").increment()
                }

                respond(TilgangResponse(harTilgang))
            }
        }
        route("/test/tilgangsmaskinen") {
            post<Unit, TilgangResponse, TilgangsmaskinRequest> { _, req ->
                val harTilgang =  tilgangService.harTilgangFraTilgangsmaskin(req.brukerIdenter, token())
                respond(TilgangResponse(harTilgang))
            }
        }
    }
}
