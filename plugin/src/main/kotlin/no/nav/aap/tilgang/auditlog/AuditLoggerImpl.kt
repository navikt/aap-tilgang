package no.nav.aap.tilgang.auditlog

import no.nav.aap.tilgang.auditlog.cef.CefMessage
import org.slf4j.Logger

class AuditLoggerImpl(val log: Logger) : AuditLogger {
    override fun log(message: CefMessage?) {
        log.info(message.toString())
    }

    override fun log(message: String?) {
        log.info(message)
    }
}