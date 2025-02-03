package tilgang

/**
 * Muligene operasjoner en rolle kan gjøre.
 */
enum class Operasjon {
    /**
     * Lesetilgang.
     */
    SE,

    /**
     * Utføre saksbehandling for en sak.
     */
    SAKSBEHANDLE,

    /**
     * Drift-operasjoner, f.eks restart av motor.
     */
    DRIFTE,

    DELEGERE
}
