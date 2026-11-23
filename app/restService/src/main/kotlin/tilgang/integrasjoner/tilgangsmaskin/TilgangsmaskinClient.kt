package tilgang.integrasjoner.tilgangsmaskin

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.util.AttributeKey
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import tilgang.TilgangsmaskinConfig
import tilgang.auth.ITokenProvider
import tilgang.http.createHttpClient
import kotlin.time.Duration.Companion.seconds

internal val tilgangsmaskinCurrentTokenKey = AttributeKey<OidcToken>("TilgangsmaskinCurrentToken")

fun createTilgangsmaskinClient(
    config: TilgangsmaskinConfig,
    tokenProvider: ITokenProvider,
    robust: Boolean = false,
): HttpClient {
    val configure: HttpClientConfig<*>.() -> Unit = {
        configureTilgangsmaskin(config, tokenProvider)
    }
    return if (robust) {
        TilgangsmaskinHttpClient.create(configureClient = configure)
    } else {
        createHttpClient(10.seconds, configure)
    }
}

private fun HttpClientConfig<*>.configureTilgangsmaskin(
    config: TilgangsmaskinConfig,
    tokenProvider: ITokenProvider,
) {
    defaultRequest {
        url("${config.baseUrl.toString().trimEnd('/')}/")
        header("Nav-Consumer-Id", config.consumerId)
    }
    install(createClientPlugin("TilgangsmaskinAuth") {
        onRequest { request, _ ->
            val currentToken = request.attributes[tilgangsmaskinCurrentTokenKey]
            request.bearerAuth(tokenProvider.oboToken(config.scope, currentToken))
        }
    })
}
