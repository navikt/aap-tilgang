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
import no.nav.aap.tilgang.Rolle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import tilgang.fakes.Fakes
import tilgang.integrasjoner.behandlingsflyt.IdenterRespons
import tilgang.integrasjoner.msgraph.Group
import tilgang.integrasjoner.msgraph.IMsGraphClient
import tilgang.integrasjoner.msgraph.MemberOf
import tilgang.integrasjoner.nom.INomClient
import tilgang.integrasjoner.pdl.Gradering
import tilgang.integrasjoner.pdl.HentGeografiskTilknytningResult
import tilgang.integrasjoner.pdl.IPdlGraphQLClient
import tilgang.integrasjoner.pdl.PdlGeoType
import tilgang.integrasjoner.pdl.PersonResultat
import tilgang.integrasjoner.skjerming.SkjermingClient
import tilgang.service.AdressebeskyttelseService
import tilgang.service.EnhetService
import tilgang.service.GeoService
import tilgang.service.SkjermingService
import java.util.*
import kotlin.test.Test

class RegelServiceTest {
    companion object {
        private val FAKES = Fakes()

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            System.setProperty("fortrolig.adresse.ad", UUID.randomUUID().toString())
            System.setProperty("strengt.fortrolig.adresse.ad", UUID.randomUUID().toString())
        }

        @AfterAll
        @JvmStatic
        fun afterall() {
            FAKES.close()
        }
    }

    @ParameterizedTest
    @EnumSource(Definisjon::class)
    fun `skal alltid gi false når roller er tom array`(avklaringsbehov: Definisjon) {

        val graphClient = object : IMsGraphClient {
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

        val geoService = GeoService(graphClient)
        val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

        val pdlService = object : IPdlGraphQLClient {
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

            override fun hentBarnForPerson(personidenter: List<String>, callId: String): List<PersonResultat>? {
                return null
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
        val enhetService = EnhetService(graphClient)
        val skjermingClient = object : SkjermingClient(
            FAKES.redis, prometheus
        ) {
        }

        val nomClient = object : INomClient {
            override fun personNummerTilNavIdent(søkerIdent: String, callId: String): String {
                return "T131785"
            }
        }

        val skjermingService = SkjermingService(graphClient)

        val regelService = RegelService(
            geoService,
            enhetService,
            pdlService,
            skjermingClient,
            nomClient,
            skjermingService,
            AdressebeskyttelseService(graphClient)
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

    @Test
    fun `skal avslå tilgang til person dersom barn har adressebeskyttelse`() {
        val graphClient = object : IMsGraphClient {
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

        val geoService = GeoService(graphClient)
        val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

        val pdlService = object : IPdlGraphQLClient {
            override fun hentPersonBolk(
                personidenter: List<String>,
                callId: String
            ): List<PersonResultat> {
                // Person har selv ikke adressebeskyttelse
                return personidenter.map {
                    PersonResultat(
                        ident = it,
                        adressebeskyttelse = listOf(),
                        code = "XXX"
                    )
                }
            }

            override fun hentBarnForPerson(personidenter: List<String>, callId: String): List<PersonResultat>? {
                // Person har barn med strengt fortrolig adresse
                return listOf(PersonResultat(
                    ident = "1234",
                    adressebeskyttelse = listOf(Gradering.STRENGT_FORTROLIG),
                    code = "XXX"
                ))
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
        val enhetService = EnhetService(graphClient)
        val skjermingClient = object : SkjermingClient(
            FAKES.redis, prometheus
        ) {
            override fun isSkjermet(identer: IdenterRespons): Boolean {
                return false
            }
        }
        

        val nomClient = object : INomClient {
            override fun personNummerTilNavIdent(søkerIdent: String, callId: String): String {
                return "T131785"
            }
        }

        val skjermingService = SkjermingService(graphClient)

        val regelService = RegelService(
            geoService,
            enhetService,
            pdlService,
            skjermingClient,
            nomClient,
            skjermingService,
            AdressebeskyttelseService(graphClient)
        )

        val token = AzureTokenGen("tilgangazure", "tilgang").generate()
        val regelInput = RegelInput(
            callId = UUID.randomUUID().toString(),
            ansattIdent = "123",
            avklaringsbehovFraBehandlingsflyt = Definisjon.AVKLAR_SYKDOM,
            avklaringsbehovFraPostmottak = null,
            currentToken = OidcToken(token),
            søkerIdenter = IdenterRespons(
                søker = listOf("423", "456"),
                barn = emptyList()
            ),
            operasjoner = listOf(Operasjon.SAKSBEHANDLE),
            roller = listOf(Rolle.SAKSBEHANDLER_OPPFOLGING)
        )

        // Barn-identer skal inkluderes i input til adressebeskyttelsesregel
        val adressebeskyttelseInputGenerator = AdressebeskyttelseInputGenerator(
            pdlService = pdlService,
            adressebeskyttelseService = AdressebeskyttelseService(graphClient)
        )

        val adressebeskyttelseInput = adressebeskyttelseInputGenerator.generer(regelInput)
        assertThat(adressebeskyttelseInput.personer.size).isEqualTo(3)
        assertThat(adressebeskyttelseInput.personer.map { it.ident }).containsExactlyInAnyOrder("423", "456", "1234")

        val svar = regelService.vurderTilgang(
            regelInput,
        )
        // Tilgang skal avslås basert på barns adressebeskyttelse
        assertThat(svar[Operasjon.SAKSBEHANDLE] != true)
    }
}