package tilgang.regler

import tilgang.integrasjoner.tilgangsmaskin.HarTilgangFraTilgangsmaskinen
import tilgang.integrasjoner.tilgangsmaskin.ITilgangsmaskinGateway

data object TilgangsmaskinKomplettRegel : Regel<TilgangsmaskinKomplettInput> {
    override fun vurder(input: TilgangsmaskinKomplettInput): Boolean {
        return input.tilgangsmaskinResponse.harTilgang
    }
}

class TilgangsmaskinKomplettInputGenerator(private val tilgangsmaskinGateway: ITilgangsmaskinGateway) :
    InputGenerator<TilgangsmaskinKomplettInput> {
    override suspend fun generer(input: RegelInput): TilgangsmaskinKomplettInput {
        val tilgangsmaskinResponse =
            tilgangsmaskinGateway.harTilgangTilPersonKomplett(input.søkerIdenter.søker.first(), input.currentToken, input.ansattIdent)
        return TilgangsmaskinKomplettInput(tilgangsmaskinResponse)
    }
}

data class TilgangsmaskinKomplettInput(val tilgangsmaskinResponse: HarTilgangFraTilgangsmaskinen)