package tilgang

import com.papsign.ktor.openapigen.model.info.InfoModel
import com.papsign.ktor.openapigen.route.apiRouting
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlin.time.Duration.Companion.seconds
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.server.auth.IdentityProvider
import no.nav.aap.komponenter.server.commonKtorModule
import org.slf4j.LoggerFactory
import tilgang.auth.TexasGateway
import tilgang.auth.TokenProvider
import tilgang.auth.createTexasClient
import tilgang.foresporsel.Persontilgang
import tilgang.foresporsel.TilgangsforespørselService
import tilgang.grunnlag.IdentOppslag
import tilgang.grunnlag.JournalpostInfo
import tilgang.grunnlag.JournalpostOppslag
import tilgang.grunnlag.TilgangsgrunnlagService
import tilgang.http.StatusPagesConfigHelper
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytGateway
import tilgang.integrasjoner.behandlingsflyt.createBehandlingsflytClient
import tilgang.integrasjoner.msgraph.AdressebeskyttelseGruppe
import tilgang.integrasjoner.msgraph.AdressebeskyttelseService
import tilgang.integrasjoner.msgraph.MsGraphGateway
import tilgang.integrasjoner.msgraph.createMsGraphClient
import tilgang.integrasjoner.pdl.Gradering
import tilgang.integrasjoner.pdl.IPdlGraphQLGateway
import tilgang.integrasjoner.pdl.PdlGraphQLGateway
import tilgang.integrasjoner.pdl.createPdlClient
import tilgang.integrasjoner.saf.BrukerIdType
import tilgang.integrasjoner.saf.SafGraphqlGateway
import tilgang.integrasjoner.saf.createSafClient
import tilgang.integrasjoner.tilgangsmaskin.BrukerOgRegeltype
import tilgang.integrasjoner.tilgangsmaskin.ITilgangsmaskinGateway
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistGrunn
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import tilgang.integrasjoner.tilgangsmaskin.createTilgangsmaskinClient
import tilgang.kontroll.BrukerToken
import tilgang.kontroll.RelevanteIdenter
import tilgang.kontroll.Personregel
import tilgang.kontroll.TilgangService
import tilgang.kontroll.Tilgangskontekst
import tilgang.kontroll.regler.AdressebeskyttelseOppslag
import tilgang.kontroll.regler.Adressebeskyttelsesrolle
import tilgang.kontroll.regler.Adressebeskyttelsesroller
import tilgang.kontroll.regler.Adressegradering
import tilgang.kontroll.regler.Habilitetsgrunnlag
import tilgang.kontroll.regler.Personkontroll
import tilgang.mapping.tilDomene
import tilgang.redis.Redis
import tilgang.routes.actuator
import tilgang.routes.tilgang


internal object AppConfig {
    // Matcher terminationGracePeriodSeconds for podden i Kubernetes-manifestet ("nais.yaml")
    private val kubernetesTimeout = 20.seconds

    // Tid før ktor avslutter uansett. Må være litt mindre enn `kubernetesTimeout`.
    val shutdownTimeout = kubernetesTimeout - 2.seconds

    // Tid appen får til å fullføre påbegynte requests, jobber etc. Må være mindre enn `endeligShutdownTimeout`.
    val shutdownGracePeriod = shutdownTimeout - 3.seconds

    // Vi skrur opp ktor sin default-verdi, som er "antall CPUer", satt ved -XX:ActiveProcessorCount i Dockerfile,
    // fordi appen vår er veldig I/O-bound når den venter på svar fra andre tjenester.
    private const val ktorParallellitet = 4

    // Vi følger ktor sin metodikk for å regne ut tuning parametre som funksjon av parallellitet
    // https://github.com/ktorio/ktor/blob/3.3.1/ktor-server/ktor-server-core/common/src/io/ktor/server/engine/ApplicationEngine.kt#L30
    const val connectionGroupSize = ktorParallellitet / 2 + 1
    const val workerGroupSize = ktorParallellitet / 2 + 1

