package tilgang.regler

data object AvklaringsbehovInputGenerator : InputGenerator<AvklaringsbehovRolleInput> {
    override suspend fun generer(input: RegelInput): AvklaringsbehovRolleInput {
        return AvklaringsbehovRolleInput(
            input.avklaringsbehovRoller,
            input.påkrevdRolle,
            input.kontekst.roller,
        )
    }
}
