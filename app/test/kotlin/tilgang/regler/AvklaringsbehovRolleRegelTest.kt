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
            avklaringsbehovRoller = listOf(Definisjon.MANUELT_SATT_PÅ_VENT.løsesAv),
            påkrevdRolle = emptyList(),
            roller = listOf(Rolle.SAKSBEHANDLER_OPPFOLGING)
        )
        assertTrue(AvklaringsbehovRolleRegel.vurder(input))
    }

    @Test
    fun `gir ikke tilgang når bruker mangler rolle for avklaringsbehov fra behandlingsflyt`() {
        val input = AvklaringsbehovRolleInput(
            avklaringsbehovRoller = listOf(Definisjon.MANUELT_SATT_PÅ_VENT.løsesAv),
            påkrevdRolle = emptyList(),
            roller = listOf(Rolle.BESLUTTER)
        )
        assertFalse(AvklaringsbehovRolleRegel.vurder(input))
    }

    @Test
    fun `gir tilgang med påkrevdRolle når bruker har rollen`() {
        val input = AvklaringsbehovRolleInput(
            avklaringsbehovRoller = emptyList(),
            påkrevdRolle = listOf(Rolle.BESLUTTER),
            roller = listOf(Rolle.BESLUTTER)
        )
        assertTrue(AvklaringsbehovRolleRegel.vurder(input))
    }

    @Test
    fun `gir ikke tilgang med påkrevdRolle når bruker mangler rollen`() {
        val input = AvklaringsbehovRolleInput(
            avklaringsbehovRoller = emptyList(),
            påkrevdRolle = listOf(Rolle.BESLUTTER),
            roller = listOf(Rolle.SAKSBEHANDLER_OPPFOLGING)
        )
        assertFalse(AvklaringsbehovRolleRegel.vurder(input))
    }

    @Test
    fun `krever både avklaringsbehov-rolle og påkrevdRolle når begge er satt`() {
        // Bruker har SAKSBEHANDLER_OPPFOLGING som kan løse MANUELT_SATT_PÅ_VENT, og BESLUTTER som er påkrevdRolle
        val input = AvklaringsbehovRolleInput(
            avklaringsbehovRoller = listOf(Definisjon.MANUELT_SATT_PÅ_VENT.løsesAv),
            påkrevdRolle = listOf(Rolle.BESLUTTER),
            roller = listOf(Rolle.SAKSBEHANDLER_OPPFOLGING, Rolle.BESLUTTER)
        )
        assertTrue(AvklaringsbehovRolleRegel.vurder(input))
    }

    @Test
    fun `gir ikke tilgang når avklaringsbehov passer men påkrevdRolle mangler`() {
        val input = AvklaringsbehovRolleInput(
            avklaringsbehovRoller = listOf(Definisjon.MANUELT_SATT_PÅ_VENT.løsesAv),
            påkrevdRolle = listOf(Rolle.BESLUTTER),
            roller = listOf(Rolle.SAKSBEHANDLER_OPPFOLGING)
        )
        assertFalse(AvklaringsbehovRolleRegel.vurder(input))
    }

    @Test
    fun `gir ikke tilgang når påkrevdRolle passer men avklaringsbehov-rolle mangler`() {
        val input = AvklaringsbehovRolleInput(
            avklaringsbehovRoller = listOf(Definisjon.MANUELT_SATT_PÅ_VENT.løsesAv),
            påkrevdRolle = listOf(Rolle.BESLUTTER),
            roller = listOf(Rolle.BESLUTTER)
        )
        assertFalse(AvklaringsbehovRolleRegel.vurder(input))
    }

    @Test
    fun `kaster feil når verken avklaringsbehov eller påkrevdRolle er satt`() {
        val input = AvklaringsbehovRolleInput(
            avklaringsbehovRoller = emptyList(),
            påkrevdRolle = emptyList(),
            roller = listOf(Rolle.BESLUTTER)
        )
        assertFalse(AvklaringsbehovRolleRegel.vurder(input))
    }

    @Test
    fun `krever rolle for hvert avklaringsbehov`() {
        val krav = listOf(listOf(Rolle.SAKSBEHANDLER_OPPFOLGING), listOf(Rolle.BESLUTTER))

        assertFalse(
            AvklaringsbehovRolleRegel.vurder(
                AvklaringsbehovRolleInput(krav, roller = listOf(Rolle.SAKSBEHANDLER_OPPFOLGING))
            )
        )
        assertTrue(
            AvklaringsbehovRolleRegel.vurder(
                AvklaringsbehovRolleInput(krav, roller = listOf(Rolle.SAKSBEHANDLER_OPPFOLGING, Rolle.BESLUTTER))
            )
        )
    }

    @Test
    fun `tomt rollekrav for et behov kan ikke erstattes av påkrevd rolle`() {
        val input = AvklaringsbehovRolleInput(
            avklaringsbehovRoller = listOf(emptyList()),
            påkrevdRolle = listOf(Rolle.BESLUTTER),
            roller = listOf(Rolle.BESLUTTER),
        )

        assertFalse(AvklaringsbehovRolleRegel.vurder(input))
    }
}
