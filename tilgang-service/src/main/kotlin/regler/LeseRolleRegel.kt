package tilgang.kontroll.regler

import tilgang.kontroll.Tilgangsgrunnlag

import tilgang.kontroll.Rolle

data object LeseRolleRegel : Regel<List<Rolle>> {
    private val tillatteRoller = setOf(
        Rolle.SAKSBEHANDLER_OPPFOLGING,
        Rolle.SAKSBEHANDLER_NASJONAL,
        Rolle.BESLUTTER,
        Rolle.LES,
        Rolle.KVALITETSSIKRER
    )

    override fun vurder(input: List<Rolle>): Boolean {
        return input.any { it in tillatteRoller }
    }
}

data object RolleInputGenerator : InputGenerator<List<Rolle>> {
    override suspend fun generer(input: Tilgangsgrunnlag): List<Rolle> {
        return input.kontekst.roller
    }
}