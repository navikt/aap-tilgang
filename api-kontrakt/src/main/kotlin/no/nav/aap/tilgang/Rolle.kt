package no.nav.aap.tilgang

/**
 * Se [Confluence](https://confluence.adeo.no/display/PAAP/Roller+og+tilgangs-kontroll+i+Kelvin) for dokumentasjon på rollene.
 */
enum class Rolle {
    /** Saksbehandling ved Nav-kontorene, i sykdomsstegene. */
    SAKSBEHANDLER_OPPFOLGING,

    /** Saksbehandling på nasjonal kø (NAY). */
    SAKSBEHANDLER_NASJONAL,

    /** Utføre kvalitetssikringssteget. */
    KVALITETSSIKRER,

    /** Utføre besluttersteget. */
    BESLUTTER,

    /** Ren lesetilgang, ment for blant annet NKS og Riksrevisjonen. */
    LES,

    /** Opprette og redigere køer i produksjonsstyringsverktøyet. */
    PRODUKSJONSSTYRING,

    /** Alle tilganger i driftsverktøyet, både lesing og skriving. Primært for utviklere av AAP. */
    DRIFT,

    /** Lesetilgang til driftsverktøyet for oppslag på saker og personer. */
    DRIFT_LES;

    companion object {
        fun kanLeseSaker() = listOf(
            LES,
            SAKSBEHANDLER_NASJONAL,
            SAKSBEHANDLER_OPPFOLGING,
            KVALITETSSIKRER,
            BESLUTTER
        )
    }

}

