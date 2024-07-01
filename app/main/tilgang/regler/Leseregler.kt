package tilgang.regler

import tilgang.Rolle
import tilgang.integrasjoner.pdl.Gradering
import tilgang.integrasjoner.pdl.PersonResultat

fun harLesetilgang(ident: String, roller: List<Rolle>, personer: List<PersonResultat>): Boolean {
    return !erEgenSak(ident, personer)
            && sjekkAdresseBeskyttelse(ident, roller, personer)
}

private fun sjekkAdresseBeskyttelse(ident: String, roller: List<Rolle>, personer: List<PersonResultat>): Boolean {
    val adresseBeskyttelse = personer.flatMap { it.adressebeskyttelse }

    return adresseBeskyttelse.isEmpty()
            || (Rolle.STRENGT_FORTROLIG_ADRESSE in roller && finnStrengeste(adresseBeskyttelse) === Gradering.STRENGT_FORTROLIG)
            || (Rolle.FORTROLIG_ADRESSE in roller && finnStrengeste(adresseBeskyttelse) === Gradering.FORTROLIG)
}

private fun erEgenSak(ident: String, personer: List<PersonResultat>): Boolean {
    return personer.any { it.ident === ident }
}