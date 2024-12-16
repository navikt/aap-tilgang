import TilgangPluginTest.Companion.IngenReferanse
import TilgangPluginTest.Companion.Journalpostinfo
import TilgangPluginTest.Companion.Saksinfo
import TilgangPluginTest.Companion.TestReferanse
import TilgangPluginTest.Companion.uuid
import com.papsign.ktor.openapigen.model.info.InfoModel
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.server.AZURE
import no.nav.aap.komponenter.server.commonKtorModule
import no.nav.aap.tilgang.AuthorizationBodyPathConfig
import no.nav.aap.tilgang.AuthorizationParamPathConfig
import no.nav.aap.tilgang.JournalpostPathParam
import no.nav.aap.tilgang.SakPathParam
import no.nav.aap.tilgang.authorizedGet
import no.nav.aap.tilgang.authorizedPost
import tilgang.Operasjon
import kotlin.random.Random

fun Application.autorisertEksempelApp() {
    commonKtorModule(PrometheusMeterRegistry(PrometheusConfig.DEFAULT), AzureConfig(), InfoModel())
    routing {
        authenticate(AZURE) {
            apiRouting {
                route("testApi/journalpost") {
                    route("{behandlingReferanse}") {
                        authorizedGet<Journalpostinfo, Long>(
                            AuthorizationParamPathConfig(
                                journalpostPathParam = JournalpostPathParam(
                                    "behandlingReferanse",
                                )
                                { Random.nextLong() }
                            )
                        ) { req ->
                            respond(Random.nextLong())
                        }
                    }
                    route("post") {
                        authorizedPost<Unit, Long, Journalpostinfo>(
                            AuthorizationBodyPathConfig(
                                operasjon = Operasjon.SAKSBEHANDLE,
                                approvedApplications = setOf("azp")
                            )
                        ) { _, dto ->
                            respond(
                                Random.nextLong()
                            )
                        }
                    }
                }
                route("testApi/authorizedGet/{saksnummer}") {
                    route("on-behalf-of") {
                        authorizedGet<TestReferanse, Saksinfo>(
                            AuthorizationParamPathConfig(sakPathParam = SakPathParam("saksnummer"))
                        ) { req ->
                            respond(Saksinfo(saksnummer = req.saksnummer))
                        }
                    }
                    route("client-credentials") {
                        authorizedGet<TestReferanse, Saksinfo>(
                            AuthorizationParamPathConfig(
                                approvedApplications = setOf("azp"),
                                applicationsOnly = true
                            )
                        ) { req ->
                            respond(Saksinfo(saksnummer = req.saksnummer))
                        }
                    }
                    route("client-credentials-and-on-behalf-of") {
                        authorizedGet<TestReferanse, Saksinfo>(
                            AuthorizationParamPathConfig(
                                sakPathParam = SakPathParam("saksnummer"),
                                approvedApplications = setOf("azp")
                            )
                        ) { req ->
                            respond(Saksinfo(saksnummer = req.saksnummer))
                        }
                    }
                }
                route("testApi/authorizedPost") {
                    route("on-behalf-of") {
                        authorizedPost<Unit, Saksinfo, Saksinfo>(
                            AuthorizationBodyPathConfig(operasjon = Operasjon.SAKSBEHANDLE)
                        ) { _, dto ->
                            respond(Saksinfo(saksnummer = uuid))
                        }
                    }
                    route("client-credentials-and-on-behalf-of") {
                        authorizedPost<Unit, Saksinfo, Saksinfo>(
                            AuthorizationBodyPathConfig(
                                operasjon = Operasjon.SAKSBEHANDLE,
                                approvedApplications = setOf("azp")
                            )
                        ) { _, dto ->
                            respond(Saksinfo(saksnummer = uuid))
                        }
                    }
                    route("client-credentials") {
                        authorizedPost<Unit, IngenReferanse, IngenReferanse>(
                            AuthorizationBodyPathConfig(
                                operasjon = Operasjon.SAKSBEHANDLE,
                                approvedApplications = setOf("azp"),
                                applicationsOnly = true
                            )
                        ) { _, dto ->
                            respond(dto)
                        }
                    }
                }
            }
        }
    }
}