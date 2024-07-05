package tilgang.regler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EgenSakRegelTest {
    @Test
    fun `Saksbehandler skal ikke ha tilgang til egen sak`() {
        val saksbehnadlerPnr = "1234"
        assertFalse(EgenSakRegel.vurder(EgenSakInput(saksbehnadlerPnr, listOf("1234"), emptyList())))
    }

}