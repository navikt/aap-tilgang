package tilgang.integrasjoner.nom;

internal data class NOMRespons (
    val errors: List<Error>?,
    val data: NOMData?
)

internal data class NOMData(
    val ressurs: Ressurs?
)

internal data class Ressurs(
    val navident: String,
    val personident: String
)

internal data class Error(
    val message: String,
    val path: List<String>,
)