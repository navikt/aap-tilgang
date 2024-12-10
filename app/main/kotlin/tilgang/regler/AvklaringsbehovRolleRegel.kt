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
        return avklaringsbehov.løsesAv.any { it in roller }
    }

    private fun kanAvklareBehov(avklaringsbehov: PostmottakDefinisjon, roller: List<Rolle>): Boolean {
        return avklaringsbehov.løsesAv.any { it in roller }
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