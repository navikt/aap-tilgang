package tilgang.integrasjoner.nom;

internal data class NOMRespons (
    val data: NOMData?
)

internal data class NOMData(
    val ressurs: Ressurs?
)

internal data class Ressurs(
    val navident: String,
    val personident: String
)