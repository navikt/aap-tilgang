package tilgang.kontroll.regler

class PersontilgangInputGenerator(private val personkontroll: Personkontroll) : InputGenerator<Boolean> {
    override suspend fun generer(input: RegelInput): Boolean {
        return personkontroll.harTilgangTilPerson(input.søkerIdenter.søker.first(), input.kontekst)
    }
}
