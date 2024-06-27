package tilgang.regler

import tilgang.Role
import tilgang.Rolle
import tilgang.integrasjoner.pdl.Gradering

fun finnStrengeste(adresseBeskyttelser: List<Gradering>): Gradering {
    return when {
        Gradering.STRENGT_FORTROLIG in adresseBeskyttelser -> Gradering.STRENGT_FORTROLIG
        Gradering.STRENGT_FORTROLIG_UTLAND in adresseBeskyttelser -> Gradering.STRENGT_FORTROLIG_UTLAND
        else -> Gradering.FORTROLIG
    }
}

fun parseRoller(rolesWithGroupIds: List<Role>, rollerFraToken: String): List<Rolle> {
    return rollerFraToken
        .split(",")
        .filter {
            it in rolesWithGroupIds
                .map { it.objectId.toString() }
        }
        .map { rollefraToken ->
            Rolle.valueOf(rolesWithGroupIds.first { it.objectId.toString() == rollefraToken }.name.name)
        }
}
