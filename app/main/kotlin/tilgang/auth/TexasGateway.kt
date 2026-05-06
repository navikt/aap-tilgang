package tilgang.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken

class TexasGateway(private val httpClient: HttpClient) : ITokenProvider {
    private val texasTokenEndpoint by lazy { requiredConfigForKey("nais.token.endpoint") }
    private val texasExchangeEndpoint by lazy { requiredConfigForKey("nais.token.exchange.endpoint") }

    override suspend fun m2mToken(scope: String): String {
        return httpClient.post(texasTokenEndpoint) {
            contentType(ContentType.Application.Json)
            setBody(mapOf("identity_provider" to "entra_id", "target" to scope))
        }.body<Map<String, String>>()["access_token"]!!
    }

    override suspend fun oboToken(scope: String, currentToken: OidcToken): String {
        return httpClient.post(texasExchangeEndpoint) {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "identity_provider" to "entra_id",
                    "target" to scope,
                    "user_token" to currentToken.token()
                )
            )
        }.body<Map<String, String>>()["access_token"]!!
    }

}