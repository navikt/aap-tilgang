package tilgang.regler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EgenSakRegelTest {
    @Test
    fun `Saksbehandler skal ikke ha tilgang til egen sak`() {
        val navAnsattIdent = "1234"
        val navIdentFraNOM = "1234"
        assertFalse(EgenSakRegel.vurder(EgenSakInput(navAnsattIdent, navIdentFraNOM)))
    }
}