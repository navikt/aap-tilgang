package no.nav.aap.tilgang

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import java.net.URI
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken

object TilgangGateway {
    private val baseUrl = URI.create(requiredConfigForKey("INTEGRASJON_TILGANG_URL"))
    private var prometheus: MeterRegistry? = null
    private val tilgangScope = requiredConfigForKey("INTEGRASJON_TILGANG_SCOPE")
    private val texasUrl = requiredConfigForKey("NAIS_TOKEN_EXCHANGE_ENDPOINT")

    private var tilgangGatewayBehandlingCache = Caffeine.newBuilder()
        .maximumSize(2_000)
        .expireAfterWrite(Duration.ofMinutes(30))
        .recordStats()
        .build<BehandlingTilgangRequestMedNavIdent, TilgangResponse>()

    private var tilgangGatewaySakCache = Caffeine.newBuilder()
        .maximumSize(2_000)
        .expireAfterWrite(Duration.ofMinutes(30))
        .recordStats()
        .build<SakTilgangRequestMedNavIdent, TilgangResponse>()

    private var tilgangGatewayJournalpostCache = Caffeine.newBuilder()
        .maximumSize(2_000)
        .expireAfterWrite(Duration.ofMinutes(30))
        .recordStats()
        .build<JournalpostTilgangRequestMedNavIdent, TilgangResponse>()

    private var tilgangGatewayPersonCache = Caffeine.newBuilder()
        .maximumSize(2_000)
        .expireAfterWrite(Duration.ofMinutes(30))
        .recordStats()
        .build<PersonTilgangRequestMedNavIdent, TilgangResponse>()


    fun initialiserPrometheus(registry: MeterRegistry) {
        if (prometheus == null) {
            prometheus = registry
            CaffeineCacheMetrics.monitor(registry, tilgangGatewayBehandlingCache, "tilgang_behandling_cache")
            CaffeineCacheMetrics.monitor(registry, tilgangGatewaySakCache, "tilgang_sak_cache")
            CaffeineCacheMetrics.monitor(registry, tilgangGatewayJournalpostCache, "tilgang_journalpost_cache")
            CaffeineCacheMetrics.monitor(registry, tilgangGatewayPersonCache, "tilgang_person_cache")
        }
    }

    private val client = HttpClient(CIO) {
        expectSuccess = true
        install(HttpRequestRetry)
        install(HttpTimeout) {
            socketTimeoutMillis = 5.seconds.inWholeMilliseconds
            connectTimeoutMillis = 2.seconds.inWholeMilliseconds
            requestTimeoutMillis = 5.seconds.inWholeMilliseconds
        }
        install(ContentNegotiation) {
            jackson()
        }
    }

    private val tilgangSakUrl = baseUrl.resolve("/tilgang/sak").toString()
    suspend fun harTilgangTilSak(body: SakTilgangRequest, currentToken: OidcToken): TilgangResponse {
        return get(tilgangGatewaySakCache, SakTilgangRequestMedNavIdent(body, currentToken.navIdent())) {
            post(currentToken, tilgangSakUrl, body)
        }
    }

    private val tilgangBehandlingUrl = baseUrl.resolve("/tilgang/behandling").toString()
    suspend fun harTilgangTilBehandling(body: BehandlingTilgangRequest, currentToken: OidcToken): TilgangResponse {
        return get(tilgangGatewayBehandlingCache, BehandlingTilgangRequestMedNavIdent(body, currentToken.navIdent())) {
            post(currentToken, tilgangBehandlingUrl, body)
        }
    }

    private val tilgangJournalpostUrl = baseUrl.resolve("/tilgang/journalpost").toString()
    suspend fun harTilgangTilJournalpost(body: JournalpostTilgangRequest, currentToken: OidcToken): TilgangResponse {
      return get(tilgangGatewayJournalpostCache, JournalpostTilgangRequestMedNavIdent(body, currentToken.navIdent())) {
        post(currentToken, tilgangJournalpostUrl, body)
      }
    }


    private val tilgangPersonUrl = baseUrl.resolve("/tilgang/person").toString()
    suspend fun harTilgangTilPerson(body: PersonTilgangRequest, currentToken: OidcToken): TilgangResponse {
      return get(tilgangGatewayPersonCache, PersonTilgangRequestMedNavIdent(body, currentToken.navIdent())) {
          post(currentToken, tilgangPersonUrl, body)
      }
    }

    private val tilgangTilbakekrevingUrl = baseUrl.resolve("/tilgang/tilbakekreving").toString()
    suspend fun harTilgangTilTilbakekreving(body: TilbakekrevingTilgangRequest, currentToken: OidcToken) =
        post(currentToken, tilgangTilbakekrevingUrl, body)

    /** Oppslag på samme key vil kunne kjøre parallelt. */
    suspend fun <Key : Any, Value : Any> get(
        cache: Cache<Key, Value>,
        key: Key,
        mapper: suspend (key: Key) -> Value
    ): Value {
        val cached = cache.getIfPresent(key)
        if (cached != null) {
            return cached
        }

        val value = mapper(key)
        cache.put(key, value)
        return value
    }

    private suspend inline fun <reified Request> post(
        currentToken: OidcToken,
        url: String,
        request: Request
    ): TilgangResponse {
        val newToken = client.post(texasUrl) {
            contentType(ContentType.Application.Json)
            setBody(buildMap {
                put("identity_provider", "entra_id")
                put("target", tilgangScope)
                put("user_token", currentToken.token())
            })
        }.body<Map<String, String>>()["access_token"]
        requireNotNull(newToken) {
            "mottok ikke user_token fra texas"
        }

        return client.post(url) {
            bearerAuth(newToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    /** Noen unit-tester i andre apper tester overganger hvor bruker
     * mister eller får tilganger.
     */
    fun disableCaching() {
        tilgangGatewayBehandlingCache = Caffeine.newBuilder()
            .maximumSize(0)
            .build()

        tilgangGatewaySakCache = Caffeine.newBuilder()
            .maximumSize(0)
            .build()

        tilgangGatewayJournalpostCache = Caffeine.newBuilder()
            .maximumSize(0)
            .build()

        tilgangGatewayPersonCache = Caffeine.newBuilder()
            .maximumSize(0)
            .build()
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

private data class JournalpostTilgangRequestMedNavIdent(
    val journalpostTilgangRequest: JournalpostTilgangRequest,
    val navIdent: String
)

private data class PersonTilgangRequestMedNavIdent(
    val personReq: PersonTilgangRequest,
    val navIdent: String
)
