package tilgang.fakes

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.ContentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import tilgang.integrasjoner.pdl.PdlRequest

fun Application.pdlFake() {
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    routing {
        post("/graphql") {
            val body = call.receive<String>()
            if (body.contains("hentPersonBolk")) {
                call.respondText(genererHentPersonBolkRespons(body), ContentType.Application.Json)
            } else if (body.contains("hentGeografiskTilknytning")) {
                call.respondText(genererHentAdressebeskytelseOgGeotilknytning(), ContentType.Application.Json)
            } else {
                throw NotImplementedError("Mangler implementasjon for fake PDL-spørring")
            }
        }
    }
}

private fun genererHentPersonBolkRespons(body: String): String {
    val identer = jacksonObjectMapper().readValue<PdlRequest>(body).variables.identer.orEmpty()
    val personer = identer.mapIndexed { index, ident ->
        val notFound = identer.size == 2 && index == 1
        mapOf(
            "ident" to ident,
            "person" to if (notFound) null else mapOf("adressebeskyttelse" to emptyList<Any>()),
            "code" to if (notFound) "not_found" else "ok",
        )
    }
    return jacksonObjectMapper().writeValueAsString(mapOf("data" to mapOf("hentPersonBolk" to personer)))
}

private fun genererHentAdressebeskytelseOgGeotilknytning(): String {
    return """
            {
              "data": {
                "hentPerson": {
                  "adressebeskyttelse": [
                    { "gradering": "UGRADERT" }
                  ]
                },
                "hentGeografiskTilknytning": {
                  "gtType": "KOMMUNE",
                  "gtKommune": "3207",
                  "gtBydel": null,
                  "gtLand": null
                }
              }
            }
        """.trimIndent()
}
