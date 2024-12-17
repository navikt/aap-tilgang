package tilgang.regler

import tilgang.Rolle

data object LeseRolleRegel : Regel<List<Rolle>> {
    override fun vurder(input: List<Rolle>): Boolean {
        return input.any { it in listOf(Rolle.VEILEDER, Rolle.SAKSBEHANDLER, Rolle.BESLUTTER, Rolle.LES) }
    }
}

data object RolleInputGenerator: InputGenerator<List<Rolle>> {
    override fun generer(input: RegelInput): List<Rolle> {
        return input.roller
    }
}