package tilgang

import tilgang.auth.AzureConfig
import java.net.URI
import java.util.*

private fun getEnvVar(envar: String) = System.getenv(envar) ?: error("missing envvar $envar")

data class Config(
    val azureConfig: AzureConfig = AzureConfig(
        clientId = getEnvVar("AZURE_APP_CLIENT_ID"),
        clientSecret = getEnvVar("AZURE_APP_CLIENT_SECRET"),
        tokenEndpoint = URI.create(getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT")).toURL(),
        jwks = URI.create(getEnvVar("AZURE_OPENID_CONFIG_JWKS_URI")).toURL(),
        issuer = getEnvVar("AZURE_OPENID_CONFIG_ISSUER")
    ),
    val roles: List<Role> = listOf(
        Role(Rolle.VEILEDER, UUID.fromString(getEnvVar("AAP_VEILEDER"))),
        Role(Rolle.SAKSBEHANDLER, UUID.fromString(getEnvVar("AAP_SAKSBEHANDLER"))),
        Role(Rolle.VEILEDER, UUID.fromString(getEnvVar("AAP_VEILEDER"))),
        Role(Rolle.BESLUTTER, UUID.fromString(getEnvVar("AAP_BESLUTTER"))),
        Role(Rolle.LES, UUID.fromString(getEnvVar("AAP_LES"))),
        // Role(Rolle.AVDELINGSLEDER, UUID.fromString(getEnvVar("AAP_AVDELINGSLEDER"))),
        // Role(Rolle.UTVIKLER, UUID.fromString(getEnvVar("AAP_UTVIKLER"))),
        Role(Rolle.STRENGT_FORTROLIG_ADRESSE, UUID.fromString(getEnvVar("STRENGT_FORTROLIG_ADRESSE"))),
        Role(
            Rolle.FORTROLIG_ADRESSE, UUID.fromString(getEnvVar("FORTROLIG_ADRESSE"))
        )
    ),
    val pdlConfig: PdlConfig = PdlConfig(),
    val msGraphConfig: MsGraphConfig = MsGraphConfig(),
    val behandlingsflytConfig: BehandlingsflytConfig = BehandlingsflytConfig(),
    val skjermingConfig: SkjermingConfig = SkjermingConfig()
)

data class PdlConfig(
    val baseUrl: String = getEnvVar("PDL_BASE_URL"),
    val audience: String = getEnvVar("PDL_AUDIENCE"),
    val scope: String = getEnvVar("PDL_SCOPE")
)

data class MsGraphConfig(
    val baseUrl: String = getEnvVar("MS_GRAPH_BASE_URL"),
    val scope: String = getEnvVar("MS_GRAPH_SCOPE")
)

data class BehandlingsflytConfig(
    val baseUrl: String = getEnvVar("BEHANDLINGSFLYT_BASE_URL"),
    val scope: String = getEnvVar("BEHANDLINGSFLYT_SCOPE")
)

data class SkjermingConfig(
    val baseUrl: String = getEnvVar("SKJERMING_BASE_URL"),
    val scope: String = getEnvVar("SKJERMING_SCOPE")
)

data class Role(
    val name: Rolle,
    val objectId: UUID,
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
    SKJERMET,
}