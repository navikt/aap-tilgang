package tilgang.integrasjoner.msgraph

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import tilgang.MsGraphConfig
import tilgang.auth.AzureAdTokenProvider
import tilgang.auth.AzureConfig
import tilgang.http.HttpClientFactory
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis

interface IMsGraphClient {
    suspend fun hentAdGrupper(currentToken: String, ident:String): MemberOf
}

class MsGraphClient(azureConfig: AzureConfig, private val msGraphConfig: MsGraphConfig, private val redis: Redis, private val prometheus: PrometheusMeterRegistry) : IMsGraphClient {
    private val httpClient = HttpClientFactory.create()
    private val azureTokenProvider = AzureAdTokenProvider(
        azureConfig,
        msGraphConfig.scope,
    )

    override suspend fun hentAdGrupper(currentToken: String, ident: String): MemberOf {
        val graphToken = azureTokenProvider.getOnBehalfOfToken(currentToken)

        if (redis.exists(Key("msgraph", ident))) {
            prometheus.cacheHit("msgraph").increment()
            return redis[Key("msgraph", ident)]!!.toMemberOf()
        }
        prometheus.cacheMiss("msgraph").increment()

        val respons = httpClient.get("${msGraphConfig.baseUrl}/me/memberOf") {
            bearerAuth(graphToken)
            contentType(ContentType.Application.Json)
        }

        return when (respons.status) {
            HttpStatusCode.OK -> {
                val memberOf = respons.body<MemberOf>()
                redis.set(Key("msgraph", ident), memberOf.toByteArray(), 3600)
                respons.body()
            }
            else -> throw MsGraphException("Feil fra Microsoft Graph: ${respons.status} : ${respons.bodyAsText()}")
        }
    }

    fun ByteArray.toMemberOf(): MemberOf {
        val mapper = ObjectMapper()
        val tr = object : TypeReference<MemberOf>() {}
        return mapper.readValue(this, tr)
    }

    fun MemberOf.toByteArray(): ByteArray {
        val mapper = ObjectMapper()
        return mapper.writeValueAsBytes(this)
    }
}

data class MemberOf(
    @JsonProperty("value")
    val groups: List<Group> = emptyList()
)

data class Group(
    @JsonProperty("id")
    val id: String,
    @JsonProperty("mailNickname")
    val name: String
)