package tilgang

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.tilgang.BehandlingTilgangRequest
import no.nav.aap.tilgang.JournalpostTilgangRequest
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.PersonTilgangRequest
import no.nav.aap.tilgang.Rolle
import no.nav.aap.tilgang.SakTilgangRequest
import no.nav.aap.tilgang.TilbakekrevingTilgangRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Tester at JSON-kontrakten for TilgangRequest-klassene er bakoverkompatibel.
 *
 * Disse testene sikrer at:
 * - Gamle klienter som sender minimale JSON-payloads fortsatt fungerer
 * - Ukjente felter i JSON ignoreres (framoverkompatibilitet)
 * - Defaultverdier settes korrekt når valgfrie felter utelates
 */
class TilgangRequestBakoverkompatibilitetTest {

    private val objectMapper = DefaultJsonMapper.objectMapper()

    // --- SakTilgangRequest ---

    @Test
    fun `SakTilgangRequest - minimal JSON med påkrevetRolle satt til null`() {
        val json = """
            {
                "saksnummer": "12345",
                "påkrevdRolle": null,
                "operasjon": "SE"
            }
        """.trimIndent()

        val request = objectMapper.readValue<SakTilgangRequest>(json)

        assertThat(request.saksnummer).isEqualTo("12345")
        assertThat(request.operasjon).isEqualTo(Operasjon.SE)
        assertThat(request.påkrevdRolle).isNull()
        assertThat(request.relevanteIdenter).isNull()
    }

    @Test
    fun `SakTilgangRequest - minimal JSON uten valgfrie felter`() {
        val json = """
            {
                "saksnummer": "12345",
                "operasjon": "SE"
            }
        """.trimIndent()

        val request = objectMapper.readValue<SakTilgangRequest>(json)

        assertThat(request.saksnummer).isEqualTo("12345")
        assertThat(request.operasjon).isEqualTo(Operasjon.SE)
        assertThat(request.påkrevdRolle).isNull()
        assertThat(request.relevanteIdenter).isNull()
    }

    @Test
    fun `SakTilgangRequest - komplett JSON med alle felter`() {
        val json = """
            {
                "saksnummer": "12345",
                "påkrevdRolle": ["SAKSBEHANDLER_OPPFOLGING"],
                "operasjon": "SAKSBEHANDLE",
                "relevanteIdenter": {
                    "søker": ["12345678901"],
                    "barn": ["09876543210"]
                }
            }
        """.trimIndent()

        val request = objectMapper.readValue<SakTilgangRequest>(json)

        assertThat(request.saksnummer).isEqualTo("12345")
        assertThat(request.operasjon).isEqualTo(Operasjon.SAKSBEHANDLE)
        assertThat(request.påkrevdRolle).containsExactly(Rolle.SAKSBEHANDLER_OPPFOLGING)
        assertThat(request.relevanteIdenter).isNotNull
        assertThat(request.relevanteIdenter!!.søker).containsExactly("12345678901")
        assertThat(request.relevanteIdenter!!.barn).containsExactly("09876543210")
    }

    @Test
    fun `SakTilgangRequest - ukjente felter ignoreres`() {
        val json = """
            {
                "saksnummer": "12345",
                "operasjon": "SE",
                "ukjentFelt": "verdi",
                "annetUkjentFelt": 42
            }
        """.trimIndent()

        val request = objectMapper.readValue<SakTilgangRequest>(json)

        assertThat(request.saksnummer).isEqualTo("12345")
        assertThat(request.operasjon).isEqualTo(Operasjon.SE)
    }

    // --- BehandlingTilgangRequest ---

    @Test
    fun `BehandlingTilgangRequest - minimal JSON uten valgfrie felter`() {
        val uuid = UUID.randomUUID()
        val json = """
            {
                "behandlingsreferanse": "$uuid",
                "avklaringsbehovKode": null,
                "påkrevdRolle": [],
                "operasjon": "SE"
            }
        """.trimIndent()

        val request = objectMapper.readValue<BehandlingTilgangRequest>(json)

        assertThat(request.behandlingsreferanse).isEqualTo(uuid)
        assertThat(request.avklaringsbehovKode).isNull()
        assertThat(request.operasjon).isEqualTo(Operasjon.SE)
        assertThat(request.påkrevdRolle).isEmpty()
        assertThat(request.relevanteIdenter).isNull()
        assertThat(request.operasjonerIKontekst).isEmpty()
    }

