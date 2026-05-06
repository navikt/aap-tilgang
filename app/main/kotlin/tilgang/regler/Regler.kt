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

    private val regelOpppsett = mapOf(
        Operasjon.SE to listOf(
            LeseRolleRegel,
            TilgangsmaskinKjerneRegel,
            AdressebeskyttelseRegel,
            GeoRegel,
            EgenAnsattRegel,
        ),
        Operasjon.DRIFTE to listOf(
            DriftRolleRegel,
            TilgangsmaskinKjerneRegel,
        ),
        Operasjon.DELEGERE to listOf(
            AvdelingslederRolleRegel,
        ),
        Operasjon.SAKSBEHANDLE to listOf(
            AvklaringsbehovRolleRegel,
            TilgangsmaskinKjerneRegel,
            AdressebeskyttelseRegel,
            GeoRegel,
            EgenAnsattRegel,
        )
    )
    private val alleRegler = listOf(
        RegelMedInputgenerator(LeseRolleRegel, RolleInputGenerator),
        RegelMedInputgenerator(TilgangsmaskinKjerneRegel, TilgangsmaskinKjerneInputGenerator(tilgangsmaskinGateway)),
        RegelMedInputgenerator(AdressebeskyttelseRegel, AdressebeskyttelseInputGenerator(pdlGateway, adressebeskyttelseService)),
        RegelMedInputgenerator(GeoRegel, GeoInputGenerator(geoService, pdlGateway)),
        RegelMedInputgenerator(EgenAnsattRegel, EgenAnsattInputGenerator(skjermetGateway, skjermingService)),
        RegelMedInputgenerator(DriftRolleRegel, RolleInputGenerator),
        RegelMedInputgenerator(AvdelingslederRolleRegel, RolleInputGenerator),
        RegelMedInputgenerator(AvklaringsbehovRolleRegel, AvklaringsbehovInputGenerator),
        RegelMedInputgenerator(GeoRegel, GeoInputGenerator(geoService, pdlGateway)),
        RegelMedInputgenerator(EgenAnsattRegel, EgenAnsattInputGenerator(skjermetGateway, skjermingService))
    )

    fun vurderTilgang(
        input: RegelInput
    ): Map<Operasjon, Boolean> {
        val alleReglerVurdert = alleRegler.map {
            Pair(it.regel, it.vurder(input))
        }

        val tilgangsMap = this.regelOpppsett.entries.associate { (operasjon, operasjonRegler) ->
            operasjon to operasjonRegler.all { operasjonRegel ->
                alleReglerVurdert.find { it.first == operasjonRegel }?.second
                    ?: error("Fant ikke verdi for $operasjonRegel")
            }
        }
        return tilgangsMap
    }
}