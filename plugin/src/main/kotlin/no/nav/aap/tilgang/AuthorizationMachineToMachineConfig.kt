package no.nav.aap.tilgang

import java.util.*

/** Maskin-til-maskin autorisering uten on-behalf-of (OBO) tokens.
 *
 * Siden kallet ikke skjer på vegne av en innlogget person, så er det heller
 * tilgangsstyring utover at det er riktig app som kaller oss.
 *
 * Spesifiser hvilke apper som har tilgang, enten med å oppgi `azp` eller
 * roller definert i inbound-access-policy.
 *
 * Du kan finne azp (client id) ved å kjøre:
 * ```
 * kubectl get azureadapplication -n aap
 * ```
 */
data class AuthorizationMachineToMachineConfig(
    /** `CLIENT_ID` til applikasjonen som får lov til å kalle oss. */
    val authorizedAzps: List<UUID> = listOf(),

    @Deprecated("Bruk av roles på inbound access policy er deprecated av nais-teamet. Bruk azp i stedet.")
    val authorizedRoles: List<String> = listOf(),
) : AuthorizationRouteConfig {
    init {
        if (authorizedRoles.isEmpty() == authorizedAzps.isEmpty()) {
            log.warn("{} må sette enten authorizedRoles eller authourizedAzps", javaClass.name, Exception())
        }
    }
}