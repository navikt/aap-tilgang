package no.nav.aap.tilgang

import com.papsign.ktor.openapigen.APITag
import com.papsign.ktor.openapigen.modules.RouteOpenAPIModule
import com.papsign.ktor.openapigen.route.TagModule
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.path.normal.put
import com.papsign.ktor.openapigen.route.response.OpenAPIPipelineResponseContext
import no.nav.aap.tilgang.auditlog.AuditLogConfig
import no.nav.aap.tilgang.auditlog.AuditLogPathParamConfig

enum class Tags(override val description: String) : APITag {
    Tilgangkontrollert("Dette endepunktet er tilgangkontrollert.")
}

val tilgangkontrollertTag = TagModule(listOf(Tags.Tilgangkontrollert))

inline fun <reified TParams : Any, reified TResponse : Any> NormalOpenAPIRoute.authorizedGet(
    routeConfig: AuthorizationRouteConfig,
    auditLogConfig: AuditLogPathParamConfig? = null,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams) -> Unit
) {
    when (routeConfig) {
        is RollerConfig -> ktorRoute.installerTilgangRollePlugin(routeConfig)
        is AuthorizationParamPathConfig -> ktorRoute.installerTilgangParamPlugin(routeConfig, if (auditLogConfig == null) null else auditLogConfig as AuditLogPathParamConfig)
        is AuthorizationMachineToMachineConfig -> ktorRoute.installerTilgangMachineToMachinePlugin(routeConfig, auditLogConfig)
        else -> throw IllegalArgumentException("Unsupported routeConfig type for GET: $routeConfig")
    }
    @Suppress("UnauthorizedGet")
    get<TParams, TResponse>(*modules, tilgangkontrollertTag) { params -> body(params) }
}

inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Any> NormalOpenAPIRoute.authorizedPost(
    routeConfig: AuthorizationRouteConfig,
    auditLogConfig: AuditLogConfig? = null,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    when (routeConfig) {
        is AuthorizationParamPathConfig -> ktorRoute.installerTilgangParamPlugin(routeConfig, if (auditLogConfig == null) null else auditLogConfig as AuditLogPathParamConfig)
        is AuthorizationBodyPathConfig -> ktorRoute.installerTilgangBodyPlugin<TRequest>(routeConfig, auditLogConfig)
        is AuthorizationMachineToMachineConfig -> ktorRoute.installerTilgangMachineToMachinePlugin(routeConfig, auditLogConfig)
        is RollerConfig -> ktorRoute.installerTilgangRollePlugin(routeConfig)
        else -> throw IllegalArgumentException("Unsupported routeConfig type for POST: $routeConfig")
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
    routeConfig: AuthorizationRouteConfig,
    auditLogConfig: AuditLogConfig? = null,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    when (routeConfig) {
        is AuthorizationParamPathConfig -> ktorRoute.installerTilgangParamPlugin(routeConfig, if (auditLogConfig == null) null else auditLogConfig as AuditLogPathParamConfig)
        is AuthorizationBodyPathConfig ->ktorRoute.installerTilgangBodyPlugin<TRequest>(routeConfig, auditLogConfig)
        is AuthorizationMachineToMachineConfig -> ktorRoute.installerTilgangMachineToMachinePlugin(routeConfig, auditLogConfig)
        is RollerConfig -> ktorRoute.installerTilgangRollePlugin(routeConfig)
        else -> throw IllegalArgumentException("Unsupported routeConfig type for PUT: $routeConfig")
    }

    @Suppress("UnauthorizedPut")
    put<TParams, TResponse, TRequest>(tilgangkontrollertTag, *modules) { params, request ->
        body(
            params,
            request
        )
    }
}
