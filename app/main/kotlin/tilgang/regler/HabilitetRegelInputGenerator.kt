package tilgang.regler

class HabilitetRegelInputGenerator(private val personkontroll: Personkontroll) :
    InputGenerator<Habilitetsgrunnlag> {
    override suspend fun generer(input: RegelInput): Habilitetsgrunnlag {
        return personkontroll.hentHabilitetsgrunnlag(input.søkerIdenter.søker.first(), input.kontekst)
    }
}
