package tilgang.regler

import tilgang.integrasjoner.tilgangsmaskin.HarTilgangFraTilgangsmaskinen
import tilgang.integrasjoner.tilgangsmaskin.ITilgangsmaskinGateway
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistGrunn

/**
 * Kaller tilgangsmaskinen og avslår hvis egen eller nær familie sin sak
 */
data object HabilitetRegel : Regel<HabilitetRegelInput> {
    override fun vurder(input: HabilitetRegelInput): Boolean {
        val avvistMedHabilitetsgrunn =
            input.tilgangsmaskinResponse.tilgangsmaskinAvvistResponse?.title == TilgangsmaskinAvvistGrunn.AVVIST_HABILITET.toString()

        return input.tilgangsmaskinResponse.harTilgang || !avvistMedHabilitetsgrunn
    }
}

class HabilitetRegelInputGenerator(private val tilgangsmaskinGateway: ITilgangsmaskinGateway) :
    InputGenerator<HabilitetRegelInput> {
    override suspend fun generer(input: RegelInput): HabilitetRegelInput {
        val tilgangsmaskinResponse =
            tilgangsmaskinGateway.harTilgangTilPersonKjerne(
                input.søkerIdenter.søker.first(),
                input.currentToken,
                input.ansattIdent
            )
        return HabilitetRegelInput(tilgangsmaskinResponse)
    }
}

data class HabilitetRegelInput(var tilgangsmaskinResponse: HarTilgangFraTilgangsmaskinen)