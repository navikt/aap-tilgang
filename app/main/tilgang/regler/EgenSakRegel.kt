package tilgang.regler

data object EgenSakRegel : Regel<EgenSakInput> {
    override fun vurder(input: EgenSakInput): Boolean {
        return input.ident in input.søkerIdenter.union(input.barnIdententer)
    }
}

data object EgenSakInputGenerator : InputGenerator<EgenSakInput> {
    override suspend fun generer(input: RegelInput): EgenSakInput {
        // TODO: Se om man kan hente pnr for navident - ellers vil denne alltid gå gjennom ettersom de andre identene er på pnr-format
        // TODO: RegelInput må opprettes med riktig objekt for identer
        return EgenSakInput(input.ident, input.identer, input.identer)
    }
}

data class EgenSakInput(val ident: String, val søkerIdenter: List<String>, val barnIdententer: List<String>)
