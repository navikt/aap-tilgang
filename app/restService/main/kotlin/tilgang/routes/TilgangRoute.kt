package tilgang.routes

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.OpenAPIPipelineContext
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.server.auth.token
import no.nav.aap.tilgang.ArenasakTilgangRequest
import no.nav.aap.tilgang.BehandlingTilgangRequest
import no.nav.aap.tilgang.JournalpostTilgangRequest
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.PersonTilgangRequest
import no.nav.aap.tilgang.Rolle
import no.nav.aap.tilgang.SakTilgangRequest
import no.nav.aap.tilgang.TilbakekrevingTilgangRequest
import no.nav.aap.tilgang.TilgangResponse
import tilgang.Role
import tilgang.auth.ident
import tilgang.auth.roller
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinRequest
import tilgang.kontroll.ArenasakstilgangInput
import tilgang.kontroll.BehandlingstilgangInput
import tilgang.kontroll.BrukerToken
import tilgang.kontroll.JournalposttilgangInput
import tilgang.kontroll.Personregel
import tilgang.kontroll.PersontilgangInput
import tilgang.kontroll.SakstilgangInput
import tilgang.kontroll.TilbakekrevingstilgangInput
import tilgang.kontroll.TilgangService
import tilgang.kontroll.Tilgangskontekst
import tilgang.metrics.httpCallCounter
import tilgang.metrics.nektetTilgangTeller

private val log = org.slf4j.LoggerFactory.getLogger("tilgang.routes.TilgangRoute")

