package no.nav.aap.tilgang.auditlog.cef

import no.nav.aap.tilgang.auditlog.cef.CefMessageExtensionFields.FIELD_DESTINATION_USER_ID
import no.nav.aap.tilgang.auditlog.cef.CefMessageExtensionFields.FIELD_END_TIME
import no.nav.aap.tilgang.auditlog.cef.CefMessageExtensionFields.FIELD_REQUEST
import no.nav.aap.tilgang.auditlog.cef.CefMessageExtensionFields.FIELD_SOURCE_USER_ID
import no.nav.aap.tilgang.auditlog.cef.CefMessageExtensionFields.FIELD_SPROC

class CefMessageExtension {
    private val extension: MutableMap<String, String> = HashMap()
    
    fun getExtension(): Map<String, String> {
        return extension
    }

    private fun flexString(position: Int, label: String, value: String) {
        require(!(position < 1 || position > 2)) { "position must be either 1 or 2" }

        this.extension["flexString${position}Label"] = label
        this.extension["flexString$position"] = value
    }

    fun sourceUserId(sourceUserId: String) {
        extension[FIELD_SOURCE_USER_ID] = sourceUserId
    }

    fun destinationUserId(destinationUserId: String) {
        extension[FIELD_DESTINATION_USER_ID] = destinationUserId
    }

    fun timeEnded(epochMillis: Long) {
        extension[FIELD_END_TIME] = epochMillis.toString()
    }

    fun callId(callId: String) {
        extension[FIELD_SPROC] = callId
    }
    
    fun request(request: String) {
        extension[FIELD_REQUEST] = request
    }

    fun customString(position: Int, label: String, value: String) {
        require(!(position < 1 || position > 6)) { "position must be a value from 1 to 6 inclusive" }

        extension["cs${position}Label"] = label
        extension["cs$position"] = value
    }
    
    fun authorizationDecision(decision: AuthorizationDecision) {
        this.flexString(1, "Decision", decision.loggString)
    }
}