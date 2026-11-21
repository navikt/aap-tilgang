package tilgang.kontroll.regler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TilgangmaskinRegelTest {
    @Test
    fun `Skal avslå når avvist med grunn inhabil`() {
        val input = Habilitetsgrunnlag(harTilgang = false, avvistForHabilitet = true)
        assertFalse(HabilitetRegel.vurder(input))
    }

    @Test
    fun `Skal gi tilgang når avvist med grunn ulikt inhabil`() {
        val input = Habilitetsgrunnlag(harTilgang = false, avvistForHabilitet = false)
        assertTrue(HabilitetRegel.vurder(input))
    }

    @Test
    fun `Skal gi tilgang når tilgangsmaskinen gir positivt svar`() {
        val input = Habilitetsgrunnlag(harTilgang = true, avvistForHabilitet = false)
        assertTrue(HabilitetRegel.vurder(input))
    }
}