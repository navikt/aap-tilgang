package tilgang.regler

import no.nav.aap.tilgang.Operasjon

class RegelService(
    adressebeskyttelseOppslag: AdressebeskyttelseOppslag,
    adressebeskyttelsesroller: Adressebeskyttelsesroller,
    personkontroll: Personkontroll
) {

    private val regelOppsett = mapOf(
        Operasjon.SE to listOf(
            LeseRolleRegel,
            AdressebeskyttelseRegel,
            PersontilgangRegel
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
            PersontilgangRegel
        )
    )

    private val regelMedVurdering = mapOf<Regel<*>, RegelMedInputgenerator<*>>(
        LeseRolleRegel to RegelMedInputgenerator(LeseRolleRegel, RolleInputGenerator),
        HabilitetRegel to RegelMedInputgenerator(
            HabilitetRegel,
            HabilitetRegelInputGenerator(personkontroll)
        ),
        AdressebeskyttelseRegel to RegelMedInputgenerator(
            AdressebeskyttelseRegel,
            AdressebeskyttelseInputGenerator(adressebeskyttelseOppslag, adressebeskyttelsesroller)
        ),
        DriftRolleRegel to RegelMedInputgenerator(DriftRolleRegel, RolleInputGenerator),
        DriftLesRolleRegel to RegelMedInputgenerator(DriftLesRolleRegel, RolleInputGenerator),
        AvdelingslederRolleRegel to RegelMedInputgenerator(AvdelingslederRolleRegel, RolleInputGenerator),
        AvklaringsbehovRolleRegel to RegelMedInputgenerator(AvklaringsbehovRolleRegel, AvklaringsbehovInputGenerator),
        PersontilgangRegel to RegelMedInputgenerator(
            PersontilgangRegel,
            PersontilgangInputGenerator(personkontroll)
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