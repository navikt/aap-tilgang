package no.nav.aap.tilgang

import io.ktor.http.Parameters
import io.ktor.server.util.getOrFail
import no.nav.aap.tilgang.plugin.kontrakt.Behandlingsreferanse
import no.nav.aap.tilgang.plugin.kontrakt.Journalpostreferanse
import no.nav.aap.tilgang.plugin.kontrakt.Saksreferanse
import no.nav.aap.tilgang.plugin.kontrakt.TilgangReferanse
import tilgang.BehandlingTilgangRequest
import tilgang.JournalpostTilgangRequest
import tilgang.Operasjon
import tilgang.SakTilgangRequest
import tilgang.TilgangRequest

data class AuthorizationParamPathConfig(
    val approvedApplications: Set<String> = emptySet(),
    val applicationsOnly: Boolean = false,
    val sakPathParam: SakPathParam? = null,
    val behandlingPathParam: BehandlingPathParam? = null,
    val journalpostPathParam: JournalpostPathParam? = null) {

    internal fun tilTilgangRequest(operasjon: Operasjon, parameters: Parameters): AuthorizedRequest {
        if (sakPathParam != null) {
            return AuthorizedRequest(
                applicationsOnly,
                approvedApplications,
                SakTilgangRequest(saksnummer = parameters.getOrFail(sakPathParam.param), operasjon = operasjon)
            )
        }
        if (behandlingPathParam != null) {
            return AuthorizedRequest(
                applicationsOnly,
                approvedApplications, BehandlingTilgangRequest(
                    behandlingsreferanse = parameters.getOrFail(behandlingPathParam.param),
                    operasjon = operasjon,
                    avklaringsbehovKode = null
                )
            )
        }
        if (journalpostPathParam != null) {
            return AuthorizedRequest(
                applicationsOnly,
                approvedApplications, JournalpostTilgangRequest(
                    journalpostId = parameters.getOrFail(journalpostPathParam.param).toLong(),
                    operasjon = operasjon,
                    avklaringsbehovKode = null
                )
            )
        }
        throw IllegalArgumentException("Klarer ikke avgjøre hva slags type request dette er")
    }
}

data class AuthorizationBodyPathConfig(
    val operasjon: Operasjon,
    val approvedApplications: Set<String> = emptySet(),
    val applicationsOnly: Boolean = false
) {

    fun tilTilgangRequest(request: TilgangReferanse): AuthorizedRequest {
        when (request) {
            is Saksreferanse -> {
                val referanse = request.hentSaksreferanse()
                return AuthorizedRequest(
                    applicationsOnly,
                    approvedApplications,
                    SakTilgangRequest(referanse, operasjon)
                )
            }

            is Behandlingsreferanse -> {
                val referanse = request.hentBehandlingsreferanse()
                val avklaringsbehovKode = request.hentAvklaringsbehovKode()
                return AuthorizedRequest(
                    applicationsOnly,
                    approvedApplications,
                    BehandlingTilgangRequest(referanse, avklaringsbehovKode, operasjon)
                )
            }

            is Journalpostreferanse -> {
                val referanse = request.hentJournalpostreferanse()
                val avklaringsbehovKode = request.hentAvklaringsbehovKode()
                return AuthorizedRequest(
                    applicationsOnly,
                    approvedApplications,
                    JournalpostTilgangRequest(referanse, avklaringsbehovKode, operasjon)
                )
            }
        }
        throw IllegalArgumentException("Klarer ikke avgjøre hva slags type request dette er")
    }
}


data class AuthorizedRequest(val applicationsOnly: Boolean,
                             val approvedApplications: Set<String> = emptySet(),
                             val tilgangRequest: TilgangRequest)
