package tilgang.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tilgang.Role
import tilgang.auth.ident
import tilgang.auth.roller
import tilgang.integrasjoner.pdl.PdlGraphQLClient
import tilgang.regler.*

fun Route.tilgang(pdlClient: PdlGraphQLClient, roles: List<Role>) {
    route("/tilgang") {
        post("/lese") {
            val body = call.receive<TilgangLeseRequest>()
            val roller = parseRoller(rolesWithGroupIds = roles, call.roller())
            val personer = pdlClient.hentPersonBolk(body.identer, call.request.header("Nav-CallId") ?: "ukjent")

            if (harLesetilgang(roller, personer)) {
                call.respond(HttpStatusCode.OK)
            }
            call.respond(HttpStatusCode.Forbidden, body)
        }
        post("/skrive") {
            val body = call.receive<TilgangSkriveRequest>()
            val roller = parseRoller(rolesWithGroupIds = roles, call.roller())
            val personer = pdlClient.hentPersonBolk(body.identer, call.request.header("Nav-CallId") ?: "ukjent")
            val enhet = finnEnhet(call.ident())

            if (kanSkriveTilAvklaringsbehov(Avklaringsbehov.fraKode(body.avklaringsbehov), roller, enhet, personer)) {
                call.respond(HttpStatusCode.OK)
            }
            call.respond(HttpStatusCode.Forbidden, body)
        }
    }
}

// TODO: Erstatt med kall til ekstern tjeneste
fun finnEnhet(ident: String): Enhet {
    return Enhet.NAY
}

data class TilgangLeseRequest(val identer: List<String>)
data class TilgangSkriveRequest(val identer: List<String>, val avklaringsbehov: String)