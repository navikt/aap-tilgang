package no.nav.aap.tilgang.auditlog.cef


/**
 * Common extension fields for CEF messages
 */
object CefMessageExtensionFields {
    /**
     * CEF-navn: sourceUserId
     * Beskrivelse: Brukeren som utfører en operasjon, kan f.eks være veileder som gjør oppslag på en bruker
     * Eksempel: Z12345
     */
    const val FIELD_SOURCE_USER_ID: String = "suid"

    /**
     * CEF-navn: destinationUserId
     * Beskrivelse: Brukeren som det blir gjort oppslag på
     * Eksempel: fnr/dnr/aktør-id
     */
    const val FIELD_DESTINATION_USER_ID: String = "duid"


    /**
     * CEF-navn: endTime
     * Beskrivelse: Epoch tidstempel for når slutten på eventet oppstod
     * Eksempel: 1654252356153
     */
    const val FIELD_END_TIME: String = "end"

    /**
     * CEF-navn: sourceProcessName
     * Beskrivelse: Id som identifiserer operasjonen, dette kan f.eks være Nav-Call-Id HTTP-headeren som brukes for tracing
     * Eksempel: 7b3a4c34-1b0c-40fb-bf15-743f3612c350
     */
    const val FIELD_SPROC: String = "sproc"

    /**
     * NB: Beskrivelse og eksempel er hentet fra CEF dokumentasjon
     * CEF-navn: destinationProcessName
     * Beskrivelse: The name of the event's destination process
     * Eksempel: "telnetd" or "sshd"
     */
    const val FIELD_DPROC: String = "dproc"

    /**
     * CEF-navn: requestUrl
     * Beskrivelse: URLen til HTTP-requestet som trigget eventet
     * Eksempel: /api/sak
     */
    const val FIELD_REQUEST: String = "request"

    /**
     * CEF-navn: requestMethod
     * Beskrivelse: Metoden til HTTP-requestet som trigget eventet
     * Eksempel: POST
     */
    const val FIELD_REQUEST_METHOD: String = "requestMethod"
}