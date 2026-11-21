package tilgang.kontroll.regler

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class AdressebeskyttelseInputGenerator(
    private val adressebeskyttelseOppslag: AdressebeskyttelseOppslag,
    private val adressebeskyttelsesroller: Adressebeskyttelsesroller,
) : InputGenerator<AdressebeskyttelseInput> {
    override suspend fun generer(input: RegelInput): AdressebeskyttelseInput = coroutineScope {
        val graderinger = async {
            adressebeskyttelseOppslag.hentGraderinger(
                input.søkerIdenter.søker.union(input.søkerIdenter.barn).toList(),
                input.kontekst.callId,
            )
        }
        val roller = async {
            adressebeskyttelsesroller.hentRoller(input.kontekst.brukerToken, input.kontekst.ansattIdent)
        }

        AdressebeskyttelseInput(roller.await(), graderinger.await())
    }
}
