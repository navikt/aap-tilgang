package tilgang.integrasjoner.tilgangsmaskin

data class TilgangsmaskinRequest(val brukerIdenter: List<BrukerOgRegeltype>)
data class BrukerOgRegeltype(val brukerId: String, val type: String)