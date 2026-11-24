package tilgang.kontroll.regler

import tilgang.kontroll.Rolle
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AvklaringsbehovRolleRegelTest {
    private val rollerSomLøserBehov = listOf(Rolle.SAKSBEHANDLER_OPPFOLGING, Rolle.SAKSBEHANDLER_NASJONAL)

    @Test
    fun `gir tilgang når bruker har riktig rolle for avklaringsbehov`() {
        val input = AvklaringsbehovRolleInput(
            avklaringsbehovRoller = listOf(rollerSomLøserBehov),
            påkrevdRolle = emptyList(),
            roller = listOf(Rolle.SAKSBEHANDLER_OPPFOLGING)
        )
        assertTrue(AvklaringsbehovRolleRegel.vurder(input))
    }

    @Test
    fun `gir ikke tilgang når bruker mangler rolle for avklaringsbehov`() {
        val input = AvklaringsbehovRolleInput(
            avklaringsbehovRoller = listOf(rollerSomLøserBehov),
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
        // Bruker har SAKSBEHANDLER_OPPFOLGING som kan løse behovet, og BESLUTTER som er påkrevdRolle
        val input = AvklaringsbehovRolleInput(
            avklaringsbehovRoller = listOf(rollerSomLøserBehov),
            påkrevdRolle = listOf(Rolle.BESLUTTER),
            roller = listOf(Rolle.SAKSBEHANDLER_OPPFOLGING, Rolle.BESLUTTER)
        )
        assertTrue(AvklaringsbehovRolleRegel.vurder(input))
    }

    @Test
    fun `gir ikke tilgang når avklaringsbehov passer men påkrevdRolle mangler`() {
        val input = AvklaringsbehovRolleInput(
            avklaringsbehovRoller = listOf(rollerSomLøserBehov),
            påkrevdRolle = listOf(Rolle.BESLUTTER),
            roller = listOf(Rolle.SAKSBEHANDLER_OPPFOLGING)
        )
        assertFalse(AvklaringsbehovRolleRegel.vurder(input))
    }

    @Test
    fun `gir ikke tilgang når påkrevdRolle passer men avklaringsbehov-rolle mangler`() {
        val input = AvklaringsbehovRolleInput(
            avklaringsbehovRoller = listOf(rollerSomLøserBehov),
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
