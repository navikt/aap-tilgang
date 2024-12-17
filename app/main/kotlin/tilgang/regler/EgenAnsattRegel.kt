package tilgang.regler

import tilgang.integrasjoner.skjerming.SkjermingClient
import tilgang.service.SkjermingService

data object EgenAnsattRegel : Regel<EgenAnsattInput> {
    override fun vurder(input: EgenAnsattInput): Boolean {

        return when (input.finnesAnsattIFamilie) {
            true -> input.harSkjermedePersonerRolle
            false -> true
        }
    }
}

class EgenAnsattInputGenerator(
    private val skjermingClient: SkjermingClient,
    private val skjermingService: SkjermingService
) : InputGenerator<EgenAnsattInput> {
    override fun generer(input: RegelInput): EgenAnsattInput {
        val finnesAnsattIFamilie = skjermingClient.isSkjermet(input.søkerIdenter)
        val harSkjermedePersonerRolle =
            skjermingService.harSkjermedePersonerRolle(input.currentToken, input.ansattIdent)
        return EgenAnsattInput(finnesAnsattIFamilie, harSkjermedePersonerRolle)
    }
}

data class EgenAnsattInput(val finnesAnsattIFamilie: Boolean, val harSkjermedePersonerRolle: Boolean)