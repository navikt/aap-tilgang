package tilgang.regler

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.test.runTest
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.RelevanteIdenter
import no.nav.aap.tilgang.Rolle
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import tilgang.AzureTokenGen
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
import tilgang.service.AdressebeskyttelseService
import java.util.UUID

@WithFakes
class RegelServiceTest {
    private val redis = Fakes.getRedisServer()

    // Bestill brev er deprecated og mangler løses-av

    @ParameterizedTest
    @EnumSource(value = Definisjon::class, names = ["BESTILL_BREV"], mode = EnumSource.Mode.EXCLUDE)
    fun `skal alltid gi false når roller er tom array`(avklaringsbehov: Definisjon) = runTest {
        val svar = regelService.vurderTilgang(
            RegelInput(
                callId = UUID.randomUUID().toString(),
                ansattIdent = "123",
                avklaringsbehovFraBehandlingsflyt = null,
                avklaringsbehovFraPostmottak = null,
                currentToken = OidcToken(token),
                søkerIdenter = RelevanteIdenter(søker = listOf("123"), barn = listOf()),
                operasjoner = listOf(Operasjon.SAKSBEHANDLE),
                påkrevdRolle = avklaringsbehov.løsesAv,
                roller = listOf()
            )
        )
        Assertions.assertEquals(RegelResultat(false), svar[Operasjon.SAKSBEHANDLE])
    }

    @ParameterizedTest
    @EnumSource(value = Definisjon::class, names = ["BESTILL_BREV"], mode = EnumSource.Mode.EXCLUDE)
    fun `skal gi tilgang til operasjoner for NAY-steg som saksbehandler nasjonal`(avklaringsbehov: Definisjon) =
        runTest {
            val svar = regelService.vurderTilgang(
                RegelInput(
                    callId = UUID.randomUUID().toString(),
                    ansattIdent = "123",
                    avklaringsbehovFraBehandlingsflyt = null,
                    avklaringsbehovFraPostmottak = null,
                    currentToken = OidcToken(token),
                    søkerIdenter = RelevanteIdenter(søker = listOf("123"), barn = listOf()),
                    operasjoner = Operasjon.entries,
                    påkrevdRolle = avklaringsbehov.løsesAv,
                    roller = listOf(Rolle.SAKSBEHANDLER_NASJONAL)
                )
            )
            if (avklaringsbehov.løsesAv.contains(Rolle.SAKSBEHANDLER_NASJONAL)) {
                Assertions.assertEquals(RegelResultat(true), svar[Operasjon.SAKSBEHANDLE])
            } else {
                Assertions.assertEquals(RegelResultat(false), svar[Operasjon.SAKSBEHANDLE])
            }
            Assertions.assertEquals(RegelResultat(false), svar[Operasjon.DRIFTE])
            Assertions.assertEquals(RegelResultat(false), svar[Operasjon.DELEGERE])
        }

    @ParameterizedTest
    @EnumSource(value = Definisjon::class, names = ["BESTILL_BREV"], mode = EnumSource.Mode.EXCLUDE)
    fun `skal gi tilgang til drift, men ikke noe annet for driftsrolleinnehavere`(avklaringsbehov: Definisjon) =
        runTest {

            val svar = regelService.vurderTilgang(
                RegelInput(
                    callId = UUID.randomUUID().toString(),
                    ansattIdent = "123",
                    avklaringsbehovFraBehandlingsflyt = null,
                    avklaringsbehovFraPostmottak = null,
                    currentToken = OidcToken(token),
                    søkerIdenter = RelevanteIdenter(søker = listOf("123"), barn = listOf()),
                    operasjoner = Operasjon.entries,
                    påkrevdRolle = avklaringsbehov.løsesAv,
                    roller = listOf(Rolle.DRIFT)
                )
            )
            Assertions.assertEquals(RegelResultat(true), svar[Operasjon.DRIFTE])
            Assertions.assertEquals(RegelResultat(true), svar[Operasjon.DRIFT_LES])
            Assertions.assertEquals(RegelResultat(false), svar[Operasjon.SAKSBEHANDLE])
            Assertions.assertEquals(RegelResultat(false), svar[Operasjon.SE])
            Assertions.assertEquals(RegelResultat(false), svar[Operasjon.DELEGERE])
        }

