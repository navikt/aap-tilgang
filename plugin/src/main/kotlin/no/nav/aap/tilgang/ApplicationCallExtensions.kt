package no.nav.aap.tilgang

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import no.nav.aap.komponenter.httpklient.auth.AzpName

fun ApplicationCall.azp(): AzpName {
    val azp = principal<JWTPrincipal>()?.getClaim("azp_name", String::class)
    if (azp == null) {
        error("azp mangler i AzureAD claims")
    }
    return AzpName(azp)
}

fun ApplicationCall.ident(): String {
    return requireNotNull(principal<JWTPrincipal>()) {
        "principal mangler i ktor auth"
    }.getClaim("NAVident", String::class)
        ?: error("Ident mangler i token claims")
}