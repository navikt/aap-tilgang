package tilgang.kontroll.regler

import tilgang.kontroll.Tilgangsgrunnlag

class PersontilgangInputGenerator(private val personkontroll: Personkontroll) : InputGenerator<Boolean> {
    override suspend fun generer(input: Tilgangsgrunnlag): Boolean {
        return personkontroll.harTilgangTilPerson(input.søkerIdenter.søker.first(), input.kontekst)
    }
}
