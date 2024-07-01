package tilgang.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tilgang.Role
import tilgang.auth.ident
import tilgang.auth.roller
import tilgang.auth.token
import tilgang.geo.GeoService
import tilgang.integrasjoner.pdl.PdlGraphQLClient
import tilgang.regler.*

fun Route.tilgang(pdlClient: PdlGraphQLClient, geoService: GeoService, roles: List<Role>) {
    route("/tilgang") {
        post("/lese") {
            val body = call.receive<TilgangLeseRequest>()
            val roller = parseRoller(rolesWithGroupIds = roles, call.roller())
            val geoRoller = geoService.hentGeoRoller(call.token())
            val personer =
                requireNotNull(pdlClient.hentPersonBolk(body.identer, call.request.header("Nav-CallId") ?: "ukjent"))

            if (harLesetilgang(call.ident(), geoRoller, roller, personer)) {
                call.respond(HttpStatusCode.OK, TilgangResponse(true))
            }
            call.respond(HttpStatusCode.OK, TilgangResponse(false))
        }
        post("/skrive") {
            val body = call.receive<TilgangSkriveRequest>()
            val roller = parseRoller(rolesWithGroupIds = roles, call.roller())
            val geoRoller = geoService.hentGeoRoller(call.token())
            val personer =
                requireNotNull(pdlClient.hentPersonBolk(body.identer, call.request.header("Nav-CallId") ?: "ukjent"))
            val ident = call.ident()

            if (kanSkriveTilAvklaringsbehov(
                    ident,
                    Avklaringsbehov.fraKode(body.avklaringsbehov),
                    geoRoller,
                    roller,
                    personer
                )
            ) {
                call.respond(HttpStatusCode.OK, TilgangResponse(true))
            }
            call.respond(HttpStatusCode.OK, TilgangResponse(false))
        }
    }
}


data class TilgangLeseRequest(val identer: List<String>)
data class TilgangSkriveRequest(val identer: List<String>, val avklaringsbehov: String)
data class TilgangResponse(val tilgang: Boolean)