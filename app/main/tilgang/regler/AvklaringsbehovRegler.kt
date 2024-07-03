package tilgang.regler

import tilgang.Rolle

fun kanAvklareBehov(avklaringsbehov: Avklaringsbehov, roller: List<Rolle>): Boolean {
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