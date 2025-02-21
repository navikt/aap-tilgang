package tilgang.regler

import no.nav.aap.tilgang.Rolle

data object LeseRolleRegel : Regel<List<Rolle>> {
    override fun vurder(input: List<Rolle>): Boolean {
        return input.any {
            it in listOf(
                Rolle.SAKSBEHANDLER_OPPFOLGING,
                Rolle.SAKSBEHANDLER_NASJONAL, Rolle.BESLUTTER, Rolle.LES, Rolle.KVALITETSSIKRER
            )
        }
    }
}

data object RolleInputGenerator : InputGenerator<List<Rolle>> {
    override fun generer(input: RegelInput): List<Rolle> {
        return input.roller
    }
}