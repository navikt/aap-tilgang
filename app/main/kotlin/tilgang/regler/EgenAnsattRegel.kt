package tilgang.regler

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    override suspend fun generer(input: RegelInput): EgenAnsattInput {
        return coroutineScope {
            val skalHaSkjerming = async { skjermingGateway.isSkjermet(input.søkerIdenter) }
            val harSkjermedePersonerRolle =
                async { skjermingService.harSkjermedePersonerRolle(input.currentToken, input.ansattIdent) }
            EgenAnsattInput(skalHaSkjerming.await(), harSkjermedePersonerRolle.await())
        }
    }
}

data class EgenAnsattInput(val skalHaSkjerming: Boolean, val harSkjermedePersonerRolle: Boolean)