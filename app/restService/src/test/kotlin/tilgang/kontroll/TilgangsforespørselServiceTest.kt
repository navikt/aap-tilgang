package tilgang.kontroll

import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verifySequence
import kotlinx.coroutines.test.runTest
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.RelevanteIdenter
import no.nav.aap.tilgang.Rolle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import tilgang.foresporsel.Persontilgang
import tilgang.foresporsel.TilgangsforespørselService
import tilgang.grunnlag.IdentOppslag
import tilgang.grunnlag.JournalpostInfo
import tilgang.grunnlag.JournalpostOppslag
import tilgang.grunnlag.TilgangsgrunnlagService
import tilgang.logging.ILogger
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertFailsWith

class TilgangsforespørselServiceTest {

    private val journalpostOppslag = mockk<JournalpostOppslag>()
    private val identOppslag = mockk<IdentOppslag>()
    private val tilgangsvurdering = mockk<Tilgangsvurdering>(relaxed = true)
    private val persontilgang = mockk<Persontilgang>(relaxed = true)
    private val log = mockk<ILogger>(relaxUnitFun = true)

    private val service = TilgangsforespørselService(
        grunnlagService = TilgangsgrunnlagService(identOppslag, journalpostOppslag, log),
        tilgangService = TilgangService(tilgangsvurdering),
        persontilgang = persontilgang,
        log = log,
    )

    @AfterEach
    fun afterEach() {
        clearAllMocks()
    }

    @Test
    fun `krever rolle ved saksbehandling av tilbakekreving`() {
        val feil = assertFailsWith<IllegalArgumentException> {
            TilbakekrevingstilgangInput("sak-1", UUID.randomUUID(), emptyList(), Operasjon.SAKSBEHANDLE)
        }

        assertThat(feil).hasMessage("Påkrevd rolle må være satt for operasjon SAKSBEHANDLE")
    }

