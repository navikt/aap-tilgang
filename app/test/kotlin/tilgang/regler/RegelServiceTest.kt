package tilgang.regler

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import tilgang.SkjermingConfig
import tilgang.auth.AzureConfig
import tilgang.enhet.EnhetService
import tilgang.geo.GeoService
import tilgang.integrasjoner.behandlingsflyt.IdenterRespons
import tilgang.integrasjoner.msgraph.Group
import tilgang.integrasjoner.msgraph.IMsGraphClient
import tilgang.integrasjoner.msgraph.MemberOf
import tilgang.integrasjoner.pdl.HentGeografiskTilknytningResult
import tilgang.integrasjoner.pdl.IPdlGraphQLClient
import tilgang.integrasjoner.pdl.PdlGeoType
import tilgang.integrasjoner.pdl.PersonResultat
import tilgang.integrasjoner.skjerming.SkjermingClient
import tilgang.routes.Operasjon
import java.net.URI
import java.util.*

class RegelServiceTest {
    @ParameterizedTest
    @EnumSource(Avklaringsbehov::class)
    fun `skal alltid gi false når roller er tom array`(avklaringsbehov: Avklaringsbehov) {
        val graphClient = object : IMsGraphClient {
            override suspend fun hentAdGrupper(currentToken: String): MemberOf {
                return MemberOf(groups = listOf(Group(id = "0", name = "000-GA-GEO-abc")))
            }
        }
        val geoService = GeoService(graphClient)

        val pdlService = object : IPdlGraphQLClient {
            override suspend fun hentPersonBolk(personidenter: List<String>, callId: String): List<PersonResultat> {
                return personidenter.map { PersonResultat(ident = it, adressebeskyttelse = listOf(), code = "XXX") }
            }

            override suspend fun hentGeografiskTilknytning(
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
        val skjermingClient = object : SkjermingClient(AzureConfig(
            clientId = "",
            clientSecret = "",
            tokenEndpoint = URI.create("http://localhost:1234").resolve("/token").toURL(),
            jwks = URI.create("http://localhost:1234").resolve("/jwks").toURL(),
            issuer = ""
        ), SkjermingConfig("skjerming_base_url", "skjerming_scope")) {
        }
        val regelService = RegelService(geoService, enhetService, pdlService, skjermingClient)

        runBlocking {
            val svar = regelService.vurderTilgang(
                RegelInput(
                    callId = UUID.randomUUID().toString(),
                    ident = "123",
                    avklaringsbehov = avklaringsbehov,
                    behandlingsreferanse = null,
                    currentToken = "xxx",
                    identer = IdenterRespons(søker = listOf("423"), barn = listOf()),
                    operasjon = Operasjon.SAKSBEHANDLE,
                    roller = listOf()
                )
            )
            Assertions.assertFalse(svar)
        }
    }
}