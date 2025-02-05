package tilgang.regler

import no.nav.aap.tilgang.Rolle

data object AvdelingslederRolleRegel: Regel<List<Rolle>> {
    override fun vurder(input: List<Rolle>): Boolean {
        return Rolle.PRODUKSJONSSTYRING in input
    }
}