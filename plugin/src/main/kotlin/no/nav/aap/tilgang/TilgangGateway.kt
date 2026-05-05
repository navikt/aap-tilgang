package no.nav.aap.tilgang

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.net.URI
import java.time.Duration
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

    fun harTilgangTilSak(body: SakTilgangRequest, currentToken: OidcToken): TilgangResponse {
        return tilgangGatewaySakCache.get(SakTilgangRequestMedNavIdent(body, currentToken.navIdent())) {
            val httpRequest = PostRequest(
                body = body,
                currentToken = currentToken
            )
            val respons = requireNotNull(
                client.retryablePost<_, TilgangResponse>(
                    uri = baseUrl.resolve("/tilgang/sak"),
                    request = httpRequest
                )
            )
            respons
        }
    }

    fun harTilgangTilBehandling(body: BehandlingTilgangRequest, currentToken: OidcToken): TilgangResponse {
        return tilgangGatewayBehandlingCache.get(BehandlingTilgangRequestMedNavIdent(body, currentToken.navIdent())) {
            val httpRequest = PostRequest(
                body = body,
                currentToken = currentToken
            )
            val respons = requireNotNull(
                client.retryablePost<_, TilgangResponse>(
                    uri = baseUrl.resolve("/tilgang/behandling"),
                    request = httpRequest
                )
            )
            respons
        }
    }

    fun harTilgangTilJournalpost(
        body: JournalpostTilgangRequest,
        currentToken: OidcToken
    ): TilgangResponse {
        val httpRequest = PostRequest(
            body = body,
            currentToken = currentToken
        )
        val respons = requireNotNull(
            client.retryablePost<_, TilgangResponse>(
                uri = baseUrl.resolve("/tilgang/journalpost"),
                request = httpRequest
            )
        )
        return respons
    }

    fun harTilgangTilPerson(body: PersonTilgangRequest, currentToken: OidcToken): TilgangResponse {
        val httpRequest = PostRequest(
            body = body,
            currentToken = currentToken
        )
        val respons = requireNotNull(
            client.retryablePost<_, TilgangResponse>(
                uri = baseUrl.resolve("/tilgang/person"),
                request = httpRequest
            )
        )
        return respons
    }

    fun harTilgangTilTilbakekreving(body: TilbakekrevingTilgangRequest, currentToken: OidcToken): TilgangResponse {
        val httpRequest = PostRequest(
            body = body,
            currentToken = currentToken
        )
        val respons = requireNotNull(
            client.retryablePost<_, TilgangResponse>(
                uri = baseUrl.resolve("/tilgang/tilbakekreving"),
                request = httpRequest
            )
        )
        return respons
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
