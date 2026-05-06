package tilgang.auth

import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import tilgang.http.defaultHttpClient

interface ITokenProvider {
    suspend fun m2mToken(scope: String): String
    suspend fun oboToken(scope: String, currentToken: OidcToken): String
}

object TokenProvider : ITokenProvider {
    private val texasGateway: TexasGateway = TexasGateway(defaultHttpClient)

    override suspend fun oboToken(scope: String, currentToken: OidcToken): String {
        return texasGateway.oboToken(scope, currentToken)
    }

    override suspend fun m2mToken(scope: String): String {
        return texasGateway.m2mToken(scope)
    }
}