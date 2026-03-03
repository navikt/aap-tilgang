package no.nav.aap.tilgang

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.net.URI
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.retryablePost
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.OnBehalfOfTokenProvider

object TilgangGateway {
    private val baseUrl = URI.create(requiredConfigForKey("integrasjon.tilgang.url"))
    private val config = ClientConfig(scope = requiredConfigForKey("integrasjon.tilgang.scope"))
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    private val tilgangGatewayBehandlingCache = Caffeine.newBuilder()
        .maximumSize(2_000)
        .expireAfterWrite(Duration.ofMinutes(30))
        .recordStats()
        .build<BehandlingTilgangRequestMedNavIdent, TilgangResponse>()

    private val tilgangGatewaySakCache = Caffeine.newBuilder()
        .maximumSize(2_000)
        .expireAfterWrite(Duration.ofMinutes(30))
        .recordStats()
        .build<SakTilgangRequestMedNavIdent, TilgangResponse>()

    init {
        CaffeineCacheMetrics.monitor(prometheus, tilgangGatewayBehandlingCache, "tilgang_behandling_cache")
        CaffeineCacheMetrics.monitor(prometheus, tilgangGatewaySakCache, "tilgang_sak_cache")
    }

    private val client = RestClient.withDefaultResponseHandler(
        config = config,
        tokenProvider = OnBehalfOfTokenProvider
    )

    suspend fun harTilgangTilSak(body: SakTilgangRequest, currentToken: OidcToken): TilgangResponse {
        return get(tilgangGatewaySakCache, SakTilgangRequestMedNavIdent(body, currentToken.navIdent())) {
            val httpRequest = PostRequest(
                body = body,
                currentToken = currentToken
            )
            runInterruptible(Dispatchers.IO) {
                requireNotNull(
                    client.retryablePost<_, TilgangResponse>(
                        uri = baseUrl.resolve("/tilgang/sak"),
                        request = httpRequest
                    )
                )
            }
        }
    }

    suspend fun harTilgangTilBehandling(body: BehandlingTilgangRequest, currentToken: OidcToken): TilgangResponse {
        return get(tilgangGatewayBehandlingCache, BehandlingTilgangRequestMedNavIdent(body, currentToken.navIdent())) {
            val httpRequest = PostRequest(
                body = body,
                currentToken = currentToken
            )
            runInterruptible(Dispatchers.IO) {
                requireNotNull(
                    client.retryablePost<_, TilgangResponse>(
                        uri = baseUrl.resolve("/tilgang/behandling"),
                        request = httpRequest
                    )
                )
            }
        }
    }

    suspend fun harTilgangTilJournalpost(
        body: JournalpostTilgangRequest,
        currentToken: OidcToken
    ): TilgangResponse {
        val httpRequest = PostRequest(
            body = body,
            currentToken = currentToken
        )
        return runInterruptible(Dispatchers.IO) {
            requireNotNull(
                client.retryablePost<_, TilgangResponse>(
                    uri = baseUrl.resolve("/tilgang/journalpost"),
                    request = httpRequest
                )
            )
        }
    }

    suspend fun harTilgangTilPerson(body: PersonTilgangRequest, currentToken: OidcToken): TilgangResponse {
        val httpRequest = PostRequest(
            body = body,
            currentToken = currentToken
        )
        return runInterruptible(Dispatchers.IO) {
            requireNotNull(
                client.retryablePost<_, TilgangResponse>(
                    uri = baseUrl.resolve("/tilgang/person"),
                    request = httpRequest
                )
            )
        }
    }

    suspend fun harTilgangTilTilbakekreving(
        body: TilbakekrevingTilgangRequest,
        currentToken: OidcToken
    ): TilgangResponse {
        val httpRequest = PostRequest(
            body = body,
            currentToken = currentToken
        )
        return runInterruptible(Dispatchers.IO) {
            requireNotNull(
                client.retryablePost<_, TilgangResponse>(
                    uri = baseUrl.resolve("/tilgang/tilbakekreving"),
                    request = httpRequest
                )
            )
        }
    }

    /** Oppslag på samme key vil kunne kjøre parallelt. */
    suspend fun <Key : Any, Value: Any> get(cache: Cache<Key, Value>, key: Key, mapper: suspend (key: Key) -> Value): Value {
        val cached = cache.getIfPresent(key)
        if (cached != null) {
            return cached
        }

        val value = mapper(key)
        cache.put(key, value)
        return value
    }
}

private data class BehandlingTilgangRequestMedNavIdent(
    val behandlingTilgangRequest: BehandlingTilgangRequest,
    val navIdent: String
)

private data class SakTilgangRequestMedNavIdent(
    val sakTilgangRequest: SakTilgangRequest,
    val navIdent: String
)
