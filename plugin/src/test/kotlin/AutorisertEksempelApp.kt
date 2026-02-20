import TilgangPluginTest.Companion.IngenReferanse
import TilgangPluginTest.Companion.Journalpostinfo
import TilgangPluginTest.Companion.TestReferanse
import com.fasterxml.jackson.annotation.JsonValue
import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.model.info.InfoModel
import com.papsign.ktor.openapigen.route.TagModule
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.ktor.server.application.Application
import io.ktor.server.auth.*
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
import no.nav.aap.tilgang.plugin.kontrakt.*
import java.util.*
import kotlin.random.Random

val enAnnenReferanseTilbehandlingReferanse = mutableMapOf<String, UUID>()

fun Application.autorisertEksempelApp() {
    commonKtorModule(PrometheusMeterRegistry(PrometheusConfig.DEFAULT), AzureConfig(), InfoModel())
    routing {
        authenticate(AZURE) {
            apiRouting {
                route("kun-roller") {
                    authorizedGet<Unit, IngenReferanse>(
                        RollerConfig(
                            listOf(Beslutter)
                        )
                    ) {
                        respond(IngenReferanse("test"))
                    }
                }

                route("testApi/getGrunnlag/{referanse}") {
                    getGrunnlag<BehandlingReferanse, Boolean>(
                        behandlingPathParam = BehandlingPathParam(
                            param = "referanse"
                        ),
                        avklaringsbehovKode = "1234"
                    ) { _ ->
                        val kanSaksbehandle = pipeline.call.attributes[kanSaksbehandleKey]
                        if (kanSaksbehandle == "true") {
                            respond(true)
                            return@getGrunnlag
                        } else {
                            respond(false)
                        }
                    }
                }
                route("testApi/pathForPost/resolve") {
                    route("behandlingreferanse/{enAnnenReferanse}") {
                        authorizedPost<EnAnnenReferanse, IngenReferanse, IngenReferanse>(
                            AuthorizationParamPathConfig(
                                operasjon = Operasjon.SAKSBEHANDLE,
                                avklaringsbehovKode = "1337",
                                relevanteIdenterResolver = {
                                    RelevanteIdenter(
                                        søker = listOf("1234"),
                                        barn = listOf("3456")
                                    )
                                },
                                behandlingPathParam = BehandlingPathParam(
                                    param = "enAnnenReferanse",
                                    resolver = { enAnnenReferanseTilbehandlingReferanse.getValue(it) })
                            )
                        ) { _, dto ->
                            respond(
                                dto
                            )
                        }
                    }
                    route("journalpost/{behandlingReferanse}") {
                        authorizedPost<Journalpostinfo, IngenReferanse, IngenReferanse>(
                            AuthorizationParamPathConfig(
                                operasjon = Operasjon.SAKSBEHANDLE,
                                avklaringsbehovKode = "1337",
                                journalpostPathParam = JournalpostPathParam("behandlingReferanse") { 456 }
                            )
                        ) { _, dto ->
                            respond(
                                dto
                            )
                        }
                    }
                    route("hello") {
                        authorizedPost<Unit, IngenReferanse, IngenReferanse>(
                            NoAuthConfig
                        ) { _, dto ->
                            respond(
                                dto
                            )
                        }
                    }
                }
                route("testApi/person") {
                    route("post") {
                        authorizedPost<Unit, Personinfo, Personinfo>(
                            AuthorizationBodyPathConfig(
                                operasjon = Operasjon.SE
                            )
                        ) { _, dto ->
                            respond(dto)
                        }
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
                        ) { _ ->
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
                        ) { _, _ ->
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
                    route("application-role-machine-to-machine") {
                        authorizedGet<TestReferanse, Saksinfo>(
                            AuthorizationMachineToMachineConfig(
                                authorizedRoles = listOf("tilgang-rolle")
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
                    route("on-behalf-of/saksinfo") {
                        authorizedPost<Unit, Saksinfo, Saksinfo>(
                            AuthorizationBodyPathConfig(operasjon = Operasjon.SAKSBEHANDLE)
                        ) { _, dto ->
                            respond(dto)
                        }
                    }

                    route("on-behalf-of/behandlinginfo") {
                        authorizedPost<Unit, Behandlinginfo, Behandlinginfo>(
                            AuthorizationBodyPathConfig(
                                operasjon = Operasjon.SAKSBEHANDLE,
                                behandlingreferanseResolver = { enAnnenReferanseTilbehandlingReferanse.getValue(it) })
                        ) { _, dto ->
                            respond(dto)
                        }
                    }
                    route("client-credentials-and-on-behalf-of") {
                        authorizedPost<Unit, Saksinfo, Saksinfo>(
                            AuthorizationBodyPathConfig(
                                operasjon = Operasjon.SAKSBEHANDLE,
                                applicationRole = "tilgang-rolle"
                            )
                        ) { _, dto ->
                            respond(dto)
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

data class Saksinfo(val saksnummer: UUID) : Saksreferanse {
    override fun hentSaksreferanse(): String {
        return saksnummer.toString()
    }
}

data class Personinfo(val personIdent: String) : Personreferanse {
    override fun hentPersonreferanse(): String {
        return personIdent
    }
}

data class Behandlinginfo(val enAnnenReferanse: String) : Behandlingsreferanse {
    override fun behandlingsreferanseResolverInput(): String {
        return enAnnenReferanse
    }

    override fun hentAvklaringsbehovKode(): String? {
        return null
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

data class EnAnnenReferanse(@param:PathParam("enAnnenReferanse") val enAnnenReferanse: String)
data class BehandlingReferanse(@JsonValue @param:PathParam("referanse") val referanse: UUID = UUID.randomUUID()) {
    override fun toString(): String {
        return referanse.toString()
    }
}
