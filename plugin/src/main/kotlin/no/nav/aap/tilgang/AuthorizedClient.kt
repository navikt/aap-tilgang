package no.nav.aap.tilgang

import tilgang.Operasjon
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.OpenAPIPipelineResponseContext

inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Saksreferanse> NormalOpenAPIRoute.authorizedSakPost(
    operasjon: Operasjon,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    ktorRoute.installerTilgangPostPlugin<TRequest>(operasjon)
    @Suppress("UnauthorizedPost")
    post<TParams, TResponse, TRequest> { params, request -> body(params, request) }
}

inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Behandlingsreferanse> NormalOpenAPIRoute.authorizedBehandlingPost(
    operasjon: Operasjon,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    ktorRoute.installerTilgangBPostPlugin<TRequest>(operasjon)
    @Suppress("UnauthorizedPost")
    post<TParams, TResponse, TRequest> { params, request -> body(params, request) }
}

inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Any> NormalOpenAPIRoute.authorizedPostWithApprovedList(
    vararg approvedList: String,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    ktorRoute.installerTilgangPluginWithApprovedList(approvedList.toList())
    @Suppress("UnauthorizedPost")
    post<TParams, TResponse, TRequest> { params, request -> body(params, request) }
}

inline fun <reified TParams : Any, reified TResponse : Any> NormalOpenAPIRoute.authorizedGet(
    operasjon: Operasjon,
    ressurs: Ressurs,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams) -> Unit
) {
    ktorRoute.installerTilgangGetPlugin(operasjon, ressurs)
    @Suppress("UnauthorizedGet")
    get<TParams, TResponse> { params -> body(params) }
}

inline fun <reified TParams : Any, reified TResponse : Any> NormalOpenAPIRoute.authorizedGetWithApprovedList(
    vararg approvedList: String,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams) -> Unit
) {
    ktorRoute.installerTilgangPluginWithApprovedList(approvedList.toList())
    @Suppress("UnauthorizedGet")
    get<TParams, TResponse> { params -> body(params) }
}