package tilgang.integrasjoner.nom;

data class NOMRespons (
    val ressurs: Ressurs
)

data class Ressurs(
    val navident: String,
    val personident: String
)