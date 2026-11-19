package tilgang.regler

import tilgang.Tilgangskontekst

interface Personkontroll {
    suspend fun hentHabilitetsgrunnlag(personIdent: String, kontekst: Tilgangskontekst): Habilitetsgrunnlag
    suspend fun harTilgangTilPerson(personIdent: String, kontekst: Tilgangskontekst): Boolean
}
