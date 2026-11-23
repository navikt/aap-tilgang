package tilgang.regler

import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.tilgang.Avvisning
import no.nav.aap.tilgang.BegrunnetTilgangsResponse
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.RelevanteIdenter
import no.nav.aap.tilgang.Rolle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import tilgang.integrasjoner.pdl.Gradering
import tilgang.integrasjoner.pdl.IPdlGraphQLGateway
import tilgang.integrasjoner.pdl.PersonResultat
import tilgang.integrasjoner.tilgangsmaskin.HarTilgangFraTilgangsmaskinen
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistGrunn
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistResponse
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import tilgang.service.AdressebeskyttelseService

class RegelResultatTest {
    private val pdlGateway = mockk<IPdlGraphQLGateway>()
    private val adressebeskyttelseService = mockk<AdressebeskyttelseService>()
    private val tilgangsmaskinGateway = mockk<TilgangsmaskinGateway>()
    private val regelService = RegelService(pdlGateway, adressebeskyttelseService, tilgangsmaskinGateway)
    private val input = RegelInput(
        callId = "callId",
        ansattIdent = "ansattIdent",
        currentToken = mockk<OidcToken>(),
        roller = listOf(Rolle.LES, Rolle.DRIFT, Rolle.DRIFT_LES),
        søkerIdenter = RelevanteIdenter(listOf("123"), emptyList()),
        avklaringsbehovFraBehandlingsflyt = null,
        avklaringsbehovFraPostmottak = null,
        påkrevdRolle = listOf(Rolle.LES),
        operasjoner = Operasjon.entries,
    )

    init {
        coEvery { pdlGateway.hentPersonBolk(listOf("123"), input.callId) } returns
                listOf(PersonResultat("123", listOf(Gradering.UGRADERT), "ok"))
        coEvery {
            adressebeskyttelseService.hentAdressebeskyttelseRoller(input.currentToken, input.ansattIdent)
        } returns emptyList()
    }

    @ParameterizedTest
    @EnumSource(TilgangsmaskinAvvistGrunn::class)
    fun `preserves denial responses for all affected operations`(grunn: TilgangsmaskinAvvistGrunn) = runTest {
        val avvist = TilgangsmaskinAvvistResponse(
            type = "avvist",
            title = grunn.name,
            status = 403,
            navIdent = input.ansattIdent,
            begrunnelse = "Ikke tilgang",
            kanOverstyres = false,
        )
        mockTilgangsmaskin(HarTilgangFraTilgangsmaskinen(false, avvist))

        val resultat = regelService.vurderTilgang(input)

        for (operasjon in listOf(Operasjon.SE, Operasjon.SAKSBEHANDLE)) {
            assertThat(resultat[operasjon]).isEqualTo(RegelResultat(false, avvist))
            assertThat(resultat[operasjon]?.tilgangsmaskinAvvistResponse).isSameAs(avvist)
        }
        val forventetHabilitet = if (grunn == TilgangsmaskinAvvistGrunn.AVVIST_HABILITET) {
            RegelResultat(false, avvist)
        } else {
            RegelResultat(true)
        }
        assertThat(resultat[Operasjon.DRIFTE]).isEqualTo(forventetHabilitet)
        assertThat(resultat[Operasjon.DRIFT_LES]).isEqualTo(forventetHabilitet)
        coVerify(exactly = 1) { tilgangsmaskinGateway.harTilgangTilPersonKomplett(any(), any(), any(), any()) }
        coVerify(exactly = 1) { tilgangsmaskinGateway.harTilgangTilPersonKjerne(any(), any(), any(), any()) }
    }

    @Test
    fun `granted access has no denial response`() = runTest {
        mockTilgangsmaskin(HarTilgangFraTilgangsmaskinen(true))

        val resultat = regelService.vurderTilgang(input)

        for (operasjon in Operasjon.entries.filterNot { it == Operasjon.DELEGERE }) {
            assertThat(resultat[operasjon]).isEqualTo(RegelResultat(true))
        }
        assertThat(resultat[Operasjon.DELEGERE]).isEqualTo(RegelResultat(false))
    }

