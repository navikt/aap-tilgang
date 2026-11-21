package tilgang.kontroll.regler

/**
 * Sjekker alltid alle regler - kan derfor hende at man ikke har sendt med hverken påkrevd rolle eller avklaringsbehov.
 * Skal returnere false i disse tilfellene
 */
data object AvklaringsbehovRolleRegel : Regel<AvklaringsbehovRolleInput> {
    override fun vurder(input: AvklaringsbehovRolleInput): Boolean {
        val sjekker = input.avklaringsbehovRoller
            .map { påkrevdeRoller -> påkrevdeRoller.any { it in input.roller } }
            .toMutableList()
        if (input.påkrevdRolle.isNotEmpty()) {
            sjekker.add(input.påkrevdRolle.any { it in input.roller })
        }

        /**
         * Dette kan skje dersom det kun sjekkes på lesetilgang og det ikke finnes noe å saksbehandle
         */
        if (sjekker.isEmpty()) {
            return false
        }

        return sjekker.all { it }
    }
}