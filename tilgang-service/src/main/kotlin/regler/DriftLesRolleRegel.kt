package tilgang.kontroll.regler

import tilgang.kontroll.Rolle

data object DriftLesRolleRegel : Regel<List<Rolle>> {
    override fun vurder(input: List<Rolle>): Boolean {
        return Rolle.DRIFT_LES in input || Rolle.DRIFT in input
    }
}