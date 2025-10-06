package tilgang.regler

import tilgang.integrasjoner.tilgangsmaskin.HarTilgangFraTilgangsmaskinen
import tilgang.integrasjoner.tilgangsmaskin.ITilgangsmaskinClient
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistGrunn

/*
* Tilgangsmaskin-regel, per nå erstatter denne
* EgenSakRegel (AVVIST_HABILITET)
*
* */
data object TilgangsmaskinKjerneRegel : Regel<TilgangsmaskinKjerneInput> {
    override fun vurder(input: TilgangsmaskinKjerneInput): Boolean {
        val avvistMedHabilitetsgrunn =
            input.tilgangsmaskinResponse.TilgangsmaskinAvvistResponse?.title == TilgangsmaskinAvvistGrunn.AVVIST_HABILITET.toString()

        return input.tilgangsmaskinResponse.harTilgang || !avvistMedHabilitetsgrunn
    }
}

class TilgangsmaskinKjerneInputGenerator(private val tilgangsmaskinClient: ITilgangsmaskinClient) :
    InputGenerator<TilgangsmaskinKjerneInput> {
    override fun generer(input: RegelInput): TilgangsmaskinKjerneInput {
        val tilgangsmaskinResponse =
            tilgangsmaskinClient.harTilgangTilPersonKjerne(input.ansattIdent, input.currentToken)
        return TilgangsmaskinKjerneInput(tilgangsmaskinResponse)
    }
}

data class TilgangsmaskinKjerneInput(var tilgangsmaskinResponse: HarTilgangFraTilgangsmaskinen)