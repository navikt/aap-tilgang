package no.nav.aap.tilgang

import com.papsign.ktor.openapigen.APITag
import com.papsign.ktor.openapigen.modules.RouteOpenAPIModule
import com.papsign.ktor.openapigen.route.TagModule
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.path.normal.put
import com.papsign.ktor.openapigen.route.response.OpenAPIPipelineResponseContext
import io.ktor.http.*
import io.ktor.server.routing.*
import no.nav.aap.tilgang.auditlog.AuditLogBodyConfig
import no.nav.aap.tilgang.auditlog.AuditLogConfig
import no.nav.aap.tilgang.auditlog.AuditLogPathParamConfig
import org.jetbrains.annotations.ApiStatus
import no.nav.aap.tilgang.config.AuthorizationBodyPathConfig
import no.nav.aap.tilgang.config.AuthorizationMachineToMachineConfig
import no.nav.aap.tilgang.config.AuthorizationParamPathConfig
import no.nav.aap.tilgang.config.AuthorizationRouteConfig
import no.nav.aap.tilgang.config.BehandlingPathParam
import no.nav.aap.tilgang.config.NoAuthConfig
import no.nav.aap.tilgang.config.RollerConfig
import no.nav.aap.tilgang.resolver.RelevanteIdenterResolver

public enum class Tags(override val description: String) : APITag {
    Tilgangkontrollert("Dette endepunktet er tilgangkontrollert.")
}

public val tilgangkontrollertTag: TagModule = TagModule(listOf(Tags.Tilgangkontrollert))

public inline fun <reified TParams : Any, reified TResponse : Any> NormalOpenAPIRoute.authorizedGet(
    routeConfig: AuthorizationRouteConfig,
    auditLogConfig: AuditLogPathParamConfig? = null,
    exampleResponse: TResponse? = null,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams) -> Unit
) {
    when (routeConfig) {
        is RollerConfig -> ktorRoute.installerTilgangRollePlugin(routeConfig, HttpMethod.Get)
        is NoAuthConfig -> håndterNoAuth(this.ktorRoute, auditLogConfig)
        is AuthorizationParamPathConfig -> ktorRoute.installerTilgangParamPlugin(
            routeConfig,
            if (auditLogConfig == null) null else auditLogConfig,
            HttpMethod.Get
        )

        is AuthorizationMachineToMachineConfig -> ktorRoute.installerTilgangMachineToMachinePlugin(
            routeConfig,
            auditLogConfig,
            HttpMethod.Get
        )

        else -> throw IllegalArgumentException("Unsupported routeConfig type for GET: $routeConfig")
    }
    @Suppress("UnauthorizedGet")
    get<TParams, TResponse>(
        example = exampleResponse,
        modules = arrayOf(*modules, tilgangkontrollertTag)
    ) { params ->
        body(
            params
        )
    }
}

public inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Any> NormalOpenAPIRoute.authorizedPost(
    routeConfig: AuthorizationRouteConfig,
    auditLogConfig: AuditLogConfig? = null,
    exampleResponse: TResponse? = null,
    exampleRequest: TRequest? = null,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    when (routeConfig) {
        is AuthorizationParamPathConfig -> ktorRoute.installerTilgangParamPlugin(
            routeConfig,
            if (auditLogConfig == null) null else auditLogConfig as AuditLogPathParamConfig,
            HttpMethod.Post
        )

        is AuthorizationBodyPathConfig<*> -> {
            @Suppress("UNCHECKED_CAST")
            ktorRoute.installerTilgangBodyPlugin(
                routeConfig as AuthorizationBodyPathConfig<TRequest>,
                auditLogConfig,
                HttpMethod.Post
            )
        }

        is AuthorizationMachineToMachineConfig -> ktorRoute.installerTilgangMachineToMachinePlugin(
            routeConfig,
            auditLogConfig,
            HttpMethod.Post
        )

        is RollerConfig -> ktorRoute.installerTilgangRollePlugin(routeConfig, HttpMethod.Post)
        is NoAuthConfig -> håndterNoAuth(this.ktorRoute, auditLogConfig)
        else -> throw IllegalArgumentException("Unsupported routeConfig type for POST: $routeConfig")
    }

    @Suppress("UnauthorizedPost")
    post<TParams, TResponse, TRequest>(
        exampleResponse = exampleResponse,
        exampleRequest = exampleRequest,
        modules = arrayOf(
            tilgangkontrollertTag,
            *modules
        )
    ) { params, request ->
        body(
            params,
            request
        )
    }
}

