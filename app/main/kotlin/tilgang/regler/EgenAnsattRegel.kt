package tilgang.regler

import tilgang.integrasjoner.skjerming.SkjermingGateway
import tilgang.service.SkjermingService

data object EgenAnsattRegel : Regel<EgenAnsattInput> {
    override fun vurder(input: EgenAnsattInput): Boolean {

        return when (input.skalHaSkjerming) {
            true -> input.harSkjermedePersonerRolle
            false -> true
        }
    }
}

class EgenAnsattInputGenerator(
    private val skjermingGateway: SkjermingGateway,
    private val skjermingService: SkjermingService
) : InputGenerator<EgenAnsattInput> {
    override fun generer(input: RegelInput): EgenAnsattInput {
        val skalHaSkjerming = skjermingGateway.isSkjermet(input.søkerIdenter)
        val harSkjermedePersonerRolle =
            skjermingService.harSkjermedePersonerRolle(input.currentToken, input.ansattIdent)
        return EgenAnsattInput(skalHaSkjerming, harSkjermedePersonerRolle)
    }
}

data class EgenAnsattInput(val skalHaSkjerming: Boolean, val harSkjermedePersonerRolle: Boolean)