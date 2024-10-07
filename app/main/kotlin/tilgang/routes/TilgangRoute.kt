package tilgang.routes

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.ktor.server.request.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import tilgang.Role
import tilgang.auth.ident
import tilgang.auth.roller
import tilgang.auth.token
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytClient
import tilgang.metrics.httpCallCounter
import tilgang.TilgangRequest
import tilgang.TilgangResponse
import tilgang.regler.RegelInput
import tilgang.regler.RegelService
import tilgang.regler.parseRoller

fun NormalOpenAPIRoute.tilgang(
    behandlingsflytClient: BehandlingsflytClient,
    regelService: RegelService,
    roles: List<Role>,
    prometheus: PrometheusMeterRegistry
) {
    route("/tilgang") {
        post<Unit, TilgangResponse, TilgangRequest> { _, req ->
            prometheus.httpCallCounter("/tilgang").increment()

            val callId = pipeline.context.request.header("Nav-CallId") ?: "ukjent"

            val token = token()
            val roller = parseRoller(rolesWithGroupIds = roles, roller())

            require(req.saksnummer != null || req.behandlingsreferanse != null)
            val identer = when (req.saksnummer != null) {
                true -> behandlingsflytClient.hentIdenterForSak(
                    token,
                    req.saksnummer!!
                )

                false -> behandlingsflytClient.hentIdenterForBehandling(
                    token,
                    req.behandlingsreferanse!!
                )
            }

            val avklaringsbehov =
                if (req.avklaringsbehovKode != null) Definisjon.forKode(req.avklaringsbehovKode!!) else null

            val regelInput = RegelInput(
                callId, ident(), token, roller, identer, avklaringsbehov, req.operasjon
            )
            val harTilgang = regelService.vurderTilgang(regelInput)

            respond(TilgangResponse(harTilgang))
        }
    }
}