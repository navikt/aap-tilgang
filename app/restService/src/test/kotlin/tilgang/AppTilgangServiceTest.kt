package tilgang

import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import tilgang.kontroll.Operasjon
import tilgang.kontroll.RelevanteIdenter
import tilgang.kontroll.Rolle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import tilgang.integrasjoner.msgraph.AdressebeskyttelseService
import tilgang.integrasjoner.pdl.Gradering
import tilgang.integrasjoner.pdl.IPdlGraphQLGateway
import tilgang.integrasjoner.pdl.PersonResultat
import tilgang.integrasjoner.tilgangsmaskin.HarTilgangFraTilgangsmaskinen
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistResponse
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import tilgang.kontroll.BrukerToken
import tilgang.kontroll.Tilgangskontekst
import tilgang.kontroll.Tilgangsgrunnlag
import kotlin.test.assertFailsWith

class AppTilgangServiceTest {
    private val pdl = mockk<IPdlGraphQLGateway>()
    private val adressebeskyttelse = mockk<AdressebeskyttelseService>()
    private val tilgangsmaskin = mockk<TilgangsmaskinGateway>()
    private val service = createTilgangService(pdl, adressebeskyttelse, tilgangsmaskin)
    private val token = BrukerToken(AzureTokenGen("issuer", "audience").generate())
    private val kontekst = Tilgangskontekst("ansatt", listOf(Rolle.LES, Rolle.DRIFT), token, "call-id")
    private val identer = RelevanteIdenter(listOf("søker", "annen-søker"), listOf("søker", "barn"))

    @BeforeEach
    fun tillatOppslag() {
        coEvery { pdl.hentPersonBolk(any(), any()) } returns
                listOf(PersonResultat("søker", listOf(Gradering.UGRADERT), "ok"))
        coEvery { adressebeskyttelse.hentAdressebeskyttelseRoller(any(), any()) } returns emptyList()
        coEvery { tilgangsmaskin.harTilgangTilPersonKjerne(any(), any(), any(), any()) } returns
                HarTilgangFraTilgangsmaskinen(true)
        coEvery { tilgangsmaskin.harTilgangTilPersonKomplett(any(), any(), any(), any()) } returns
                HarTilgangFraTilgangsmaskinen(true)
    }

    @ParameterizedTest
    @EnumSource(Gradering::class)
    fun `oversetter graderinger for både søker og barn og bevarer kontekst`(gradering: Gradering) = runTest {
        coEvery { pdl.hentPersonBolk(any(), any()) } returns listOf(
            PersonResultat("søker", emptyList(), "ok"),
            PersonResultat("barn", listOf(gradering), "ok"),
        )

        val resultat = service.vurderTilgang(input(Operasjon.SE))

        assertThat(resultat[Operasjon.SE]).isEqualTo(gradering == Gradering.UGRADERT)
        coVerify(exactly = 1) {
            pdl.hentPersonBolk(listOf("søker", "annen-søker", "barn"), "call-id")
            adressebeskyttelse.hentAdressebeskyttelseRoller(match { it.token() == token.verdi }, "ansatt")
        }
        coVerify(exactly = if (gradering == Gradering.UGRADERT) 1 else 0) {
            tilgangsmaskin.harTilgangTilPersonKomplett("søker", any(), "ansatt", "call-id")
        }
    }

    @Test
    fun `manglende PDL-svar er feil og ikke et tomt adressegrunnlag`() = runTest {
        coEvery { pdl.hentPersonBolk(any(), any()) } returns null

        assertFailsWith<IllegalArgumentException> {
            service.vurderTilgang(input(Operasjon.SE))
        }

        coVerify { tilgangsmaskin wasNot Called }
    }

