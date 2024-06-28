package tilgang.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import tilgang.LOGGER
import java.util.*
import java.util.concurrent.TimeUnit

const val AZURE = "azure"

internal fun ApplicationCall.roller(): List<String> {
    val roller = requireNotNull(principal<JWTPrincipal>()) {
        "principal mangler i ktor auth"
    }.getListClaim("groups", String::class)
    LOGGER.info("Roller: $roller")
    return roller
}

internal fun ApplicationCall.ident(): String {
    return requireNotNull(principal<JWTPrincipal>()) {
        "principal mangler i ktor auth"
    }.getClaim("NAVident", String::class)
        ?: error("Ident mangler i token claims")
}


fun Application.authentication(config: AzureConfig) {
    val idPortenProvider: JwkProvider = JwkProviderBuilder(config.jwks)
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    authentication {
        jwt(AZURE) {
            verifier(idPortenProvider, config.issuer)
            challenge { _, _ -> call.respond(HttpStatusCode.Unauthorized, "AzureAD validering feilet") }
            validate { cred ->
                val now = Date()

                if (config.clientId !in cred.audience) {
                    LOGGER.warn("AzureAD validering feilet (clientId var ikke i audience: ${cred.audience}")
                    return@validate null
                }

                if (cred.expiresAt?.before(now) == true) {
                    LOGGER.warn("AzureAD validering feilet (expired at: ${cred.expiresAt})")
                    return@validate null
                }

                if (cred.notBefore?.after(now) == true) {
                    LOGGER.warn("AzureAD validering feilet (not valid yet, valid from: ${cred.notBefore})")
                    return@validate null
                }

                if (cred.issuedAt?.after(cred.expiresAt ?: return@validate null) == true) {
                    LOGGER.warn("AzureAD validering feilet (issued after expiration: ${cred.issuedAt} )")
                    return@validate null
                }

                JWTPrincipal(cred.payload)
            }
        }
    }
}
