package tilgang.regler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import no.nav.aap.tilgang.Rolle

class AvdelingslederRolleRegelTest {
    @Test
    fun `Rolle avdelingsleder påkrevd`() {
        assertTrue(AvdelingslederRolleRegel.vurder(listOf(Rolle.PRODUKSJONSSTYRING)))
        assertFalse(
            AvdelingslederRolleRegel.vurder(
                listOf(
                    Rolle.DRIFT,
                    Rolle.BESLUTTER,
                    Rolle.SAKSBEHANDLER_OPPFOLGING,
                    Rolle.LES,
                    Rolle.SAKSBEHANDLER_NASJONAL
                )
            )
        )
    }
}