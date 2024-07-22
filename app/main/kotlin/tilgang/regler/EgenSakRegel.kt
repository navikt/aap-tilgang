package tilgang.regler

data object EgenSakRegel : Regel<EgenSakInput> {
    override fun vurder(input: EgenSakInput): Boolean {
        val identer = input.søkerIdenter.union(input.barnIdententer)
        return input.ident !in identer
    }
}

data object EgenSakInputGenerator : InputGenerator<EgenSakInput> {
    override suspend fun generer(input: RegelInput): EgenSakInput {
        val (søkerIdenter, barnIdenter) = input.identer

        //TODO: input.ident er NAV-ident - må finne pnr

        return EgenSakInput(input.ident, søkerIdenter, barnIdenter)
    }
}

data class EgenSakInput(val ident: String, val søkerIdenter: List<String>, val barnIdententer: List<String>)
