package tilgang.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tilgang.Role
import tilgang.auth.roller
import tilgang.integrasjoner.pdl.PdlGraphQLClient
import tilgang.regler.harTilgangTilPersoner
import tilgang.regler.parseRoller

fun Route.tilgang(pdlClient: PdlGraphQLClient, roles: List<Role>) {
    route("/tilgang") {
        post("/lese") {
            val body = call.receive<TilgangRequest>()
            val roller = parseRoller(rolesWithGroupIds = roles, call.roller())
            val personer = pdlClient.hentPersonBolk(body.identer, call.request.header("Nav-CallId") ?: "ukjent")

            if (harTilgangTilPersoner(roller, personer)) {
                call.respond(HttpStatusCode.OK)
            }

            call.respond(HttpStatusCode.Forbidden, body)
        }
    }
}

data class TilgangRequest(val identer: List<String>)