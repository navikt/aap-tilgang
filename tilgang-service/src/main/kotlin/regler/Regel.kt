package tilgang.kontroll.regler

import tilgang.kontroll.Tilgangsgrunnlag

internal sealed interface Regel<T> {
    fun vurder(input: T): Boolean
}

internal sealed interface InputGenerator<out T> {
    suspend fun generer(input: Tilgangsgrunnlag): T
}

internal class RegelMedInputgenerator<T>(val regel: Regel<T>, val inputGenerator: InputGenerator<T>) {
    suspend fun vurder(input: Tilgangsgrunnlag): Boolean {
        val regelInput = inputGenerator.generer(input)
        return regel.vurder(regelInput)
    }
}