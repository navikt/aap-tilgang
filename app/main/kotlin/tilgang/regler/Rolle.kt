package tilgang.regler

import tilgang.Role
import tilgang.Rolle
import tilgang.geo.GeoRolle

fun parseRoller(rolesWithGroupIds: List<Role>, rollerFraToken: List<String>): List<Rolle> {
    return rollerFraToken
        .filter {
            it in rolesWithGroupIds
                .map { it.objectId.toString() }
        }
        .map { rollefraToken ->
            Rolle.valueOf(rolesWithGroupIds.first { it.objectId.toString() == rollefraToken }.name.name)
        }
}

data class Roller(
    val geoRoller: List<GeoRolle>,
    val roller: List<Rolle>
)