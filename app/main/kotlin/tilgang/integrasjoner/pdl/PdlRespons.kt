package tilgang.integrasjoner.pdl

import tilgang.graphql.GraphQLError
import tilgang.graphql.GraphQLExtensions

data class PersonResultat(
    val ident: String,
    val adressebeskyttelse: List<Gradering>,
    val code: String
)

internal data class PdlResponse(
    val data: PdlData?,
    val errors: List<GraphQLError>?,
    val extensions: GraphQLExtensions?
)

internal data class PdlData(
    val hentGeografiskTilknytning: HentGeografiskTilknytningResult?,
    val hentPerson: PdlPerson?,
    val hentPersonBolk: List<HentPersonBolkResult>?
)

data class HentPersonBolkResult(
    val ident: String,
    val person: PdlPerson?,
    val code: String,
)

data class PdlPerson(
    val adressebeskyttelse: List<Adressebeskyttelse>?,
    val code: Code?,     // Denne er påkrevd ved hentPersonBolk
    val forelderBarnRelasjon: List<PdlForelderBarnRelasjon>?,
)

data class PdlForelderBarnRelasjon(
    val relatertPersonsIdent: String,
    val relatertPersonsRolle: PdlRelatertPersonsRolleType,
)

data class HentGeografiskTilknytningResult(
    val gtType: PdlGeoType,
    val gtKommune: String?,
    val gtBydel: String?,
    val gtLand: String?
)

enum class PdlRelatertPersonsRolleType {
    FAR, MOR, BARN, MEDMOR
}

enum class PdlGeoType {
    BYDEL, KOMMUNE, UDEFINERT, UTLAND
}

enum class Code {
    ok, not_found, bad_request //TODO: add more
}

internal data class PdlVegadresse(
    val adressenavn: String,
    val husbokstav: String?,
    val husnummer: String?,
    val postnummer: String,
)

data class Adressebeskyttelse(
    val gradering: Gradering
)

enum class Gradering {
    FORTROLIG,
    STRENGT_FORTROLIG_UTLAND,
    STRENGT_FORTROLIG,
    UGRADERT
}