package tilgang.kontroll.regler

data object HabilitetRegel : Regel<Habilitetsgrunnlag> {
    override fun vurder(input: Habilitetsgrunnlag): Boolean {
        return input.harTilgang || !input.avvistForHabilitet
    }
}