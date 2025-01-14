import TilgangPluginTest.Companion.IngenReferanse
import TilgangPluginTest.Companion.Journalpostinfo
import TilgangPluginTest.Companion.Saksinfo
import TilgangPluginTest.Companion.TestReferanse
import TilgangPluginTest.Companion.uuid
import com.papsign.ktor.openapigen.model.info.InfoModel
import com.papsign.ktor.openapigen.route.TagModule
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
import no.nav.aap.tilgang.*
import no.nav.aap.tilgang.auditlog.AuditLogBodyConfig
import no.nav.aap.tilgang.auditlog.AuditLogPathParamConfig
import no.nav.aap.tilgang.auditlog.PathBrukerIdentResolver
import no.nav.aap.tilgang.plugin.kontrakt.AuditlogResolverInput
import no.nav.aap.tilgang.plugin.kontrakt.BrukerIdentResolver
import no.nav.aap.tilgang.plugin.kontrakt.Saksreferanse
import tilgang.Operasjon
import java.util.*
import kotlin.random.Random

fun Application.autorisertEksempelApp() {
    commonKtorModule(PrometheusMeterRegistry(PrometheusConfig.DEFAULT), AzureConfig(), InfoModel())
    routing {
        authenticate(AZURE) {
            apiRouting {
                route("testApi/pathForPost/{behandlingReferanse}") {
                    authorizedPost<Journalpostinfo, IngenReferanse, IngenReferanse>(
                        AuthorizationParamPathConfig(
                            operasjon = Operasjon.SAKSBEHANDLE,
                            avklaringsbehovKode = "1337",
                            journalpostPathParam = JournalpostPathParam(
                                "behandlingReferanse",
                            )
                            { 456 }
                        )
                    ) { _, dto ->
                        respond(
                            dto
                        )
                    }
                }
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
                                applicationRole = "tilgang-rolle",
                            ),
                            modules = arrayOf(TagModule(listOf(Tags.Tilgangkontrollert)))
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
                            AuthorizationParamPathConfig(sakPathParam = SakPathParam("saksnummer")),
                            AuditLogPathParamConfig(
                                logger = log,
                                app = "behandlingsflyt",
                                brukerIdentResolver = PathBrukerIdentResolver(
                                    TestResolver(),
                                    "saksnummer"
                                )
                            )
                        ) { req ->
                            respond(Saksinfo(saksnummer = req.saksnummer))
                        }
                    }
                    route("client-credentials-application-role") {
                        authorizedGet<TestReferanse, Saksinfo>(
                            AuthorizationParamPathConfig(
                                applicationRole = "tilgang-rolle",
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
                                applicationRole = "tilgang-rolle"
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
                                applicationRole = "tilgang-rolle"
                            )
                        ) { _, dto ->
                            respond(Saksinfo(saksnummer = uuid))
                        }
                    }
                    route("client-credentials-application-role") {
                        authorizedPost<Unit, IngenReferanse, IngenReferanse>(
                            AuthorizationBodyPathConfig(
                                operasjon = Operasjon.SAKSBEHANDLE,
                                applicationRole = "tilgang-rolle",
                                applicationsOnly = true
                            )
                        ) { _, dto ->
                            respond(dto)
                        }
                    }
                    route("med-audit-resolver") {
                        authorizedPost<Unit, RequestMedAuditResolver, RequestMedAuditResolver>(
                            AuthorizationBodyPathConfig(
                                operasjon = Operasjon.SAKSBEHANDLE,
                            ),
                            AuditLogBodyConfig(
                                logger = log,
                                app = "behandlingsflyt",
                                brukerIdentResolver = TestResolver()
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

class TestResolver : BrukerIdentResolver {
    override fun resolve(referanse: String): String {
        log.info("Resolving ident for $referanse")
        return "12345678901"
    }
}

class RequestMedAuditResolver(val saksreferanse: UUID) : AuditlogResolverInput, Saksreferanse {
    override fun hentAuditlogResolverInput(): String {
        return saksreferanse.toString()
    }

    override fun hentSaksreferanse(): String {
        return saksreferanse.toString()
    }

}