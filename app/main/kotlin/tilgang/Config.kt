package tilgang

import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import java.net.URI
import java.util.*

private fun getEnvVar(envar: String) = System.getenv(envar) ?: error("missing envvar $envar")

data class Config(
    val azureConfig: AzureConfig = AzureConfig(),
    val roles: List<Role> = listOf(
        Role(Rolle.SAKSBEHANDLER_OPPFOLGING, UUID.fromString(getEnvVar("AAP_SAKSBEHANDLER_OPPFOLGING"))),
        Role(Rolle.SAKSBEHANDLER_NASJONAL, UUID.fromString(getEnvVar("AAP_SAKSBEHANDLER_NASJONAL"))),
        Role(Rolle.BESLUTTER, UUID.fromString(getEnvVar("AAP_BESLUTTER"))),
        Role(Rolle.LES, UUID.fromString(getEnvVar("AAP_LES"))),
        Role(Rolle.DRIFT, UUID.fromString(getEnvVar("AAP_DRIFT"))),
        Role(Rolle.PRODUKSJONSSTYRING, UUID.fromString(getEnvVar("AAP_PRODUKSJONSSTYRING"))),
        Role(Rolle.KVALITETSSIKRER, UUID.fromString(getEnvVar("AAP_KVALITETSSIKRER"))),
    ),
    val redis: RedisConfig = RedisConfig()
)

data class Role(
    val name: Rolle,
    val objectId: UUID,
)

data class RedisConfig(
    val uri: URI = URI(getEnvVar("REDIS_URI_TILGANG")),
    val username: String = getEnvVar("REDIS_USERNAME_TILGANG"),
    val password: String = getEnvVar("REDIS_PASSWORD_TILGANG"),
)
