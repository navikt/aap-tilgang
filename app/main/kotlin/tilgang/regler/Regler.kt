package tilgang.regler

import org.slf4j.LoggerFactory
import tilgang.enhet.EnhetService
import tilgang.geo.GeoService
import tilgang.integrasjoner.nom.NOMClient
import tilgang.integrasjoner.pdl.IPdlGraphQLClient
import tilgang.integrasjoner.skjerming.SkjermingClient
import tilgang.routes.Operasjon

private val logger = LoggerFactory.getLogger(RegelService::class.java)

class RegelService(geoService: GeoService, enhetService: EnhetService, pdlService: IPdlGraphQLClient, skjermetClient: SkjermingClient, nomClient: NOMClient){
    private val reglerForOperasjon = mapOf(
        Operasjon.SE to listOf(
            RegelMedInputgenerator(EgenSakRegel, EgenSakInputGenerator(nomClient)),
            RegelMedInputgenerator(LeseRolleRegel, RolleInputGenerator),
            RegelMedInputgenerator(AdressebeskyttelseRegel, AdressebeskyttelseInputGenerator(pdlService)),
            RegelMedInputgenerator(GeoRegel, GeoInputGenerator(geoService, pdlService)),
            RegelMedInputgenerator(EgenAnsattRegel, EgenAnsattInputGenerator(skjermetClient))
        ),
        Operasjon.DRIFTE to listOf(
            RegelMedInputgenerator(EgenSakRegel, EgenSakInputGenerator(nomClient)),
            RegelMedInputgenerator(DriftRolleRegel, RolleInputGenerator)
        ),
        Operasjon.DELEGERE to listOf(
            RegelMedInputgenerator(AvdelingslederRolleRegel, RolleInputGenerator),
        ),
        Operasjon.SAKSBEHANDLE to listOf(
            RegelMedInputgenerator(EgenSakRegel, EgenSakInputGenerator(nomClient)),
            RegelMedInputgenerator(AvklaringsbehovRolleRegel, AvklaringsbehovInputGenerator),
            RegelMedInputgenerator(AdressebeskyttelseRegel, AdressebeskyttelseInputGenerator(pdlService)),
            RegelMedInputgenerator(GeoRegel, GeoInputGenerator(geoService, pdlService)),
            // TODO: Enhetsregelen gir kun mening hvis saker er knyttet mot enhet, noe de p.d. ikke er
            //RegelMedInputgenerator(EnhetRegel, EnhetInputGenerator(enhetService)),
            RegelMedInputgenerator(EgenAnsattRegel, EgenAnsattInputGenerator(skjermetClient))
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