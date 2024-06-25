package tilgang

import java.net.URI

private fun getEnvVar(envar: String) = System.getenv(envar) ?: error("missing envvar $envar")

data class Config(
    val azureConfig: AzureConfig = AzureConfig(
        clientId = getEnvVar("AZURE_APP_CLIENT_ID"),
        clientSecret = getEnvVar("AZURE_APP_CLIENT_SECRET"),
        tokenEndpoint = URI.create(getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT")).toURL(),
        jwks =  URI.create(getEnvVar("AZURE_OPENID_CONFIG_JWKS_URI")).toURL(),
        issuer = getEnvVar("AZURE_OPENID_CONFIG_ISSUER")
    ),
    val pdlConfig: PdlConfig = PdlConfig(),
)

data class PdlConfig(
    val baseUrl: String = getEnvVar("PDL_BASE_URL"),
    val audience: String = getEnvVar("PDL_AUDIENCE"),
    val scope: String = getEnvVar("PDL_SCOPE")
)

