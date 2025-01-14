package no.nav.aap.tilgang.auditlog.cef

data class CefMessage(
    private val version: Int, // Version
    private val deviceVendor: String, // Application name
    private var deviceProduct: String, // Name of the log that originated the event
    private val deviceVersion: String,  // Version of the log format
    private val signatureId: String, // Event type
    private val name: String, // Description
    private val severity: CefMessageSeverity, // Severity of the event, usually "INFO" or "WARN"
    private val extension: Map<String, String> // Additional attributes
) {
    /**
     * Returns a CEF formatted string representing this message.
     * Example: "CEF:Version|Device Vendor|Device Product|Device Version|Signature ID|Name|Severity|Extension"
     * @return CEF formatted string
     */
    override fun toString(): String {
        val extensionStr =
            extension.entries.joinToString(" ") { entry -> "${entry.key}=${escapeExtensionValue(entry.value)}" }

        return "CEF:$version" +
                "|${escapeHeader(deviceVendor)}" +
                "|${escapeHeader(deviceProduct)}" +
                "|${escapeHeader(deviceVersion)}" +
                "|${escapeHeader(signatureId)}" +
                "|${escapeHeader(name)}" +
                "|${escapeHeader(severity.name)}" +
                "|$extensionStr"
    }

    private fun escapeHeader(header: String): String {
        return header
            .replace("\\", "\\\\")
            .replace("|", "\\|")
    }

    private fun escapeExtensionValue(attribute: String): String {
        return attribute
            .replace("\\", "\\\\")
            .replace("=", "\\=")
            .replace("\n", "\\n")
    }

    companion object {
        fun konstruer(
            deviceVendor: String = "Kelvin",
            version: Int = 0,
            deviceVersion: String = "1.0",
            name: String = "Auditlogg",
            app: String,
            ansattIdent: String,
            brukerIdent: String,
            path: String,
            decision: AuthorizationDecision,
            event: CefMessageEvent = CefMessageEvent.ACCESS,
            callId: String? = null
        ): CefMessage {
            val extension = CefMessageExtension()
            extension.authorizationDecision(decision)
            extension.sourceUserId(ansattIdent)
            extension.destinationUserId(brukerIdent)
            if (callId != null) extension.callId(callId)
            extension.timeEnded(System.currentTimeMillis())
            extension.request(path)

            return CefMessage(
                version = version,
                deviceVendor = deviceVendor,
                deviceVersion = deviceVersion,
                deviceProduct = app,
                signatureId = event.type,
                severity = decision.tilSeverity(),
                extension = extension.getExtension(),
                name = name
            )
        }
    }
}

private fun AuthorizationDecision.tilSeverity(): CefMessageSeverity {
    return when (this) {
        AuthorizationDecision.PERMIT -> CefMessageSeverity.INFO
        AuthorizationDecision.DENY -> CefMessageSeverity.WARN
    }
}