/**
 * Som [authorizedPost] over, men typet mot request body slik at [AuthorizationBodyPathConfig.kontekst]
 * og [AuditLogBodyConfig.resolverInput] får [TRequest] som parameter.
 */
public inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Any> NormalOpenAPIRoute.authorizedPost(
    routeConfig: AuthorizationBodyPathConfig<TRequest>,
    auditLogConfig: AuditLogBodyConfig<TRequest>? = null,
    exampleResponse: TResponse? = null,
    exampleRequest: TRequest? = null,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    val config: AuthorizationRouteConfig = routeConfig
    val audit: AuditLogConfig? = auditLogConfig
    authorizedPost<TParams, TResponse, TRequest>(config, audit, exampleResponse, exampleRequest, *modules, body = body)
}

/**
 * Som typet [authorizedPost] over, men med auditlogging fra path-parameter.
 */
public inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Any> NormalOpenAPIRoute.authorizedPost(
    routeConfig: AuthorizationBodyPathConfig<TRequest>,
    auditLogConfig: AuditLogPathParamConfig,
    exampleResponse: TResponse? = null,
    exampleRequest: TRequest? = null,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    val config: AuthorizationRouteConfig = routeConfig
    val audit: AuditLogConfig = auditLogConfig
    authorizedPost<TParams, TResponse, TRequest>(config, audit, exampleResponse, exampleRequest, *modules, body = body)
}

/**
 * Fanger [AuthorizationBodyPathConfig] eller [AuditLogBodyConfig] med annen type enn [TRequest] ved kompilering.
 */
@Deprecated(BODY_TYPE_MISMATCH, level = DeprecationLevel.ERROR)
@Suppress("UNUSED_PARAMETER", "UnusedReceiverParameter")
public fun <TParams : Any, TResponse : Any, TRequest : Any> NormalOpenAPIRoute.authorizedPost(
    routeConfig: AuthorizationBodyPathConfig<*>,
    auditLogConfig: AuditLogConfig? = null,
    exampleResponse: TResponse? = null,
    exampleRequest: TRequest? = null,
    vararg modules: RouteOpenAPIModule,
    body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
): Unit = throw UnsupportedOperationException(BODY_TYPE_MISMATCH)

private const val BODY_TYPE_MISMATCH: String =
    "Typeparameteren til AuthorizationBodyPathConfig/AuditLogBodyConfig må være lik request body-typen (TRequest)"

public inline fun <reified TParams : Any, reified TResponse : Any> NormalOpenAPIRoute.getGrunnlag(
    behandlingPathParam: BehandlingPathParam,
    avklaringsbehovKode: String,
    relevanteIdenterResolver: RelevanteIdenterResolver? = null,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams) -> Unit
) {
    ktorRoute.installerTilgangParamPlugin(
        AuthorizationParamPathConfig(
            behandlingPathParam = behandlingPathParam,
            avklaringsbehovKode = avklaringsbehovKode,
            relevanteIdenterResolver = relevanteIdenterResolver,
            operasjonerIKontekst = listOf(
                Operasjon.SAKSBEHANDLE
            )
        ), null, HttpMethod.Get
    )

    @Suppress("UnauthorizedGet")
    get<TParams, TResponse>(*modules, tilgangkontrollertTag) { params -> body(params) }
}

