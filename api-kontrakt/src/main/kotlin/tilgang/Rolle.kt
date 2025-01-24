package tilgang

/**
 * Se [Confluence](https://confluence.adeo.no/display/PAAP/Roller+og+tilgangs-kontroll+i+Kelvin) for dokumentasjon på rollene.
 */
enum class Rolle {
    @Deprecated(
        "Utgår, bruk [SAKSBEHANDLER_OPPFOLGING] i stedet.",
        replaceWith = ReplaceWith("SAKSBEHANDLER_OPPFOLGING")
    )
    VEILEDER,
    SAKSBEHANDLER_OPPFOLGING,

    @Deprecated(
        "Utgår, bruk [SAKSBEHANDLER_NASJONAL] i stedet.",
        replaceWith = ReplaceWith("SAKSBEHANDLER_NASJONAL")
    )
    SAKSBEHANDLER,
    SAKSBEHANDLER_NASJONAL,
    KVALITETSSIKRER,
    BESLUTTER,
    LES,

    @Deprecated(
        "Denne utgår, bruk [PRODUKSJONSSTYRING] i stedet.",
        replaceWith = ReplaceWith("PRODUKSJONSSTYRING")
    )
    AVDELINGSLEDER,
    PRODUKSJONSSTYRING,

    @Deprecated("Utgår. Bruk [DRIFT] i stedet.", replaceWith = ReplaceWith("DRIFT"))
    UTVIKLER,
    DRIFT
}

