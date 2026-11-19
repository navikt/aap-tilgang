package tilgang.regler

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.RelevanteIdenter
import no.nav.aap.tilgang.Rolle
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import tilgang.AzureTokenGen
import tilgang.BrukerToken
import tilgang.Tilgangskontekst
import tilgang.createRegelService
import tilgang.TilgangsmaskinConfig
import tilgang.auth.ITokenProvider
import tilgang.fakes.Fakes
import tilgang.fakes.WithFakes
import tilgang.integrasjoner.msgraph.Group
import tilgang.integrasjoner.msgraph.IMsGraphGateway
import tilgang.integrasjoner.msgraph.MemberOf
import tilgang.integrasjoner.pdl.HentGeografiskTilknytningResult
import tilgang.integrasjoner.pdl.IPdlGraphQLGateway
import tilgang.integrasjoner.pdl.PdlGeoType
import tilgang.integrasjoner.pdl.PersonResultat
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import tilgang.integrasjoner.tilgangsmaskin.createTilgangsmaskinClient
import tilgang.service.AdressebeskyttelseService
import java.util.UUID

@WithFakes
class RegelServiceTest {
    private val redis = Fakes.getRedisServer()
    private val tokenProvider = mockk<ITokenProvider> {
        coEvery { oboToken(any(), any()) } returns "test-token"
    }
    private val tilgangsmaskinClient = createTilgangsmaskinClient(TilgangsmaskinConfig(), tokenProvider)

    @AfterEach
    fun closeClient() {
        tilgangsmaskinClient.close()
    }

    // Bestill brev er deprecated og mangler løses-av

    @ParameterizedTest
    @EnumSource(value = Definisjon::class, names = ["BESTILL_BREV"], mode = EnumSource.Mode.EXCLUDE)
    fun `skal alltid gi false når roller er tom array`(avklaringsbehov: Definisjon) = runTest {
        val svar = regelService.vurderTilgang(
            RegelInput(
                kontekst = Tilgangskontekst("123", emptyList(), BrukerToken(token), UUID.randomUUID().toString()),
                søkerIdenter = RelevanteIdenter(søker = listOf("123"), barn = listOf()),
                operasjoner = listOf(Operasjon.SAKSBEHANDLE),
                påkrevdRolle = avklaringsbehov.løsesAv,
            )
        )
        Assertions.assertTrue(svar[Operasjon.SAKSBEHANDLE] == false)
    }

    @ParameterizedTest
    @EnumSource(value = Definisjon::class, names = ["BESTILL_BREV"], mode = EnumSource.Mode.EXCLUDE)
    fun `skal gi tilgang til operasjoner for NAY-steg som saksbehandler nasjonal`(avklaringsbehov: Definisjon) =
        runTest {
            val svar = regelService.vurderTilgang(
                RegelInput(
                    kontekst = Tilgangskontekst(
                        "123", listOf(Rolle.SAKSBEHANDLER_NASJONAL), BrukerToken(token), UUID.randomUUID().toString()
                    ),
                    søkerIdenter = RelevanteIdenter(søker = listOf("123"), barn = listOf()),
                    operasjoner = Operasjon.entries,
                    påkrevdRolle = avklaringsbehov.løsesAv,
                )
            )
            if (avklaringsbehov.løsesAv.contains(Rolle.SAKSBEHANDLER_NASJONAL)) {
                Assertions.assertTrue(svar[Operasjon.SAKSBEHANDLE] == true)
            } else {
                Assertions.assertTrue(svar[Operasjon.SAKSBEHANDLE] == false)
            }
            Assertions.assertTrue(svar[Operasjon.DRIFTE] == false)
            Assertions.assertTrue(svar[Operasjon.DELEGERE] == false)
        }

    @ParameterizedTest
    @EnumSource(value = Definisjon::class, names = ["BESTILL_BREV"], mode = EnumSource.Mode.EXCLUDE)
    fun `skal gi tilgang til drift, men ikke noe annet for driftsrolleinnehavere`(avklaringsbehov: Definisjon) =
        runTest {

            val svar = regelService.vurderTilgang(
                RegelInput(
                    kontekst = Tilgangskontekst(
                        "123", listOf(Rolle.DRIFT), BrukerToken(token), UUID.randomUUID().toString()
                    ),
                    søkerIdenter = RelevanteIdenter(søker = listOf("123"), barn = listOf()),
                    operasjoner = Operasjon.entries,
                    påkrevdRolle = avklaringsbehov.løsesAv,
                )
            )
            Assertions.assertTrue(svar[Operasjon.DRIFTE] == true)
            Assertions.assertTrue(svar[Operasjon.DRIFT_LES] == true)
            Assertions.assertTrue(svar[Operasjon.SAKSBEHANDLE] == false)
            Assertions.assertTrue(svar[Operasjon.SE] == false)
            Assertions.assertTrue(svar[Operasjon.DELEGERE] == false)
        }

