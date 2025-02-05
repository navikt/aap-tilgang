package tilgang.regler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import no.nav.aap.tilgang.Rolle

class DriftRolleRegelTest {
    @Test
    fun `Kun utvikler skal ha tilgang til operasjon 'DRIFTE'`() {
        assertTrue(DriftRolleRegel.vurder(listOf(Rolle.DRIFT)))
        assertFalse(
            DriftRolleRegel.vurder(
                listOf(
                    Rolle.LES,
                    Rolle.SAKSBEHANDLER_NASJONAL,
                    Rolle.SAKSBEHANDLER_OPPFOLGING,
                    Rolle.PRODUKSJONSSTYRING,
                    Rolle.BESLUTTER,
                )
            )
        )
    }
}