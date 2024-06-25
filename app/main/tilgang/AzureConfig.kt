package tilgang

import java.net.URL

data class AzureConfig(
    val tokenEndpoint: URL,
    val clientId: String,
    val clientSecret: String,
    val jwks: URL,
    val issuer: String,
)
