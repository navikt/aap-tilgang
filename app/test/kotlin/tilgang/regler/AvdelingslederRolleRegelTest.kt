package tilgang.regler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tilgang.Rolle

class AvdelingslederRolleRegelTest {
    @Test
    fun `Rolle avdelingsleder påkrevd`() {
        assertTrue(AvdelingslederRolleRegel.vurder(listOf(Rolle.AVDELINGSLEDER)))
        assertFalse(
            AvdelingslederRolleRegel.vurder(
                listOf(
                    Rolle.UTVIKLER,
                    Rolle.BESLUTTER,
                    Rolle.VEILEDER,
                    Rolle.LES,
                    Rolle.SAKSBEHANDLER
                )
            )
        )
    }
}