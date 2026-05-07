package tilgang.regler

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.util.UUID
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.RelevanteIdenter
import no.nav.aap.tilgang.Rolle
import org.junit.jupiter.api.Assertions
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
import tilgang.integrasjoner.skjerming.SkjermingGateway
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import tilgang.service.AdressebeskyttelseService
import tilgang.service.GeoService
import tilgang.service.SkjermingService

@WithFakes
class RegelServiceTest {
    private val redis = Fakes.getRedisServer()

    // Bestill brev er deprecated og mangler løses-av

    @ParameterizedTest
    @EnumSource(value = Definisjon::class, names = [ "BESTILL_BREV" ], mode = EnumSource.Mode.EXCLUDE)
    fun `skal alltid gi false når roller er tom array`(avklaringsbehov: Definisjon) {

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
            Assertions.assertTrue(svar[Operasjon.SAKSBEHANDLE] == false)
    }

    @ParameterizedTest
    @EnumSource(value = Definisjon::class, names = [ "BESTILL_BREV" ], mode = EnumSource.Mode.EXCLUDE)
    fun `skal gi tilgang til operasjoner for NAY-steg som saksbehandler nasjonal`(avklaringsbehov: Definisjon) {

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
            Assertions.assertTrue(svar[Operasjon.SAKSBEHANDLE] == true)
        } else {
            Assertions.assertTrue(svar[Operasjon.SAKSBEHANDLE] == false)
        }
        Assertions.assertTrue(svar[Operasjon.DRIFTE] == false)
        Assertions.assertTrue(svar[Operasjon.DELEGERE] == false)
    }

    @ParameterizedTest
    @EnumSource(value = Definisjon::class, names = [ "BESTILL_BREV" ], mode = EnumSource.Mode.EXCLUDE)
    fun `skal gi tilgang til drift, men ikke noe annet for driftsrolleinnehavere`(avklaringsbehov: Definisjon) {

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
        Assertions.assertTrue(svar[Operasjon.DRIFTE] == true)
        Assertions.assertTrue(svar[Operasjon.SAKSBEHANDLE] == false)
        Assertions.assertTrue(svar[Operasjon.SE] == false)
        Assertions.assertTrue(svar[Operasjon.DELEGERE] == false)
    }


    @ParameterizedTest
    @EnumSource(value = Definisjon::class, names = [ "BESTILL_BREV" ], mode = EnumSource.Mode.EXCLUDE)
    fun `skal kun gi tilgang til å se for leserolle`(avklaringsbehov: Definisjon) {

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
        Assertions.assertTrue(svar[Operasjon.SE] == true)
        Assertions.assertTrue(svar[Operasjon.SAKSBEHANDLE] == false)
        Assertions.assertTrue(svar[Operasjon.DRIFTE] == false)
        Assertions.assertTrue(svar[Operasjon.DELEGERE] == false)
    }

    val graphGateway = object : IMsGraphGateway {
        override fun hentAdGrupper(currentToken: OidcToken, ident: String): MemberOf {
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

    val geoService = GeoService(graphGateway)
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val pdlService = object : IPdlGraphQLGateway {
        override fun hentPersonBolk(
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

        override fun hentGeografiskTilknytning(
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
    val skjermingGateway = object : SkjermingGateway(redis, prometheus) {
        override fun isSkjermet(identer: RelevanteIdenter): Boolean {
            return false
        }
    }

    val skjermingService = SkjermingService(graphGateway)

    val regelService = RegelService(
        geoService,
        pdlService,
        skjermingGateway,
        skjermingService,
        AdressebeskyttelseService(graphGateway),
        TilgangsmaskinGateway(redis, prometheus)
    )

    val token = AzureTokenGen("tilgangazure", "tilgang").generate()


}