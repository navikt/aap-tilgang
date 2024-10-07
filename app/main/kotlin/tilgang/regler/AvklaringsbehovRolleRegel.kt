package tilgang.regler

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import tilgang.Rolle

data object AvklaringsbehovRolleRegel: Regel<AvklaringsbehovRolleInput> {
    override fun vurder(input: AvklaringsbehovRolleInput): Boolean {
        return kanAvklareBehov(input.avklaringsbehov, input.roller)
    }

    private fun kanAvklareBehov(avklaringsbehov: Definisjon, roller: List<Rolle>): Boolean {
        val erLokalSaksbehandler = Rolle.VEILEDER in roller
        val erNaySaksbehandler = Rolle.SAKSBEHANDLER in roller
        val erBeslutter = Rolle.BESLUTTER in roller
        
        return when (avklaringsbehov) {
            Definisjon.MANUELT_SATT_PÅ_VENT -> erLokalSaksbehandler || erNaySaksbehandler
            Definisjon.AVKLAR_STUDENT -> erNaySaksbehandler
            Definisjon.AVKLAR_SYKDOM -> erLokalSaksbehandler
            Definisjon.AVKLAR_BISTANDSBEHOV -> erLokalSaksbehandler
            Definisjon.FRITAK_MELDEPLIKT -> erLokalSaksbehandler
            Definisjon.FASTSETT_ARBEIDSEVNE -> erLokalSaksbehandler
            Definisjon.AVKLAR_SYKEPENGEERSTATNING -> erNaySaksbehandler
            Definisjon.FASTSETT_BEREGNINGSTIDSPUNKT -> erNaySaksbehandler
            Definisjon.AVKLAR_BARNETILLEGG -> erNaySaksbehandler
            Definisjon.AVKLAR_SONINGSFORRHOLD -> erNaySaksbehandler
            Definisjon.AVKLAR_HELSEINSTITUSJON -> erNaySaksbehandler
            Definisjon.AVKLAR_SAMORDNING_GRADERING -> erNaySaksbehandler
            Definisjon.FORESLÅ_VEDTAK -> erNaySaksbehandler
            Definisjon.KVALITETSSIKRING -> erNaySaksbehandler
            Definisjon.FATTE_VEDTAK -> erBeslutter
        }
    }
}

data class AvklaringsbehovRolleInput(val avklaringsbehov: Definisjon, val roller: List<Rolle>)

data object AvklaringsbehovInputGenerator: InputGenerator<AvklaringsbehovRolleInput> {
    override suspend fun generer(input: RegelInput): AvklaringsbehovRolleInput {
        val avklaringsbehov = requireNotNull(input.avklaringsbehov){ "Avklaringsbehov er påkrevd for operasjon 'SAKSBEHANDLE'" }
        return AvklaringsbehovRolleInput(avklaringsbehov, input.roller)
    }
}