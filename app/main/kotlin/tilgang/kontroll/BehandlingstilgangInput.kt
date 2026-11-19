package tilgang.kontroll

import no.nav.aap.tilgang.RelevanteIdenter
import no.nav.aap.tilgang.Rolle
import java.util.UUID

data class BehandlingstilgangInput(
    val behandlingsreferanse: UUID,
    val avklaringsbehovKode: String?,
    val påkrevdRolle: List<Rolle>,
    val relevanteIdenter: RelevanteIdenter?,
)
