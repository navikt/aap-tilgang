package tilgang.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tilgang.auth.rolle
import tilgang.integrasjoner.pdl.PdlGraphQLClient

fun Route.tilgang(pdlClient: PdlGraphQLClient){
    route("/tilgang") {
        post("/lese") {
            val body = call.receive<TilgangRequest>()
            val personer = pdlClient.hentPersonBolk(call.authentication.toString(),body.identer, call.request.header("Nav-CallId") ?: "ukjent")
            val addresseBeskyttelse = personer?.flatMap { it.adressebeskyttelse!! } //TODO: skriv denne litt penere

            /*
            sjekk at token tilgang dekker addressebeskyttelse nivået fra pdl
             */

            call.respond(HttpStatusCode.OK, body)

        }
    }
}

data class TilgangRequest(val identer: List<String>)
