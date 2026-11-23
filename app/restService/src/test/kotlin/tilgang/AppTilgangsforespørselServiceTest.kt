package tilgang

import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import tilgang.kontroll.Operasjon
import tilgang.kontroll.RelevanteIdenter
import no.nav.aap.tilgang.RelevanteIdenter as KontraktRelevanteIdenter
import tilgang.kontroll.Rolle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytGateway
import tilgang.integrasjoner.saf.Bruker
import tilgang.integrasjoner.saf.BrukerIdType
import tilgang.integrasjoner.saf.SafGraphqlGateway
import tilgang.integrasjoner.saf.SafJournalpost
import tilgang.integrasjoner.saf.Sak
import tilgang.integrasjoner.saf.Sakstype
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import tilgang.grunnlag.AvklaringsbehovType
import tilgang.kontroll.BehandlingstilgangInput
import tilgang.kontroll.BrukerToken
import tilgang.kontroll.JournalposttilgangInput
import tilgang.kontroll.ManglerSøkerIdentException
import tilgang.kontroll.PersontilgangInput
import tilgang.kontroll.Tilgangskontekst
import tilgang.kontroll.Tilgangsgrunnlag
import tilgang.kontroll.TilgangService
import java.util.UUID
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon as PostmottakDefinisjon
import tilgang.mapping.tilDomene

class AppTilgangsforespørselServiceTest {
    private val saf = mockk<SafGraphqlGateway>()
    private val behandlingsflyt = mockk<BehandlingsflytGateway>()
    private val tilgangService = mockk<TilgangService>()
    private val tilgangsmaskin = mockk<TilgangsmaskinGateway>()
    private val service = createTilgangsforespørselService(saf, behandlingsflyt, tilgangService, tilgangsmaskin)
    private val token = BrukerToken(AzureTokenGen("issuer", "audience").generate())
    private val kontekst = Tilgangskontekst("ansatt", emptyList(), token, "call-id")

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
        val saksidenter = KontraktRelevanteIdenter(listOf("søker-ident"), listOf("barn-ident"))
        coEvery { behandlingsflyt.hentIdenterForSak("sak-1") } returns saksidenter
        val input = slot<Tilgangsgrunnlag>()
        coEvery { tilgangService.vurderTilgang(capture(input)) } returns mapOf(Operasjon.SE to false)

        val resultat = service.harTilgangTilJournalpost(
            JournalposttilgangInput(123, null, emptyList(), Operasjon.SE), kontekst
        )

