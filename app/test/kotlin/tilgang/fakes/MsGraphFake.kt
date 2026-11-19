package tilgang.fakes

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.util.UUID
import tilgang.integrasjoner.msgraph.Group
import tilgang.integrasjoner.msgraph.MemberOf

fun Application.msGraphFake() {
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    routing {
        get("/me/memberOf") {
            call.respond(
                MemberOf(
                    groups = listOf(
                        gruppe("0000-GA-GEO_NASJONAL"),
                        gruppe("0000-GA-GEO_UTLAND"),
                    )
                )
            )
        }
    }
}

private fun gruppe(name: String) = Group(
    id = UUID.randomUUID(),
    name = name,
)
