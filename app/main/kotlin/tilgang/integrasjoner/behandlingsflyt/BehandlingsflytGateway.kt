package tilgang.integrasjoner.behandlingsflyt

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.tilgang.RelevanteIdenter
import org.slf4j.LoggerFactory
import tilgang.auth.ITokenProvider
import tilgang.auth.TokenProvider
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize

private val log = LoggerFactory.getLogger(BehandlingsflytGateway::class.java)

class BehandlingsflytGateway(
    private val redis: Redis,
    private val httpClient: HttpClient,
    private val prometheus: PrometheusMeterRegistry,
    private val tokenProvider: ITokenProvider = TokenProvider,
) {
    private val baseUrl = requiredConfigForKey("BEHANDLINGSFLYT_BASE_URL")
    private val scope = requiredConfigForKey("BEHANDLINGSFLYT_SCOPE")

    suspend fun hentIdenterForSak(saksnummer: String): RelevanteIdenter {
        redis[Key(IDENTER_SAK_PREFIX, saksnummer)]?.let {
            prometheus.cacheHit(BEHANDLINGSFLYT).increment()
            return it.deserialize()
        }
        prometheus.cacheMiss(BEHANDLINGSFLYT).increment()

        log.info("Kaller behandlingsflyt for å hente identer for sak ($saksnummer)")
        val identer = try {
            httpClient.get("$baseUrl/pip/api/sak/$saksnummer/identer") {
                bearerAuth(tokenProvider.m2mToken(scope))
            }.body<RelevanteIdenter>()
        } catch (e: Exception) {
            throw BehandlingsflytException(e.message ?: "Ukjent feil oppsto mot behandlingsflyt")
        }

        redis.set(Key(IDENTER_SAK_PREFIX, saksnummer), identer.serialize())
        return identer
    }

    suspend fun hentIdenterForBehandling(behandlingsnummer: String): RelevanteIdenter {
        redis[Key(IDENTER_BEHANDLING_PREFIX, behandlingsnummer)]?.let {
            prometheus.cacheHit(BEHANDLINGSFLYT).increment()
            return it.deserialize()
        }
        prometheus.cacheMiss(BEHANDLINGSFLYT).increment()

        log.info("Kaller behandlingsflyt for å hente identer for behandling ($behandlingsnummer)")
        val identer = httpClient.get("$baseUrl/pip/api/behandling/$behandlingsnummer/identer") {
            bearerAuth(tokenProvider.m2mToken(scope))
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
