package tilgang.integrasjoner.tilgangsmaskin

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.micrometer.core.instrument.MeterRegistry
import java.net.URI
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
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

interface ITilgangsmaskinGateway {
    suspend fun harTilgangTilPerson(brukerIdent: String, token: OidcToken): Boolean
    suspend fun harTilganger(brukerIdenter: List<BrukerOgRegeltype>, token: OidcToken): Boolean
    suspend fun harTilgangTilPersonKjerne(
        brukerIdent: String,
        token: OidcToken,
        ansattIdent: String,
    ): HarTilgangFraTilgangsmaskinen
}

private val log = LoggerFactory.getLogger(TilgangsmaskinGateway::class.java)

/**
 * Se Confluence for dokumentasjon.
 * https://confluence.adeo.no/spaces/TM/pages/628888614/Intro+til+Tilgangsmaskinen
 */
class TilgangsmaskinGateway(
    private val redis: Redis,
    private val prometheus: MeterRegistry,
    private val tokenProvider: ITokenProvider = TokenProvider,
) : ITilgangsmaskinGateway {
    private val baseUrl = URI.create(requiredConfigForKey("integrasjon.tilgangsmaskin.url"))

    override suspend fun harTilgangTilPerson(brukerIdent: String, token: OidcToken): Boolean {
        val url = baseUrl.resolve("/api/v1/komplett").toString()
        log.info("Kaller tilgangsmaskin med url: $url")
        return try {
            defaultHttpClient.post(url) {
                bearerAuth(tokenProvider.oboToken(requiredConfigForKey("integrasjon.tilgangsmaskin.scope"), token))
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
    ): HarTilgangFraTilgangsmaskinen {
        val url = baseUrl.resolve("/api/v1/kjerne").toString()
        redis.get(Key(TILGANGSMASKIN_KJERNE_PREFIX, brukerIdent + ansattIdent))?.let {
            prometheus.cacheHit(TILGANGSMASKIN_KJERNE_PREFIX).increment()
            return it.deserialize()
        }
        prometheus.cacheMiss(TILGANGSMASKIN_KJERNE_PREFIX).increment()

        return try {
            defaultHttpClient.post(url) {
                bearerAuth(tokenProvider.oboToken(requiredConfigForKey("integrasjon.tilgangsmaskin.scope"), token))
                contentType(ContentType.Application.Json)
                setBody(brukerIdent)
            }
            val tilgang = HarTilgangFraTilgangsmaskinen(true)
            redis.set(Key(TILGANGSMASKIN_KJERNE_PREFIX, brukerIdent + ansattIdent), tilgang.serialize(), 21600)
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
                redis.set(Key(TILGANGSMASKIN_KJERNE_PREFIX, brukerIdent + ansattIdent), ikkeTilgang.serialize(), 21600)
                ikkeTilgang
            } else throw e
        }
    }

    override suspend fun harTilganger(brukerIdenter: List<BrukerOgRegeltype>, token: OidcToken): Boolean {
        val url = baseUrl.resolve("/api/v1/bulk").toString()
        log.info("Kaller tilgangsmaskin med url: $url")
        return try {
            defaultHttpClient.post(url) {
                bearerAuth(tokenProvider.oboToken(requiredConfigForKey("integrasjon.tilgangsmaskin.scope"), token))
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
    }
}