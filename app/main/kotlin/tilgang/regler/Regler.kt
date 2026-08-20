package tilgang.regler

import no.nav.aap.komponenter.miljo.Miljø
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

    private val regelOppsettMedTilgangsmaskinKomplett = mapOf(
        Operasjon.SE to listOf(
            LeseRolleRegel,
            AdressebeskyttelseRegel,
            TilgangsmaskinKomplettRegel
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
            AdressebeskyttelseRegel,
            TilgangsmaskinKomplettRegel
        )
    )

    private val regelOppsettUtenTilgangsmaskinKomplett = mapOf(
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
        TilgangsmaskinKomplettRegel to RegelMedInputgenerator(
            TilgangsmaskinKomplettRegel,
            TilgangsmaskinKomplettInputGenerator(tilgangsmaskinGateway)
        )
    )

    suspend fun vurderTilgang(input: RegelInput): Map<Operasjon, Boolean> {
        val aktuelleOperasjoner = if (Miljø.erProd()) {
            regelOppsettUtenTilgangsmaskinKomplett.filterKeys { it in input.operasjoner }
        } else {
            regelOppsettMedTilgangsmaskinKomplett.filterKeys { it in input.operasjoner }
        }

        val regelCache = mutableMapOf<Regel<*>, Boolean>()

        return aktuelleOperasjoner.mapValues { (_, regler) ->
            regler.all { regel ->
                hentRegelresultat(regelCache, regel, input)
            }
        }
    }

    private suspend fun hentRegelresultat(
        regelCache: MutableMap<Regel<*>, Boolean>,
        regel: Regel<*>,
        input: RegelInput,
    ): Boolean {
        regelCache[regel]?.let { return it }

        val vurdering = regelMedVurdering[regel]?.vurder(input)
            ?: error("Fant ikke vurdering for regel $regel")
        regelCache[regel] = vurdering
        return vurdering
    }
}