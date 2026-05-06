package tilgang.regler

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.tilgang.Rolle
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon as PostmottakDefinisjon

/**
 * Sjekker alltid alle regler - kan derfor hende at man ikke har sendt med hverken påkrevd rolle eller avklaringsbehov.
 * Skal returnere false i disse tilfellene
 */
data object AvklaringsbehovRolleRegel : Regel<AvklaringsbehovRolleInput> {
    override fun vurder(input: AvklaringsbehovRolleInput): Boolean {
        val sjekker = mutableListOf<Boolean>()

        if (input.avklaringsbehovFraBehandlingsflyt != null) {
            sjekker.add(kanAvklareBehov(input.avklaringsbehovFraBehandlingsflyt, input.roller))
        }
        if (input.avklaringsbehovFraPostmottak != null) {
            sjekker.add(kanAvklareBehov(input.avklaringsbehovFraPostmottak, input.roller))
        }
        if (input.påkrevdRolle.isNotEmpty()) {
            sjekker.add(input.påkrevdRolle.any { it in input.roller })
        }

        if (sjekker.isEmpty()) {
            return false
        }
        return sjekker.all { it }
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
    val påkrevdRolle: List<Rolle> = emptyList(),
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