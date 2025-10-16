package tilgang.regler

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import tilgang.AzureTokenGen
import no.nav.aap.tilgang.Operasjon
import tilgang.fakes.Fakes
import tilgang.integrasjoner.behandlingsflyt.IdenterRespons
import tilgang.integrasjoner.msgraph.Group
import tilgang.integrasjoner.msgraph.IMsGraphGateway
import tilgang.integrasjoner.msgraph.MemberOf
import tilgang.integrasjoner.nom.INomGateway
import tilgang.integrasjoner.pdl.HentGeografiskTilknytningResult
import tilgang.integrasjoner.pdl.IPdlGraphQLGateway
import tilgang.integrasjoner.pdl.PdlGeoType
import tilgang.integrasjoner.pdl.PersonResultat
import tilgang.integrasjoner.skjerming.SkjermingGateway
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import tilgang.service.AdressebeskyttelseService
import tilgang.service.EnhetService
import tilgang.service.GeoService
import tilgang.service.SkjermingService
import java.util.*

class RegelServiceTest {
    companion object {
        private val FAKES = Fakes()

        @AfterAll
        @JvmStatic
        fun afterall() {
            FAKES.close()
        }
    }

    @ParameterizedTest
    @EnumSource(Definisjon::class)
    fun `skal alltid gi false når roller er tom array`(avklaringsbehov: Definisjon) {

        val graphGateway = object : IMsGraphGateway {
            override fun hentAdGrupper(currentToken: OidcToken, ident: String): MemberOf {
                return MemberOf(
                    groups = listOf(
                        Group(
                            id = UUID.randomUUID(),
                            name = "000-GA-GEO-abc"
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
                callId: String
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
                callId: String
            ): HentGeografiskTilknytningResult {
                return HentGeografiskTilknytningResult(
                    gtType = PdlGeoType.KOMMUNE,
                    gtLand = "NOR",
                    gtBydel = null,
                    gtKommune = "fff"
                )
            }
        }
        val enhetService = EnhetService(graphGateway)
        val skjermingGateway = object : SkjermingGateway(
            FAKES.redis, prometheus
        ) {
        }

        val nomGateway = object : INomGateway {
            override fun personNummerTilNavIdent(søkerIdent: String, callId: String): String {
                return "T131785"
            }
        }

        val skjermingService = SkjermingService(graphGateway)

        val regelService = RegelService(
            geoService,
            enhetService,
            pdlService,
            skjermingGateway,
            nomGateway,
            skjermingService,
            AdressebeskyttelseService(graphGateway),
            TilgangsmaskinGateway(FAKES.redis, prometheus)
        )

        val token = AzureTokenGen("tilgangazure", "tilgang").generate()
        val svar = regelService.vurderTilgang(
            RegelInput(
                callId = UUID.randomUUID().toString(),
                ansattIdent = "123",
                avklaringsbehovFraBehandlingsflyt = avklaringsbehov,
                avklaringsbehovFraPostmottak = null,
                currentToken = OidcToken(token),
                søkerIdenter = IdenterRespons(søker = listOf("423"), barn = listOf()),
                operasjoner = listOf(Operasjon.SAKSBEHANDLE),
                roller = listOf()
            )
        )
        Assertions.assertFalse(svar[Operasjon.SAKSBEHANDLE] == true)

    }
}