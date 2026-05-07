package tilgang.fakes

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.behandlingsflytFake() {
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    routing {
        get("/pip/api/sak/{saksnummer}/identer") {
            call.respondText(genererRelevanteIdenter())
        }

        get("/pip/api/behandling/{behandlingsnummer}/identer") {
            call.respondText(genererRelevanteIdenter())
        }
    }
}

private fun genererRelevanteIdenter(): String {
    return """
        {
          "søker": ["12345678910"],
          "barn": []
        }
    """.trimIndent()
}
