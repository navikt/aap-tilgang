package no.nav.aap.tilgang.auditlog.cef

enum class AuthorizationDecision(val loggString: String) {
    PERMIT("Permit"),
    DENY("Deny")
}