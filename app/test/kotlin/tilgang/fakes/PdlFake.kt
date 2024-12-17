package tilgang.fakes

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

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
                call.respondText(genererHentPersonBolkRespons(body))
            } else if (body.contains("hentGeografiskTilknytning")) {
                call.respondText(genererHentAdressebeskytelseOgGeotilknytning())
            } else {
                throw NotImplementedError("Mangler implementasjon for fake PDL-spørring")
            }
        }
    }
}

private fun genererHentPersonBolkRespons(body: String): String {
    val identer = finnIdenterIBody(body)

    if (identer.size == 2) {
        return """
                    { "data":
                    {"hentPersonBolk": [
                            {
                              "ident": "${identer[0]}",
                              "person": {
                                 "navn": [
                                   {
                                     "fornavn": "Ola",
                                     "mellomnavn": null,
                                     "etternavn": "Normann"
                                   }
                                 ]
                              },
                              "code": "ok"
                            },
                            {
                              "ident": "${identer[1]}",
                              "person": null,
                              "code": "not_found"
                            }
                            ]
                    }}
                """.trimIndent()
    } else {
        return """
                    { "data":
                    {"hentPersonBolk": [
                            {
                              "ident": "${if (identer.isEmpty()) "1234568" else identer[0]}",
                              "person": {
                                 "navn": [
                                   {
                                     "fornavn": "Ola",
                                     "mellomnavn": null,
                                     "etternavn": "Normann"
                                   }
                                 ]
                              },
                              "code": "ok"
                            }
                            ]
                    }}
                """.trimIndent()
    }
}

private fun genererHentAdressebeskytelseOgGeotilknytning(): String {
    return """
            {
              "data": {
                "hentPerson": {
                  "adressebeskyttelse": ["UGRADERT"]
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

private fun finnIdenterIBody(body: String): List<String> {
    return body.substringAfter("\"identer\" :")
        .substringBefore("}")
        .substringBefore(",")
        .replace("[", "")
        .replace("]", "")
        .replace("\"", "")
        .split(",")
        .map { it.replace("\n", "").trim() }
        .filter { it != "null" }
}