    // Vi bruker nå suspend-funksjoner for all I/O, så vi trenger ikke flere tråder enn parallelliteten.
    const val callGroupSize = ktorParallellitet

    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
}

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        LoggerFactory.getLogger("aap-tilgang").error("Uhåndtert feil", e)
    }

    embeddedServer(
        Netty,
        configure = {
            connectionGroupSize = AppConfig.connectionGroupSize
            workerGroupSize = AppConfig.workerGroupSize
            callGroupSize = AppConfig.callGroupSize

            shutdownGracePeriod = AppConfig.shutdownGracePeriod.inWholeMilliseconds
            shutdownTimeout = AppConfig.shutdownTimeout.inWholeMilliseconds
            connector {
                port = System.getenv("PORT")?.toInt() ?: 8080
            }
        },
        module = Application::api
    ).start(wait = true)
}

fun Application.api(
    config: Config = Config(),
    redis: Redis? = null,
) {
    api(config, redis, Redis::from)
}

internal fun Application.api(
    config: Config,
    redis: Redis?,
    redisFactory: (RedisConfig) -> Redis,
) {
    val ownsRedis = redis == null
    val redisClient = redis ?: redisFactory(config.redis)
    val prometheus = AppConfig.prometheus

    val texasTokenClient = createTexasClient(config.texas.tokenEndpoint)
    val texasExchangeClient = createTexasClient(config.texas.exchangeEndpoint)
    val tokenProvider = TokenProvider(TexasGateway(texasTokenClient, texasExchangeClient), prometheus)

    val pdlClient = createPdlClient(config.pdl, tokenProvider)
    val pdl = PdlGraphQLGateway(redisClient, pdlClient, prometheus)
    val msGraphClient = createMsGraphClient(config.msGraph, tokenProvider)
    val msGraph = MsGraphGateway(redisClient, msGraphClient, prometheus)
    val behandlingsflytClient = createBehandlingsflytClient(config.behandlingsflyt, tokenProvider)
    val behandlingsflyt = BehandlingsflytGateway(redisClient, behandlingsflytClient, prometheus)
    val safClient = createSafClient(config.saf, tokenProvider)
    val saf = SafGraphqlGateway(redisClient, safClient, prometheus)
    val tilgangsmaskinClient = createTilgangsmaskinClient(config.tilgangsmaskin, tokenProvider)
    val tilgangsmaskinGateway = TilgangsmaskinGateway(redisClient, tilgangsmaskinClient, prometheus)
    val tilgangService = createTilgangService(pdl, AdressebeskyttelseService(msGraph), tilgangsmaskinGateway)
    val forespørselService =
        createTilgangsforespørselService(saf, behandlingsflyt, tilgangService, tilgangsmaskinGateway)

    install(StatusPages, StatusPagesConfigHelper.setup(prometheus))

    commonKtorModule(
        prometheus = prometheus,
        infoModel = InfoModel(title = "AAP - Tilgang"),
        identityProvider = IdentityProvider.ENTRA_ID
    )

    routing {
        actuator(prometheus, redisClient)

        authenticate(IdentityProvider.ENTRA_ID.value) {
            apiRouting {
                tilgang(forespørselService, config.roles, prometheus)
            }
        }
    }

    monitor.subscribe(ApplicationStopPreparing) { environment ->
        environment.log.info("ktor forbereder seg på å stoppe.")
    }
    monitor.subscribe(ApplicationStopping) { environment ->
        environment.log.info("ktor stopper nå å ta imot nye requester, og lar mottatte requester kjøre frem til timeout.")
    }
    monitor.subscribe(ApplicationStopped) { environment ->
        pdlClient.close()
        msGraphClient.close()
        behandlingsflytClient.close()
        safClient.close()
        tilgangsmaskinClient.close()
        tokenProvider.close()
        texasTokenClient.close()
        texasExchangeClient.close()
        if (ownsRedis) redisClient.close()
        environment.log.info("ktor har fullført nedstoppingen sin. Eventuelle requester og annet arbeid som ikke ble fullført innen timeout ble avbrutt.")
    }
}

