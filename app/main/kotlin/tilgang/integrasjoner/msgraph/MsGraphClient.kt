package tilgang.integrasjoner.msgraph

import com.fasterxml.jackson.annotation.JsonProperty
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.get
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.OnBehalfOfTokenProvider
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize
import java.net.URI
import java.util.*

interface IMsGraphClient {
    fun hentAdGrupper(currentToken: String, ident: String): MemberOf
}

class MsGraphClient(
    private val redis: Redis,
    private val prometheus: PrometheusMeterRegistry
) : IMsGraphClient {
    private val baseUrl = URI.create(requiredConfigForKey("ms.graph.base.url"))
    
    private val clientConfig = ClientConfig(
        scope = requiredConfigForKey("ms.graph.scope")
    )
    private val httpClient =  RestClient.withDefaultResponseHandler(
        config = clientConfig,
        tokenProvider = OnBehalfOfTokenProvider,
    )

    override fun hentAdGrupper(currentToken: String, ident: String): MemberOf {
        if (redis.exists(Key(MSGRAPH_PREFIX, ident))) {
            prometheus.cacheHit(MSGRAPH_PREFIX).increment()
            return redis[Key(MSGRAPH_PREFIX, ident)]!!.deserialize()
        }
        prometheus.cacheMiss(MSGRAPH_PREFIX).increment()
        
        val url = baseUrl.resolve("/me/memberOf")
        val respons = httpClient.get<MemberOf>(url, GetRequest()) ?: MemberOf()
        redis.set(Key(MSGRAPH_PREFIX, ident), respons.serialize())
        return respons
    }

    companion object {
        private const val MSGRAPH_PREFIX = "msgraph"
    }
}

data class MemberOf(
    @JsonProperty("value")
    val groups: List<Group> = emptyList()
)

data class Group(
    @JsonProperty("id")
    val id: UUID,
    @JsonProperty("mailNickname")
    val name: String
)