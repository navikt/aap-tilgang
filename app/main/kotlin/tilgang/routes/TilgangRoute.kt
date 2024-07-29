package tilgang.routes

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.ktor.server.request.*
import tilgang.Role
import tilgang.auth.ident
import tilgang.auth.roller
import tilgang.auth.token
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytClient
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.regler.Avklaringsbehov
import tilgang.regler.RegelInput
import tilgang.regler.RegelService
import tilgang.regler.parseRoller

fun NormalOpenAPIRoute.tilgang(
    behandlingsflytClient: BehandlingsflytClient, regelService: RegelService, roles: List<Role>, redis: Redis
) {
    route("/tilgang") {
        post<Unit, TilgangResponse, TilgangRequest> {_, req ->
            val callId = pipeline.context.request.header("Nav-CallId") ?: "ukjent"
            
            val key = Key(req.hashCode().toString())
            if (redis.exists(key)) {
                respond(redis[key]!!.toTilgangResponse())
            }
            
            val token = token()
            val roller = parseRoller(rolesWithGroupIds = roles, roller())
            val identer = behandlingsflytClient.hentIdenter(token, req.saksnummer)
            val avklaringsbehov =
                if (req.avklaringsbehovKode != null) Avklaringsbehov.fraKode(req.avklaringsbehovKode) else null

            val regelInput = RegelInput(
                callId, ident(), token, roller, identer, req.behandlingsreferanse, avklaringsbehov, req.operasjon
            )
            val harTilgang = regelService.vurderTilgang(regelInput)
            
            redis.set(key, TilgangResponse(harTilgang).toByteArray(), 3600)
            respond(TilgangResponse(harTilgang))
        }
    }
}

fun ByteArray.toTilgangResponse(): TilgangResponse {
    val mapper = ObjectMapper()
    val tr = object : TypeReference<TilgangResponse>() {}
    return mapper.readValue(this, tr)
}

fun TilgangResponse.toByteArray(): ByteArray {
    val mapper = ObjectMapper()
    return mapper.writeValueAsBytes(this)
}

data class TilgangRequest(
    val saksnummer: String,
    val behandlingsreferanse: String?,
    val avklaringsbehovKode: String?,
    val operasjon: Operasjon
)

enum class Operasjon {
    SE, SAKSBEHANDLE, DRIFTE, DELEGERE
}

data class TilgangResponse(val tilgang: Boolean)