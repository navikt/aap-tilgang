package tilgang.regler

import no.nav.aap.tilgang.Operasjon
import org.slf4j.LoggerFactory
import tilgang.integrasjoner.nom.INomClient
import tilgang.integrasjoner.pdl.IPdlGraphQLClient
import tilgang.integrasjoner.skjerming.SkjermingClient
import tilgang.service.AdressebeskyttelseService
import tilgang.service.EnhetService
import tilgang.service.GeoService
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
            RegelMedInputgenerator(GeoRegel, GeoInputGenerator(geoService, pdlService)),
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
            RegelMedInputgenerator(GeoRegel, GeoInputGenerator(geoService, pdlService)),
            // TODO: Enhetsregelen gir kun mening hvis saker er knyttet mot enhet, noe de p.d. ikke er
            //RegelMedInputgenerator(EnhetRegel, EnhetInputGenerator(enhetService)),
            RegelMedInputgenerator(EgenAnsattRegel, EgenAnsattInputGenerator(skjermetClient, skjermingService))
        )
    )

    fun vurderTilgang(
        input: RegelInput
    ): Map<Operasjon, Boolean> {
        val tilgangsMap = mutableMapOf<Operasjon, Boolean>()
        input.operasjoner.forEach { operasjon ->
            val harTilgangForRolle = this.reglerForOperasjon[operasjon]!!.all {
                it.vurder(input)
            }
            tilgangsMap[operasjon] = harTilgangForRolle
        }
        return tilgangsMap
    }
}