package no.nav.aap.tilgang

import com.papsign.ktor.openapigen.APITag
import com.papsign.ktor.openapigen.modules.RouteOpenAPIModule
import com.papsign.ktor.openapigen.route.TagModule
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.path.normal.put
import com.papsign.ktor.openapigen.route.response.OpenAPIPipelineResponseContext
import com.papsign.ktor.openapigen.route.route
import no.nav.aap.tilgang.plugin.kontrakt.Behandlingsreferanse
import no.nav.aap.tilgang.plugin.kontrakt.Journalpostreferanse
import no.nav.aap.tilgang.plugin.kontrakt.Saksreferanse
import tilgang.Operasjon

enum class Tags(override val description: String) : APITag {
    Tilgangkontrollert("Dette endepunktet er tilgangkontrollert.")
}

val tilgangkontrollertTag = TagModule(listOf(Tags.Tilgangkontrollert))

@Deprecated(message = "Erstatt med pathConfig")
inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Saksreferanse> NormalOpenAPIRoute.authorizedSakPost(
    operasjon: Operasjon,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    ktorRoute.installerTilgangTilSakPostPlugin<TRequest>(operasjon)
    @Suppress("UnauthorizedPost")
    post<TParams, TResponse, TRequest>(*modules, tilgangkontrollertTag) { params, request ->
        body(
            params,
            request
        )
    }
}

@Deprecated(message = "Erstatt med pathConfig")
inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Behandlingsreferanse> NormalOpenAPIRoute.authorizedBehandlingPost(
    operasjon: Operasjon,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    ktorRoute.installerTilgangTilBehandlingPostPlugin<TRequest>(operasjon)
    @Suppress("UnauthorizedPost")
    post<TParams, TResponse, TRequest>(*modules, tilgangkontrollertTag) { params, request ->
        body(
            params,
            request
        )
    }
}

@Deprecated(message = "Erstatt med pathConfig")
inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Journalpostreferanse> NormalOpenAPIRoute.authorizedJournalpostPost(
    operasjon: Operasjon,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    ktorRoute.installerTilgangTilJournalpostPlugin<TRequest>(operasjon)
    @Suppress("UnauthorizedPost")
    post<TParams, TResponse, TRequest>(*modules, tilgangkontrollertTag) { params, request ->
        body(
            params,
            request
        )
    }
}

@Deprecated(message = "Erstatt med pathConfig istedenfor pathParams")
inline fun <reified TParams : Any, reified TResponse : Any> NormalOpenAPIRoute.authorizedGet(
    sakPathParam: SakPathParam,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams) -> Unit
) {
    ktorRoute.installerTilgangGetPlugin(sakPathParam)
    @Suppress("UnauthorizedGet")
    get<TParams, TResponse>(*modules, tilgangkontrollertTag) { params -> body(params) }
}

inline fun <reified TParams : Any, reified TResponse : Any> NormalOpenAPIRoute.authorizedGet(
    pathConfig: AuthorizationParamPathConfig,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams) -> Unit
) {
    ktorRoute.installerTilgangParamPlugin(pathConfig)
    @Suppress("UnauthorizedGet")
    get<TParams, TResponse>(*modules, tilgangkontrollertTag) { params -> body(params) }
}

inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Any> NormalOpenAPIRoute.authorizedPost(
    pathConfig: AuthorizationBodyPathConfig,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    ktorRoute.installerTilgangBodyPlugin<TRequest>(pathConfig)
    @Suppress("UnauthorizedPost")
    post<TParams, TResponse, TRequest>(tilgangkontrollertTag, *modules) { params, request ->
        body(
            params,
            request
        )
    }
}

inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Any> NormalOpenAPIRoute.authorizedPut(
    pathConfig: AuthorizationBodyPathConfig,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    ktorRoute.installerTilgangBodyPlugin<TRequest>(pathConfig)
    @Suppress("UnauthorizedPut")
    put<TParams, TResponse, TRequest>(tilgangkontrollertTag, *modules) { params, request ->
        body(
            params,
            request
        )
    }
}

inline fun <reified TParams : Any, reified TResponse : Any> NormalOpenAPIRoute.authorizedGet(
    journalpostIdResolver: JournalpostIdResolver<TParams, Unit>,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams) -> Unit
) {
    ktorRoute.installerTilgangPlugin(journalpostIdResolver)
    @Suppress("UnauthorizedGet")
    get<TParams, TResponse>(*modules, tilgangkontrollertTag) { params -> body(params) }
}

inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Any> NormalOpenAPIRoute.authorizedPost(
    journalpostIdResolver: JournalpostIdResolver<TParams, TRequest>,
    avklaringsbehovResolver: AvklaringsbehovResolver<TRequest>,
    operasjon: Operasjon,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    ktorRoute.installerTilgangPlugin(journalpostIdResolver, avklaringsbehovResolver, operasjon)
    @Suppress("UnauthorizedPost")
    post<TParams, TResponse, TRequest>(tilgangkontrollertTag, *modules) { params, request ->
        body(
            params,
            request
        )
    }
}

@Deprecated(message = "Erstatt med pathConfig istedenfor pathParams")
inline fun <reified TParams : Any, reified TResponse : Any> NormalOpenAPIRoute.authorizedGet(
    journalpostPathParam: JournalpostPathParam,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams) -> Unit
) {
    ktorRoute.installerTilgangGetPlugin(journalpostPathParam)
    @Suppress("UnauthorizedGet")
    get<TParams, TResponse>(*modules, tilgangkontrollertTag) { params -> body(params) }
}

@Deprecated(message = "Erstatt med pathConfig istedenfor pathParams")
inline fun <reified TParams : Any, reified TResponse : Any> NormalOpenAPIRoute.authorizedGet(
    behandlingPathParam: BehandlingPathParam,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams) -> Unit
) {
    ktorRoute.installerTilgangGetPlugin(behandlingPathParam)
    @Suppress("UnauthorizedGet")
    get<TParams, TResponse>(*modules, tilgangkontrollertTag) { params -> body(params) }
}

@Deprecated(message = "Erstatt med pathConfig istedenfor pathParams")
inline fun <reified TParams : Any, reified TResponse : Any> NormalOpenAPIRoute.authorizedGetWithApprovedList(
    vararg approvedList: String,
    modules: List<RouteOpenAPIModule> = listOf(),
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams) -> Unit
) {
    ktorRoute.installerTilgangPluginWithApprovedList(approvedList.toList())
    @Suppress("UnauthorizedGet")
    get<TParams, TResponse>(
        *modules.toTypedArray(),
        tilgangkontrollertTag
    ) { params -> body(params) }
}

inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Any> NormalOpenAPIRoute.authorizedPostWithApprovedList(
    vararg approvedList: String,
    modules: List<RouteOpenAPIModule>,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    ktorRoute.installerTilgangPluginWithApprovedList(approvedList.toList())
    @Suppress("UnauthorizedPost")
    post<TParams, TResponse, TRequest>(
        tilgangkontrollertTag,
        *modules.toTypedArray()
    ) { params, request -> body(params, request) }
}

inline fun NormalOpenAPIRoute.azpRoute(
    path: String,
    vararg approvedList: String,
    crossinline fn: NormalOpenAPIRoute.() -> Unit
) {
    ktorRoute.installerTilgangPluginWithApprovedList(approvedList.toList())
    route(path) { fn() }
}
