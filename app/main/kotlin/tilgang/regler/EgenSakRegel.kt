package tilgang.regler

import tilgang.integrasjoner.nom.NOMClient

data object EgenSakRegel : Regel<EgenSakInput> {
    override fun vurder(input: EgenSakInput): Boolean {
        return input.navAnsattIdent == input.navIdentFraNOM
    }
}

class EgenSakInputGenerator(private val nomClient: NOMClient) : InputGenerator<EgenSakInput> {
    override suspend fun generer(input: RegelInput): EgenSakInput {
        val søkerIdent = input.søkerIdenter.søker.first()
        val navIdentFraNOM = nomClient.personNummerTilNavIdent(søkerIdent)
        return EgenSakInput(input.ansattIdent, navIdentFraNOM)
    }
}

data class EgenSakInput(val navAnsattIdent: String, val navIdentFraNOM: String)