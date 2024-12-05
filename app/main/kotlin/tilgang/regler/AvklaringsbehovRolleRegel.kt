package tilgang.regler

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon as PostmottakDefinisjon
import tilgang.Rolle

data object AvklaringsbehovRolleRegel : Regel<AvklaringsbehovRolleInput> {
    override fun vurder(input: AvklaringsbehovRolleInput): Boolean {
        require(input.avklaringsbehovFraBehandlingsflyt != null || input.avklaringsbehovFraPostmottak != null) { "Avklaringsbehov er påkrevd" }
        if (input.avklaringsbehovFraBehandlingsflyt != null) {
            return kanAvklareBehov(input.avklaringsbehovFraBehandlingsflyt, input.roller)
        } else {
            return kanAvklareBehov(input.avklaringsbehovFraPostmottak!!, input.roller)
        }
    }

    private fun kanAvklareBehov(avklaringsbehov: Definisjon, roller: List<Rolle>): Boolean {
        val erLokalSaksbehandler = erLokalSaksbehandler(roller)
        val erNaySaksbehandler = erNaySaksbehandler(roller)
        val erBeslutter = erBeslutter(roller)

        return when (avklaringsbehov) {
            Definisjon.MANUELT_SATT_PÅ_VENT, Definisjon.BESTILL_LEGEERKLÆRING -> erLokalSaksbehandler || erNaySaksbehandler
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
            Definisjon.FASTSETT_YRKESSKADEINNTEKT -> erNaySaksbehandler
            Definisjon.AVKLAR_YRKESSKADE -> erNaySaksbehandler
            Definisjon.FATTE_VEDTAK -> erBeslutter
            Definisjon.SKRIV_BREV -> erLokalSaksbehandler || erNaySaksbehandler // TODO: Avklar hvem som skal kunne løse brev-behovene
            Definisjon.BESTILL_BREV -> erLokalSaksbehandler || erNaySaksbehandler
        }
    }

    private fun kanAvklareBehov(avklaringsbehov: PostmottakDefinisjon, roller: List<Rolle>): Boolean {
        return when (avklaringsbehov) {
            else -> erNaySaksbehandler(roller)
        }
    }

    private fun erLokalSaksbehandler(roller: List<Rolle>): Boolean {
        return Rolle.VEILEDER in roller
    }

    private fun erNaySaksbehandler(roller: List<Rolle>): Boolean {
        return Rolle.SAKSBEHANDLER in roller
    }

    private fun erBeslutter(roller: List<Rolle>): Boolean {
        return Rolle.BESLUTTER in roller
    }
}

data class AvklaringsbehovRolleInput(
    val avklaringsbehovFraBehandlingsflyt: Definisjon? = null,
    val avklaringsbehovFraPostmottak: PostmottakDefinisjon?,
    val roller: List<Rolle>
)

data object AvklaringsbehovInputGenerator : InputGenerator<AvklaringsbehovRolleInput> {
    override suspend fun generer(input: RegelInput): AvklaringsbehovRolleInput {
        return AvklaringsbehovRolleInput(
            input.avklaringsbehovFraBehandlingsflyt,
            input.avklaringsbehovFraPostmottak,
            input.roller
        )
    }
}