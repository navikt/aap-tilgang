package tilgang.regler

import tilgang.Rolle
import tilgang.integrasjoner.pdl.PersonResultat

fun kanSkriveTilAvklaringsbehov(
    avklaringsbehov: Avklaringsbehov,
    roller: List<Rolle>,
    enhet: Enhet,
    personer: List<PersonResultat>?
): Boolean {
    return kanRolleOgEnhetAvklareBehov(avklaringsbehov, roller, enhet)
            && harLesetilgang(roller, personer)
}

private fun kanRolleOgEnhetAvklareBehov(avklaringsbehov: Avklaringsbehov, roller: List<Rolle>, enhet: Enhet): Boolean {
    val erLokalSaksbehandler = Rolle.SAKSBEHANDLER in roller && enhet === Enhet.LOKAL
    val erNaySaksbehandler = Rolle.SAKSBEHANDLER in roller && enhet === Enhet.NAY
    val erBeslutter = Rolle.BESLUTTER in roller

    if (avklaringsbehov === Avklaringsbehov.MANUELT_SATT_PÅ_VENT) {
        // TODO: Hvordan løse denne?
        return true
    }

    return when (avklaringsbehov) {
        Avklaringsbehov.FATTE_VEDTAK -> erBeslutter
        Avklaringsbehov.AVKLAR_STUDENT -> erNaySaksbehandler
        Avklaringsbehov.FORESLÅ_VEDTAK -> erNaySaksbehandler
        Avklaringsbehov.VURDER_SYKEPENGEERSTATNING -> erNaySaksbehandler
        Avklaringsbehov.FASTSETT_BEREGNINGSTIDSPUNKT -> erNaySaksbehandler
        Avklaringsbehov.AVKLAR_SYKDOM -> erLokalSaksbehandler
        Avklaringsbehov.AVKLAR_BISTANDSBEHOV -> erLokalSaksbehandler
        Avklaringsbehov.FASTSETT_ARBEIDSEVNE -> erLokalSaksbehandler
        Avklaringsbehov.FRITAK_MELDEPLIKT -> erLokalSaksbehandler
        else -> false
    }
}