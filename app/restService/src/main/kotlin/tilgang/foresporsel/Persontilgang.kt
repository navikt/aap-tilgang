package tilgang.foresporsel

import tilgang.kontroll.BrukerToken

interface Persontilgang {
    suspend fun harTilgangTilPerson(brukerIdent: String, token: BrukerToken, callId: String): Boolean
}
