package tilgang.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tilgang.auth.roller
import tilgang.integrasjoner.pdl.Gradering
import tilgang.integrasjoner.pdl.PdlGraphQLClient

fun Route.tilgang(pdlClient: PdlGraphQLClient){
    route("/tilgang") {
        post("/lese") {
            val body = call.receive<TilgangRequest>()
            val roller = call.roller().split(",").filter{parse(it) != null}.map{Rolle.valueOf(it)}
            val personer = pdlClient.hentPersonBolk(body.identer, call.request.header("Nav-CallId") ?: "ukjent")
            val addresseBeskyttelse = personer?.flatMap { it.adressebeskyttelse!! } //TODO: skriv denne litt penere

            if (addresseBeskyttelse.isNullOrEmpty()) {
                call.respond(HttpStatusCode.OK, body)
            } else if (Rolle.KODE_6 in roller) {
                call.respond(HttpStatusCode.OK, body)
            } else if (Rolle.KODE_7 in roller && finnStrengeste(addresseBeskyttelse) === Gradering.FORTROLIG) {
                call.respond(HttpStatusCode.OK, body)
            }

            call.respond(HttpStatusCode.Forbidden, body)
        }
    }
}

data class TilgangRequest(val identer: List<String>)

enum class Rolle {
    VEILEDER,
    SAKSBEHANDLER,
    BESLUTTER,
    AVDELINGSLEDER,
    UTVIKLER,
    KODE_6,
    KODE_7
}

fun parse(rolle: String): Rolle? {
    return try {
        Rolle.valueOf(rolle)
    } catch (e: Exception) {
        null
    }
}

fun finnStrengeste(addresseBeskyttelser: List<Gradering>): Gradering {
    return when {
        Gradering.STRENGT_FORTROLIG in addresseBeskyttelser -> Gradering.STRENGT_FORTROLIG
        Gradering.STRENGT_FORTROLIG_UTLAND in addresseBeskyttelser -> Gradering.STRENGT_FORTROLIG_UTLAND
        else -> Gradering.FORTROLIG
    }
}
