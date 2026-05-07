package tilgang.fakes

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.safFake() {
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    routing {
        post("/graphql") {
            val body = call.receive<String>()
            if (body.contains("journalpost")) {
                call.respondText(genererJournalpostRespons())
            } else {
                throw NotImplementedError("Mangler implementasjon for fake SAF-spørring")
            }
        }
    }
}

private fun genererJournalpostRespons(): String {
    return """
        {
          "data": {
            "journalpost": {
              "journalpostId": 123456789,
              "sak": {
                "fagsakId": "SAK123",
                "fagsaksystem": "AAP",
                "sakstype": "FAGSAK"
              },
              "bruker": {
                "id": "12345678910",
                "type": "FNR"
              }
            }
          }
        }
    """.trimIndent()
}
