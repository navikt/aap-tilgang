package tilgang.regler

import org.slf4j.LoggerFactory
import tilgang.Operasjon
import tilgang.service.EnhetService
import tilgang.service.GeoService
import tilgang.integrasjoner.nom.INomClient
import tilgang.integrasjoner.pdl.IPdlGraphQLClient
import tilgang.integrasjoner.skjerming.SkjermingClient
import tilgang.service.AdressebeskyttelseService
import tilgang.service.SkjermingService

private val logger = LoggerFactory.getLogger(RegelService::class.java)

class RegelService(
    geoService: GeoService,
    enhetService: EnhetService,
    pdlService: IPdlGraphQLClient,
    skjermetClient: SkjermingClient,
    nomClient: INomClient,
    skjermingService: SkjermingService,
    adressebeskyttelseService: AdressebeskyttelseService
) {
    private val reglerForOperasjon = mapOf(
        Operasjon.SE to listOf(
            RegelMedInputgenerator(LeseRolleRegel, RolleInputGenerator),
            RegelMedInputgenerator(EgenSakRegel, EgenSakInputGenerator(nomClient)),
            RegelMedInputgenerator(AdressebeskyttelseRegel, AdressebeskyttelseInputGenerator(pdlService, adressebeskyttelseService)),
            // TODO: Aktiver når vi har georoller
            // RegelMedInputgenerator(GeoRegel, GeoInputGenerator(geoService, pdlService)),
            RegelMedInputgenerator(EgenAnsattRegel, EgenAnsattInputGenerator(skjermetClient, skjermingService))
        ),
        Operasjon.DRIFTE to listOf(
            RegelMedInputgenerator(DriftRolleRegel, RolleInputGenerator),
            RegelMedInputgenerator(EgenSakRegel, EgenSakInputGenerator(nomClient))
        ),
        Operasjon.DELEGERE to listOf(
            RegelMedInputgenerator(AvdelingslederRolleRegel, RolleInputGenerator),
        ),
        Operasjon.SAKSBEHANDLE to listOf(
            RegelMedInputgenerator(AvklaringsbehovRolleRegel, AvklaringsbehovInputGenerator),
            RegelMedInputgenerator(EgenSakRegel, EgenSakInputGenerator(nomClient)),
            RegelMedInputgenerator(AdressebeskyttelseRegel, AdressebeskyttelseInputGenerator(pdlService, adressebeskyttelseService)),
            // TODO: Aktiver når vi har georoller
            // RegelMedInputgenerator(GeoRegel, GeoInputGenerator(geoService, pdlService)),
            // TODO: Enhetsregelen gir kun mening hvis saker er knyttet mot enhet, noe de p.d. ikke er
            //RegelMedInputgenerator(EnhetRegel, EnhetInputGenerator(enhetService)),
            RegelMedInputgenerator(EgenAnsattRegel, EgenAnsattInputGenerator(skjermetClient, skjermingService))
        )
    )

    fun vurderTilgang(
        input: RegelInput
    ): Boolean {
        return this.reglerForOperasjon[input.operasjon]!!.all {
            val resultat = it.vurder(input)
            logger.info("Vurderte regel ${it.regel} med svar: $resultat")
            resultat
        }
    }
}