    @Test
    fun `BehandlingTilgangRequest - komplett JSON med alle felter`() {
        val uuid = UUID.randomUUID()
        val json = """
            {
                "behandlingsreferanse": "$uuid",
                "avklaringsbehovKode": "5003",
                "påkrevdRolle": ["SAKSBEHANDLER_OPPFOLGING", "KVALITETSSIKRER"],
                "operasjon": "SAKSBEHANDLE",
                "relevanteIdenter": {
                    "søker": ["12345678901"],
                    "barn": []
                },
                "operasjonerIKontekst": ["SE", "SAKSBEHANDLE"]
            }
        """.trimIndent()

        val request = objectMapper.readValue<BehandlingTilgangRequest>(json)

        assertThat(request.behandlingsreferanse).isEqualTo(uuid)
        assertThat(request.avklaringsbehovKode).isEqualTo("5003")
        assertThat(request.påkrevdRolle).containsExactly(Rolle.SAKSBEHANDLER_OPPFOLGING, Rolle.KVALITETSSIKRER)
        assertThat(request.operasjon).isEqualTo(Operasjon.SAKSBEHANDLE)
        assertThat(request.relevanteIdenter).isNotNull
        assertThat(request.operasjonerIKontekst).containsExactly(Operasjon.SE, Operasjon.SAKSBEHANDLE)
    }

    @Test
    fun `BehandlingTilgangRequest - uten operasjonerIKontekst bruker default tom liste`() {
        val uuid = UUID.randomUUID()
        val json = """
            {
                "behandlingsreferanse": "$uuid",
                "påkrevdRolle": ["SAKSBEHANDLER_OPPFOLGING"],
                "operasjon": "SE"
            }
        """.trimIndent()

        val request = objectMapper.readValue<BehandlingTilgangRequest>(json)

        assertThat(request.operasjonerIKontekst).isEmpty()
    }

    @Test
    fun `BehandlingTilgangRequest - ukjente felter ignoreres`() {
        val uuid = UUID.randomUUID()
        val json = """
            {
                "behandlingsreferanse": "$uuid",
                "avklaringsbehovKode": null,
                "operasjon": "SE",
                "nyttFremtidigFelt": true,
                "påkrevdRolle": []
            }
        """.trimIndent()

        val request = objectMapper.readValue<BehandlingTilgangRequest>(json)

        assertThat(request.behandlingsreferanse).isEqualTo(uuid)
    }

    // --- JournalpostTilgangRequest ---

    @Test
    fun `JournalpostTilgangRequest - minimal JSON uten valgfrie felter`() {
        val json = """
            {
                "journalpostId": 123456789,
                "avklaringsbehovKode": null,
                "operasjon": "SE"
            }
        """.trimIndent()

        val request = objectMapper.readValue<JournalpostTilgangRequest>(json)

        assertThat(request.journalpostId).isEqualTo(123456789L)
        assertThat(request.avklaringsbehovKode).isNull()
        assertThat(request.operasjon).isEqualTo(Operasjon.SE)
        assertThat(request.påkrevdRolle).isNull()
    }

    @Test
    fun `JournalpostTilgangRequest - komplett JSON med alle felter`() {
        val json = """
            {
                "journalpostId": 123456789,
                "avklaringsbehovKode": "5001",
                "påkrevdRolle": ["BESLUTTER"],
                "operasjon": "SAKSBEHANDLE"
            }
        """.trimIndent()

        val request = objectMapper.readValue<JournalpostTilgangRequest>(json)

        assertThat(request.journalpostId).isEqualTo(123456789L)
        assertThat(request.avklaringsbehovKode).isEqualTo("5001")
        assertThat(request.påkrevdRolle).containsExactly(Rolle.BESLUTTER)
        assertThat(request.operasjon).isEqualTo(Operasjon.SAKSBEHANDLE)
    }

