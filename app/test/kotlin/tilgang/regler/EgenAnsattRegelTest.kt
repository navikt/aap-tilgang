package tilgang.regler

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import tilgang.Rolle

class EgenAnsattRegelTest {

    @Test
    fun `Egen ansatt med SKJERMET-rolle skal ha tilgang`() {
        val input = EgenAnsattInput(true, true)
        assertTrue(EgenAnsattRegel.vurder(input))
    }

    @Test
    fun `Egen ansatt uten SKJERMET-rolle skal ikke ha tilgang`() {
        val input = EgenAnsattInput(true, false)
        assertTrue(!EgenAnsattRegel.vurder(input))
    }

    @Test
    fun `Ikke-ansatt skal ha tilgang`() {
        val input = EgenAnsattInput(false, false)
        assertTrue(EgenAnsattRegel.vurder(input))
    }

    @Test
    fun `Ikke-ansatt med SKJERMET-rolle skal ha tilgang`() {
        val input = EgenAnsattInput(false, true)
        assertTrue(EgenAnsattRegel.vurder(input))
    }
}