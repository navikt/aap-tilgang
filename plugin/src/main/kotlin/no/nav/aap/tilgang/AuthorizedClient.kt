package no.nav.aap.tilgang

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.path.normal.put
import com.papsign.ktor.openapigen.route.response.OpenAPIPipelineResponseContext
import com.papsign.ktor.openapigen.route.route
import no.nav.aap.tilgang.plugin.kontrakt.Behandlingsreferanse
import no.nav.aap.tilgang.plugin.kontrakt.Journalpostreferanse
import no.nav.aap.tilgang.plugin.kontrakt.Saksreferanse
import no.nav.aap.tilgang.plugin.kontrakt.TilgangReferanse
import tilgang.Operasjon

@Deprecated(message = "Erstatt med pathConfig")
inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Saksreferanse> NormalOpenAPIRoute.authorizedSakPost(
    operasjon: Operasjon,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    ktorRoute.installerTilgangTilSakPostPlugin<TRequest>(operasjon)
    @Suppress("UnauthorizedPost")
    post<TParams, TResponse, TRequest> { params, request -> body(params, request) }
}

@Deprecated(message = "Erstatt med pathConfig")
inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Behandlingsreferanse> NormalOpenAPIRoute.authorizedBehandlingPost(
    operasjon: Operasjon,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    ktorRoute.installerTilgangTilBehandlingPostPlugin<TRequest>(operasjon)
    @Suppress("UnauthorizedPost")
    post<TParams, TResponse, TRequest> { params, request -> body(params, request) }
}

@Deprecated(message = "Erstatt med pathConfig")
inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Journalpostreferanse> NormalOpenAPIRoute.authorizedJournalpostPost(
    operasjon: Operasjon,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    ktorRoute.installerTilgangTilJournalpostPlugin<TRequest>(operasjon)
    @Suppress("UnauthorizedPost")
    post<TParams, TResponse, TRequest> { params, request -> body(params, request) }
}

@Deprecated(message = "Erstatt med pathConfig istedenfor pathParams")
inline fun <reified TParams : Any, reified TResponse : Any> NormalOpenAPIRoute.authorizedGet(
    sakPathParam: SakPathParam,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams) -> Unit
) {
    ktorRoute.installerTilgangGetPlugin(sakPathParam)
    @Suppress("UnauthorizedGet")
    get<TParams, TResponse> { params -> body(params) }
}

inline fun <reified TParams : Any, reified TResponse : Any> NormalOpenAPIRoute.authorizedGet(
    pathConfig: AuthorizationParamPathConfig,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams) -> Unit
) {
    ktorRoute.installerTilgangParamPlugin(pathConfig)
    @Suppress("UnauthorizedGet")
    get<TParams, TResponse> { params -> body(params) }
}

inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : TilgangReferanse> NormalOpenAPIRoute.authorizedPost(
    pathConfig: AuthorizationBodyPathConfig,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    ktorRoute.installerTilgangBodyPlugin<TRequest>(pathConfig)
    @Suppress("UnauthorizedPost")
    post<TParams, TResponse, TRequest> { params, request -> body(params, request) }
}

inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : TilgangReferanse> NormalOpenAPIRoute.authorizedPut(
    pathConfig: AuthorizationBodyPathConfig,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    ktorRoute.installerTilgangBodyPlugin<TRequest>(pathConfig)
    @Suppress("UnauthorizedPut")
    put<TParams, TResponse, TRequest> { params, request -> body(params, request) }
}

inline fun <reified TParams : Any, reified TResponse : Any> NormalOpenAPIRoute.authorizedGet(
    journalpostIdResolver: JournalpostIdResolver<TParams, Unit>,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams) -> Unit
) {
    ktorRoute.installerTilgangPlugin(journalpostIdResolver)
    @Suppress("UnauthorizedGet")
    get<TParams, TResponse> { params -> body(params) }
}

inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Any> NormalOpenAPIRoute.authorizedPost(
    journalpostIdResolver: JournalpostIdResolver<TParams, TRequest>,
    avklaringsbehovResolver: AvklaringsbehovResolver<TRequest>,
    operasjon: Operasjon,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    ktorRoute.installerTilgangPlugin(journalpostIdResolver, avklaringsbehovResolver, operasjon)
    @Suppress("UnauthorizedPost")
    post<TParams, TResponse, TRequest> { params, request -> body(params, request) }
}

@Deprecated(message = "Erstatt med pathConfig istedenfor pathParams")
inline fun <reified TParams : Any, reified TResponse : Any> NormalOpenAPIRoute.authorizedGet(
    journalpostPathParam: JournalpostPathParam,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams) -> Unit
) {
    ktorRoute.installerTilgangGetPlugin(journalpostPathParam)
    @Suppress("UnauthorizedGet")
    get<TParams, TResponse> { params -> body(params) }
}

@Deprecated(message = "Erstatt med pathConfig istedenfor pathParams")
inline fun <reified TParams : Any, reified TResponse : Any> NormalOpenAPIRoute.authorizedGet(
    behandlingPathParam: BehandlingPathParam,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams) -> Unit
) {
    ktorRoute.installerTilgangGetPlugin(behandlingPathParam)
    @Suppress("UnauthorizedGet")
    get<TParams, TResponse> { params -> body(params) }
}

@Deprecated(message = "Erstatt med pathConfig istedenfor pathParams")
inline fun <reified TParams : Any, reified TResponse : Any> NormalOpenAPIRoute.authorizedGetWithApprovedList(
    vararg approvedList: String,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams) -> Unit
) {
    ktorRoute.installerTilgangPluginWithApprovedList(approvedList.toList())
    @Suppress("UnauthorizedGet")
    get<TParams, TResponse> { params -> body(params) }
}

inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Any> NormalOpenAPIRoute.authorizedPostWithApprovedList(
    vararg approvedList: String,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    ktorRoute.installerTilgangPluginWithApprovedList(approvedList.toList())
    @Suppress("UnauthorizedPost")
    post<TParams, TResponse, TRequest> { params, request -> body(params, request) }
}

inline fun NormalOpenAPIRoute.azpRoute(
    path: String,
    vararg approvedList: String,
    crossinline fn: NormalOpenAPIRoute.() -> Unit
) {
    ktorRoute.installerTilgangPluginWithApprovedList(approvedList.toList())
    route(path) { fn() }
}