    @Test
    fun `JournalpostTilgangRequest - ukjente felter ignoreres`() {
        val json = """
            {
                "journalpostId": 123456789,
                "avklaringsbehovKode": null,
                "operasjon": "SE",
                "ukjentFelt": [1, 2, 3]
            }
        """.trimIndent()

        val request = objectMapper.readValue<JournalpostTilgangRequest>(json)

        assertThat(request.journalpostId).isEqualTo(123456789L)
    }

    // --- PersonTilgangRequest ---

    @Test
    fun `PersonTilgangRequest - deserialisering av enkel request`() {
        val json = """
            {
                "personIdent": "12345678901"
            }
        """.trimIndent()

        val request = objectMapper.readValue<PersonTilgangRequest>(json)

        assertThat(request.personIdent).isEqualTo("12345678901")
    }

    @Test
    fun `PersonTilgangRequest - ukjente felter ignoreres`() {
        val json = """
            {
                "personIdent": "12345678901",
                "ukjentFelt": "verdi"
            }
        """.trimIndent()

        val request = objectMapper.readValue<PersonTilgangRequest>(json)

        assertThat(request.personIdent).isEqualTo("12345678901")
    }

    // --- TilbakekrevingTilgangRequest ---

    @Test
    fun `TilbakekrevingTilgangRequest - med kun gammel påkrevdRolle-felt (bakoverkompatibilitet)`() {
        val uuid = UUID.randomUUID()
        val json = """
            {
                "saksnummer": "12345",
                "behandlingsreferanse": "$uuid",
                "påkrevdRolle": "SAKSBEHANDLER_OPPFOLGING",
                "operasjon": "SAKSBEHANDLE"
            }
        """.trimIndent()

        @Suppress("DEPRECATION")
        val request = objectMapper.readValue<TilbakekrevingTilgangRequest>(json)

        assertThat(request.saksnummer).isEqualTo("12345")
        assertThat(request.behandlingsreferanse).isEqualTo(uuid)
        @Suppress("DEPRECATION")
        assertThat(request.påkrevdRolle).isEqualTo(Rolle.SAKSBEHANDLER_OPPFOLGING)
        assertThat(request.påkrevdRoller).isNull()
        assertThat(request.operasjon).isEqualTo(Operasjon.SAKSBEHANDLE)
        assertThat(request.effektivePåkrevdRoller()).containsExactly(Rolle.SAKSBEHANDLER_OPPFOLGING)
    }

    @Test
    fun `TilbakekrevingTilgangRequest - med nytt påkrevdRoller-felt`() {
        val uuid = UUID.randomUUID()
        val json = """
            {
                "saksnummer": "12345",
                "behandlingsreferanse": "$uuid",
                "påkrevdRoller": ["SAKSBEHANDLER_OPPFOLGING", "KVALITETSSIKRER"],
                "operasjon": "SAKSBEHANDLE"
            }
        """.trimIndent()

        val request = objectMapper.readValue<TilbakekrevingTilgangRequest>(json)

        assertThat(request.påkrevdRoller).containsExactly(Rolle.SAKSBEHANDLER_OPPFOLGING, Rolle.KVALITETSSIKRER)
        @Suppress("DEPRECATION")
        assertThat(request.påkrevdRolle).isNull()
        assertThat(request.effektivePåkrevdRoller()).containsExactly(Rolle.SAKSBEHANDLER_OPPFOLGING, Rolle.KVALITETSSIKRER)
    }

    @Test
    fun `TilbakekrevingTilgangRequest - med begge påkrevdRolle-feltene prioriterer påkrevdRoller`() {
        val uuid = UUID.randomUUID()
        val json = """
            {
                "saksnummer": "12345",
                "behandlingsreferanse": "$uuid",
                "påkrevdRolle": "SAKSBEHANDLER_OPPFOLGING",
                "påkrevdRoller": ["KVALITETSSIKRER"],
                "operasjon": "SAKSBEHANDLE"
            }
        """.trimIndent()

        val request = objectMapper.readValue<TilbakekrevingTilgangRequest>(json)

        assertThat(request.effektivePåkrevdRoller()).containsExactly(Rolle.KVALITETSSIKRER)
    }

