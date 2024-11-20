package tilgang.integrasjoner.behandlingsflyt

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import org.slf4j.LoggerFactory
import tilgang.BehandlingsflytConfig
import tilgang.auth.AzureAdTokenProvider
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

    suspend fun hentIdenterForSak(saksnummer: String): IdenterRespons {
        val token = azureTokenProvider.getClientCredentialToken()
        if (redis.exists(Key(IDENTER_SAK_PREFIX, saksnummer))) {
            prometheus.cacheHit(BEHANDLINGSFLYT).increment()
            return redis[Key(IDENTER_SAK_PREFIX, saksnummer)]!!.deserialize()
        }
        prometheus.cacheMiss(BEHANDLINGSFLYT).increment()

        val url = "${behandlingsflytConfig.baseUrl}/pip/api/sak/${saksnummer}/identer"
        log.info("Kaller behandlingsflyt med URL: $url")

        val respons = httpClient.get(url) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
        }

        val body = respons.body<IdenterRespons>()

        return when (respons.status) {
            HttpStatusCode.OK -> {
                val identer = body
                redis.set(Key(IDENTER_SAK_PREFIX, saksnummer), identer.serialize())
                identer
            }

            else -> throw BehandlingsflytException("Feil ved henting av identer fra behandlingsflyt: ${respons.status} : ${respons.bodyAsText()}")
        }
    }

    suspend fun hentIdenterForBehandling(behandlingsnummer: String): IdenterRespons {
        if (redis.exists(Key(IDENTER_BEHANDLING_PREFIX, behandlingsnummer))) {
            prometheus.cacheHit(BEHANDLINGSFLYT).increment()
            return redis[Key(IDENTER_BEHANDLING_PREFIX, behandlingsnummer)]!!.deserialize()
        }
        prometheus.cacheMiss(BEHANDLINGSFLYT).increment()

        val token = azureTokenProvider.getClientCredentialToken()
        val url = "${behandlingsflytConfig.baseUrl}/pip/api/behandling/${behandlingsnummer}/identer"
        log.info("Kaller behandlingsflyt med URL: $url")

        val respons = httpClient.get(url) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
        }

        val body = respons.body<IdenterRespons>()

        return when (respons.status) {
            HttpStatusCode.OK -> {
                val identer = body
                redis.set(Key(IDENTER_BEHANDLING_PREFIX, behandlingsnummer), identer.serialize())
                identer
            }

            else -> throw BehandlingsflytException("Feil ved henting av identer fra behandlingsflyt: ${respons.status} : ${respons.bodyAsText()}")
        }
    }

    companion object {
        private const val IDENTER_SAK_PREFIX = "identer_sak"
        private const val IDENTER_BEHANDLING_PREFIX = "identer_behandling"
        private const val BEHANDLINGSFLYT = "Behandlingsflyt"
    }
}

data class IdenterRespons(val søker: List<String>, val barn: List<String>)
