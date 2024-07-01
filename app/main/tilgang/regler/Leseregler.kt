package tilgang.regler

import tilgang.Rolle
import tilgang.geo.GeoService
import tilgang.integrasjoner.pdl.Gradering
import tilgang.integrasjoner.pdl.PersonResultat

fun harLesetilgang(ident: String, geoRoller: List<String>, roller: List<Rolle>, personer: List<PersonResultat>): Boolean {
    return !erEgenSak(ident, personer)
            && sjekkGeo(geoRoller, personer)
            && sjekkAdresseBeskyttelse(ident, roller, personer)
}

private fun sjekkGeo(geoRoller: List<String>, personer: List<PersonResultat>): Boolean {
    if (GeoService.NASJONAL in geoRoller) {
        return true
    }
    // TODO: Gjør sammenligning etter å ha hentet geografisk tilknytning fra pdl
    return true
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