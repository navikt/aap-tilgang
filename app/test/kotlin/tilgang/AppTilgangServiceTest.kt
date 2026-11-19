package tilgang

import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.tilgang.BehandlingTilgangRequest
import no.nav.aap.tilgang.JournalpostTilgangRequest
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.PersonTilgangRequest
import no.nav.aap.tilgang.RelevanteIdenter
import no.nav.aap.tilgang.Rolle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytGateway
import tilgang.integrasjoner.saf.Bruker
import tilgang.integrasjoner.saf.BrukerIdType
import tilgang.integrasjoner.saf.SafGraphqlGateway
import tilgang.integrasjoner.saf.SafJournalpost
import tilgang.integrasjoner.saf.Sak
import tilgang.integrasjoner.saf.Sakstype
import tilgang.integrasjoner.tilgangsmaskin.BrukerOgRegeltype
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import tilgang.regler.RegelInput
import tilgang.regler.RegelService
import java.util.UUID
import kotlin.test.assertFailsWith

class AppTilgangServiceTest {
    private val saf = mockk<SafGraphqlGateway>()
    private val behandlingsflyt = mockk<BehandlingsflytGateway>()
    private val regelService = mockk<RegelService>()
    private val tilgangsmaskin = mockk<TilgangsmaskinGateway>()
    private val service = createTilgangService(saf, behandlingsflyt, regelService, tilgangsmaskin)
    private val token = BrukerToken(AzureTokenGen("issuer", "audience").generate())

