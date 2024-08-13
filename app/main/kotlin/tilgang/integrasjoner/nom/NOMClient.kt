package tilgang.integrasjoner.nom

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.slf4j.LoggerFactory
import tilgang.LOGGER
import tilgang.NOMConfig
import tilgang.auth.AzureAdTokenProvider
import tilgang.auth.AzureConfig
import tilgang.http.HttpClientFactory
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytClient
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis

interface INOMClient {
    suspend fun personNummerTilNavIdent(søkerIdent: String): String
}

private val log = LoggerFactory.getLogger(BehandlingsflytClient::class.java)

open class NOMClient(azureConfig: AzureConfig, private val redis: Redis, private val nomConfig: NOMConfig, private val prometheus: PrometheusMeterRegistry): INOMClient {
    val httpClient = HttpClientFactory.create()
    private val azureTokenProvider = AzureAdTokenProvider(
        azureConfig,
        nomConfig.scope
    ).also { LOGGER.info("azure scope: ${nomConfig.scope}") }

    override suspend fun personNummerTilNavIdent(søkerIdent: String): String {
        val azureToken = azureTokenProvider.getClientCredentialToken()

        log.info("Got token: $azureToken")

        if (redis.exists(Key("nom", søkerIdent))) {
            prometheus.cacheHit("nom").increment()
            return redis[Key("nom", søkerIdent)]!!.toNavident()
        }

        prometheus.cacheMiss("nom").increment()

        val query = NOMRequest.hentNavIdentFraPersonIdent(søkerIdent)
        val response = httpClient.post(nomConfig.baseUrl) {
            accept(ContentType.Application.Json)
            bearerAuth(azureToken)
            contentType(ContentType.Application.Json)
            setBody(query)
        }

        log.info("Got response status: ${response.status}")

        return when (response.status) {
            HttpStatusCode.OK -> {
                val result = response.body<NOMRespons>()
                log.info("Got response body: $result")
                val navIdentFraNOM = result.data?.ressurs?.navident.orEmpty()
                log.info("Got navIdentFraNOM: $navIdentFraNOM")
                redis.set(Key("nom", søkerIdent), navIdentFraNOM.toByteArray(), 3600)
                navIdentFraNOM
            }
            else -> throw NOMException("Feil ved henting av match for søkerIdent ($søkerIdent) mot NOM: ${response.status} : ${response.bodyAsText()}")
        }
    }
}

fun ByteArray.toNavident(): String {
    val mapper = jacksonObjectMapper()
    val tr = object : TypeReference<String>() {}
    return mapper.readValue(this, tr)
}

fun String.toByteArray(): ByteArray {
    val mapper = jacksonObjectMapper()
    return mapper.writeValueAsBytes(this)
}