package tilgang.regler

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon as PostmottakDefinisjon
import no.nav.aap.tilgang.Rolle

data object AvklaringsbehovRolleRegel : Regel<AvklaringsbehovRolleInput> {
    override fun vurder(input: AvklaringsbehovRolleInput): Boolean {
        require(input.avklaringsbehovFraBehandlingsflyt != null || input.avklaringsbehovFraPostmottak != null || input.påkrevdRolle != null) { "Avklaringsbehov eller påkrevd rolle må være satt" }

        val harRolleForAvklaringsbehov = when {
            input.avklaringsbehovFraBehandlingsflyt != null -> kanAvklareBehov(input.avklaringsbehovFraBehandlingsflyt, input.roller)
            input.avklaringsbehovFraPostmottak != null -> kanAvklareBehov(input.avklaringsbehovFraPostmottak, input.roller)
            else -> true
        }

        val harPåkrevdRolle = if (input.påkrevdRolle != null) {
            input.roller.contains(input.påkrevdRolle)
        } else {
            true
        }

        return harRolleForAvklaringsbehov && harPåkrevdRolle
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
    val påkrevdRolle: Rolle?,
    val roller: List<Rolle>
)

data object AvklaringsbehovInputGenerator : InputGenerator<AvklaringsbehovRolleInput> {
    override fun generer(input: RegelInput): AvklaringsbehovRolleInput {
        return AvklaringsbehovRolleInput(
            input.avklaringsbehovFraBehandlingsflyt,
            input.avklaringsbehovFraPostmottak,
            input.påkrevdRolle,
            input.roller
        )
    }
}