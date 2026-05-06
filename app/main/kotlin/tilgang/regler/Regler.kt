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
    private val regelMedVurdering = mapOf<Regel<*>, RegelMedInputgenerator<*>>(
        LeseRolleRegel to RegelMedInputgenerator(LeseRolleRegel, RolleInputGenerator),
        TilgangsmaskinKjerneRegel to RegelMedInputgenerator(TilgangsmaskinKjerneRegel, TilgangsmaskinKjerneInputGenerator(tilgangsmaskinGateway)),
        AdressebeskyttelseRegel to RegelMedInputgenerator(AdressebeskyttelseRegel, AdressebeskyttelseInputGenerator(pdlGateway, adressebeskyttelseService)),
        GeoRegel to RegelMedInputgenerator(GeoRegel, GeoInputGenerator(geoService, pdlGateway)),
        EgenAnsattRegel to RegelMedInputgenerator(EgenAnsattRegel, EgenAnsattInputGenerator(skjermetGateway, skjermingService)),
        DriftRolleRegel to RegelMedInputgenerator(DriftRolleRegel, RolleInputGenerator),
        AvdelingslederRolleRegel to RegelMedInputgenerator(AvdelingslederRolleRegel, RolleInputGenerator),
        AvklaringsbehovRolleRegel to RegelMedInputgenerator(AvklaringsbehovRolleRegel, AvklaringsbehovInputGenerator),
    )

    suspend fun vurderTilgang(input: RegelInput): Map<Operasjon, Boolean> {
        val aktuelleOperasjoner = regelOpppsett.filterKeys { it in input.operasjoner }

        val regelResultater: Map<Regel<*>, Boolean> = aktuelleOperasjoner.values
            .flatten()
            .toSet()
            .associateWith { regel -> (regelMedVurdering[regel]?.vurder(input) ?: error("Fant ikke vurdering for regel $regel")) }

        return aktuelleOperasjoner.mapValues { (_, regler) ->
            regler.all { regelResultater[it] == true }
        }
    }
}