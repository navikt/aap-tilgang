package tilgang.integrasjoner.msgraph

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.util.UUID
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize

interface IMsGraphGateway {
    suspend fun hentAdGrupper(currentToken: OidcToken, ident: String): MemberOf
}

class MsGraphGateway(
    private val redis: Redis,
    private val httpClient: HttpClient,
    private val prometheus: PrometheusMeterRegistry,
) : IMsGraphGateway {
    override suspend fun hentAdGrupper(currentToken: OidcToken, ident: String): MemberOf {
        redis[Key(MSGRAPH_PREFIX, ident)]?.let {
            prometheus.cacheHit(MSGRAPH_PREFIX).increment()
            return it.deserialize()
        }
        prometheus.cacheMiss(MSGRAPH_PREFIX).increment()

        val respons = httpClient.get("me/memberOf?\$top=500&\$select=id,mailNickname") {
            attributes.put(msGraphCurrentTokenKey, currentToken)
        }.body<MemberOf>()

        redis.set(Key(MSGRAPH_PREFIX, ident), respons.serialize())
        return respons
    }

    companion object {
        private const val MSGRAPH_PREFIX = "msgraph"
    }
}

data class MemberOf(
    @param:JsonProperty("value")
    val groups: List<Group> = emptyList(),
)

data class Group(
    @param:JsonProperty("id")
    val id: UUID,
    @param:JsonProperty("mailNickname")
    val name: String? = null,
)