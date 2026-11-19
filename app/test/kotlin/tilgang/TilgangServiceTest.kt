package tilgang

import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.RelevanteIdenter
import no.nav.aap.tilgang.Rolle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import tilgang.integrasjoner.pdl.Gradering
import tilgang.integrasjoner.pdl.IPdlGraphQLGateway
import tilgang.integrasjoner.pdl.PersonResultat
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytGateway
import tilgang.integrasjoner.saf.SafGraphqlGateway
import tilgang.integrasjoner.tilgangsmaskin.HarTilgangFraTilgangsmaskinen
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistGrunn
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistResponse
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import tilgang.regler.RegelService
import tilgang.service.AdressebeskyttelseGruppe
import tilgang.service.AdressebeskyttelseService
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertFailsWith

class TilgangServiceTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            System.setProperty("FORTROLIG_ADRESSE_AD", UUID.randomUUID().toString())
            System.setProperty("STRENGT_FORTROLIG_ADRESSE_AD", UUID.randomUUID().toString())
        }
    }

    private val journalpostOppslag = mockk<JournalpostOppslag>()
    private val identOppslag = mockk<IdentOppslag>()
    private val tilgangsvurdering = mockk<Tilgangsvurdering>(relaxed = true)
    private val persontilgang = mockk<Persontilgang>(relaxed = true)

    private val service = TilgangService(
        journalpostOppslag = journalpostOppslag,
        identOppslag = identOppslag,
        tilgangsvurdering = tilgangsvurdering,
        persontilgang = persontilgang
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
                "arenasaker" -> service.harTilgangTilArensakerForPerson(
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
    inner class TilgangTilArenasakerMedRegler {
        private val pdlGateway = mockk<IPdlGraphQLGateway>()
        private val adressebeskyttelseService = mockk<AdressebeskyttelseService>()
        private val tilgangsmaskinGateway = mockk<TilgangsmaskinGateway>()
        private val safGateway = mockk<SafGraphqlGateway>()
        private val behandlingsflytGateway = mockk<BehandlingsflytGateway>()
        private val token = BrukerToken(AzureTokenGen("issuer", "audience").generate())
        private val personIdent = "12345678901"
        private val serviceMedRegler = createTilgangService(
            safGateway,
            behandlingsflytGateway,
            RegelService(pdlGateway, adressebeskyttelseService, tilgangsmaskinGateway),
            tilgangsmaskinGateway
        )

        @ParameterizedTest
        @CsvSource(
            "LES, true",
            "SAKSBEHANDLER_NASJONAL, true",
            "SAKSBEHANDLER_OPPFOLGING, true",
            "KVALITETSSIKRER, true",
            "BESLUTTER, true",
            "DRIFT_LES, false",
            "DRIFT, false",
            "PRODUKSJONSSTYRING, false"
        )
        fun `sjekker hvilke roller som kan lese arenasaker`(rolle: Rolle, forventetTilgang: Boolean) = runTest {
            coEvery { pdlGateway.hentPersonBolk(listOf(personIdent), "callId") } returns
                    listOf(PersonResultat(personIdent, listOf(Gradering.UGRADERT), "ok"))
            coEvery {
                adressebeskyttelseService.hentAdressebeskyttelseRoller(
                    match { it.token() == token.verdi }, "ansattIdent"
                )
            } returns emptyList()
            coEvery {
                tilgangsmaskinGateway.harTilgangTilPersonKomplett(
                    personIdent, match { it.token() == token.verdi }, "ansattIdent", "callId"
                )
            } returns HarTilgangFraTilgangsmaskinen(true)

            val harTilgang = serviceMedRegler.harTilgangTilArensakerForPerson(
                ArenasakstilgangInput(personIdent, Rolle.kanLeseSaker(), Operasjon.SE),
                Tilgangskontekst("ansattIdent", listOf(rolle), token, "callId"),
            )

            assertThat(harTilgang).isEqualTo(forventetTilgang)
        }

        @ParameterizedTest
        @CsvSource(
            "UGRADERT, INGEN, true",
            "FORTROLIG, INGEN, false",
            "STRENGT_FORTROLIG, INGEN, false",
            "STRENGT_FORTROLIG_UTLAND, INGEN, false",
            "FORTROLIG, FORTROLIG_ADRESSE, true",
            "STRENGT_FORTROLIG, STRENGT_FORTROLIG_ADRESSE, true",
            "STRENGT_FORTROLIG_UTLAND, STRENGT_FORTROLIG_ADRESSE, true",
            "STRENGT_FORTROLIG, FORTROLIG_ADRESSE, false",
            nullValues = ["INGEN"]
        )
        fun `sjekker adressebeskyttelse for arenasaker`(
            gradering: Gradering,
            adressebeskyttelseRolle: AdressebeskyttelseGruppe?,
            forventetTilgang: Boolean
        ) = runTest {
            coEvery { pdlGateway.hentPersonBolk(listOf(personIdent), "callId") } returns
                    listOf(PersonResultat(personIdent, listOf(gradering), "ok"))
            coEvery {
                adressebeskyttelseService.hentAdressebeskyttelseRoller(
                    match { it.token() == token.verdi }, "ansattIdent"
                )
            } returns listOfNotNull(adressebeskyttelseRolle)
            coEvery {
                tilgangsmaskinGateway.harTilgangTilPersonKomplett(
                    personIdent, match { it.token() == token.verdi }, "ansattIdent", "callId"
                )
            } returns HarTilgangFraTilgangsmaskinen(true)

            val harTilgang = serviceMedRegler.harTilgangTilArensakerForPerson(
                ArenasakstilgangInput(personIdent, Rolle.kanLeseSaker(), Operasjon.SE),
                Tilgangskontekst("ansattIdent", listOf(Rolle.LES), token, "callId"),
            )

            assertThat(harTilgang).isEqualTo(forventetTilgang)
            coVerify(exactly = if (forventetTilgang) 1 else 0) {
                tilgangsmaskinGateway.harTilgangTilPersonKomplett(
                    personIdent, match { it.token() == token.verdi }, "ansattIdent", "callId"
                )
            }
            coVerify {
                safGateway wasNot Called
                behandlingsflytGateway wasNot Called
            }
        }

        @ParameterizedTest
        @EnumSource(TilgangsmaskinAvvistGrunn::class)
        fun `nekter tilgang til arenasaker ved avslag fra tilgangsmaskinen`(
            grunn: TilgangsmaskinAvvistGrunn
        ) = runTest {
            coEvery { pdlGateway.hentPersonBolk(listOf(personIdent), "callId") } returns
                    listOf(PersonResultat(personIdent, listOf(Gradering.UGRADERT), "ok"))
            coEvery {
                adressebeskyttelseService.hentAdressebeskyttelseRoller(
                    match { it.token() == token.verdi }, "ansattIdent"
                )
            } returns emptyList()
            coEvery {
                tilgangsmaskinGateway.harTilgangTilPersonKomplett(
                    personIdent, match { it.token() == token.verdi }, "ansattIdent", "callId"
                )
            } returns HarTilgangFraTilgangsmaskinen(
                false,
                TilgangsmaskinAvvistResponse(
                    type = "avvist",
                    title = grunn.name,
                    status = 403,
                    navIdent = "ansattIdent",
                    begrunnelse = "Ikke tilgang",
                    kanOverstyres = false
                )
            )

            val harTilgang = serviceMedRegler.harTilgangTilArensakerForPerson(
                ArenasakstilgangInput(personIdent, Rolle.kanLeseSaker(), Operasjon.SE),
                Tilgangskontekst("ansattIdent", listOf(Rolle.LES), token, "callId"),
            )

            assertThat(harTilgang).isFalse()
            coVerify(exactly = 1) {
                tilgangsmaskinGateway.harTilgangTilPersonKomplett(
                    personIdent, match { it.token() == token.verdi }, "ansattIdent", "callId"
                )
            }
            coVerify(exactly = 0) {
                tilgangsmaskinGateway.harTilgangTilPersonKjerne(any(), any(), any(), any())
            }
        }
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

            val harTilgang = service.harTilgangTilArensakerForPerson(req, kontekst)

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

            val resultat = service.harTilgangTilArensakerForPerson(
                ArenasakstilgangInput("12345678901", Rolle.kanLeseSaker(), Operasjon.SE),
                Tilgangskontekst("ansattIdent", emptyList(), BrukerToken("test-token"), "callId"),
            )

            assertThat(resultat).isEqualTo(harTilgang)
        }

        @Test
        suspend fun `nekter tilgang når regelservice mangler resultat for SE`() {
            coEvery { tilgangsvurdering.vurderTilgang(any()) } returns emptyMap()

            val harTilgang = service.harTilgangTilArensakerForPerson(
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

            service.harTilgangTilArensakerForPerson(
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