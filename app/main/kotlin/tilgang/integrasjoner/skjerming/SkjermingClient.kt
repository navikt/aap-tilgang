package tilgang.integrasjoner.skjerming

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import tilgang.integrasjoner.behandlingsflyt.IdenterRespons
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize
import java.net.URI

open class SkjermingClient(
    private val redis: Redis,
    private val prometheus: PrometheusMeterRegistry
) {
    private val baseUrl = URI.create(requiredConfigForKey("skjerming.base.url"))
    private val config = ClientConfig(
        scope = requiredConfigForKey("skjerming.scope")
    )
    private val httpClient = RestClient.withDefaultResponseHandler(
        config = config,
        tokenProvider = ClientCredentialsTokenProvider,
        prometheus = prometheus,
    )

    open fun isSkjermet(identer: IdenterRespons): Boolean {
        if (redis.exists(Key(SKJERMING_PREFIX, identer.søker.first()))) {
            prometheus.cacheHit(SKJERMING_PREFIX).increment()
            return redis[Key(SKJERMING_PREFIX, identer.søker.first())]!!.deserialize()
        }
        prometheus.cacheMiss(SKJERMING_PREFIX).increment()

        val url = baseUrl.resolve("/skjermetBulk")
        val alleRelaterteSøkerIdenter = (identer.søker + identer.barn).distinct()

        val response: Map<String, Boolean> =
            httpClient.post(url, PostRequest(SkjermetDataBulkRequestDTO(alleRelaterteSøkerIdenter)))
                ?: throw SkjermingException("Feil ved henting av skjerming")

        val eksistererSkjermet = response.values.any { identIsSkjermet -> identIsSkjermet }
        redis.set(Key(SKJERMING_PREFIX, identer.søker.first()), eksistererSkjermet.serialize())
        return eksistererSkjermet
    }

    companion object {
        private const val SKJERMING_PREFIX = "skjerming"
    }

}

internal data class SkjermetDataBulkRequestDTO(val personidenter: List<String>)
