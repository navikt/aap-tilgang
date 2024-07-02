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
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytClient
import tilgang.integrasjoner.pdl.PdlGraphQLClient
import tilgang.regler.*

fun Route.tilgang(
    pdlClient: PdlGraphQLClient,
    behandlingsflytClient: BehandlingsflytClient,
    geoService: GeoService,
    roles: List<Role>
) {
    route("/tilgang") {
        post {
            val body = call.receive<TilgangRequest>()
            val token = call.token()
            val roller = parseRoller(rolesWithGroupIds = roles, call.roller())
            val geoRoller = geoService.hentGeoRoller(token)

            val identer = behandlingsflytClient.hentIdenter(token, body.saksnummer).identer
            val søkerIdent = identer.first()
            val personer =
                requireNotNull(
                    pdlClient.hentPersonBolk(
                        identer,
                        call.request.header("Nav-CallId") ?: "ukjent"
                    )
                )
            val søkersGeografiskeTilknytning = requireNotNull(
                pdlClient.hentGeografiskTilknytning(
                    søkerIdent,
                    call.request.header("Nav-CallId") ?: "ukjent"
                )
            )

            if (vurderTilgang(
                    call.ident(),
                    Roller(geoRoller, roller),
                    søkerIdent,
                    søkersGeografiskeTilknytning,
                    personer,
                    body.behandlingsreferanse,
                    body.avklaringsbehov,
                    body.operasjon
                )
            ) {
                call.respond(HttpStatusCode.OK, TilgangResponse(true))
            }
            call.respond(HttpStatusCode.OK, TilgangResponse(false))

        }

        // TODO: Fjern denne
        post("/lese") {
            val body = call.receive<TilgangLeseRequest>()
            val roller = parseRoller(rolesWithGroupIds = roles, call.roller())
            val geoRoller = geoService.hentGeoRoller(call.token())
            val personer =
                requireNotNull(pdlClient.hentPersonBolk(body.identer, call.request.header("Nav-CallId") ?: "ukjent"))
            val søkersGeografiskeTilknytning = requireNotNull(
                pdlClient.hentGeografiskTilknytning(
                    body.identer.first(),
                    call.request.header("Nav-CallId") ?: "ukjent"
                )
            )
            if (harLesetilgang(call.ident(), Roller(geoRoller, roller), personer, søkersGeografiskeTilknytning)) {
                call.respond(HttpStatusCode.OK, TilgangResponse(true))
            }
            call.respond(HttpStatusCode.OK, TilgangResponse(false))
        }
    }
}

data class TilgangRequest(
    val saksnummer: String,
    val behandlingsreferanse: String,
    val avklaringsbehov: Avklaringsbehov?,
    val operasjon: Operasjon
)

enum class Operasjon {
    SE,
    SAKSBEHANDLE,
    DRIFTE,
    DELEGERE
}

data class TilgangLeseRequest(val identer: List<String>)
data class TilgangResponse(val tilgang: Boolean)