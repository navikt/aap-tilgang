package tilgang.foresporsel

import tilgang.kontroll.BrukerToken
import tilgang.kontroll.Personregel

interface Persontilgang {
    suspend fun harTilgangTilPerson(brukerIdent: String, token: BrukerToken, callId: String): Boolean
    suspend fun harTilganger(
        brukerIdenter: List<Personregel>,
        token: BrukerToken,
        callId: String,
    ): Boolean
}
