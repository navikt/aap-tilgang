package tilgang.integrasjoner.nom

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.auth.UserIdPrincipal
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.config.requiredConfigForKey
import tilgang.auth.ITokenProvider
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize

interface INomGateway {
    suspend fun personNummerTilNavIdent(søkerIdent: String, callId: String): String
}

/**
 * Bruk av oppslagene er erstattet av tilgangsmaskinen, kan fjernes med tid når vi ser at tilgangsmaskinen er stabil nok
 */
open class NomGateway(
    private val redis: Redis,
    private val httpClient: HttpClient,
    private val tokenProvider: ITokenProvider,
    private val prometheus: PrometheusMeterRegistry,
) : INomGateway {
    private val nomBaseUrl = requiredConfigForKey("nom.base.url")

    override suspend fun personNummerTilNavIdent(søkerIdent: String, callId: String): String {
        redis.get(Key(NOM_PREFIX, søkerIdent))?.let {
            prometheus.cacheHit(NOM_PREFIX).increment()
            return it.deserialize()
        }

        prometheus.cacheMiss(NOM_PREFIX).increment()

        val query = NomRequest.hentNavIdentFraPersonIdent(søkerIdent)
        val response = httpClient.post(nomBaseUrl) {
            bearerAuth(tokenProvider.m2mToken(requiredConfigForKey("nom.scope")))
            header("Accept", "application/json")
            header("Nav-Call-Id", callId)
            contentType(ContentType.Application.Json)
            setBody(query)
        }.body<NOMRespons>()

        val navIdentFraNOM = response.data?.ressurs?.navident.orEmpty()
        redis.set(Key(NOM_PREFIX, søkerIdent), navIdentFraNOM.serialize(), 3600)
        return navIdentFraNOM
    }

    companion object {
        private const val NOM_PREFIX = "nom"
    }
}