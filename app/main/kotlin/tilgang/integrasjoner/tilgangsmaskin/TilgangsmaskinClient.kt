package tilgang.integrasjoner.tilgangsmaskin

import io.micrometer.core.instrument.MeterRegistry
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.error.ManglerTilgangException
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import java.net.URI
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.ContentType
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.OnBehalfOfTokenProvider
import no.nav.aap.komponenter.json.DefaultJsonMapper
import org.slf4j.LoggerFactory
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize

interface ITilgangsmaskinClient {
    fun harTilgangTilPerson(brukerIdent: String, token: OidcToken): Boolean
    fun harTilganger(brukerIdenter: List<BrukerOgRegeltype>, token: OidcToken): Boolean
    fun harTilgangTilPersonKjerne(brukerIdent: String, token: OidcToken): HarTilgangFraTilgangsmaskinen
}

private val log = LoggerFactory.getLogger(TilgangsmaskinClient::class.java)

/**
 * Se Confluence for dokumentasjon.
 * https://confluence.adeo.no/spaces/TM/pages/628888614/Intro+til+Tilgangsmaskinen
 */
class TilgangsmaskinClient(
    private val redis: Redis,
    private val prometheus: MeterRegistry
) : ITilgangsmaskinClient {
    private val config = ClientConfig(
        scope = requiredConfigForKey("integrasjon.tilgangsmaskin.scope")
    )

    private val baseUrl = URI.create(requiredConfigForKey("integrasjon.tilgangsmaskin.url"))
    private val httpClient = RestClient.withDefaultResponseHandler(
        tokenProvider = OnBehalfOfTokenProvider,
        config = config,
        prometheus = prometheus,
    )

    override fun harTilgangTilPerson(
        brukerIdent: String,
        token: OidcToken
    ): Boolean {
        val url = baseUrl.resolve("/api/v1/komplett")
        val request = PostRequest(
            body = brukerIdent,
            currentToken = token,
            contentType = ContentType.TEXT_PLAIN
        )
        try {
            log.info("Kaller tilgangsmaskin med url: $url")
            httpClient.post<_, Unit>(url, request)
            return true
        } catch (e: ManglerTilgangException) {
            log.info("Kall til tilgangsmaskin returnerte 403")
            return false
        }
    }

    override fun harTilgangTilPersonKjerne(
        brukerIdent: String,
        token: OidcToken
    ): HarTilgangFraTilgangsmaskinen {
        val url = baseUrl.resolve("/api/v1/kjerne")
        if (redis.exists(Key(TILGANGSMASKIN_KJERNE_PREFIX, brukerIdent))) {
            prometheus.cacheHit(TILGANGSMASKIN_KJERNE_PREFIX).increment()
            return redis[Key(TILGANGSMASKIN_KJERNE_PREFIX, brukerIdent)]!!.deserialize()
        }
        prometheus.cacheMiss(TILGANGSMASKIN_KJERNE_PREFIX).increment()

        val request = PostRequest(
            body = brukerIdent,
            currentToken = token,
            contentType = ContentType.APPLICATION_JSON
        )

        return try {
            httpClient.post<_, Unit>(
                uri = url,
                request = request
            )
            val tilgang = HarTilgangFraTilgangsmaskinen(true)
            redis.set(Key(TILGANGSMASKIN_KJERNE_PREFIX, brukerIdent), tilgang.serialize(), 21600)
            tilgang
        } catch (e: Exception) {
            if (e is ManglerTilgangException) {
                val avvistResponse = e.body
                    ?.let {
                        runCatching {
                            DefaultJsonMapper.fromJson(it, TilgangsmaskinAvvistResponse::class.java)
                        }.onFailure { parseErr ->
                            log.warn("Greide ikke parse avvist-respons fra tilgangsmaskinen", parseErr)
                        }.getOrNull()
                    }

                avvistResponse?.let {
                    log.info("403 fra tilgangsmaskin: ${it.title}")
                }

                val ikkeTilgang = HarTilgangFraTilgangsmaskinen(false, avvistResponse)
                redis.set(Key(TILGANGSMASKIN_KJERNE_PREFIX, brukerIdent), ikkeTilgang.serialize(), 21600)
                ikkeTilgang
            } else {
                log.warn("Feil ved kall til tilgangsmaskin", e)
                throw e
            }
        }
    }

    override fun harTilganger(
        brukerIdenter: List<BrukerOgRegeltype>,
        token: OidcToken
    ): Boolean {
        val url = baseUrl.resolve("/api/v1/bulk")
        val request = PostRequest(
            body = brukerIdenter,
            currentToken = token
        )

        try {
            log.info("Kaller tilgangsmaskin med url: $url")
            httpClient.post<_, Unit>(url, request)
            return true
        } catch (e: ManglerTilgangException) {
            log.info("Kall til tilgangsmaskin returnerte 403")
            return false
        }
    }

    companion object {
        private const val TILGANGSMASKIN_KJERNE_PREFIX = "tilgangsmaskinKjerne"
        private const val TILGANGSMASKIN_KOMPLETT_PREFIX = "tilgangsmaskinKomplett"
    }
}