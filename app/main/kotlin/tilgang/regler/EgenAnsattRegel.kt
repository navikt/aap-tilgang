package tilgang.regler

import tilgang.Rolle
import tilgang.integrasjoner.skjerming.SkjermingClient

data object EgenAnsattRegel : Regel<EgenAnsattInput> {
    override fun vurder(input: EgenAnsattInput): Boolean {

        return when (input.erAnsatt){
            true -> input.roller.contains(Rolle.SKJERMET)
            false -> true
        }
    }
}

class EgenAnsattInputGenerator(private val skjermingClient: SkjermingClient) : InputGenerator<EgenAnsattInput> {
    override suspend fun generer(input: RegelInput): EgenAnsattInput {
        val ansatt = skjermingClient.isSkjermet(input.ident)
        return EgenAnsattInput(ansatt, input.roller)
    }
}

data class EgenAnsattInput(val erAnsatt: Boolean, val roller: List<Rolle>)