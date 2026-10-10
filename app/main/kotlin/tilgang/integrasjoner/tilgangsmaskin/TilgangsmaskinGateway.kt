package tilgang.integrasjoner.tilgangsmaskin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.micrometer.core.instrument.MeterRegistry
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import org.slf4j.LoggerFactory
import tilgang.auth.ITokenProvider
import tilgang.auth.TokenProvider
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize

interface ITilgangsmaskinGateway {
    suspend fun harTilgangTilPerson(brukerIdent: String, token: OidcToken, callId: String? = null): Boolean
    suspend fun harTilganger(
        brukerIdenter: List<BrukerOgRegeltype>,
        token: OidcToken,
        callId: String? = null,
    ): Boolean

    suspend fun harTilgangTilPersonKjerne(
        brukerIdent: String,
        token: OidcToken,
        ansattIdent: String,
        callId: String? = null,
    ): HarTilgangFraTilgangsmaskinen

    suspend fun harTilgangTilPersonKomplett(
        brukerIdent: String,
        token: OidcToken,
        ansattIdent: String,
        callId: String? = null,
    ): HarTilgangFraTilgangsmaskinen
}

private val log = LoggerFactory.getLogger(TilgangsmaskinGateway::class.java)

// NB: Denne TTL-en gjelder både positive (tilgang) og negative (avslag) resultater. Et avslag som
// senere endrer seg (f.eks. skjerming/verge fjernes) blir dermed liggende i cache i inntil 6 timer.
// Vurder kortere TTL for negative svar dersom ferskhet blir viktigere enn treff i cachen.
private val redisExpireSec = 21600L

/**
 * Se Confluence for dokumentasjon.
 * https://confluence.adeo.no/spaces/TM/pages/628888614/Intro+til+Tilgangsmaskinen
 */
