package tilgang

import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.Rolle

data class PersontilgangInput(
    val personIdent: String,
    val påkrevdRolle: List<Rolle>,
    val operasjon: Operasjon?,
)
