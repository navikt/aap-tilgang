package tilgang.integrasjoner.behandlingsflyt

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.get
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import org.slf4j.LoggerFactory
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize
import java.net.URI

private val log = LoggerFactory.getLogger(BehandlingsflytClient::class.java)

class BehandlingsflytClient(
    private val redis: Redis,
    private val prometheus: PrometheusMeterRegistry
) {
    private val baseUrl = URI.create(requiredConfigForKey("behandlingsflyt.base.url"))
    private val clientConfig = ClientConfig(
        scope = requiredConfigForKey("behandlingsflyt.scope"),
    )
    private val httpClient = RestClient.withDefaultResponseHandler(
        config = clientConfig,
        tokenProvider = ClientCredentialsTokenProvider,
        prometheus = prometheus,
    )

    fun hentIdenterForSak(saksnummer: String): IdenterRespons {
        if (redis.exists(Key(IDENTER_SAK_PREFIX, saksnummer))) {
            prometheus.cacheHit(BEHANDLINGSFLYT).increment()
            return redis[Key(IDENTER_SAK_PREFIX, saksnummer)]!!.deserialize()
        }
        prometheus.cacheMiss(BEHANDLINGSFLYT).increment()

        val url = baseUrl.resolve("/pip/api/sak/${saksnummer}/identer")
        log.info("Kaller behandlingsflyt med URL: $url")

        val respons = httpClient.get<IdenterRespons>(url, GetRequest())
            ?: throw BehandlingsflytException("Feil ved henting av identer for sak")

        redis.set(Key(IDENTER_SAK_PREFIX, saksnummer), respons.serialize())
        return respons
    }

    fun hentIdenterForBehandling(behandlingsnummer: String): IdenterRespons {
        if (redis.exists(Key(IDENTER_BEHANDLING_PREFIX, behandlingsnummer))) {
            prometheus.cacheHit(BEHANDLINGSFLYT).increment()
            return redis[Key(IDENTER_BEHANDLING_PREFIX, behandlingsnummer)]!!.deserialize()
        }
        prometheus.cacheMiss(BEHANDLINGSFLYT).increment()

        val url = baseUrl.resolve("/pip/api/behandling/${behandlingsnummer}/identer")
        log.info("Kaller behandlingsflyt med URL: $url")

        val respons = httpClient.get<IdenterRespons>(url, GetRequest())
            ?: throw BehandlingsflytException("Feil ved henting av identer for behandling")
        redis.set(Key(IDENTER_BEHANDLING_PREFIX, behandlingsnummer), respons.serialize())
        return respons
    }

    companion object {
        private const val IDENTER_SAK_PREFIX = "identer_sak"
        private const val IDENTER_BEHANDLING_PREFIX = "identer_behandling"
        private const val BEHANDLINGSFLYT = "Behandlingsflyt"
    }
}

data class IdenterRespons(val søker: List<String>, val barn: List<String>)
