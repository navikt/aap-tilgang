package tilgang.regler

import tilgang.Avklaringsbehov
import tilgang.Rolle

data object AvklaringsbehovRolleRegel: Regel<AvklaringsbehovRolleInput> {
    override fun vurder(input: AvklaringsbehovRolleInput): Boolean {
        return kanAvklareBehov(input.avklaringsbehov, input.roller)
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
}

data class AvklaringsbehovRolleInput(val avklaringsbehov: Avklaringsbehov, val roller: List<Rolle>)

data object AvklaringsbehovInputGenerator: InputGenerator<AvklaringsbehovRolleInput> {
    override suspend fun generer(input: RegelInput): AvklaringsbehovRolleInput {
        val avklaringsbehov = requireNotNull(input.avklaringsbehov){ "Avklaringsbehov er påkrevd for operasjon 'SAKSBEHANDLE'" }
        return AvklaringsbehovRolleInput(avklaringsbehov, input.roller)
    }
}