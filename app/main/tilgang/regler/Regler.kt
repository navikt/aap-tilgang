package tilgang.regler

import tilgang.geo.GeoService
import tilgang.integrasjoner.pdl.PdlGraphQLClient
import tilgang.routes.Operasjon

class RegelService(geoService: GeoService, pdlService: PdlGraphQLClient) {
    private val reglerForOperasjon = mapOf(
        Operasjon.SE to listOf(
            RegelMedInputgenerator(EgenSakRegel, EgenSakInputGenerator),
            RegelMedInputgenerator(LeseRolleRegel, RolleInputGenerator),
            RegelMedInputgenerator(AdressebeskyttelseRegel, AdressebeskyttelseInputGenerator(pdlService)),
            RegelMedInputgenerator(GeoRegel, GeoInputGenerator(geoService, pdlService))
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
            RegelMedInputgenerator(GeoRegel, GeoInputGenerator(geoService, pdlService))
        )
    )

    suspend fun vurderTilgang(
        input: RegelInput
    ): Boolean {
        return this.reglerForOperasjon[input.operasjon]!!.all {
            it.vurder(input)
        }
    }
}