class TilgangsmaskinGateway(
    private val redis: Redis,
    private val httpClient: HttpClient,
    private val prometheus: MeterRegistry,
    private val tokenProvider: ITokenProvider = TokenProvider,
) : ITilgangsmaskinGateway {
    private val baseUrl = requiredConfigForKey("INTEGRASJON_TILGANGSMASKIN_URL")
    private val scope = requiredConfigForKey("INTEGRASJON_TILGANGSMASKIN_SCOPE")

    override suspend fun harTilgangTilPerson(brukerIdent: String, token: OidcToken, callId: String?): Boolean {
        return try {
            httpClient.post("$baseUrl/api/v1/komplett") {
                bearerAuth(tokenProvider.oboToken(scope, token))
                tilgangsmaskinSporingsHeaders(callId)
                contentType(ContentType.Text.Plain)
                setBody(brukerIdent)
            }
            true
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Forbidden) {
                log.info("Kall til tilgangsmaskin returnerte 403")
                false
            } else throw e
        }
    }

    override suspend fun harTilgangTilPersonKjerne(
        brukerIdent: String,
        token: OidcToken,
        ansattIdent: String,
        callId: String?,
    ): HarTilgangFraTilgangsmaskinen {
        redis[Key(TILGANGSMASKIN_KJERNE_PREFIX, brukerIdent + ansattIdent)]?.let {
            prometheus.cacheHit(TILGANGSMASKIN_KJERNE_PREFIX).increment()
            return it.deserialize()
        }
        prometheus.cacheMiss(TILGANGSMASKIN_KJERNE_PREFIX).increment()

        return try {
            httpClient.post("$baseUrl/api/v1/kjerne") {
                bearerAuth(tokenProvider.oboToken(scope, token))
                tilgangsmaskinSporingsHeaders(callId)
                contentType(ContentType.Application.Json)
                setBody(brukerIdent)
            }
            val tilgang = HarTilgangFraTilgangsmaskinen(true)
            redis.set(Key(TILGANGSMASKIN_KJERNE_PREFIX, brukerIdent + ansattIdent), tilgang.serialize(), redisExpireSec)
            tilgang
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Forbidden) {
                val avvistResponse = runCatching {
                    e.response.body<TilgangsmaskinAvvistResponse>()
                }.onFailure { parseErr ->
                    log.warn("Greide ikke parse avvist-respons fra tilgangsmaskinen", parseErr)
                }.getOrNull()
                avvistResponse?.let { log.info("403 fra tilgangsmaskin: ${it.title}") }
                val ikkeTilgang = HarTilgangFraTilgangsmaskinen(false, avvistResponse)
                redis.set(
                    Key(TILGANGSMASKIN_KJERNE_PREFIX, brukerIdent + ansattIdent),
                    ikkeTilgang.serialize(),
                    redisExpireSec
                )
                ikkeTilgang
            } else throw e
        }
    }

    override suspend fun harTilgangTilPersonKomplett(
        brukerIdent: String,
        token: OidcToken,
        ansattIdent: String,
        callId: String?,
    ): HarTilgangFraTilgangsmaskinen {
        redis[Key(TILGANGSMASKIN_KOMPLETT_PREFIX, brukerIdent + ansattIdent)]?.let {
            prometheus.cacheHit(TILGANGSMASKIN_KOMPLETT_PREFIX).increment()
            return it.deserialize()
        }
        prometheus.cacheMiss(TILGANGSMASKIN_KOMPLETT_PREFIX).increment()

        return try {
            httpClient.post("$baseUrl/api/v1/komplett") {
                bearerAuth(tokenProvider.oboToken(scope, token))
                tilgangsmaskinSporingsHeaders(callId)
                contentType(ContentType.Application.Json)
                setBody(brukerIdent)
            }
            val tilgang = HarTilgangFraTilgangsmaskinen(true)
            redis.set(
                Key(TILGANGSMASKIN_KOMPLETT_PREFIX, brukerIdent + ansattIdent),
                tilgang.serialize(),
                redisExpireSec
            )
            tilgang
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Forbidden) {
                val avvistResponse = runCatching {
                    e.response.body<TilgangsmaskinAvvistResponse>()
                }.onFailure { parseErr ->
                    log.warn("Greide ikke parse avvist-respons fra tilgangsmaskinen", parseErr)
                }.getOrNull()
                avvistResponse?.let { log.info("403 fra tilgangsmaskin: ${it.title}") }
                val ikkeTilgang = HarTilgangFraTilgangsmaskinen(false, avvistResponse)
                redis.set(
                    Key(TILGANGSMASKIN_KOMPLETT_PREFIX, brukerIdent + ansattIdent),
                    ikkeTilgang.serialize(),
                    redisExpireSec
                )
                ikkeTilgang
            } else throw e
        }
    }

    override suspend fun harTilganger(
        brukerIdenter: List<BrukerOgRegeltype>,
        token: OidcToken,
        callId: String?,
    ): Boolean {
        return try {
            httpClient.post("$baseUrl/api/v1/bulk") {
                bearerAuth(tokenProvider.oboToken(scope, token))
                tilgangsmaskinSporingsHeaders(callId)
                contentType(ContentType.Application.Json)
                setBody(brukerIdenter)
            }
            true
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Forbidden) {
                log.info("Kall til tilgangsmaskin returnerte 403")
                false
            } else throw e
        }
    }

    companion object {
        private const val TILGANGSMASKIN_KJERNE_PREFIX = "tilgangsmaskinKjerne"
        private const val TILGANGSMASKIN_KOMPLETT_PREFIX = "tilgangsmaskinKomplett"
        private const val NAV_CONSUMER_ID_HEADER = "Nav-Consumer-Id"
        private val consumerId = System.getenv("NAIS_APP_NAME") ?: "tilgang"
    }

    // Tilgangsmaskinen leser X-Correlation-ID for sporing på tvers av tjenester, og Nav-Consumer-Id
    // for å identifisere kallende applikasjon. Kalleren sender med callId-en fra inngående request
    // (samme som PDL-/SAF-gatewayene bruker); er den ikke satt bruker vi verdikonvensjonen med 'ukjent'.
    private fun HttpRequestBuilder.tilgangsmaskinSporingsHeaders(callId: String?) {
        header(HttpHeaders.XCorrelationId, callId ?: "ukjent")
        header(NAV_CONSUMER_ID_HEADER, consumerId)
    }
}