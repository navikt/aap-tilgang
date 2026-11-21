package tilgang.integrasjoner.behandlingsflyt

import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import tilgang.BehandlingsflytConfig
import tilgang.auth.ITokenProvider
import tilgang.http.createHttpClient
import kotlin.time.Duration.Companion.seconds

fun createBehandlingsflytClient(
    config: BehandlingsflytConfig,
    tokenProvider: ITokenProvider,
): HttpClient = createHttpClient(4.seconds) {
    defaultRequest {
        url("${config.baseUrl.toString().trimEnd('/')}/")
    }
    install(createClientPlugin("BehandlingsflytAuth") {
        onRequest { request, _ ->
            request.bearerAuth(tokenProvider.m2mToken(config.scope))
        }
    })
}
