package tilgang.kontroll.regler

data object PersontilgangRegel : Regel<Boolean> {
    override fun vurder(input: Boolean): Boolean = input
}
