package tilgang.regler

import tilgang.Rolle
import tilgang.integrasjoner.pdl.Gradering
import tilgang.integrasjoner.pdl.PersonResultat

fun harLesetilgang(roller: List<Rolle>, personer: List<PersonResultat>?): Boolean {
    return harTilgangTilPersoner(roller, personer)
}

fun harTilgangTilPersoner(roller: List<Rolle>, personer: List<PersonResultat>?): Boolean {
    if (personer.isNullOrEmpty()) {
        return true
    }

    val adresseBeskyttelse = personer.flatMap { it.adressebeskyttelse }

    return adresseBeskyttelse.isEmpty()
            || (Rolle.STRENGT_FORTROLIG_ADRESSE in roller && finnStrengeste(adresseBeskyttelse) === Gradering.STRENGT_FORTROLIG)
            || (Rolle.FORTROLIG_ADRESSE in roller && finnStrengeste(adresseBeskyttelse) === Gradering.FORTROLIG)
}