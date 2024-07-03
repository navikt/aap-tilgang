package tilgang.regler

import tilgang.Rolle
import tilgang.integrasjoner.pdl.HentGeografiskTilknytningResult
import tilgang.integrasjoner.pdl.PersonResultat
import tilgang.routes.Operasjon

fun vurderTilgang(
    ident: String,
    roller: Roller,
    søkerIdent: String,
    søkersGeografiskeTilknytning: HentGeografiskTilknytningResult,
    personer: List<PersonResultat>,
    behandlingsreferanse: String,
    avklaringsbehov: Avklaringsbehov?,
    operasjon: Operasjon
): Boolean {
    return when (operasjon) {
        Operasjon.SE -> harLesetilgang(ident, roller, personer, søkersGeografiskeTilknytning)
        Operasjon.DRIFTE -> harDriftTilgang(roller.roller)
        Operasjon.DELEGERE -> harLesetilgang(ident, roller, personer, søkersGeografiskeTilknytning)
                && erAvdelingsleder(roller.roller)

        Operasjon.SAKSBEHANDLE -> harLesetilgang(ident, roller, personer, søkersGeografiskeTilknytning)
                && kanAvklareBehov(
            requireNotNull(avklaringsbehov) { "Avklaringsbehov er påkrevd for operasjon 'SAKSBEHANDLE'" }, roller.roller
        )
    }
}

fun harLesetilgang(
    ident: String,
    roller: Roller,
    personer: List<PersonResultat>,
    søkersGeografiskeTilknytning: HentGeografiskTilknytningResult
): Boolean {
    return !erEgenSak(ident, personer)
            && harLeseRoller(roller.roller)
            && sjekkGeo(roller.geoRoller, søkersGeografiskeTilknytning)
            && sjekkAdresseBeskyttelse(roller.roller, personer)
}

fun harDriftTilgang(roller: List<Rolle>): Boolean {
    // TODO: Flere begrensninger?
    return Rolle.UTVIKLER in roller
}

fun erAvdelingsleder(roller: List<Rolle>): Boolean {
    return Rolle.AVDELINGSLEDER in roller
}

private fun erEgenSak(ident: String, personer: List<PersonResultat>): Boolean {
    return personer.any { it.ident === ident }
}

private fun harLeseRoller(roller: List<Rolle>): Boolean {
    return roller.any { it in listOf(Rolle.VEILEDER, Rolle.SAKSBEHANDLER, Rolle.BESLUTTER, Rolle.LES) }
}
