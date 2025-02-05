package tilgang.regler

import no.nav.aap.tilgang.Rolle

data object DriftRolleRegel : Regel<List<Rolle>> {
    override fun vurder(input: List<Rolle>): Boolean {
        return Rolle.DRIFT in input
    }
}