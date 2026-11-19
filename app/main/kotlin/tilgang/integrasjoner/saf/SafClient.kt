package tilgang.integrasjoner.saf

import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import tilgang.SafConfig
import tilgang.auth.ITokenProvider
import tilgang.auth.TokenProvider
import tilgang.http.createHttpClient
import kotlin.time.Duration.Companion.seconds

fun createSafClient(
    config: SafConfig,
    tokenProvider: ITokenProvider = TokenProvider,
): HttpClient = createHttpClient(10.seconds) {
    defaultRequest {
        url(config.baseUrl.toString())
    }
    install(createClientPlugin("SafAuth") {
        onRequest { request, _ ->
            request.bearerAuth(tokenProvider.m2mToken(config.scope))
        }
    })
}
