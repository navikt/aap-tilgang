package tilgang.integrasjoner.pdl

import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import tilgang.PdlConfig
import tilgang.auth.ITokenProvider
import tilgang.http.createHttpClient
import kotlin.time.Duration.Companion.seconds

fun createPdlClient(
    config: PdlConfig,
    tokenProvider: ITokenProvider,
): HttpClient = createHttpClient(10.seconds) {
    defaultRequest {
        url(config.baseUrl.toString())
    }
    install(createClientPlugin("PdlAuth") {
        onRequest { request, _ ->
            request.bearerAuth(tokenProvider.m2mToken(config.scope))
        }
    })
}
