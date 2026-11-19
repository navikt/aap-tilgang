package tilgang

import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.tilgang.ArenasakTilgangRequest
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.PersonTilgangRequest
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
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytGateway
import tilgang.integrasjoner.saf.SafGraphqlGateway
import tilgang.integrasjoner.pdl.Gradering
import tilgang.integrasjoner.pdl.IPdlGraphQLGateway
import tilgang.integrasjoner.pdl.PersonResultat
import tilgang.integrasjoner.tilgangsmaskin.HarTilgangFraTilgangsmaskinen
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistGrunn
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistResponse
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import tilgang.regler.RegelInput
import tilgang.regler.RegelResultat
import tilgang.regler.RegelService
import tilgang.service.AdressebeskyttelseGruppe
import tilgang.service.AdressebeskyttelseService
import java.util.UUID
import kotlin.random.Random

class TilgangServiceTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            System.setProperty("FORTROLIG_ADRESSE_AD", UUID.randomUUID().toString())
            System.setProperty("STRENGT_FORTROLIG_ADRESSE_AD", UUID.randomUUID().toString())
        }
    }

    private val safGateway = mockk<SafGraphqlGateway>()
    private val behandlingsflytGateway = mockk<BehandlingsflytGateway>()
    private val regelService = mockk<RegelService>(relaxed = true)
    private val tilgangsmaskinGateway = mockk<TilgangsmaskinGateway>(relaxed = true)

    private val service = TilgangService(
        safGateway = safGateway,
        behandlingsflytGateway = behandlingsflytGateway,
        regelService = regelService,
        tilgangsmaskinGateway = tilgangsmaskinGateway
    )

    @AfterEach
    fun afterEach() {
        clearAllMocks()
    }

    @Nested
    inner class TilgangTilArenasakerMedRegler {
        private val pdlGateway = mockk<IPdlGraphQLGateway>()
        private val adressebeskyttelseService = mockk<AdressebeskyttelseService>()
        private val token = mockk<OidcToken>()
        private val personIdent = "12345678901"
        private val serviceMedRegler = TilgangService(
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
            coEvery { adressebeskyttelseService.hentAdressebeskyttelseRoller(token, "ansattIdent") } returns emptyList()
            coEvery {
                tilgangsmaskinGateway.harTilgangTilPersonKomplett(personIdent, token, "ansattIdent", "callId")
            } returns HarTilgangFraTilgangsmaskinen(true)

            val harTilgang = serviceMedRegler.harTilgangTilArensakerForPerson(
                "ansattIdent", ArenasakTilgangRequest(personIdent), token, listOf(rolle), "callId"
            )

            assertThat(harTilgang).isEqualTo(RegelResultat(forventetTilgang))
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
            coEvery { adressebeskyttelseService.hentAdressebeskyttelseRoller(token, "ansattIdent") } returns
                    listOfNotNull(adressebeskyttelseRolle)
            coEvery {
                tilgangsmaskinGateway.harTilgangTilPersonKomplett(personIdent, token, "ansattIdent", "callId")
            } returns HarTilgangFraTilgangsmaskinen(true)

            val harTilgang = serviceMedRegler.harTilgangTilArensakerForPerson(
                "ansattIdent", ArenasakTilgangRequest(personIdent), token, listOf(Rolle.LES), "callId"
            )

            assertThat(harTilgang).isEqualTo(RegelResultat(forventetTilgang))
            coVerify(exactly = if (forventetTilgang) 1 else 0) {
                tilgangsmaskinGateway.harTilgangTilPersonKomplett(personIdent, token, "ansattIdent", "callId")
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
            val avvistResponse = TilgangsmaskinAvvistResponse(
                type = "avvist",
                title = grunn.name,
                status = 403,
                navIdent = "ansattIdent",
                begrunnelse = "Ikke tilgang",
                kanOverstyres = false
            )
            coEvery { pdlGateway.hentPersonBolk(listOf(personIdent), "callId") } returns
                    listOf(PersonResultat(personIdent, listOf(Gradering.UGRADERT), "ok"))
            coEvery { adressebeskyttelseService.hentAdressebeskyttelseRoller(token, "ansattIdent") } returns emptyList()
            coEvery {
                tilgangsmaskinGateway.harTilgangTilPersonKomplett(personIdent, token, "ansattIdent", "callId")
            } returns HarTilgangFraTilgangsmaskinen(
                false,
                avvistResponse
            )

            val harTilgang = serviceMedRegler.harTilgangTilArensakerForPerson(
                "ansattIdent", ArenasakTilgangRequest(personIdent), token, listOf(Rolle.LES), "callId"
            )

            assertThat(harTilgang).isEqualTo(RegelResultat(false, avvistResponse))
            coVerify(exactly = 1) {
                tilgangsmaskinGateway.harTilgangTilPersonKomplett(personIdent, token, "ansattIdent", "callId")
            }
            coVerify(exactly = 0) {
                tilgangsmaskinGateway.harTilgangTilPersonKjerne(any(), any(), any(), any())
            }
        }
    }

    @Nested
    inner class TilgangTilArenasaker {

        @Test
        suspend fun `bruker leseroller og SE som standard og sender kontekst til regelservice`() {
            val req = ArenasakTilgangRequest(personIdent = "12345678901")
            val token = mockk<OidcToken>()
            val roller = listOf(Rolle.LES)
            val input = slot<RegelInput>()
            coEvery { regelService.vurderTilgang(capture(input)) } returns mapOf(Operasjon.SE to RegelResultat(true))

            val harTilgang = service.harTilgangTilArensakerForPerson("ansattIdent", req, token, roller, "callId")

            assertThat(harTilgang).isEqualTo(RegelResultat(true))
            assertThat(input.captured.callId).isEqualTo("callId")
            assertThat(input.captured.ansattIdent).isEqualTo("ansattIdent")
            assertThat(input.captured.currentToken).isSameAs(token)
            assertThat(input.captured.roller).containsExactly(Rolle.LES)
            assertThat(input.captured.søkerIdenter.søker).containsExactly(req.personIdent)
            assertThat(input.captured.søkerIdenter.barn).isEmpty()
            assertThat(input.captured.avklaringsbehovFraBehandlingsflyt).isNull()
            assertThat(input.captured.avklaringsbehovFraPostmottak).isNull()
            assertThat(input.captured.påkrevdRolle).containsExactly(
                Rolle.LES,
                Rolle.SAKSBEHANDLER_NASJONAL,
                Rolle.SAKSBEHANDLER_OPPFOLGING,
                Rolle.KVALITETSSIKRER,
                Rolle.BESLUTTER
            )
            assertThat(input.captured.operasjoner).containsExactly(Operasjon.SE)
            coVerify(exactly = 1) { regelService.vurderTilgang(any()) }
            coVerify {
                safGateway wasNot Called
                behandlingsflytGateway wasNot Called
                tilgangsmaskinGateway wasNot Called
            }
        }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        suspend fun `returnerer regelservice sitt resultat for SE`(harTilgang: Boolean) {
            coEvery { regelService.vurderTilgang(any()) } returns mapOf(Operasjon.SE to RegelResultat(harTilgang))

            val resultat = service.harTilgangTilArensakerForPerson(
                "ansattIdent", ArenasakTilgangRequest("12345678901"), mockk(), emptyList(), "callId"
            )

            assertThat(resultat).isEqualTo(RegelResultat(harTilgang))
        }

        @Test
        suspend fun `returnerer null når regelservice mangler resultat for SE`() {
            coEvery { regelService.vurderTilgang(any()) } returns emptyMap()

            val harTilgang = service.harTilgangTilArensakerForPerson(
                "ansattIdent", ArenasakTilgangRequest("12345678901"), mockk(), emptyList(), "callId"
            )

            assertThat(harTilgang).isNull()
        }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        suspend fun `bevarer eksplisitt angitte roller og operasjon`(tomRolleliste: Boolean) {
            val påkrevdRolle = if (tomRolleliste) emptyList() else listOf(Rolle.BESLUTTER)
            val req = ArenasakTilgangRequest("12345678901", påkrevdRolle, Operasjon.SE)
            val input = slot<RegelInput>()
            coEvery { regelService.vurderTilgang(capture(input)) } returns mapOf(Operasjon.SE to RegelResultat(true))

            service.harTilgangTilArensakerForPerson("ansattIdent", req, mockk(), emptyList(), "callId")

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
            val req = PersonTilgangRequest(personIdent, emptyList(), operasjon)

            service.harTilgangTilPerson("ansattIdent", req, mockk(), emptyList(), "callId")

            coVerify {
                regelService wasNot Called

                tilgangsmaskinGateway.harTilgangTilPerson(personIdent, any(), "callId")
            }
        }

        @Test
        suspend fun `harTilgangTilPerson - skal kun kalle RegelService hvis operasjon er DRIFTE`() {
            val personIdent = Random.nextLong().toString()
            val req = PersonTilgangRequest(personIdent, emptyList(), Operasjon.DRIFTE)

            service.harTilgangTilPerson("ansattIdent", req, mockk(), emptyList(), "callId")

            coVerify {
                regelService.vurderTilgang(any())
                tilgangsmaskinGateway wasNot Called
            }
        }

    }

}