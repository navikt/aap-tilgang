package tilgang

import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import java.net.URI
import java.util.*

private fun getEnvVar(envar: String) = System.getenv(envar) ?: error("missing envvar $envar")

data class Config(
    val azureConfig: AzureConfig = AzureConfig(),
    val roles: List<Role> = listOf(
        Role(Rolle.VEILEDER, UUID.fromString(getEnvVar("AAP_VEILEDER"))),
        Role(Rolle.SAKSBEHANDLER, UUID.fromString(getEnvVar("AAP_SAKSBEHANDLER"))),
        Role(Rolle.BESLUTTER, UUID.fromString(getEnvVar("AAP_BESLUTTER"))),
        Role(Rolle.LES, UUID.fromString(getEnvVar("AAP_LES"))),
        // Role(Rolle.AVDELINGSLEDER, UUID.fromString(getEnvVar("AAP_AVDELINGSLEDER"))), //TODO: Les inn disse
        // Role(Rolle.UTVIKLER, UUID.fromString(getEnvVar("AAP_UTVIKLER"))),
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
