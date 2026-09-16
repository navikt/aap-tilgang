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
    private val texasTokenEndpoint by lazy { requiredConfigForKey("NAIS_TOKEN_ENDPOINT") }
    private val texasExchangeEndpoint by lazy { requiredConfigForKey("NAIS_TOKEN_EXCHANGE_ENDPOINT") }

    override suspend fun m2mToken(scope: String): String {
        return machineToMachineToken(scope).access_token
    }

    override suspend fun oboToken(scope: String, currentToken: OidcToken): String {
        return exchangeToken(scope, currentToken).access_token
    }

    suspend fun machineToMachineToken(scope: String): TexasTokenResponse {
        return httpClient.post(texasTokenEndpoint) {
            contentType(ContentType.Application.Json)
            setBody(mapOf("identity_provider" to "entra_id", "target" to scope))
        }.body<TexasTokenResponse>()
    }

    suspend fun exchangeToken(scope: String, currentToken: OidcToken): TexasTokenResponse {
        return httpClient.post(texasExchangeEndpoint) {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "identity_provider" to "entra_id",
                    "target" to scope,
                    "user_token" to currentToken.token()
                )
            )
        }.body<TexasTokenResponse>()
    }

}

data class TexasTokenResponse(
    val access_token: String,
    val expires_in: Long? = null,
)