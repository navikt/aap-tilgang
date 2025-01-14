package no.nav.aap.tilgang.auditlog.cef

/**
 * Common event types for audit logging.
 * Is used in CefMessage.signatureId
 */
enum class CefMessageEvent(val type: String) {
    CREATE("audit:create"),
    ACCESS("audit:access"),
    UPDATE("audit:update"),
    DELETE("audit:delete")
}