    @ParameterizedTest
    @CsvSource("KELVIN, true", "ANNET, false", "NULL, false", nullValues = ["NULL"])
    fun `oversetter journalpost og bruker saksidenter bare for Kelvin`(
        fagsystem: String?,
        brukerSaksidenter: Boolean,
    ) = runTest {
        coEvery { saf.hentJournalpostInfo(123, "call-id") } returns SafJournalpost(
            journalpostId = 123,
            bruker = Bruker("journalpost-ident", BrukerIdType.FNR),
            sak = fagsystem?.let { Sak("sak-1", it, Sakstype.FAGSAK) },
        )
        val saksidenter = RelevanteIdenter(listOf("søker-ident"), listOf("barn-ident"))
        coEvery { behandlingsflyt.hentIdenterForSak("sak-1") } returns saksidenter
        val input = slot<RegelInput>()
        coEvery { regelService.vurderTilgang(capture(input)) } returns mapOf(Operasjon.SE to false)

        val resultat = service.harTilgangTilJournalpost(
            "ansatt", JournalpostTilgangRequest(123, operasjon = Operasjon.SE), emptyList(), token, "call-id"
        )

        assertThat(resultat).isFalse()
        assertThat(input.captured.søkerIdenter).isEqualTo(
            if (brukerSaksidenter) saksidenter else RelevanteIdenter(listOf("journalpost-ident"), emptyList())
        )
        assertThat(input.captured.currentToken.token()).isEqualTo(token.verdi)
        assertThat(input.captured.callId).isEqualTo("call-id")
        coVerify(exactly = if (brukerSaksidenter) 1 else 0) {
            behandlingsflyt.hentIdenterForSak(any())
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["KELVIN", "ANNET"])
    fun `bevarer organisasjonsunntaket uten identoppslag eller regelvurdering`(fagsystem: String) = runTest {
        coEvery { saf.hentJournalpostInfo(123, "call-id") } returns SafJournalpost(
            journalpostId = 123,
            bruker = Bruker(type = BrukerIdType.ORGNR),
            sak = Sak("sak-1", fagsystem, Sakstype.FAGSAK),
        )

        val resultat = service.harTilgangTilJournalpost(
            "ansatt", JournalpostTilgangRequest(123, operasjon = Operasjon.SE), emptyList(), token, "call-id"
        )

        assertThat(resultat).isTrue()
        coVerify {
            behandlingsflyt wasNot Called
            regelService wasNot Called
            tilgangsmaskin wasNot Called
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `avviser manglende identgrunnlag etter oversetting`(kelvinsak: Boolean) = runTest {
        coEvery { saf.hentJournalpostInfo(123, "call-id") } returns SafJournalpost(
            journalpostId = 123,
            sak = if (kelvinsak) Sak(null, "KELVIN", Sakstype.FAGSAK) else null,
        )

        assertFailsWith<IllegalArgumentException> {
            service.harTilgangTilJournalpost(
                "ansatt", JournalpostTilgangRequest(123, operasjon = Operasjon.SE), emptyList(), token, "call-id"
            )
        }

        coVerify {
            behandlingsflyt wasNot Called
            regelService wasNot Called
        }
    }

    @Test
    fun `bevarer hele behandlingsgrunnlaget og regelresultatet`() = runTest {
        val referanse = UUID.randomUUID()
        val identer = RelevanteIdenter(listOf("søker"), listOf("barn"))
        val roller = listOf(Rolle.LES, Rolle.BESLUTTER)
        val påkrevdeRoller = listOf(Rolle.BESLUTTER)
        val input = slot<RegelInput>()
        val forventet = mapOf(Operasjon.SE to true, Operasjon.SAKSBEHANDLE to false)
        coEvery { behandlingsflyt.hentIdenterForBehandling(referanse.toString()) } returns identer
        coEvery { regelService.vurderTilgang(capture(input)) } returns forventet
        val req = BehandlingTilgangRequest(
            behandlingsreferanse = referanse,
            avklaringsbehovKode = "5003",
            påkrevdRolle = påkrevdeRoller,
            operasjon = Operasjon.SAKSBEHANDLE,
        )

        val resultat = service.harTilgangTilBehandling("ansatt", req, roller, token, "call-id")

        assertThat(resultat).isEqualTo(forventet)
        assertThat(input.captured.ansattIdent).isEqualTo("ansatt")
        assertThat(input.captured.callId).isEqualTo("call-id")
        assertThat(input.captured.currentToken.token()).isEqualTo(token.verdi)
        assertThat(input.captured.roller).containsExactlyElementsOf(roller)
        assertThat(input.captured.søkerIdenter).isEqualTo(identer)
        assertThat(input.captured.avklaringsbehovFraBehandlingsflyt).isEqualTo(Definisjon.forKode("5003"))
        assertThat(input.captured.avklaringsbehovFraPostmottak).isNull()
        assertThat(input.captured.påkrevdRolle).containsExactlyElementsOf(påkrevdeRoller)
        assertThat(input.captured.operasjoner).containsExactlyElementsOf(Operasjon.entries)
    }

    @Test
    fun `holder token per personkall og returnerer tilgang og avslag`() = runTest {
        val annetToken = BrukerToken(AzureTokenGen("issuer", "audience").generate())
        coEvery {
            tilgangsmaskin.harTilgangTilPerson("person-1", match { it.token() == token.verdi }, "call-1")
        } returns true
        coEvery {
            tilgangsmaskin.harTilgangTilPerson("person-2", match { it.token() == annetToken.verdi }, "call-2")
        } returns false

        assertThat(
            service.harTilgangTilPerson("ansatt-1", PersonTilgangRequest("person-1"), token, emptyList(), "call-1")
        ).isTrue()
        assertThat(
            service.harTilgangTilPerson("ansatt-2", PersonTilgangRequest("person-2"), annetToken, emptyList(), "call-2")
        ).isFalse()
        coVerify { regelService wasNot Called }
    }

    @Test
    fun `oversetter personregler og token for bulk og bevarer avslag`() = runTest {
        val forventet = listOf(BrukerOgRegeltype("person-1", "KJERNE"), BrukerOgRegeltype("person-2", "KOMPLETT"))
        coEvery {
            tilgangsmaskin.harTilganger(forventet, match { it.token() == token.verdi }, "call-id")
        } returns false

        val resultat = service.harTilgangFraTilgangsmaskin(
            listOf(Personregel("person-1", "KJERNE"), Personregel("person-2", "KOMPLETT")),
            token,
            "call-id",
        )

        assertThat(resultat).isFalse()
        coVerify(exactly = 1) {
            tilgangsmaskin.harTilganger(forventet, match { it.token() == token.verdi }, "call-id")
        }
    }
}
