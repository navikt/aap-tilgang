package tilgang.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tilgang.Role
import tilgang.auth.ident
import tilgang.auth.roller
import tilgang.auth.token
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytClient
import tilgang.regler.*

fun Route.tilgang(
    behandlingsflytClient: BehandlingsflytClient,
    regelService: RegelService,
    roles: List<Role>
) {
    route("/tilgang") {
        post {
            val body = call.receive<TilgangRequest>()
            val callId = call.request.header("Nav-CallId") ?: "ukjent"
            val token = call.token()
            val roller = parseRoller(rolesWithGroupIds = roles, call.roller())
            val identer = behandlingsflytClient.hentIdenter(token, body.saksnummer)
            val avklaringsbehov =
                if (body.avklaringsbehovKode != null) Avklaringsbehov.fraKode(body.avklaringsbehovKode) else null

            val regelInput = RegelInput(
                callId,
                call.ident(),
                token,
                roller,
                identer,
                body.behandlingsreferanse,
                avklaringsbehov, body.operasjon
            )
            if (regelService.vurderTilgang(regelInput)
            ) {
                call.respond(HttpStatusCode.OK, TilgangResponse(true))
            }
            call.respond(HttpStatusCode.OK, TilgangResponse(false))

        }
    }
}

data class TilgangRequest(
    val saksnummer: String,
    val behandlingsreferanse: String?,
    val avklaringsbehovKode: String?,
    val operasjon: Operasjon
)

enum class Operasjon {
    SE,
    SAKSBEHANDLE,
    DRIFTE,
    DELEGERE
}

data class TilgangResponse(val tilgang: Boolean)