    @ParameterizedTest
    @EnumSource(value = Definisjon::class, names = ["BESTILL_BREV"], mode = EnumSource.Mode.EXCLUDE)
    fun `skal gi tilgang til drift_les, men ikke drift for driftsleserolleinnehavere`(avklaringsbehov: Definisjon) =
        runTest {

            val svar = regelService.vurderTilgang(
                RegelInput(
                    callId = UUID.randomUUID().toString(),
                    ansattIdent = "123",
                    avklaringsbehovFraBehandlingsflyt = null,
                    avklaringsbehovFraPostmottak = null,
                    currentToken = OidcToken(token),
                    søkerIdenter = RelevanteIdenter(søker = listOf("123"), barn = listOf()),
                    operasjoner = Operasjon.entries,
                    påkrevdRolle = avklaringsbehov.løsesAv,
                    roller = listOf(Rolle.DRIFT_LES)
                )
            )
            Assertions.assertEquals(RegelResultat(true), svar[Operasjon.DRIFT_LES])
            Assertions.assertEquals(RegelResultat(false), svar[Operasjon.DRIFTE])
            Assertions.assertEquals(RegelResultat(false), svar[Operasjon.SAKSBEHANDLE])
            Assertions.assertEquals(RegelResultat(false), svar[Operasjon.SE])
            Assertions.assertEquals(RegelResultat(false), svar[Operasjon.DELEGERE])
        }

    @ParameterizedTest
    @EnumSource(value = Definisjon::class, names = ["BESTILL_BREV"], mode = EnumSource.Mode.EXCLUDE)
    fun `skal kun gi tilgang til å se for leserolle`(avklaringsbehov: Definisjon) = runTest {

        val svar = regelService.vurderTilgang(
            RegelInput(
                callId = UUID.randomUUID().toString(),
                ansattIdent = "123",
                avklaringsbehovFraBehandlingsflyt = null,
                avklaringsbehovFraPostmottak = null,
                currentToken = OidcToken(token),
                søkerIdenter = RelevanteIdenter(søker = listOf("123"), barn = listOf()),
                operasjoner = Operasjon.entries,
                påkrevdRolle = avklaringsbehov.løsesAv,
                roller = listOf(Rolle.LES)
            )
        )
        Assertions.assertEquals(RegelResultat(true), svar[Operasjon.SE])
        Assertions.assertEquals(RegelResultat(false), svar[Operasjon.SAKSBEHANDLE])
        Assertions.assertEquals(RegelResultat(false), svar[Operasjon.DRIFTE])
        Assertions.assertEquals(RegelResultat(false), svar[Operasjon.DRIFT_LES])
        Assertions.assertEquals(RegelResultat(false), svar[Operasjon.DELEGERE])
    }

    @Test
    fun `skal returnere false på saksbehandle dersom det ikke finnes noe avklaringsbehov å vurdere `() = runTest {

        val svar = regelService.vurderTilgang(
            RegelInput(
                callId = UUID.randomUUID().toString(),
                ansattIdent = "123",
                avklaringsbehovFraBehandlingsflyt = null,
                avklaringsbehovFraPostmottak = null,
                currentToken = OidcToken(token),
                søkerIdenter = RelevanteIdenter(søker = listOf("123"), barn = listOf()),
                operasjoner = Operasjon.entries,
                påkrevdRolle = emptyList(),
                roller = listOf(Rolle.SAKSBEHANDLER_NASJONAL)
            )
        )
        Assertions.assertEquals(RegelResultat(false), svar[Operasjon.SAKSBEHANDLE])
        Assertions.assertEquals(RegelResultat(true), svar[Operasjon.SE])
        Assertions.assertEquals(RegelResultat(false), svar[Operasjon.DRIFTE])
        Assertions.assertEquals(RegelResultat(false), svar[Operasjon.DELEGERE])
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

    val regelService = RegelService(
        pdlService,
        AdressebeskyttelseService(graphGateway),
        TilgangsmaskinGateway(redis, Fakes.getHttpClient(), prometheus)
    )

    val token = AzureTokenGen("tilgangazure", "tilgang").generate()


}