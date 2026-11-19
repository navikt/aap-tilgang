package tilgang.regler

import tilgang.BrukerToken

interface Adressebeskyttelsesroller {
    suspend fun hentRoller(token: BrukerToken, ansattIdent: String): List<Adressebeskyttelsesrolle>
}
