package tilgang.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.tilgang() {
    route("/tilgang") {
        post("/lese") {
            val body = call.receive<TilgangRequest>()

            call.respond(HttpStatusCode.OK, body)

        }
    }
}

data class TilgangRequest(val identer: List<String>)
