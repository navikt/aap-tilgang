package tilgang.regler

import no.nav.aap.komponenter.miljo.Miljø.erProd
import no.nav.aap.tilgang.Operasjon
import org.slf4j.LoggerFactory
import tilgang.integrasjoner.nom.INomGateway
import tilgang.integrasjoner.pdl.IPdlGraphQLGateway
import tilgang.integrasjoner.skjerming.SkjermingGateway
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import tilgang.service.AdressebeskyttelseService
import tilgang.service.EnhetService
import tilgang.service.GeoService
import tilgang.service.SkjermingService

private val logger = LoggerFactory.getLogger(RegelService::class.java)

class RegelService(
    geoService: GeoService,
    enhetService: EnhetService,
    pdlGateway: IPdlGraphQLGateway,
    skjermetGateway: SkjermingGateway,
    nomGateway: INomGateway,
    skjermingService: SkjermingService,
    adressebeskyttelseService: AdressebeskyttelseService,
    tilgangsmaskinGateway: TilgangsmaskinGateway
) {
    private val reglerForOperasjon = mapOf(
        Operasjon.SE to listOf(
            RegelMedInputgenerator(LeseRolleRegel, RolleInputGenerator),
            if (erProd()) {
                RegelMedInputgenerator(EgenSakRegel, EgenSakInputGenerator(nomGateway))
            } else {
                RegelMedInputgenerator(TilgangsmaskinKjerneRegel, TilgangsmaskinKjerneInputGenerator(tilgangsmaskinGateway))
            },
            RegelMedInputgenerator(AdressebeskyttelseRegel, AdressebeskyttelseInputGenerator(pdlGateway, adressebeskyttelseService)),
            RegelMedInputgenerator(GeoRegel, GeoInputGenerator(geoService, pdlGateway)),
            RegelMedInputgenerator(EgenAnsattRegel, EgenAnsattInputGenerator(skjermetGateway, skjermingService))
        ),
        Operasjon.DRIFTE to listOf(
            RegelMedInputgenerator(DriftRolleRegel, RolleInputGenerator),
            if (erProd()) {
                RegelMedInputgenerator(EgenSakRegel, EgenSakInputGenerator(nomGateway))
            } else {
                RegelMedInputgenerator(TilgangsmaskinKjerneRegel, TilgangsmaskinKjerneInputGenerator(tilgangsmaskinGateway))
            },
        ),
        Operasjon.DELEGERE to listOf(
            RegelMedInputgenerator(AvdelingslederRolleRegel, RolleInputGenerator),
        ),
        Operasjon.SAKSBEHANDLE to listOf(
            RegelMedInputgenerator(AvklaringsbehovRolleRegel, AvklaringsbehovInputGenerator),
            if (erProd()) {
                RegelMedInputgenerator(EgenSakRegel, EgenSakInputGenerator(nomGateway))
            } else {
                RegelMedInputgenerator(TilgangsmaskinKjerneRegel, TilgangsmaskinKjerneInputGenerator(tilgangsmaskinGateway))
            },
            RegelMedInputgenerator(AdressebeskyttelseRegel, AdressebeskyttelseInputGenerator(pdlGateway, adressebeskyttelseService)),
            RegelMedInputgenerator(GeoRegel, GeoInputGenerator(geoService, pdlGateway)),
            // TODO: Enhetsregelen gir kun mening hvis saker er knyttet mot enhet, noe de p.d. ikke er
            //RegelMedInputgenerator(EnhetRegel, EnhetInputGenerator(enhetService)),
            RegelMedInputgenerator(EgenAnsattRegel, EgenAnsattInputGenerator(skjermetGateway, skjermingService))
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