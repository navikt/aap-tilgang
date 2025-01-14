package no.nav.aap.tilgang.auditlog.cef

/**
 * Common event types for audit logging.
 * Is used in CefMessage.severity
 */
enum class CefMessageSeverity {
    INFO,  // Ex: An event that has occurred
    WARN // Ex: An event where a person does not have access to a resource
}