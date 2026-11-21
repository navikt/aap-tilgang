package tilgang.kontroll.regler

import tilgang.kontroll.Tilgangsgrunnlag

class HabilitetRegelInputGenerator(private val personkontroll: Personkontroll) :
    InputGenerator<Habilitetsgrunnlag> {
    override suspend fun generer(input: Tilgangsgrunnlag): Habilitetsgrunnlag {
        return personkontroll.hentHabilitetsgrunnlag(input.søkerIdenter.søker.first(), input.kontekst)
    }
}
