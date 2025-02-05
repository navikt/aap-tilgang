package tilgang.regler

import tilgang.Role
import tilgang.Rolle

fun parseRoller(rolesWithGroupIds: List<Role>, rollerFraToken: List<String>): List<Rolle> {
    val map = rolesWithGroupIds
        .map { role -> role.objectId.toString() }
    return rollerFraToken
        .filter {
            it in map
        }
        .map { rollefraToken ->
            Rolle.valueOf(rolesWithGroupIds.first { it.objectId.toString() == rollefraToken }.name.name)
        }
}
