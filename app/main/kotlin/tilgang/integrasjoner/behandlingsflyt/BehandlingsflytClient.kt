package tilgang.integrasjoner.behandlingsflyt

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory
import tilgang.BehandlingsflytConfig
import tilgang.auth.AzureAdTokenProvider
import tilgang.auth.AzureConfig
import tilgang.http.HttpClientFactory
import tilgang.redis.Key
import tilgang.redis.Redis

private val log = LoggerFactory.getLogger(BehandlingsflytClient::class.java)

class BehandlingsflytClient(azureConfig: AzureConfig, private val behandlingsflytConfig: BehandlingsflytConfig, private val redis: Redis) {
    private val httpClient = HttpClientFactory.create()
    private val azureTokenProvider = AzureAdTokenProvider(azureConfig, behandlingsflytConfig.scope)

    suspend fun hentIdenter(currentToken: String, saksnummer: String): IdenterRespons {
        val token = azureTokenProvider.getOnBehalfOfToken(currentToken)
        if (redis.exists(Key("identer", saksnummer))) {
            return redis[Key("identer", saksnummer)]!!.toIdenterRespons()
        }
        val url = "${behandlingsflytConfig.baseUrl}/api/sak/${saksnummer}/identer"
        log.info("Kaller behandlingsflyt med URL: $url")

        val respons = httpClient.get(url) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
        }

        return when (respons.status) {
            HttpStatusCode.OK -> {
                val identer = respons.body<IdenterRespons>()
                redis.set(Key("identer", saksnummer), identer.toByteArray(), 3600)
                identer
            }
            else -> throw BehandlingsflytException("Feil ved henting av identer fra behandlingsflyt: ${respons.status} : ${respons.bodyAsText()}")
        }
    }

    fun ByteArray.toIdenterRespons(): IdenterRespons {
        val mapper = ObjectMapper()
        val tr = object : TypeReference<IdenterRespons>() {}
        return mapper.readValue(this, tr)
    }

    fun IdenterRespons.toByteArray(): ByteArray {
        val mapper = ObjectMapper()
        return mapper.writeValueAsBytes(this)
    }
}

data class IdenterRespons(val søker: List<String>, val barn: List<String>)
