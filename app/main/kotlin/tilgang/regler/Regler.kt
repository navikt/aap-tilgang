package tilgang.regler

import no.nav.aap.tilgang.Operasjon
import tilgang.integrasjoner.pdl.IPdlGraphQLGateway
import tilgang.integrasjoner.skjerming.SkjermingGateway
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import tilgang.service.AdressebeskyttelseService
import tilgang.service.GeoService
import tilgang.service.SkjermingService

class RegelService(
    geoService: GeoService,
    pdlGateway: IPdlGraphQLGateway,
    skjermetGateway: SkjermingGateway,
    skjermingService: SkjermingService,
    adressebeskyttelseService: AdressebeskyttelseService,
    tilgangsmaskinGateway: TilgangsmaskinGateway
) {
    private val reglerForOperasjon = mapOf(
        Operasjon.SE to listOf(
            RegelMedInputgenerator(LeseRolleRegel, RolleInputGenerator),
            RegelMedInputgenerator(TilgangsmaskinKjerneRegel, TilgangsmaskinKjerneInputGenerator(tilgangsmaskinGateway)),
            RegelMedInputgenerator(AdressebeskyttelseRegel, AdressebeskyttelseInputGenerator(pdlGateway, adressebeskyttelseService)),
            RegelMedInputgenerator(GeoRegel, GeoInputGenerator(geoService, pdlGateway)),
            RegelMedInputgenerator(EgenAnsattRegel, EgenAnsattInputGenerator(skjermetGateway, skjermingService))
        ),
        Operasjon.DRIFTE to listOf(
            RegelMedInputgenerator(DriftRolleRegel, RolleInputGenerator),
            RegelMedInputgenerator(TilgangsmaskinKjerneRegel, TilgangsmaskinKjerneInputGenerator(tilgangsmaskinGateway))
        ),
        Operasjon.DELEGERE to listOf(
            RegelMedInputgenerator(AvdelingslederRolleRegel, RolleInputGenerator),
        ),
        Operasjon.SAKSBEHANDLE to listOf(
            RegelMedInputgenerator(AvklaringsbehovRolleRegel, AvklaringsbehovInputGenerator),
            RegelMedInputgenerator(TilgangsmaskinKjerneRegel, TilgangsmaskinKjerneInputGenerator(tilgangsmaskinGateway)),
            RegelMedInputgenerator(AdressebeskyttelseRegel, AdressebeskyttelseInputGenerator(pdlGateway, adressebeskyttelseService)),
            RegelMedInputgenerator(GeoRegel, GeoInputGenerator(geoService, pdlGateway)),
            RegelMedInputgenerator(EgenAnsattRegel, EgenAnsattInputGenerator(skjermetGateway, skjermingService))
        )
    )

    suspend fun vurderTilgang(
        input: RegelInput
    ): Map<Operasjon, Boolean> {
        val tilgangsMap = mutableMapOf<Operasjon, Boolean>()
        for (operasjon in input.operasjoner) {
            var harTilgangForRolle = true
            for (regel in this.reglerForOperasjon[operasjon]!!) {
                if (!regel.vurder(input)) {
                    harTilgangForRolle = false
                    break
                }
            }
            tilgangsMap[operasjon] = harTilgangForRolle
        }
        return tilgangsMap
    }
}