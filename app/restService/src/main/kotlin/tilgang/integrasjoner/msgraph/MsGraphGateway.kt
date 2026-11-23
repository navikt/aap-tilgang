package tilgang.integrasjoner.msgraph

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.net.URI
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

        var nextUrl: String? = "me/memberOf?\$top=500&\$select=id,mailNickname"
        var origin: URI? = null
        val groups = mutableListOf<Group>()

        while (nextUrl != null) {
            val response = httpClient.get(nextUrl) {
                attributes.put(msGraphCurrentTokenKey, currentToken)
            }
            val page = response.body<MemberOfPage>()
            val currentUri = URI(response.call.request.url.toString())
            val currentOrigin = currentUri.origin()
            if (origin != null && origin != currentOrigin) {
                throw IllegalStateException("MS Graph-side endret vert under paginering")
            }
            origin = currentOrigin

            groups += page.groups
            nextUrl = page.nextLink?.let { link ->
                val resolved = currentUri.resolve(link)
                require(resolved.origin() == currentOrigin) {
                    "MS Graph-side peker til en annen vert"
                }
                resolved.toString()
            }
        }

        return MemberOf(groups).also { redis.set(Key(MSGRAPH_PREFIX, ident), it.serialize()) }
    }

    companion object {
        private const val MSGRAPH_PREFIX = "msgraph"
    }
}

private fun URI.origin(): URI {
    require(scheme != null && host != null && userInfo == null && fragment == null) {
        "Ugyldig MS Graph-side"
    }
    return URI(scheme.lowercase(), null, host.lowercase(), port, null, null, null)
}

private data class MemberOfPage(
    @param:JsonProperty("value")
    val groups: List<Group> = emptyList(),
    @param:JsonProperty("@odata.nextLink")
    val nextLink: String? = null,
)

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