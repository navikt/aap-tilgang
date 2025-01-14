package no.nav.aap.tilgang.auditlog

import io.ktor.http.*
import io.ktor.server.util.*
import no.nav.aap.tilgang.plugin.kontrakt.BrukerIdentResolver
import org.slf4j.Logger
import no.nav.aap.tilgang.plugin.kontrakt.AuditlogResolverInput


/**
 * Auditlogconfig som krever at body implementerer [AuditlogResolverInput] for å hente ut input til [BrukerIdentResolver]
 * Kun spørringer med OBO-token vil auditlogges
 * @property logger Typisk syslog. Se [guide](https://github.com/navikt/naudit)
 * @property brukerIdentResolver  Funksjon for å finne fnr for bruker det gjøres oppslag på
 */
data class AuditLogBodyConfig(
    val logger: Logger,
    val app: String,
    val brukerIdentResolver: BrukerIdentResolver,
    val inkluderCallId: Boolean = false
) : AuditLogConfig {
    override fun logger() = logger
    override fun app() = app
    override fun inkluderCallId() = inkluderCallId

    fun tilIdent(request: AuditlogResolverInput): String {
        return brukerIdentResolver.resolve(request.hentAuditlogResolverInput())
    }
}

/**
 * Auditlogconfig som henter ut input til [BrukerIdentResolver] fra path param
 * Kun spørringer med OBO-token vil auditlogges
 * @property logger Typisk syslog. Se [guide](https://github.com/navikt/naudit)
 * @property brukerIdentResolver  Funksjon for å finne fnr for bruker det gjøres oppslag på
 */
data class AuditLogPathParamConfig(
    val logger: Logger,
    val app: String,
    val brukerIdentResolver: PathBrukerIdentResolver,
    val inkluderCallId: Boolean = false
) : AuditLogConfig {
    override fun logger() = logger
    override fun app() = app
    override fun inkluderCallId() = inkluderCallId

    fun tilIdent(parameters: Parameters): String {
        val input = parameters.getOrFail(brukerIdentResolver.param)
        return brukerIdentResolver.resolver.resolve(input)
    }
}

interface AuditLogConfig {
    fun logger(): Logger
    fun app(): String
    fun inkluderCallId(): Boolean
}

data class PathBrukerIdentResolver(
    /**
     * Funskjon for å finne fnr for bruker det gjøres oppslag på,
     * f.eks. ved å slå opp ident i databasen vha. saksnummer
     */
    val resolver: BrukerIdentResolver,
    /**
     * Path parameter hvis verdi skal brukes som input til BrukerIdentResolver
     */
    val param: String
)