    @Test
    fun `TilbakekrevingTilgangRequest - minimal JSON for SE-operasjon`() {
        val uuid = UUID.randomUUID()
        val json = """
            {
                "saksnummer": "12345",
                "behandlingsreferanse": "$uuid",
                "operasjon": "SE"
            }
        """.trimIndent()

        val request = objectMapper.readValue<TilbakekrevingTilgangRequest>(json)

        assertThat(request.saksnummer).isEqualTo("12345")
        assertThat(request.operasjon).isEqualTo(Operasjon.SE)
        assertThat(request.effektivePåkrevdRoller()).isEmpty()
    }

    @Test
    fun `TilbakekrevingTilgangRequest - ukjente felter ignoreres`() {
        val uuid = UUID.randomUUID()
        val json = """
            {
                "saksnummer": "12345",
                "behandlingsreferanse": "$uuid",
                "operasjon": "SE",
                "nyttFelt": "ukjent"
            }
        """.trimIndent()

        val request = objectMapper.readValue<TilbakekrevingTilgangRequest>(json)

        assertThat(request.saksnummer).isEqualTo("12345")
    }

    // --- Serialisering/deserialisering roundtrip ---

    @Test
    fun `SakTilgangRequest - serialisering og deserialisering gir samme objekt`() {
        val original = SakTilgangRequest(
            saksnummer = "12345",
            påkrevdRolle = listOf(Rolle.SAKSBEHANDLER_OPPFOLGING),
            operasjon = Operasjon.SAKSBEHANDLE,
        )

        val json = objectMapper.writeValueAsString(original)
        val deserialisert = objectMapper.readValue<SakTilgangRequest>(json)

        assertThat(deserialisert).isEqualTo(original)
    }

    @Test
    fun `BehandlingTilgangRequest - serialisering og deserialisering gir samme objekt`() {
        val original = BehandlingTilgangRequest(
            behandlingsreferanse = UUID.randomUUID(),
            avklaringsbehovKode = "5003",
            påkrevdRolle = listOf(Rolle.KVALITETSSIKRER),
            operasjon = Operasjon.SAKSBEHANDLE,
            operasjonerIKontekst = listOf(Operasjon.SE),
        )

        val json = objectMapper.writeValueAsString(original)
        val deserialisert = objectMapper.readValue<BehandlingTilgangRequest>(json)

        assertThat(deserialisert).isEqualTo(original)
    }

    @Test
    fun `JournalpostTilgangRequest - serialisering og deserialisering gir samme objekt`() {
        val original = JournalpostTilgangRequest(
            journalpostId = 123456789L,
            avklaringsbehovKode = "5001",
            påkrevdRolle = listOf(Rolle.BESLUTTER),
            operasjon = Operasjon.SAKSBEHANDLE,
        )

        val json = objectMapper.writeValueAsString(original)
        val deserialisert = objectMapper.readValue<JournalpostTilgangRequest>(json)

        assertThat(deserialisert).isEqualTo(original)
    }

    @Test
    fun `PersonTilgangRequest - serialisering og deserialisering gir samme objekt`() {
        val original = PersonTilgangRequest(personIdent = "12345678901")

        val json = objectMapper.writeValueAsString(original)
        val deserialisert = objectMapper.readValue<PersonTilgangRequest>(json)

        assertThat(deserialisert).isEqualTo(original)
    }

    @Test
    fun `alle Operasjon-verdier kan deserialiseres`() {
        Operasjon.entries.forEach { operasjon ->
            val json = """
                {
                    "saksnummer": "12345",
                    "operasjon": "${operasjon.name}"
                }
            """.trimIndent()

            val request = objectMapper.readValue<SakTilgangRequest>(json)

            assertThat(request.operasjon).isEqualTo(operasjon)
        }
    }

    @Test
    fun `alle Rolle-verdier kan deserialiseres`() {
        Rolle.entries.forEach { rolle ->
            val json = """
                {
                    "saksnummer": "12345",
                    "påkrevdRolle": ["${rolle.name}"],
                    "operasjon": "SE"
                }
            """.trimIndent()

            val request = objectMapper.readValue<SakTilgangRequest>(json)

            assertThat(request.påkrevdRolle).containsExactly(rolle)
        }
    }
}
