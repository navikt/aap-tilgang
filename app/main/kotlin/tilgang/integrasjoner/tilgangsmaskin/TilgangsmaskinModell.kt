package tilgang.integrasjoner.tilgangsmaskin

data class HarTilgangFraTilgangsmaskinen(
    val harTilgang: Boolean,
    val TilgangsmaskinAvvistResponse: TilgangsmaskinAvvistResponse? = null,
)

enum class TilgangsmaskinAvvistGrunn{
    AVVIST_HABILITET,
    AVVIST_STRENGT_FORTROLIG_ADRESSE,
    AVVIST_STRENGT_FORTROLIG_UTLAND,
    AVVIST_FORTROLIG_ADRESSE,
    AVVIST_SKJERMING,
    AVVIST_VERGE,
    AVVIST_MANGLENDE_DATA
}

data class TilgangsmaskinAvvistResponse(
    val type: String,
    val title: String,
    val status: Int,
    val navIdent: String,
    val begrunnelse: String,
    val kanOverstyres: Boolean
)

data class TilgangsmaskinRequest(val brukerIdenter: List<BrukerOgRegeltype>)
data class BrukerOgRegeltype(val brukerId: String, val type: String)