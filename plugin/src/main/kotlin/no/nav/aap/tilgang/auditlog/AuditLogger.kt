package no.nav.aap.tilgang.auditlog

import no.nav.aap.tilgang.auditlog.cef.CefMessage

interface AuditLogger {
    /**
     * Write a CEF message to the logger.
     * This is the recommended way to audit log.
     * @param message the CEF formatted message that will be written to the audit log
     */
    fun log(message: CefMessage?)

    /**
     * Write a raw message to the logger.
     * Can be used to write raw messages to the audit log, the user is responsible for the message format.
     * @param message the raw message that will be written to the audit log
     */
    fun log(message: String?)
}