package tilgang.fakes

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.nomFake() {
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    routing {
        post("/graphql") {
            val body = call.receive<String>()
            if (body.contains("ressurs")) {
                call.respondText(genererNavIdentRespons())
            } else {
                throw NotImplementedError("Mangler implementasjon for fake NOM-spørring")
            }
        }
    }
}

private fun genererNavIdentRespons(): String {
    return """
        {
          "data": {
            "ressurs": {
              "navident": "Z999999"
            }
          }
        }
    """.trimIndent()
}
