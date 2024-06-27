package tilgang.regler

import tilgang.integrasjoner.pdl.Gradering
import tilgang.integrasjoner.pdl.PersonResultat

fun harTilgangTilPersoner(roller: List<Rolle>, personer: List<PersonResultat>?): Boolean {
    if (personer.isNullOrEmpty()) {
        return true
    }

    val adresseBeskyttelse = personer.flatMap { it.adressebeskyttelse }

    return adresseBeskyttelse.isEmpty()
            || (Rolle.KODE_6 in roller)
            || (Rolle.KODE_7 in roller && finnStrengeste(adresseBeskyttelse) === Gradering.FORTROLIG)
}