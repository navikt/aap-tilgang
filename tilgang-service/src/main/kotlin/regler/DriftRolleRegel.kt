package tilgang.kontroll.regler

import tilgang.kontroll.Rolle

data object DriftRolleRegel : Regel<List<Rolle>> {
    override fun vurder(input: List<Rolle>): Boolean {
        return Rolle.DRIFT in input
    }
}