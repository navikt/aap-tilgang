package tilgang.kontroll.regler

import tilgang.kontroll.Tilgangskontekst

interface Personkontroll {
    suspend fun hentHabilitetsgrunnlag(personIdent: String, kontekst: Tilgangskontekst): Habilitetsgrunnlag
    suspend fun harTilgangTilPerson(personIdent: String, kontekst: Tilgangskontekst): Boolean
}