internal fun createTilgangsforespørselService(
    saf: SafGraphqlGateway,
    behandlingsflyt: BehandlingsflytGateway,
    tilgangService: TilgangService,
    tilgangsmaskin: TilgangsmaskinGateway,
): TilgangsforespørselService {
    val log = LoggerFactory.getLogger(TilgangService::class.java)
    val grunnlagService = TilgangsgrunnlagService(
        identOppslag = object : IdentOppslag {
            override suspend fun hentIdenterForSak(saksnummer: String): RelevanteIdenter =
                behandlingsflyt.hentIdenterForSak(saksnummer).tilDomene()

            override suspend fun hentIdenterForBehandling(behandlingsnummer: String): RelevanteIdenter =
                behandlingsflyt.hentIdenterForBehandling(behandlingsnummer).tilDomene()
        },
        journalpostOppslag = object : JournalpostOppslag {
            override suspend fun hentJournalpostInfo(journalpostId: Long, callId: String): JournalpostInfo {
                val journalpost = saf.hentJournalpostInfo(journalpostId, callId)
                return JournalpostInfo(
                    journalpostId = journalpost.journalpostId,
                    søkerIdent = journalpost.bruker?.id,
                    gjelderOrganisasjon = journalpost.bruker?.type == BrukerIdType.ORGNR,
                    saksnummer = journalpost.sak?.takeIf { it.fagsaksystem == "KELVIN" }?.fagsakId,
                )
            }
        },
        log = log,
    )
    val persontilgang = object : Persontilgang {
        override suspend fun harTilgangTilPerson(
            brukerIdent: String,
            token: BrukerToken,
            callId: String,
        ): Boolean = tilgangsmaskin.harTilgangTilPerson(brukerIdent, OidcToken(token.verdi), callId)

        override suspend fun harTilganger(
            brukerIdenter: List<Personregel>,
            token: BrukerToken,
            callId: String,
        ): Boolean = tilgangsmaskin.harTilganger(
            brukerIdenter.map { BrukerOgRegeltype(it.personIdent, it.regeltype) },
            OidcToken(token.verdi),
            callId,
        )
    }
    return TilgangsforespørselService(grunnlagService, tilgangService, persontilgang, log)
}

internal fun createTilgangService(
    pdl: IPdlGraphQLGateway,
    adressebeskyttelseService: AdressebeskyttelseService,
    tilgangsmaskin: ITilgangsmaskinGateway,
): TilgangService = TilgangService(
    adressebeskyttelseOppslag = object : AdressebeskyttelseOppslag {
        override suspend fun hentGraderinger(personIdenter: List<String>, callId: String): List<Adressegradering> =
            requireNotNull(pdl.hentPersonBolk(personIdenter, callId)).flatMap { person ->
                person.adressebeskyttelse.map { gradering ->
                    when (gradering) {
                        Gradering.FORTROLIG -> Adressegradering.FORTROLIG
                        Gradering.STRENGT_FORTROLIG_UTLAND -> Adressegradering.STRENGT_FORTROLIG_UTLAND
                        Gradering.STRENGT_FORTROLIG -> Adressegradering.STRENGT_FORTROLIG
                        Gradering.UGRADERT -> Adressegradering.UGRADERT
                    }
                }
            }
    },
    adressebeskyttelsesroller = object : Adressebeskyttelsesroller {
        override suspend fun hentRoller(token: BrukerToken, ansattIdent: String): List<Adressebeskyttelsesrolle> =
            adressebeskyttelseService.hentAdressebeskyttelseRoller(OidcToken(token.verdi), ansattIdent).map { rolle ->
                when (rolle) {
                    AdressebeskyttelseGruppe.STRENGT_FORTROLIG_ADRESSE ->
                        Adressebeskyttelsesrolle.STRENGT_FORTROLIG_ADRESSE
                    AdressebeskyttelseGruppe.FORTROLIG_ADRESSE -> Adressebeskyttelsesrolle.FORTROLIG_ADRESSE
                }
            }
    },
    personkontroll = object : Personkontroll {
        override suspend fun hentHabilitetsgrunnlag(
            personIdent: String,
            kontekst: Tilgangskontekst,
        ): Habilitetsgrunnlag {
            val resultat = tilgangsmaskin.harTilgangTilPersonKjerne(
                personIdent, OidcToken(kontekst.brukerToken.verdi), kontekst.ansattIdent, kontekst.callId
            )
            return Habilitetsgrunnlag(
                harTilgang = resultat.harTilgang,
                avvistForHabilitet = resultat.tilgangsmaskinAvvistResponse?.title ==
                        TilgangsmaskinAvvistGrunn.AVVIST_HABILITET.toString(),
            )
        }

        override suspend fun harTilgangTilPerson(personIdent: String, kontekst: Tilgangskontekst): Boolean =
            tilgangsmaskin.harTilgangTilPersonKomplett(
                personIdent, OidcToken(kontekst.brukerToken.verdi), kontekst.ansattIdent, kontekst.callId
            ).harTilgang
    },
)