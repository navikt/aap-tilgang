package tilgang.integrasjoner.behandlingsflyt

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.net.URI
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.tilgang.RelevanteIdenter
import org.slf4j.LoggerFactory
import tilgang.auth.ITokenProvider
import tilgang.auth.TokenProvider
import tilgang.http.defaultHttpClient
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize

private val log = LoggerFactory.getLogger(BehandlingsflytGateway::class.java)

class BehandlingsflytGateway(
    private val redis: Redis,
    private val prometheus: PrometheusMeterRegistry,
    private val tokenProvider: ITokenProvider = TokenProvider,
) {
    private val baseUrl = URI.create(requiredConfigForKey("behandlingsflyt.base.url"))

    suspend fun hentIdenterForSak(saksnummer: String): RelevanteIdenter {
        redis.get(Key(IDENTER_SAK_PREFIX, saksnummer))?.let {
            prometheus.cacheHit(BEHANDLINGSFLYT).increment()
            return it.deserialize()
        }
        prometheus.cacheMiss(BEHANDLINGSFLYT).increment()

        val url = baseUrl.resolve("/pip/api/sak/$saksnummer/identer").toString()
        log.info("Kaller behandlingsflyt med URL: $url")
        val identer = defaultHttpClient.get(url) {
            bearerAuth(tokenProvider.m2mToken(requiredConfigForKey("behandlingsflyt.scope")))
        }.body<RelevanteIdenter>()

        redis.set(Key(IDENTER_SAK_PREFIX, saksnummer), identer.serialize())
        return identer
    }

    suspend fun hentIdenterForBehandling(behandlingsnummer: String): RelevanteIdenter {
        redis.get(Key(IDENTER_BEHANDLING_PREFIX, behandlingsnummer))?.let {
            prometheus.cacheHit(BEHANDLINGSFLYT).increment()
            return it.deserialize()
        }
        prometheus.cacheMiss(BEHANDLINGSFLYT).increment()

        val url = baseUrl.resolve("/pip/api/behandling/$behandlingsnummer/identer").toString()
        log.info("Kaller behandlingsflyt med URL: $url")
        val identer = defaultHttpClient.get(url) {
            bearerAuth(tokenProvider.m2mToken(requiredConfigForKey("behandlingsflyt.scope")))
        }.body<RelevanteIdenter>()

        redis.set(Key(IDENTER_BEHANDLING_PREFIX, behandlingsnummer), identer.serialize())
        return identer
    }

    companion object {
        private const val IDENTER_SAK_PREFIX = "identer_sak"
        private const val IDENTER_BEHANDLING_PREFIX = "identer_behandling"
        private const val BEHANDLINGSFLYT = "Behandlingsflyt"
    }
}
