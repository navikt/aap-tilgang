package tilgang.kontroll.regler

import tilgang.kontroll.BrukerToken

interface Adressebeskyttelsesroller {
    suspend fun hentRoller(token: BrukerToken, ansattIdent: String): List<Adressebeskyttelsesrolle>
}