    @ParameterizedTest
    @CsvSource(
        "AVVIST_HABILITET, false",
        "AVVIST_GEOGRAFISK, true",
        "UKJENT, true",
        "NULL, true",
        nullValues = ["NULL"],
    )
    fun `bare habilitetsavslag nekter driftsoperasjoner og samme kontroll gjenbrukes`(
        grunn: String?,
        forventetTilgang: Boolean,
    ) = runTest {
        coEvery { tilgangsmaskin.harTilgangTilPersonKjerne(any(), any(), any(), any()) } returns
                HarTilgangFraTilgangsmaskinen(
                    false,
                    grunn?.let {
                        TilgangsmaskinAvvistResponse(
                            type = "avvist",
                            title = it,
                            status = 403,
                            navIdent = "ansatt",
                            begrunnelse = "Avvist",
                            kanOverstyres = false,
                        )
                    },
                )

        val resultat = service.vurderTilgang(input(Operasjon.DRIFTE, Operasjon.DRIFT_LES))

        assertThat(resultat).containsEntry(Operasjon.DRIFTE, forventetTilgang)
            .containsEntry(Operasjon.DRIFT_LES, forventetTilgang)
        coVerify(exactly = 1) {
            tilgangsmaskin.harTilgangTilPersonKjerne(
                "søker", match { it.token() == token.verdi }, "ansatt", "call-id"
            )
        }
        coVerify(exactly = 0) { tilgangsmaskin.harTilgangTilPersonKomplett(any(), any(), any(), any()) }
        coVerify {
            pdl wasNot Called
            adressebeskyttelse wasNot Called
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `bevarer personavslag og gjenbruker kontrollen mellom operasjoner`(harTilgang: Boolean) = runTest {
        coEvery { tilgangsmaskin.harTilgangTilPersonKomplett(any(), any(), any(), any()) } returns
                HarTilgangFraTilgangsmaskinen(harTilgang)

        val resultat = service.vurderTilgang(input(Operasjon.SE, Operasjon.SAKSBEHANDLE))

        assertThat(resultat).containsEntry(Operasjon.SE, harTilgang).containsEntry(Operasjon.SAKSBEHANDLE, harTilgang)
        coVerify(exactly = 1) {
            tilgangsmaskin.harTilgangTilPersonKomplett(
                "søker", match { it.token() == token.verdi }, "ansatt", "call-id"
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["personopplysninger", "roller", "habilitet", "persontilgang"])
    fun `oppslagsfeil blir ikke tilgang eller avslag`(behov: String) = runTest {
        val feil = IllegalStateException("Oppslaget feilet")
        when (behov) {
            "personopplysninger" -> coEvery { pdl.hentPersonBolk(any(), any()) } throws feil
            "roller" -> coEvery { adressebeskyttelse.hentAdressebeskyttelseRoller(any(), any()) } throws feil
            "habilitet" -> coEvery {
                tilgangsmaskin.harTilgangTilPersonKjerne(any(), any(), any(), any())
            } throws feil
            "persontilgang" -> coEvery {
                tilgangsmaskin.harTilgangTilPersonKomplett(any(), any(), any(), any())
            } throws feil
            else -> error("Ukjent behov: $behov")
        }
        val operasjon = if (behov == "habilitet") Operasjon.DRIFTE else Operasjon.SE

        val mottattFeil = assertFailsWith<IllegalStateException> {
            service.vurderTilgang(input(operasjon))
        }

        assertThat(mottattFeil).isExactlyInstanceOf(feil.javaClass).hasMessage(feil.message)
    }

    @Test
    fun `holder token ansattidentitet og callId atskilt mellom kall`() = runTest {
        val annetToken = BrukerToken(AzureTokenGen("issuer", "audience").generate())
        val kontekster = listOf(
            Tilgangskontekst("ansatt-1", listOf(Rolle.LES), token, "call-1"),
            Tilgangskontekst("ansatt-2", listOf(Rolle.LES), annetToken, "call-2"),
        )

        for ((indeks, kallkontekst) in kontekster.withIndex()) {
            coEvery {
                tilgangsmaskin.harTilgangTilPersonKomplett(
                    "søker",
                    match { it.token() == kallkontekst.brukerToken.verdi },
                    kallkontekst.ansattIdent,
                    kallkontekst.callId,
                )
            } returns HarTilgangFraTilgangsmaskinen(indeks == 0)

            val resultat = service.vurderTilgang(
                Tilgangsgrunnlag(kallkontekst, identer, operasjoner = listOf(Operasjon.SE))
            )

            assertThat(resultat[Operasjon.SE]).isEqualTo(indeks == 0)
            coVerify(exactly = 1) {
                pdl.hentPersonBolk(listOf("søker", "annen-søker", "barn"), kallkontekst.callId)
                adressebeskyttelse.hentAdressebeskyttelseRoller(
                    match { it.token() == kallkontekst.brukerToken.verdi }, kallkontekst.ansattIdent
                )
            }
        }
    }

    private fun input(vararg operasjoner: Operasjon): Tilgangsgrunnlag = Tilgangsgrunnlag(
        kontekst = kontekst,
        søkerIdenter = identer,
        påkrevdRolle = listOf(Rolle.LES),
        operasjoner = operasjoner.toList(),
    )
}
