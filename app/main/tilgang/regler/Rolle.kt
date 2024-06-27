package tilgang.regler

import tilgang.integrasjoner.pdl.Gradering

enum class Rolle {
    VEILEDER,
    SAKSBEHANDLER,
    BESLUTTER,
    AVDELINGSLEDER,
    UTVIKLER,
    KODE_6,
    KODE_7
}


fun finnStrengeste(adresseBeskyttelser: List<Gradering>): Gradering {
    return when {
        Gradering.STRENGT_FORTROLIG in adresseBeskyttelser -> Gradering.STRENGT_FORTROLIG
        Gradering.STRENGT_FORTROLIG_UTLAND in adresseBeskyttelser -> Gradering.STRENGT_FORTROLIG_UTLAND
        else -> Gradering.FORTROLIG
    }
}

fun parseRoller(roller: String): List<Rolle> {
    return roller.split(",").filter { parseRolle(it) != null }.map { Rolle.valueOf(it) }
}

fun parseRolle(rolle: String): Rolle? {
    return try {
        Rolle.valueOf(rolle)
    } catch (e: Exception) {
        null
    }
}