package tilgang.integrasjoner.nom

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize
import java.net.URI

interface INomClient {
    fun personNummerTilNavIdent(søkerIdent: String, callId: String): String
}

open class NomClient(
    private val redis: Redis,
    private val prometheus: PrometheusMeterRegistry
) : INomClient {
    private val config = ClientConfig(
        scope = requiredConfigForKey("nom.scope")
    )
    private val httpClient = RestClient.withDefaultResponseHandler(
        config = config,
        tokenProvider = ClientCredentialsTokenProvider,
        prometheus = prometheus,
    )
    private val baseUrl = URI.create(requiredConfigForKey("nom.base.url"))

    override fun personNummerTilNavIdent(søkerIdent: String, callId: String): String {
        if (redis.exists(Key(NOM_PREFIX, søkerIdent))) {
            prometheus.cacheHit(NOM_PREFIX).increment()
            return redis[Key(NOM_PREFIX, søkerIdent)]!!.deserialize()
        }

        prometheus.cacheMiss(NOM_PREFIX).increment()

        val query = NomRequest.hentNavIdentFraPersonIdent(søkerIdent)
        val request = PostRequest(
            query,
            additionalHeaders = listOf(
                Header("Accept", "application/json"),
                Header("Nav-Call-Id", callId)
            )
        )
        val response: NOMRespons =
            httpClient.post(baseUrl, request)
                ?: throw NomException("Feil ved henting av match mot NOM")

        val navIdentFraNOM = response.data?.ressurs?.navident.orEmpty()
        redis.set(Key(NOM_PREFIX, søkerIdent), navIdentFraNOM.serialize(), 3600)
        return navIdentFraNOM
    }

    companion object {
        private const val NOM_PREFIX = "nom"
    }
}