        assertThat(resultat).isFalse()
        assertThat(input.captured.søkerIdenter).isEqualTo(
            if (brukerSaksidenter) saksidenter.tilDomene() else RelevanteIdenter(listOf("journalpost-ident"), emptyList())
        )
        assertThat(input.captured.kontekst).isSameAs(kontekst)
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
            JournalposttilgangInput(123, "ukjent", emptyList(), Operasjon.SE),
            kontekst,
        )

        assertThat(resultat).isTrue()
        coVerify {
            behandlingsflyt wasNot Called
            tilgangService wasNot Called
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

        assertFailsWith<ManglerSøkerIdentException> {
            service.harTilgangTilJournalpost(
                JournalposttilgangInput(123, null, emptyList(), Operasjon.SE), kontekst
            )
        }

        coVerify {
            behandlingsflyt wasNot Called
            tilgangService wasNot Called
        }
    }

    @Test
    fun `bevarer hele behandlingsgrunnlaget og regelresultatet`() = runTest {
        val referanse = UUID.randomUUID()
        val identer = KontraktRelevanteIdenter(listOf("søker"), listOf("barn"))
        val roller = listOf(Rolle.LES, Rolle.BESLUTTER)
        val påkrevdeRoller = listOf(Rolle.BESLUTTER)
        val input = slot<Tilgangsgrunnlag>()
        val forventet = mapOf(Operasjon.SE to true, Operasjon.SAKSBEHANDLE to false)
        coEvery { behandlingsflyt.hentIdenterForBehandling(referanse.toString()) } returns identer
        coEvery { tilgangService.vurderTilgang(capture(input)) } returns forventet
        val req = BehandlingstilgangInput(
            behandlingsreferanse = referanse,
            avklaringsbehovKode = "5003",
            påkrevdRolle = påkrevdeRoller,
            relevanteIdenter = null,
        )

        val resultat = service.harTilgangTilBehandling(req, Tilgangskontekst("ansatt", roller, token, "call-id"))

        assertThat(resultat).isEqualTo(forventet)
        assertThat(input.captured.kontekst.ansattIdent).isEqualTo("ansatt")
        assertThat(input.captured.kontekst.callId).isEqualTo("call-id")
        assertThat(input.captured.kontekst.brukerToken).isSameAs(token)
        assertThat(input.captured.kontekst.roller).containsExactlyElementsOf(roller)
        assertThat(input.captured.søkerIdenter).isEqualTo(identer.tilDomene())
        assertThat(input.captured.avklaringsbehovRoller).containsExactly(Definisjon.forKode("5003").løsesAv.tilDomene())
        assertThat(input.captured.påkrevdRolle).containsExactlyElementsOf(påkrevdeRoller)
        assertThat(input.captured.operasjoner).containsExactlyElementsOf(Operasjon.entries)
    }

    @Test
    fun `oversetter journalpostbehov til roller fra Postmottak`() = runTest {
        val definisjon = PostmottakDefinisjon.entries.first()
        val input = slot<Tilgangsgrunnlag>()
        coEvery { saf.hentJournalpostInfo(123, "call-id") } returns SafJournalpost(
            journalpostId = 123,
            bruker = Bruker("søker-ident", BrukerIdType.FNR),
        )
        coEvery { tilgangService.vurderTilgang(capture(input)) } returns mapOf(Operasjon.SAKSBEHANDLE to true)

        val resultat = service.harTilgangTilJournalpost(
            JournalposttilgangInput(
                journalpostId = 123,
                avklaringsbehovKode = definisjon.kode.name,
                påkrevdRolle = emptyList(),
                operasjon = Operasjon.SAKSBEHANDLE,
            ),
            kontekst,
        )

        assertThat(resultat).isTrue()
        assertThat(input.captured.avklaringsbehovRoller).containsExactly(definisjon.løsesAv.tilDomene())
    }

    @ParameterizedTest
    @EnumSource(AvklaringsbehovType::class)
    fun `bevarer feil for ukjent kode og avviser før regelvurdering`(type: AvklaringsbehovType) = runTest {
        val forventetFeil = assertFails {
            when (type) {
                AvklaringsbehovType.BEHANDLING -> Definisjon.forKode("ukjent")
                AvklaringsbehovType.JOURNALPOST -> PostmottakDefinisjon.forKode("ukjent")
            }
        }
        coEvery { saf.hentJournalpostInfo(123, "call-id") } returns SafJournalpost(
            journalpostId = 123,
            bruker = Bruker("søker-ident", BrukerIdType.FNR),
        )

        val feil = assertFails {
            when (type) {
                AvklaringsbehovType.BEHANDLING -> service.harTilgangTilBehandling(
                    BehandlingstilgangInput(
                        behandlingsreferanse = UUID.randomUUID(),
                        avklaringsbehovKode = "ukjent",
                        påkrevdRolle = emptyList(),
                        relevanteIdenter = RelevanteIdenter(listOf("søker-ident"), emptyList()),
                    ),
                    kontekst,
                )
                AvklaringsbehovType.JOURNALPOST -> service.harTilgangTilJournalpost(
                    JournalposttilgangInput(123, "ukjent", emptyList(), Operasjon.SE),
                    kontekst,
                )
            }
        }

        assertThat(feil).isExactlyInstanceOf(forventetFeil.javaClass).hasMessage(forventetFeil.message)
        coVerify { tilgangService wasNot Called }
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
            service.harTilgangTilPerson(
                PersontilgangInput("person-1", emptyList(), null),
                Tilgangskontekst("ansatt-1", emptyList(), token, "call-1"),
            )
        ).isTrue()
        assertThat(
            service.harTilgangTilPerson(
                PersontilgangInput("person-2", emptyList(), null),
                Tilgangskontekst("ansatt-2", emptyList(), annetToken, "call-2"),
            )
        ).isFalse()
        coVerify { tilgangService wasNot Called }
    }

}