    @ParameterizedTest
    @EnumSource(value = Definisjon::class, names = ["BESTILL_BREV"], mode = EnumSource.Mode.EXCLUDE)
    fun `skal gi tilgang til drift_les, men ikke drift for driftsleserolleinnehavere`(avklaringsbehov: Definisjon) =
        runTest {

            val svar = regelService.vurderTilgang(
                RegelInput(
                    kontekst = Tilgangskontekst(
                        "123", listOf(Rolle.DRIFT_LES), BrukerToken(token), UUID.randomUUID().toString()
                    ),
                    søkerIdenter = RelevanteIdenter(søker = listOf("123"), barn = listOf()),
                    operasjoner = Operasjon.entries,
                    påkrevdRolle = avklaringsbehov.løsesAv,
                )
            )
            Assertions.assertTrue(svar[Operasjon.DRIFT_LES] == true)
            Assertions.assertTrue(svar[Operasjon.DRIFTE] == false)
            Assertions.assertTrue(svar[Operasjon.SAKSBEHANDLE] == false)
            Assertions.assertTrue(svar[Operasjon.SE] == false)
            Assertions.assertTrue(svar[Operasjon.DELEGERE] == false)
        }

    @ParameterizedTest
    @EnumSource(value = Definisjon::class, names = ["BESTILL_BREV"], mode = EnumSource.Mode.EXCLUDE)
    fun `skal kun gi tilgang til å se for leserolle`(avklaringsbehov: Definisjon) = runTest {

        val svar = regelService.vurderTilgang(
            RegelInput(
                kontekst = Tilgangskontekst(
                    "123", listOf(Rolle.LES), BrukerToken(token), UUID.randomUUID().toString()
                ),
                søkerIdenter = RelevanteIdenter(søker = listOf("123"), barn = listOf()),
                operasjoner = Operasjon.entries,
                påkrevdRolle = avklaringsbehov.løsesAv,
            )
        )
        Assertions.assertTrue(svar[Operasjon.SE] == true)
        Assertions.assertTrue(svar[Operasjon.SAKSBEHANDLE] == false)
        Assertions.assertTrue(svar[Operasjon.DRIFTE] == false)
        Assertions.assertTrue(svar[Operasjon.DRIFT_LES] == false)
        Assertions.assertTrue(svar[Operasjon.DELEGERE] == false)
    }

    @Test
    fun `skal returnere false på saksbehandle dersom det ikke finnes noe avklaringsbehov å vurdere `() = runTest {

        val svar = regelService.vurderTilgang(
            RegelInput(
                kontekst = Tilgangskontekst(
                    "123", listOf(Rolle.SAKSBEHANDLER_NASJONAL), BrukerToken(token), UUID.randomUUID().toString()
                ),
                søkerIdenter = RelevanteIdenter(søker = listOf("123"), barn = listOf()),
                operasjoner = Operasjon.entries,
                påkrevdRolle = emptyList(),
            )
        )
        Assertions.assertTrue(svar[Operasjon.SAKSBEHANDLE] == false)
        Assertions.assertTrue(svar[Operasjon.SE] == true)
        Assertions.assertTrue(svar[Operasjon.DRIFTE] == false)
        Assertions.assertTrue(svar[Operasjon.DELEGERE] == false)
    }

    val graphGateway = object : IMsGraphGateway {
        override suspend fun hentAdGrupper(currentToken: OidcToken, ident: String): MemberOf {
            return MemberOf(
                groups = listOf(
                    Group(
                        id = UUID.randomUUID(),
                        name = "0000-GA-GEO_NASJONAL"
                    )
                )
            )
        }
    }

    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val pdlService = object : IPdlGraphQLGateway {
        override suspend fun hentPersonBolk(
            personidenter: List<String>,
            callId: String,
        ): List<PersonResultat> {
            return personidenter.map {
                PersonResultat(
                    ident = it,
                    adressebeskyttelse = listOf(),
                    code = "XXX"
                )
            }
        }

        override suspend fun hentGeografiskTilknytning(
            ident: String,
            callId: String,
        ): HentGeografiskTilknytningResult {
            return HentGeografiskTilknytningResult(
                gtType = PdlGeoType.KOMMUNE,
                gtLand = "NOR",
                gtBydel = null,
                gtKommune = "fff"
            )
        }
    }

    val regelService = createRegelService(
        pdlService,
        AdressebeskyttelseService(graphGateway),
        TilgangsmaskinGateway(redis, tilgangsmaskinClient, prometheus)
    )

    val token = AzureTokenGen("tilgangazure", "tilgang").generate()


}