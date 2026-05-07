package tilgang.fakes

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistResponse

fun Application.tilgangsmaskinFake() {
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    routing {
        post("/api/v1/kjerne") {
            val body = call.receive<String>()
            if (body.contains("123")) {
                call.respond(HttpStatusCode.NoContent)
            } else if (body.contains("456")) {
                val body = TilgangsmaskinAvvistResponse(
                    type = "https://confluence.adeo.no/display/TM/Tilgangsmaskin+API+og+regelsett",
                    title = "AVVIST_HABILITET",
                    status = 403,
                    navIdent = "Z990883",
                    begrunnelse = "Inhabil",
                    kanOverstyres = false
                )
                call.respond(HttpStatusCode.Forbidden, body)
            } else {
                call.respond(HttpStatusCode.Accepted, "{}") // For lokal kjøring
            }
        }

        post("/api/v1/komplett") {
            val body = call.receive<String>()
            if (body.contains("456")) {
                val body = TilgangsmaskinAvvistResponse(
                    type = "https://confluence.adeo.no/display/TM/Tilgangsmaskin+API+og+regelsett",
                    title = "AVVIST_HABILITET",
                    status = 403,
                    navIdent = "Z990883",
                    begrunnelse = "Inhabil",
                    kanOverstyres = false
                )
                call.respond(HttpStatusCode.Forbidden, body)
            } else {
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}