    @Test
    fun `sender info og organisasjonsadvarsel til injisert logger`() = runTest {
        val kontekst = Tilgangskontekst("ansatt", emptyList(), BrukerToken("test-token"), "call-id")
        coEvery { journalpostOppslag.hentJournalpostInfo(123, "call-id") } returns
                JournalpostInfo(123, null, true, null)

        val harTilgang = service.harTilgangTilJournalpost(
            JournalposttilgangInput(123, null, emptyList(), Operasjon.SE), kontekst
        )

        assertThat(harTilgang).isTrue()
        verifySequence {
            log.info("Sjekker tilgang til journalpost 123")
            log.warn("Journalpost 123 har orgnr som bruker – tilgang vil vurderes som OK.")
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `gir domenefeil når journalpost mangler søkeridenter`(harSaksnummer: Boolean) = runTest {
        val kontekst = Tilgangskontekst("ansatt", emptyList(), BrukerToken("test-token"), "call-id")
        coEvery { journalpostOppslag.hentJournalpostInfo(123, "call-id") } returns
                JournalpostInfo(123, null, false, if (harSaksnummer) "sak-1" else null)
        if (harSaksnummer) {
            coEvery { identOppslag.hentIdenterForSak("sak-1") } returns
                    mockk { every { søker } returns emptyList() }
        }

        val feil = assertFailsWith<ManglerSøkerIdentException> {
            service.harTilgangTilJournalpost(
                JournalposttilgangInput(123, null, emptyList(), Operasjon.SE), kontekst
            )
        }

        assertThat(feil).hasMessage(
            if (harSaksnummer) "Fant ingen søkeridenter for sak sak-1"
            else "Fant ingen ident på søker i journalposten"
        )
        if (harSaksnummer) {
            coVerify(exactly = 1) { identOppslag.hentIdenterForSak("sak-1") }
        } else {
            coVerify { identOppslag wasNot Called }
        }
        coVerify {
            tilgangsvurdering wasNot Called
            persontilgang wasNot Called
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["sak", "behandling", "journalpost", "arenasaker", "person", "tilbakekreving"])
    fun `sender riktig kallkontekst til hver regelvurdering`(ressurs: String) = runTest {
        val input = slot<Tilgangsgrunnlag>()
        val identer = RelevanteIdenter(listOf("søker-ident"), emptyList())
        coEvery { identOppslag.hentIdenterForSak("sak-1") } returns identer
        coEvery { tilgangsvurdering.vurderTilgang(capture(input)) } returns
                mapOf(Operasjon.SE to true, Operasjon.DRIFTE to true)

        for (indeks in 1..2) {
            val kontekst = Tilgangskontekst(
                ansattIdent = "ansatt-$indeks",
                roller = if (indeks == 1) listOf(Rolle.LES) else listOf(Rolle.DRIFT),
                brukerToken = BrukerToken("token-$indeks"),
                callId = "call-$indeks",
            )
            coEvery { journalpostOppslag.hentJournalpostInfo(123, kontekst.callId) } returns
                    JournalpostInfo(123, "søker-ident", false, null)

            when (ressurs) {
                "sak" -> service.harTilgangTilSak(
                    SakstilgangInput("sak-1", emptyList(), Operasjon.SE, identer), kontekst
                )
                "behandling" -> service.harTilgangTilBehandling(
                    BehandlingstilgangInput(UUID.randomUUID(), null, emptyList(), identer), kontekst
                )
                "journalpost" -> {
                    service.harTilgangTilJournalpost(
                        JournalposttilgangInput(123, null, emptyList(), Operasjon.SE), kontekst
                    )
                    coVerify(exactly = 1) { journalpostOppslag.hentJournalpostInfo(123, kontekst.callId) }
                }
                "arenasaker" -> service.harTilgangTilArenasakerForPerson(
                    ArenasakstilgangInput("søker-ident", Rolle.kanLeseSaker(), Operasjon.SE), kontekst
                )
                "person" -> service.harTilgangTilPerson(
                    PersontilgangInput("søker-ident", emptyList(), Operasjon.DRIFTE), kontekst
                )
                "tilbakekreving" -> service.harTilgangTilTilbakekreving(
                    TilbakekrevingstilgangInput("sak-1", UUID.randomUUID(), emptyList(), Operasjon.SE), kontekst
                )
                else -> error("Ukjent ressurs: $ressurs")
            }

            assertThat(input.captured.kontekst).isSameAs(kontekst)
        }
    }

    @ParameterizedTest
    @EnumSource(AvklaringsbehovType::class)
    fun `sender kode og behovstype videre uten eksterne definisjoner`(type: AvklaringsbehovType) = runTest {
        val input = slot<Tilgangsgrunnlag>()
        val kontekst = Tilgangskontekst("ansatt", emptyList(), BrukerToken("test-token"), "call-id")
        coEvery { tilgangsvurdering.vurderTilgang(capture(input)) } returns mapOf(Operasjon.SE to true)
        coEvery { journalpostOppslag.hentJournalpostInfo(123, "call-id") } returns
                JournalpostInfo(123, "søker-ident", false, null)

        val resultat = when (type) {
            AvklaringsbehovType.BEHANDLING -> service.harTilgangTilBehandling(
                BehandlingstilgangInput(
                    behandlingsreferanse = UUID.randomUUID(),
                    avklaringsbehovKode = "kode-fra-klient",
                    påkrevdRolle = emptyList(),
                    relevanteIdenter = RelevanteIdenter(listOf("søker-ident"), emptyList()),
                ),
                kontekst,
            )[Operasjon.SE]
            AvklaringsbehovType.JOURNALPOST -> service.harTilgangTilJournalpost(
                JournalposttilgangInput(123, "kode-fra-klient", emptyList(), Operasjon.SE),
                kontekst,
            )
        }

        assertThat(resultat).isTrue()
        assertThat(input.captured.avklaringsbehov).isEqualTo(Avklaringsbehov("kode-fra-klient", type))
    }

    @Nested
    inner class TilgangTilArenasaker {

        @Test
        suspend fun `sender leseroller og SE sammen med kontekst til regelservice`() {
            val req = ArenasakstilgangInput("12345678901", Rolle.kanLeseSaker(), Operasjon.SE)
            val token = BrukerToken("test-token")
            val roller = listOf(Rolle.LES)
            val kontekst = Tilgangskontekst("ansattIdent", roller, token, "callId")
            val input = slot<Tilgangsgrunnlag>()
            coEvery { tilgangsvurdering.vurderTilgang(capture(input)) } returns mapOf(Operasjon.SE to true)

            val harTilgang = service.harTilgangTilArenasakerForPerson(req, kontekst)

            assertThat(harTilgang).isTrue()
            assertThat(input.captured.kontekst).isSameAs(kontekst)
            assertThat(input.captured.søkerIdenter.søker).containsExactly(req.personIdent)
            assertThat(input.captured.søkerIdenter.barn).isEmpty()
            assertThat(input.captured.avklaringsbehov).isNull()
            assertThat(input.captured.påkrevdRolle).containsExactly(
                Rolle.LES,
                Rolle.SAKSBEHANDLER_NASJONAL,
                Rolle.SAKSBEHANDLER_OPPFOLGING,
                Rolle.KVALITETSSIKRER,
                Rolle.BESLUTTER
            )
            assertThat(input.captured.operasjoner).containsExactly(Operasjon.SE)
            coVerify(exactly = 1) { tilgangsvurdering.vurderTilgang(any()) }
            coVerify {
                journalpostOppslag wasNot Called
                identOppslag wasNot Called
                persontilgang wasNot Called
            }
        }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        suspend fun `returnerer regelservice sitt resultat for SE`(harTilgang: Boolean) {
            coEvery { tilgangsvurdering.vurderTilgang(any()) } returns mapOf(Operasjon.SE to harTilgang)

            val resultat = service.harTilgangTilArenasakerForPerson(
                ArenasakstilgangInput("12345678901", Rolle.kanLeseSaker(), Operasjon.SE),
                Tilgangskontekst("ansattIdent", emptyList(), BrukerToken("test-token"), "callId"),
            )

            assertThat(resultat).isEqualTo(harTilgang)
        }

        @Test
        suspend fun `nekter tilgang når regelservice mangler resultat for SE`() {
            coEvery { tilgangsvurdering.vurderTilgang(any()) } returns emptyMap()

            val harTilgang = service.harTilgangTilArenasakerForPerson(
                ArenasakstilgangInput("12345678901", Rolle.kanLeseSaker(), Operasjon.SE),
                Tilgangskontekst("ansattIdent", emptyList(), BrukerToken("test-token"), "callId"),
            )

            assertThat(harTilgang).isFalse()
        }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        suspend fun `bevarer eksplisitt angitte roller og operasjon`(tomRolleliste: Boolean) {
            val påkrevdRolle = if (tomRolleliste) emptyList() else listOf(Rolle.BESLUTTER)
            val req = ArenasakstilgangInput("12345678901", påkrevdRolle, Operasjon.SE)
            val input = slot<Tilgangsgrunnlag>()
            coEvery { tilgangsvurdering.vurderTilgang(capture(input)) } returns mapOf(Operasjon.SE to true)

            service.harTilgangTilArenasakerForPerson(
                req, Tilgangskontekst("ansattIdent", emptyList(), BrukerToken("test-token"), "callId")
            )

            assertThat(input.captured.påkrevdRolle).isEqualTo(påkrevdRolle)
            assertThat(input.captured.operasjoner).containsExactly(Operasjon.SE)
        }
    }

    @Nested
    inner class TilgangTilPerson {

        @ParameterizedTest
        @EnumSource(value = Operasjon::class, names = ["DRIFTE", "DRIFT_LES"], mode = EnumSource.Mode.EXCLUDE)
        suspend fun `harTilgangTilPerson - skal kalle tilgangsmaskin hvis operasjon ikke er DRIFTE eller DRIFT_LES`(operasjon: Operasjon) {
            val personIdent = Random.nextLong().toString()
            val req = PersontilgangInput(personIdent, emptyList(), operasjon)

            service.harTilgangTilPerson(
                req, Tilgangskontekst("ansattIdent", emptyList(), BrukerToken("test-token"), "callId")
            )

            coVerify {
                tilgangsvurdering wasNot Called

                persontilgang.harTilgangTilPerson(personIdent, any(), "callId")
            }
        }

        @Test
        suspend fun `harTilgangTilPerson - skal kun kalle RegelService hvis operasjon er DRIFTE`() {
            val personIdent = Random.nextLong().toString()
            val req = PersontilgangInput(personIdent, emptyList(), Operasjon.DRIFTE)

            service.harTilgangTilPerson(
                req, Tilgangskontekst("ansattIdent", emptyList(), BrukerToken("test-token"), "callId")
            )

            coVerify {
                tilgangsvurdering.vurderTilgang(any())
                persontilgang wasNot Called
            }
        }

    }

}