    @Test
    fun `role denial short circuits without calling gateways`() = runTest {
        val inputUtenRoller = RegelInput(
            callId = input.callId,
            ansattIdent = input.ansattIdent,
            currentToken = input.currentToken,
            roller = emptyList(),
            søkerIdenter = input.søkerIdenter,
            avklaringsbehovFraBehandlingsflyt = null,
            avklaringsbehovFraPostmottak = null,
            operasjoner = Operasjon.entries,
        )

        val resultat = regelService.vurderTilgang(inputUtenRoller)

        assertThat(resultat.keys).containsExactlyInAnyOrderElementsOf(Operasjon.entries)
        assertThat(resultat.values).containsOnly(RegelResultat(false))
        coVerify {
            tilgangsmaskinGateway wasNot Called
            pdlGateway wasNot Called
            adressebeskyttelseService wasNot Called
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `maps access results without denial details to the contract`(harTilgang: Boolean) {
        assertThat(RegelResultat(harTilgang).tilKontrakt()).isEqualTo(
            BegrunnetTilgangsResponse(tilgang = harTilgang)
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `maps only public denial fields to the contract`(kanOverstyres: Boolean) {
        val avvist = TilgangsmaskinAvvistResponse(
            type = "upstream-type",
            title = "AVVIST_GEOGRAFISK",
            status = 403,
            navIdent = "ansattIdent",
            begrunnelse = "Ikke tilgang",
            kanOverstyres = kanOverstyres,
        )

        val kontrakt = RegelResultat(false, avvist).tilKontrakt()

        assertThat(kontrakt).isEqualTo(
            BegrunnetTilgangsResponse(
                tilgang = false,
                avvistResponse = Avvisning(avvist.title, avvist.begrunnelse, kanOverstyres)
            )
        )
        val mapper = DefaultJsonMapper.objectMapper()
        val json = mapper.readTree(mapper.writeValueAsString(kontrakt))
        assertThat(json.path("avvistResponse").fieldNames().asSequence().toList()).containsExactlyInAnyOrder(
            "title", "begrunnelse", "kanOverstyres"
        )
        assertThat(json.has("navIdent") || json.has("type") || json.has("status")).isFalse()
    }

    @ParameterizedTest
    @EnumSource(Operasjon::class)
    fun `evaluates only the requested operation`(operasjon: Operasjon) = runTest {
        mockTilgangsmaskin(HarTilgangFraTilgangsmaskinen(true))
        val filtrertInput = medOperasjoner(listOf(operasjon))

        val resultat = regelService.vurderTilgang(filtrertInput)

        assertThat(resultat.keys).containsExactly(operasjon)
        assertThat(resultat[operasjon]).isEqualTo(RegelResultat(operasjon != Operasjon.DELEGERE))
        coVerify(exactly = if (operasjon in listOf(Operasjon.SE, Operasjon.SAKSBEHANDLE)) 1 else 0) {
            tilgangsmaskinGateway.harTilgangTilPersonKomplett(any(), any(), any(), any())
        }
        coVerify(exactly = if (operasjon in listOf(Operasjon.DRIFTE, Operasjon.DRIFT_LES)) 1 else 0) {
            tilgangsmaskinGateway.harTilgangTilPersonKjerne(any(), any(), any(), any())
        }
    }

    @Test
    fun `empty operation selection does not evaluate rules`() = runTest {
        assertThat(regelService.vurderTilgang(medOperasjoner(emptyList()))).isEmpty()
        coVerify {
            tilgangsmaskinGateway wasNot Called
            pdlGateway wasNot Called
            adressebeskyttelseService wasNot Called
        }
    }

    @Test
    fun `rule results are not cached between access evaluations`() = runTest {
        val avvist = TilgangsmaskinAvvistResponse(
            type = "avvist",
            title = "AVVIST_HABILITET",
            status = 403,
            navIdent = input.ansattIdent,
            begrunnelse = "Inhabil",
            kanOverstyres = false,
        )
        coEvery {
            tilgangsmaskinGateway.harTilgangTilPersonKomplett(any(), any(), any(), any())
        } returnsMany listOf(HarTilgangFraTilgangsmaskinen(false, avvist), HarTilgangFraTilgangsmaskinen(true))
        coEvery {
            tilgangsmaskinGateway.harTilgangTilPersonKjerne(any(), any(), any(), any())
        } returnsMany listOf(HarTilgangFraTilgangsmaskinen(false, avvist), HarTilgangFraTilgangsmaskinen(true))

        val avslag = regelService.vurderTilgang(input)
        val innvilget = regelService.vurderTilgang(input)

        for (operasjon in Operasjon.entries.filterNot { it == Operasjon.DELEGERE }) {
            assertThat(avslag[operasjon]).isEqualTo(RegelResultat(false, avvist))
            assertThat(innvilget[operasjon]).isEqualTo(RegelResultat(true))
        }
        coVerify(exactly = 2) { tilgangsmaskinGateway.harTilgangTilPersonKomplett(any(), any(), any(), any()) }
        coVerify(exactly = 2) { tilgangsmaskinGateway.harTilgangTilPersonKjerne(any(), any(), any(), any()) }
    }

    private fun medOperasjoner(operasjoner: List<Operasjon>) = RegelInput(
        callId = input.callId,
        ansattIdent = input.ansattIdent,
        currentToken = input.currentToken,
        roller = input.roller,
        søkerIdenter = input.søkerIdenter,
        avklaringsbehovFraBehandlingsflyt = null,
        avklaringsbehovFraPostmottak = null,
        påkrevdRolle = input.påkrevdRolle,
        operasjoner = operasjoner,
    )

    private fun mockTilgangsmaskin(response: HarTilgangFraTilgangsmaskinen) {
        coEvery {
            tilgangsmaskinGateway.harTilgangTilPersonKomplett("123", input.currentToken, input.ansattIdent, input.callId)
        } returns response
        coEvery {
            tilgangsmaskinGateway.harTilgangTilPersonKjerne("123", input.currentToken, input.ansattIdent, input.callId)
        } returns response
    }
}