public inline fun <reified TParams : Any, reified TResponse : Any> NormalOpenAPIRoute.getGrunnlag(
    behandlingPathParam: BehandlingPathParam,
    påkrevdRolle: List<Rolle>,
    relevanteIdenterResolver: RelevanteIdenterResolver? = null,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams) -> Unit
) {
    ktorRoute.installerTilgangParamPlugin(
        AuthorizationParamPathConfig(
            behandlingPathParam = behandlingPathParam,
            påkrevdRolle = påkrevdRolle,
            relevanteIdenterResolver = relevanteIdenterResolver,
            operasjonerIKontekst = listOf(
                Operasjon.SAKSBEHANDLE
            )
        ), null, HttpMethod.Get
    )

    @Suppress("UnauthorizedGet")
    get<TParams, TResponse>(*modules, tilgangkontrollertTag) { params -> body(params) }
}

public inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Any> NormalOpenAPIRoute.authorizedPut(
    routeConfig: AuthorizationRouteConfig,
    auditLogConfig: AuditLogConfig? = null,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    when (routeConfig) {
        is AuthorizationParamPathConfig -> ktorRoute.installerTilgangParamPlugin(
            routeConfig,
            if (auditLogConfig == null) null else auditLogConfig as AuditLogPathParamConfig,
            HttpMethod.Put
        )

        is AuthorizationBodyPathConfig<*> -> {
            @Suppress("UNCHECKED_CAST")
            ktorRoute.installerTilgangBodyPlugin(
                routeConfig as AuthorizationBodyPathConfig<TRequest>,
                auditLogConfig,
                HttpMethod.Put
            )
        }
        is AuthorizationMachineToMachineConfig -> ktorRoute.installerTilgangMachineToMachinePlugin(
            routeConfig,
            auditLogConfig,
            HttpMethod.Put
        )

        is RollerConfig -> ktorRoute.installerTilgangRollePlugin(routeConfig, HttpMethod.Put)
        is NoAuthConfig -> håndterNoAuth(this.ktorRoute, auditLogConfig)

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

/**
 * Som [authorizedPut] over, men typet mot request body slik at [AuthorizationBodyPathConfig.kontekst]
 * og [AuditLogBodyConfig.resolverInput] får [TRequest] som parameter.
 */
public inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Any> NormalOpenAPIRoute.authorizedPut(
    routeConfig: AuthorizationBodyPathConfig<TRequest>,
    auditLogConfig: AuditLogBodyConfig<TRequest>? = null,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    val config: AuthorizationRouteConfig = routeConfig
    val audit: AuditLogConfig? = auditLogConfig
    authorizedPut<TParams, TResponse, TRequest>(config, audit, *modules, body = body)
}

/**
 * Som typet [authorizedPut] over, men med auditlogging fra path-parameter.
 */
public inline fun <reified TParams : Any, reified TResponse : Any, reified TRequest : Any> NormalOpenAPIRoute.authorizedPut(
    routeConfig: AuthorizationBodyPathConfig<TRequest>,
    auditLogConfig: AuditLogPathParamConfig,
    vararg modules: RouteOpenAPIModule,
    noinline body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
) {
    val config: AuthorizationRouteConfig = routeConfig
    val audit: AuditLogConfig = auditLogConfig
    authorizedPut<TParams, TResponse, TRequest>(config, audit, *modules, body = body)
}

/**
 * Fanger [AuthorizationBodyPathConfig] eller [AuditLogBodyConfig] med annen type enn [TRequest] ved kompilering.
 */
@Deprecated(BODY_TYPE_MISMATCH, level = DeprecationLevel.ERROR)
@Suppress("UNUSED_PARAMETER", "UnusedReceiverParameter")
public fun <TParams : Any, TResponse : Any, TRequest : Any> NormalOpenAPIRoute.authorizedPut(
    routeConfig: AuthorizationBodyPathConfig<*>,
    auditLogConfig: AuditLogConfig? = null,
    vararg modules: RouteOpenAPIModule,
    body: suspend OpenAPIPipelineResponseContext<TResponse>.(TParams, TRequest) -> Unit
): Unit = throw UnsupportedOperationException(BODY_TYPE_MISMATCH)

@ApiStatus.Internal
public fun håndterNoAuth(route: Route, auditLogConfig: AuditLogConfig?) {
    log.info("Installerer ikke tilgangskontroll for $route")
    if (auditLogConfig != null) {
        log.warn("Auditlogging er ikke implementert for NoAuthConfig - ignorerer AuditLogConfig")
    }
}
