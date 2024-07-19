package tilgang.regler

import org.slf4j.LoggerFactory
import tilgang.enhet.EnhetService
import tilgang.geo.GeoService
import tilgang.integrasjoner.pdl.IPdlGraphQLClient
import tilgang.routes.Operasjon

private val logger = LoggerFactory.getLogger(RegelService::class.java)

class RegelService(geoService: GeoService, enhetService: EnhetService, pdlService: IPdlGraphQLClient) {
    private val reglerForOperasjon = mapOf(
        Operasjon.SE to listOf(
            RegelMedInputgenerator(EgenSakRegel, EgenSakInputGenerator),
            RegelMedInputgenerator(LeseRolleRegel, RolleInputGenerator),
            RegelMedInputgenerator(AdressebeskyttelseRegel, AdressebeskyttelseInputGenerator(pdlService)),
            RegelMedInputgenerator(GeoRegel, GeoInputGenerator(geoService, enhetService, pdlService))
        ),
        Operasjon.DRIFTE to listOf(
            RegelMedInputgenerator(EgenSakRegel, EgenSakInputGenerator),
            RegelMedInputgenerator(DriftRolleRegel, RolleInputGenerator)
        ),
        Operasjon.DELEGERE to listOf(
            RegelMedInputgenerator(AvdelingslederRolleRegel, RolleInputGenerator),
        ),
        Operasjon.SAKSBEHANDLE to listOf(
            RegelMedInputgenerator(EgenSakRegel, EgenSakInputGenerator),
            RegelMedInputgenerator(AvklaringsbehovRolleRegel, AvklaringsbehovInputGenerator),
            RegelMedInputgenerator(AdressebeskyttelseRegel, AdressebeskyttelseInputGenerator(pdlService)),
            RegelMedInputgenerator(GeoRegel, GeoInputGenerator(geoService, enhetService, pdlService))
        )
    )

    suspend fun vurderTilgang(
        input: RegelInput
    ): Boolean {
        return this.reglerForOperasjon[input.operasjon]!!.all {
            val resultat = it.vurder(input)
            logger.info("Vurderte regel ${it.regel} med svar: $resultat")
            resultat
        }
    }
}