fun NormalOpenAPIRoute.tilgang(
    tilgangService: TilgangService,
    roles: List<Role>,
    prometheus: PrometheusMeterRegistry
) {
    route("/tilgang") {
        route("/sak") {
            post<Unit, TilgangResponse, SakTilgangRequest> { _, req ->
                prometheus.httpCallCounter(pipeline.call).increment()

                val kontekst = tilgangskontekst(roles)

                val input = SakstilgangInput(
                    saksnummer = req.saksnummer,
                    påkrevdRolle = req.påkrevdRolle.orEmpty(),
                    operasjon = req.operasjon,
                    relevanteIdenter = req.relevanteIdenter,
                )
                val harTilgang =
                    tilgangService.harTilgangTilSak(input, kontekst)

                if (!harTilgang) {
                    prometheus.nektetTilgangTeller("sak").increment()
                }

                respond(TilgangResponse(harTilgang))
            }
        }
        route("/behandling") {
            post<Unit, TilgangResponse, BehandlingTilgangRequest> { _, req ->
                prometheus.httpCallCounter(pipeline.call).increment()

                val kontekst = tilgangskontekst(roles)

                val input = BehandlingstilgangInput(
                    behandlingsreferanse = req.behandlingsreferanse,
                    avklaringsbehovKode = req.avklaringsbehovKode,
                    påkrevdRolle = req.påkrevdRolle.orEmpty(),
                    relevanteIdenter = req.relevanteIdenter,
                )
                val harTilgang =
                    tilgangService.harTilgangTilBehandling(input, kontekst)

                if (harTilgang[req.operasjon] != true) {
                    prometheus.nektetTilgangTeller("behandling").increment()
                }

                respond(TilgangResponse(harTilgang[req.operasjon] == true, harTilgang))
            }
        }

        route("/arenasaker") {
            // Brukes av Arena-visningsklienten for å sjekke om en bruker har tilgang til å se arenasaker for en person
            post<Unit, TilgangResponse, ArenasakTilgangRequest> { _, req ->
                prometheus.httpCallCounter(pipeline.call).increment()

                val kontekst = tilgangskontekst(roles)

                val input = ArenasakstilgangInput(
                    personIdent = req.personIdent,
                    påkrevdRolle = req.påkrevdRolle,
                    operasjon = req.operasjon,
                )
                val harTilgang =
                    tilgangService.harTilgangTilArenasakerForPerson(input, kontekst)

                if (!harTilgang) {
                    prometheus.nektetTilgangTeller("arensaker").increment()
                }

                respond(TilgangResponse(harTilgang))
            }
        }

        route("/journalpost") {
            post<Unit, TilgangResponse, JournalpostTilgangRequest> { _, req ->
                prometheus.httpCallCounter(pipeline.call).increment()

                if (req.operasjon == Operasjon.SAKSBEHANDLE && req.avklaringsbehovKode == null && req.påkrevdRolle.isNullOrEmpty()) {
                    log.info("Kan ikke saksbehandle uten avklaringsbehov eller påkrevd rolle $req")
                    respondWithStatus(HttpStatusCode.BadRequest)
                }

                val kontekst = tilgangskontekst(roles)

                val input = JournalposttilgangInput(
                    journalpostId = req.journalpostId,
                    avklaringsbehovKode = req.avklaringsbehovKode,
                    påkrevdRolle = req.påkrevdRolle.orEmpty(),
                    operasjon = req.operasjon,
                )
                val harTilgang =
                    tilgangService.harTilgangTilJournalpost(input, kontekst)

                if (!harTilgang) {
                    prometheus.nektetTilgangTeller("journalpost").increment()
                }

                respond(TilgangResponse(harTilgang))
            }
        }
        route("/test/tilgangsmaskinen") {
            post<Unit, TilgangResponse, TilgangsmaskinRequest> { _, req ->
                prometheus.httpCallCounter(pipeline.call).increment()
                val callId = pipeline.call.request.header("Nav-CallId") ?: "ukjent"
                val harTilgang =
                    tilgangService.harTilgangTilPersoner(
                        req.brukerIdenter.map { Personregel(it.brukerId, it.type) },
                        BrukerToken(token().token()),
                        callId,
                    )
                respond(TilgangResponse(harTilgang))
            }
        }

        route("/person") {
            post<Unit, TilgangResponse, PersonTilgangRequest> { _, req ->
                prometheus.httpCallCounter(pipeline.call).increment()

                val kontekst = tilgangskontekst(roles)

                val input = PersontilgangInput(
                    personIdent = req.personIdent,
                    påkrevdRolle = req.påkrevdRolle.orEmpty(),
                    operasjon = req.operasjon,
                )
                val harTilgang = tilgangService.harTilgangTilPerson(
                    input, kontekst
                )

                if (!harTilgang) {
                    prometheus.nektetTilgangTeller("person").increment()
                }
                respond(TilgangResponse(harTilgang))
            }
        }

        route("/tilbakekreving") {
            post<Unit, TilgangResponse, TilbakekrevingTilgangRequest> { _, req ->
                prometheus.httpCallCounter(pipeline.call).increment()

                val kontekst = tilgangskontekst(roles)

                val input = TilbakekrevingstilgangInput(
                    saksnummer = req.saksnummer,
                    behandlingsreferanse = req.behandlingsreferanse,
                    påkrevdRolle = req.effektivePåkrevdRoller(),
                    operasjon = req.operasjon,
                )
                val harTilgang =
                    tilgangService.harTilgangTilTilbakekreving(input, kontekst)

                if (!harTilgang) {
                    prometheus.nektetTilgangTeller("tilbakekreving").increment()
                }

                respond(TilgangResponse(harTilgang))
            }
        }
        
    }
    route("/roller") {
        get<Unit, List<Rolle>> {
            prometheus.httpCallCounter(pipeline.call).increment()

            val roller = parseRoller(rolesWithGroupIds = roles, roller())

            respond(roller)
        }
    }
}

private fun OpenAPIPipelineContext.tilgangskontekst(roles: List<Role>): Tilgangskontekst =
    Tilgangskontekst(
        ansattIdent = ident(),
        roller = parseRoller(rolesWithGroupIds = roles, roller()),
        brukerToken = BrukerToken(pipeline.call.token().token()),
        callId = pipeline.call.request.header("Nav-CallId") ?: "ukjent",
    )
