package tilgang.regler

import tilgang.integrasjoner.nom.INomGateway

data object EgenSakRegel : Regel<EgenSakInput> {
    override fun vurder(input: EgenSakInput): Boolean {
        return input.navAnsattIdent != input.navIdentFraNOM
    }
}

class EgenSakInputGenerator(private val nomGateway: INomGateway) : InputGenerator<EgenSakInput> {
    override fun generer(input: RegelInput): EgenSakInput {
        val søkerIdent = input.søkerIdenter.søker.first()
        val navIdentFraNOM = nomGateway.personNummerTilNavIdent(søkerIdent, input.callId)
        return EgenSakInput(input.ansattIdent, navIdentFraNOM)
    }
}

data class EgenSakInput(val navAnsattIdent: String, val navIdentFraNOM: String)