package no.nav.aap.tilgang

import com.papsign.ktor.openapigen.APITag
import com.papsign.ktor.openapigen.modules.RouteOpenAPIModule
import com.papsign.ktor.openapigen.route.TagModule
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.path.normal.put
import com.papsign.ktor.openapigen.route.response.OpenAPIPipelineResponseContext

enum class Tags(override val description: String) : APITag {
    Tilgangkontrollert("Dette endepunktet er tilgangkontrollert.")
}

val tilgangkontrollertTag = TagModule(listOf(Tags.Tilgangkontrollert))

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
    routeConfig: AuthorizetionRouteConfig,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    when (routeConfig) {
        is AuthorizationParamPathConfig -> ktorRoute.installerTilgangParamPlugin(routeConfig)
        is AuthorizationBodyPathConfig -> ktorRoute.installerTilgangBodyPlugin<TRequest>(routeConfig)
        else -> throw IllegalArgumentException("Unsupported routeConfig type: $routeConfig")
    }

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
