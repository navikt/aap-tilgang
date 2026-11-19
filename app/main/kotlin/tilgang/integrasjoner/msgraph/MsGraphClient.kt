package tilgang.integrasjoner.msgraph

import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.util.AttributeKey
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import tilgang.MsGraphConfig
import tilgang.auth.ITokenProvider
import tilgang.http.createHttpClient
import kotlin.time.Duration.Companion.seconds

internal val msGraphCurrentTokenKey = AttributeKey<OidcToken>("MsGraphCurrentToken")

fun createMsGraphClient(
    config: MsGraphConfig,
    tokenProvider: ITokenProvider,
): HttpClient = createHttpClient(10.seconds) {
    defaultRequest {
        url("${config.baseUrl.toString().trimEnd('/')}/")
    }
    install(createClientPlugin("MsGraphAuth") {
        onRequest { request, _ ->
            val currentToken = request.attributes[msGraphCurrentTokenKey]
            request.bearerAuth(tokenProvider.oboToken(config.scope, currentToken))
        }
    })
}
