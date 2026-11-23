package tilgang.kontroll

import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import tilgang.AzureTokenGen
import tilgang.createTilgangService
import tilgang.createTilgangsforespørselService
import tilgang.createTilgangService
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytGateway
import tilgang.integrasjoner.msgraph.AdressebeskyttelseGruppe
import tilgang.integrasjoner.msgraph.AdressebeskyttelseService
import tilgang.integrasjoner.pdl.Gradering
import tilgang.integrasjoner.pdl.IPdlGraphQLGateway
import tilgang.integrasjoner.pdl.PersonResultat
import tilgang.integrasjoner.saf.SafGraphqlGateway
import tilgang.integrasjoner.tilgangsmaskin.HarTilgangFraTilgangsmaskinen
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistGrunn
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistResponse
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import java.util.UUID
import no.nav.aap.tilgang.Rolle as KontraktRolle
import tilgang.mapping.tilDomene

class TilgangServiceMedReglerTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            System.setProperty("FORTROLIG_ADRESSE_AD", UUID.randomUUID().toString())
            System.setProperty("STRENGT_FORTROLIG_ADRESSE_AD", UUID.randomUUID().toString())
        }
    }

    private val pdlGateway = mockk<IPdlGraphQLGateway>()
    private val adressebeskyttelseService = mockk<AdressebeskyttelseService>()
    private val tilgangsmaskinGateway = mockk<TilgangsmaskinGateway>()
    private val safGateway = mockk<SafGraphqlGateway>()
    private val behandlingsflytGateway = mockk<BehandlingsflytGateway>()
    private val token = BrukerToken(AzureTokenGen("issuer", "audience").generate())
    private val personIdent = "12345678901"
    private val serviceMedRegler = createTilgangsforespørselService(
        safGateway,
        behandlingsflytGateway,
        createTilgangService(pdlGateway, adressebeskyttelseService, tilgangsmaskinGateway),
        tilgangsmaskinGateway
    )

    @AfterEach
    fun afterEach() {
        clearAllMocks()
    }

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

        val harTilgang = serviceMedRegler.harTilgangTilArenasakerForPerson(
            ArenasakstilgangInput(personIdent, KontraktRolle.kanLeseSaker().tilDomene(), Operasjon.SE),
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

        val harTilgang = serviceMedRegler.harTilgangTilArenasakerForPerson(
            ArenasakstilgangInput(personIdent, KontraktRolle.kanLeseSaker().tilDomene(), Operasjon.SE),
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

        val harTilgang = serviceMedRegler.harTilgangTilArenasakerForPerson(
            ArenasakstilgangInput(personIdent, KontraktRolle.kanLeseSaker().tilDomene(), Operasjon.SE),
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
