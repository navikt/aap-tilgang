package tilgang.integrasjoner.behandlingsflyt

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.slf4j.LoggerFactory
import tilgang.BehandlingsflytConfig
import tilgang.auth.AzureAdTokenProvider
import tilgang.auth.AzureConfig
import tilgang.http.HttpClientFactory
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize

private val log = LoggerFactory.getLogger(BehandlingsflytClient::class.java)

class BehandlingsflytClient(
    azureConfig: AzureConfig,
    private val behandlingsflytConfig: BehandlingsflytConfig,
    private val redis: Redis,
    private val prometheus: PrometheusMeterRegistry
) {
    private val httpClient = HttpClientFactory.create()
    private val azureTokenProvider = AzureAdTokenProvider(azureConfig, behandlingsflytConfig.scope)

    suspend fun hentIdenter(currentToken: String, saksnummer: String): IdenterRespons {
        val token = azureTokenProvider.getOnBehalfOfToken(currentToken)
        if (redis.exists(Key(IDENTER_PREFIX, saksnummer))) {
            prometheus.cacheHit(BEHANDLINGSFLYT).increment()
            return redis[Key(IDENTER_PREFIX, saksnummer)]!!.deserialize()
        }
        prometheus.cacheMiss(BEHANDLINGSFLYT).increment()

        val url = "${behandlingsflytConfig.baseUrl}/api/sak/${saksnummer}/identer"
        log.info("Kaller behandlingsflyt med URL: $url")

        val respons = httpClient.get(url) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
        }

        log.info("Respons: $respons")
        log.info("Respons-status: ${respons.status}")
        val body = respons.body<IdenterRespons>()
        log.info("Respons-body: ${body}")

        return when (respons.status) {
            HttpStatusCode.OK -> {
                val identer = body
                redis.set(Key(IDENTER_PREFIX, saksnummer), identer.serialize())
                identer
            }

            else -> throw BehandlingsflytException("Feil ved henting av identer fra behandlingsflyt: ${respons.status} : ${respons.bodyAsText()}")
        }
    }

    companion object {
        private const val IDENTER_PREFIX = "identer"
        private const val BEHANDLINGSFLYT = "Behandlingsflyt"
    }
}

data class IdenterRespons(val søker: List<String>, val barn: List<String>)
