package tilgang.integrasjoner.tilgangsmaskin

data class TilgangsmaskinRequest(
    val ansattId: String,
    val brukerId: String,
)