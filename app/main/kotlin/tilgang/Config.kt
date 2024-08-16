package tilgang

import tilgang.auth.AzureConfig
import java.net.URI
import java.util.*

data class Config(
    val azureConfig: AzureConfig = AzureConfig(
        clientId = requiredConfigForKey("AZURE_APP_CLIENT_ID"),
        clientSecret = requiredConfigForKey("AZURE_APP_CLIENT_SECRET"),
        tokenEndpoint = URI.create(requiredConfigForKey("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT")).toURL(),
        jwks = URI.create(requiredConfigForKey("AZURE_OPENID_CONFIG_JWKS_URI")).toURL(),
        issuer = requiredConfigForKey("AZURE_OPENID_CONFIG_ISSUER")
    ),
    val roles: List<Role> = listOf(
        Role(Rolle.VEILEDER, UUID.fromString(requiredConfigForKey("AAP_VEILEDER"))),
        Role(Rolle.SAKSBEHANDLER, UUID.fromString(requiredConfigForKey("AAP_SAKSBEHANDLER"))),
        Role(Rolle.BESLUTTER, UUID.fromString(requiredConfigForKey("AAP_BESLUTTER"))),
        Role(Rolle.LES, UUID.fromString(requiredConfigForKey("AAP_LES"))),
        // Role(Rolle.AVDELINGSLEDER, UUID.fromString(requiredConfigForKey("AAP_AVDELINGSLEDER"))),
        // Role(Rolle.UTVIKLER, UUID.fromString(requiredConfigForKey("AAP_UTVIKLER"))),
        Role(Rolle.STRENGT_FORTROLIG_ADRESSE, UUID.fromString(requiredConfigForKey("STRENGT_FORTROLIG_ADRESSE"))),
        Role(
            Rolle.FORTROLIG_ADRESSE, UUID.fromString(requiredConfigForKey("FORTROLIG_ADRESSE"))
        )
        // TODO: Skjermet/Egne ansatte
    ),
    val pdlConfig: PdlConfig = PdlConfig(),
    val msGraphConfig: MsGraphConfig = MsGraphConfig(),
    val behandlingsflytConfig: BehandlingsflytConfig = BehandlingsflytConfig(),
    val skjermingConfig: SkjermingConfig = SkjermingConfig(),
    val nomConfig: NOMConfig = NOMConfig(),
    val redis: RedisConfig = RedisConfig()
)

data class PdlConfig(
    val baseUrl: String = requiredConfigForKey("PDL_BASE_URL"),
    val audience: String = requiredConfigForKey("PDL_AUDIENCE"),
    val scope: String = requiredConfigForKey("PDL_SCOPE")
)

data class MsGraphConfig(
    val baseUrl: String = requiredConfigForKey("MS_GRAPH_BASE_URL"),
    val scope: String = requiredConfigForKey("MS_GRAPH_SCOPE")
)

data class BehandlingsflytConfig(
    val baseUrl: String = requiredConfigForKey("BEHANDLINGSFLYT_BASE_URL"),
    val scope: String = requiredConfigForKey("BEHANDLINGSFLYT_SCOPE")
)

data class SkjermingConfig(
    val baseUrl: String = requiredConfigForKey("SKJERMING_BASE_URL"),
    val scope: String = requiredConfigForKey("SKJERMING_SCOPE")
)

data class NOMConfig(
    val baseUrl: String = requiredConfigForKey("NOM_BASE_URL"),
    val scope: String = requiredConfigForKey("NOM_SCOPE")
)

data class Role(
    val name: Rolle,
    val objectId: UUID,
)

data class RedisConfig(
    val uri: URI = URI(requiredConfigForKey("REDIS_URI_TILGANG")),
    val username: String = requiredConfigForKey("REDIS_USERNAME_TILGANG"),
    val password: String = requiredConfigForKey("REDIS_PASSWORD_TILGANG"),
)

enum class Rolle {
    VEILEDER,
    SAKSBEHANDLER,
    BESLUTTER,
    LES,
    AVDELINGSLEDER,
    UTVIKLER,
    STRENGT_FORTROLIG_ADRESSE,
    FORTROLIG_ADRESSE,
    KAN_BEHANDLE_SKJERMET,
}