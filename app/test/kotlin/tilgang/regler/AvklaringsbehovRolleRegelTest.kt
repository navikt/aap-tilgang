package tilgang.regler

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.tilgang.Rolle
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AvklaringsbehovRolleRegelTest {

    @Test
    fun `gir tilgang når bruker har riktig rolle for avklaringsbehov fra behandlingsflyt`() {
        val input = AvklaringsbehovRolleInput(
            avklaringsbehovFraBehandlingsflyt = Definisjon.MANUELT_SATT_PÅ_VENT,
            avklaringsbehovFraPostmottak = null,
            påkrevdRolle = null,
            roller = listOf(Rolle.SAKSBEHANDLER_OPPFOLGING)
        )
        assertTrue(AvklaringsbehovRolleRegel.vurder(input))
    }

    @Test
    fun `gir ikke tilgang når bruker mangler rolle for avklaringsbehov fra behandlingsflyt`() {
        val input = AvklaringsbehovRolleInput(
            avklaringsbehovFraBehandlingsflyt = Definisjon.MANUELT_SATT_PÅ_VENT,
            avklaringsbehovFraPostmottak = null,
            påkrevdRolle = null,
            roller = listOf(Rolle.BESLUTTER)
        )
        assertFalse(AvklaringsbehovRolleRegel.vurder(input))
    }

    @Test
    fun `gir tilgang med påkrevdRolle når bruker har rollen`() {
        val input = AvklaringsbehovRolleInput(
            avklaringsbehovFraBehandlingsflyt = null,
            avklaringsbehovFraPostmottak = null,
            påkrevdRolle = Rolle.BESLUTTER,
            roller = listOf(Rolle.BESLUTTER)
        )
        assertTrue(AvklaringsbehovRolleRegel.vurder(input))
    }

    @Test
    fun `gir ikke tilgang med påkrevdRolle når bruker mangler rollen`() {
        val input = AvklaringsbehovRolleInput(
            avklaringsbehovFraBehandlingsflyt = null,
            avklaringsbehovFraPostmottak = null,
            påkrevdRolle = Rolle.BESLUTTER,
            roller = listOf(Rolle.SAKSBEHANDLER_OPPFOLGING)
        )
        assertFalse(AvklaringsbehovRolleRegel.vurder(input))
    }

    @Test
    fun `avklaringsbehov fra behandlingsflyt har prioritet over påkrevdRolle`() {
        val input = AvklaringsbehovRolleInput(
            avklaringsbehovFraBehandlingsflyt = Definisjon.MANUELT_SATT_PÅ_VENT,
            avklaringsbehovFraPostmottak = null,
            påkrevdRolle = Rolle.BESLUTTER,
            roller = listOf(Rolle.SAKSBEHANDLER_OPPFOLGING)
        )
        // Avklaringsbehov brukes, ikke påkrevdRolle — SAKSBEHANDLER_OPPFOLGING kan løse MANUELT_SATT_PÅ_VENT
        assertTrue(AvklaringsbehovRolleRegel.vurder(input))
    }

    @Test
    fun `kaster feil når verken avklaringsbehov eller påkrevdRolle er satt`() {
        val input = AvklaringsbehovRolleInput(
            avklaringsbehovFraBehandlingsflyt = null,
            avklaringsbehovFraPostmottak = null,
            påkrevdRolle = null,
            roller = listOf(Rolle.BESLUTTER)
        )
        assertThrows<IllegalArgumentException> {
            AvklaringsbehovRolleRegel.vurder(input)
        }
    }
}
