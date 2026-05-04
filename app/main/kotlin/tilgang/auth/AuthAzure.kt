package tilgang.auth

import com.papsign.ktor.openapigen.route.response.OpenAPIPipelineContext
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken

internal fun OpenAPIPipelineContext.roller(): List<String> {
    val roller = requireNotNull(pipeline.call.principal<JWTPrincipal>()) {
        "principal mangler i ktor auth"
    }.getListClaim("groups", String::class)
    return roller
}

internal fun OpenAPIPipelineContext.ident(): String {
    return requireNotNull(pipeline.call.principal<JWTPrincipal>()) {
        "principal mangler i ktor auth"
    }.getClaim("NAVident", String::class)
        ?: error("Ident mangler i token claims")
}

