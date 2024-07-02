package tilgang.regler

import tilgang.Rolle
import tilgang.geo.GeoRolle
import tilgang.geo.GeoService
import tilgang.geo.GeoType
import tilgang.integrasjoner.pdl.PdlGeoType
import tilgang.integrasjoner.pdl.Gradering
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
        Operasjon.DELEGERE -> harLesetilgang(ident, roller, personer, søkersGeografiskeTilknytning) && erAvdelingsleder(
            roller.roller
        )

        Operasjon.SAKSBEHANDLE -> kanSkriveTilAvklaringsbehov(
            ident,
            requireNotNull(avklaringsbehov) { "Avklaringsbehov er påkrevd for operasjon 'SAKSBEHANDLE'" },
            roller,
            personer,
            søkersGeografiskeTilknytning
        )
    }
}

fun kanSkriveTilAvklaringsbehov(
    ident: String,
    avklaringsbehov: Avklaringsbehov,
    roller: Roller,
    personer: List<PersonResultat>,
    søkersGeografiskeTilknytning: HentGeografiskTilknytningResult
): Boolean {
    return kanAvklareBehov(avklaringsbehov, roller.roller)
            && harLesetilgang(ident, roller, personer, søkersGeografiskeTilknytning)
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

private fun sjekkAdresseBeskyttelse(roller: List<Rolle>, personer: List<PersonResultat>): Boolean {
    val adresseBeskyttelse = personer.flatMap { it.adressebeskyttelse }

    return adresseBeskyttelse.isEmpty()
            || (Rolle.STRENGT_FORTROLIG_ADRESSE in roller && finnStrengeste(adresseBeskyttelse) === Gradering.STRENGT_FORTROLIG)
            || (Rolle.FORTROLIG_ADRESSE in roller && finnStrengeste(adresseBeskyttelse) === Gradering.FORTROLIG)
}

private fun erEgenSak(ident: String, personer: List<PersonResultat>): Boolean {
    return personer.any { it.ident === ident }
}

private fun sjekkGeo(
    geoRoller: List<GeoRolle>,
    søkersGeografiskeTilknytning: HentGeografiskTilknytningResult
): Boolean {
    if (geoRoller.any { it.geoType == GeoType.NASJONAL }) {
        return true
    }
    return when (søkersGeografiskeTilknytning.gtType) {
        PdlGeoType.KOMMUNE -> søkersGeografiskeTilknytning.gtKommune in geoRoller.filter { it.geoType === GeoType.KOMMUNE }
            .map { it.kode }

        PdlGeoType.BYDEL -> søkersGeografiskeTilknytning.gtBydel in geoRoller.filter { it.geoType === GeoType.BYDEL }
            .map { it.kode }

        PdlGeoType.UTLAND -> geoRoller.any { it.geoType == GeoType.UTLAND }
        PdlGeoType.UDEFINERT -> geoRoller.any { it.geoType == GeoType.UDEFINERT }
    }
}

private fun harLeseRoller(roller: List<Rolle>): Boolean {
    return roller.any { it in listOf(Rolle.VEILEDER, Rolle.SAKSBEHANDLER, Rolle.BESLUTTER, Rolle.LES) }
}

private fun kanAvklareBehov(avklaringsbehov: Avklaringsbehov, roller: List<Rolle>): Boolean {
    val erLokalSaksbehandler = Rolle.VEILEDER in roller
    val erNaySaksbehandler = Rolle.SAKSBEHANDLER in roller
    val erBeslutter = Rolle.BESLUTTER in roller

    return when (avklaringsbehov) {
        Avklaringsbehov.MANUELT_SATT_PÅ_VENT -> erLokalSaksbehandler || erNaySaksbehandler
        Avklaringsbehov.FATTE_VEDTAK -> erBeslutter
        Avklaringsbehov.AVKLAR_STUDENT -> erNaySaksbehandler
        Avklaringsbehov.FORESLÅ_VEDTAK -> erNaySaksbehandler
        Avklaringsbehov.VURDER_SYKEPENGEERSTATNING -> erNaySaksbehandler
        Avklaringsbehov.FASTSETT_BEREGNINGSTIDSPUNKT -> erNaySaksbehandler
        Avklaringsbehov.AVKLAR_SYKDOM -> erLokalSaksbehandler
        Avklaringsbehov.AVKLAR_BISTANDSBEHOV -> erLokalSaksbehandler
        Avklaringsbehov.FASTSETT_ARBEIDSEVNE -> erLokalSaksbehandler
        Avklaringsbehov.FRITAK_MELDEPLIKT -> erLokalSaksbehandler
    }
}
