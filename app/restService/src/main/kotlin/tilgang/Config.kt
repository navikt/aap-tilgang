package tilgang

import java.net.URI
import java.util.UUID
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.tilgang.Rolle

private fun getEnvVar(envar: String) = System.getenv(envar) ?: error("missing envvar $envar")

data class Config(
    val roles: List<Role> = Rolle.entries.map { rolle ->
        Role(rolle, UUID.fromString(requiredConfigForKey("AAP_${rolle.name}")))
    },
    val redis: RedisConfig = RedisConfig(),
    val behandlingsflyt: BehandlingsflytConfig = BehandlingsflytConfig(),
    val saf: SafConfig = SafConfig(),
    val pdl: PdlConfig = PdlConfig(),
    val msGraph: MsGraphConfig = MsGraphConfig(),
    val tilgangsmaskin: TilgangsmaskinConfig = TilgangsmaskinConfig(),
    val texas: TexasConfig = TexasConfig()
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

data class BehandlingsflytConfig(
    val baseUrl: URI = URI(requiredConfigForKey("BEHANDLINGSFLYT_BASE_URL")),
    val scope: String = requiredConfigForKey("BEHANDLINGSFLYT_SCOPE"),
)

data class SafConfig(
    val baseUrl: URI = URI(requiredConfigForKey("SAF_BASE_URL")),
    val scope: String = requiredConfigForKey("SAF_SCOPE"),
)

data class PdlConfig(
    val baseUrl: URI = URI(requiredConfigForKey("PDL_BASE_URL")),
    val scope: String = requiredConfigForKey("PDL_SCOPE"),
)

data class MsGraphConfig(
    val baseUrl: URI = URI(requiredConfigForKey("MS_GRAPH_BASE_URL")),
    val scope: String = requiredConfigForKey("MS_GRAPH_SCOPE"),
)

data class TilgangsmaskinConfig(
    val baseUrl: URI = URI(requiredConfigForKey("INTEGRASJON_TILGANGSMASKIN_URL")),
    val scope: String = requiredConfigForKey("INTEGRASJON_TILGANGSMASKIN_SCOPE"),
    val consumerId: String = System.getenv("NAIS_APP_NAME") ?: "tilgang",
)

data class TexasConfig(
    val tokenEndpoint: URI = URI(requiredConfigForKey("NAIS_TOKEN_ENDPOINT")),
    val exchangeEndpoint: URI = URI(requiredConfigForKey("NAIS_TOKEN_EXCHANGE_ENDPOINT")),
)
