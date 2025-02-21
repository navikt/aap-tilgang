package tilgang.regler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import no.nav.aap.tilgang.Rolle

class LeseRolleRegelTest {
    @Test
    fun `Leseroller skal kunne lese saker i Kelvin`() {
        assertFalse(
            LeseRolleRegel.vurder(
                listOf(
                    Rolle.DRIFT,
                )
            )
        )
        assertTrue(LeseRolleRegel.vurder(listOf(Rolle.LES)))
        assertTrue(LeseRolleRegel.vurder(listOf(Rolle.SAKSBEHANDLER_NASJONAL)))
        assertTrue(LeseRolleRegel.vurder(listOf(Rolle.SAKSBEHANDLER_OPPFOLGING)))
        assertTrue(LeseRolleRegel.vurder(listOf(Rolle.KVALITETSSIKRER)))
    }
}