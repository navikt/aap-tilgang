package tilgang.regler

import tilgang.service.EnhetRolle
import tilgang.service.EnhetService

data object EnhetRegel : Regel<EnhetInput> {
    override fun vurder(input: EnhetInput): Boolean {
        return input.saksEnhet in input.enhetsRoller.map { it.kode }
    }
}

class EnhetInputGenerator(
    private val enhetService: EnhetService,
) : InputGenerator<EnhetInput> {
    override fun generer(input: RegelInput): EnhetInput {
        val enhetsRoller = enhetService.hentEnhetRoller(input.currentToken, input.ansattIdent)
        return EnhetInput(enhetsRoller, "placeholder")
    }
}

data class EnhetInput(
    val enhetsRoller: List<EnhetRolle>,
    val saksEnhet: String
)
