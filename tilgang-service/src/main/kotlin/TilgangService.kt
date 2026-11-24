package tilgang.kontroll

import tilgang.kontroll.regler.AdressebeskyttelseInputGenerator
import tilgang.kontroll.regler.AdressebeskyttelseOppslag
import tilgang.kontroll.regler.AdressebeskyttelseRegel
import tilgang.kontroll.regler.Adressebeskyttelsesroller
import tilgang.kontroll.regler.AvdelingslederRolleRegel
import tilgang.kontroll.regler.AvklaringsbehovInputGenerator
import tilgang.kontroll.regler.AvklaringsbehovRolleRegel
import tilgang.kontroll.regler.DriftLesRolleRegel
import tilgang.kontroll.regler.DriftRolleRegel
import tilgang.kontroll.regler.HabilitetRegel
import tilgang.kontroll.regler.HabilitetRegelInputGenerator
import tilgang.kontroll.regler.LeseRolleRegel
import tilgang.kontroll.regler.Personkontroll
import tilgang.kontroll.regler.PersontilgangInputGenerator
import tilgang.kontroll.regler.PersontilgangRegel
import tilgang.kontroll.regler.Regel
import tilgang.kontroll.regler.RegelMedInputgenerator
import tilgang.kontroll.regler.RolleInputGenerator

class TilgangService(
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

    suspend fun vurderTilgang(input: Tilgangsgrunnlag): Map<Operasjon, Boolean> {
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
        input: Tilgangsgrunnlag,
    ): Boolean {
        regelCache[regel]?.let { return it }

        val vurdering = regelMedVurdering[regel]?.vurder(input)
            ?: error("Fant ikke vurdering for regel $regel")
        regelCache[regel] = vurdering
        return vurdering
    }
}