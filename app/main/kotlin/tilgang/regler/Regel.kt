package tilgang.regler

import no.nav.aap.tilgang.Avisning
import no.nav.aap.tilgang.BegrunnetTilgangsResponse
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistResponse

data class RegelResultat(
    val harTilgang: Boolean,
    val tilgangsmaskinAvvistResponse: TilgangsmaskinAvvistResponse? = null,
) {
    fun tilKontrakt() =
        BegrunnetTilgangsResponse(
            tilgang = harTilgang,
            avvistResponse = tilgangsmaskinAvvistResponse?.let {
                Avisning(
                    title = it.title,
                    begrunnelse = it.begrunnelse,
                    kanOverstyres = it.kanOverstyres
                )
            },
        )
}

internal sealed interface Regel<T> {
    fun vurder(input: T): Boolean

    fun vurderMedResultat(input: T): RegelResultat = RegelResultat(vurder(input))
}

internal sealed interface InputGenerator<out T> {
    suspend fun generer(input: RegelInput): T
}

internal class RegelMedInputgenerator<T>(val regel: Regel<T>, val inputGenerator: InputGenerator<T>) {
    suspend fun vurder(input: RegelInput): RegelResultat {
        val regelInput = inputGenerator.generer(input)
        return regel.vurderMedResultat(regelInput)
    }
}