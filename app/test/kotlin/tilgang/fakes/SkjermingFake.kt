package tilgang.fakes

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.skjermingFake() {
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    routing {
        post("/skjermetBulk") {
            val body = call.receive<SkjermetBulkRequest>()
            val respons = body.personidenter.associateWith { false }
            call.respond(respons)
        }
    }
}

private data class SkjermetBulkRequest(val personidenter: List<String>)
