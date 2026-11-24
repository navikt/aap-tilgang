package tilgang.kontroll.regler

import tilgang.kontroll.Tilgangsgrunnlag

data object AvklaringsbehovInputGenerator : InputGenerator<AvklaringsbehovRolleInput> {
    override suspend fun generer(input: Tilgangsgrunnlag): AvklaringsbehovRolleInput {
        return AvklaringsbehovRolleInput(
            input.avklaringsbehovRoller,
            input.påkrevdRolle,
            input.kontekst.roller,
        )
    }
}
