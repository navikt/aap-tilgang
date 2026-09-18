package tilgang.regler

import no.nav.aap.tilgang.Operasjon
import tilgang.integrasjoner.pdl.IPdlGraphQLGateway
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import tilgang.service.AdressebeskyttelseService

class RegelService(
    pdlGateway: IPdlGraphQLGateway,
    adressebeskyttelseService: AdressebeskyttelseService,
    tilgangsmaskinGateway: TilgangsmaskinGateway
) {

    private val regelOppsett = mapOf(
        Operasjon.SE to listOf(
            LeseRolleRegel,
            AdressebeskyttelseRegel,
            TilgangsmaskinKomplettRegel
        ),
        Operasjon.DRIFTE to listOf(
            DriftRolleRegel,
            HabilitetRegel
        ),
        Operasjon.DRIFT_LES to listOf(
            DriftLesRolleRegel,
            HabilitetRegel
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

    private val regelMedVurdering = mapOf<Regel<*>, RegelMedInputgenerator<*>>(
        LeseRolleRegel to RegelMedInputgenerator(LeseRolleRegel, RolleInputGenerator),
        HabilitetRegel to RegelMedInputgenerator(
            HabilitetRegel,
            HabilitetRegelInputGenerator(tilgangsmaskinGateway)
        ),
        AdressebeskyttelseRegel to RegelMedInputgenerator(
            AdressebeskyttelseRegel,
            AdressebeskyttelseInputGenerator(pdlGateway, adressebeskyttelseService)
        ),
        DriftRolleRegel to RegelMedInputgenerator(DriftRolleRegel, RolleInputGenerator),
        DriftLesRolleRegel to RegelMedInputgenerator(DriftLesRolleRegel, RolleInputGenerator),
        AvdelingslederRolleRegel to RegelMedInputgenerator(AvdelingslederRolleRegel, RolleInputGenerator),
        AvklaringsbehovRolleRegel to RegelMedInputgenerator(AvklaringsbehovRolleRegel, AvklaringsbehovInputGenerator),
        TilgangsmaskinKomplettRegel to RegelMedInputgenerator(
            TilgangsmaskinKomplettRegel,
            TilgangsmaskinKomplettInputGenerator(tilgangsmaskinGateway)
        )
    )

    suspend fun vurderTilgang(input: RegelInput): Map<Operasjon, Boolean> {
        val aktuelleOperasjoner = regelOppsett.filterKeys { it in input.operasjoner }

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