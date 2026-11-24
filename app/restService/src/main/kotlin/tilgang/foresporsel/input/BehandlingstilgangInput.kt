package tilgang.foresporsel.input

import java.util.UUID
import tilgang.kontroll.RelevanteIdenter
import tilgang.kontroll.Rolle

data class BehandlingstilgangInput(
    val behandlingsreferanse: UUID,
    val avklaringsbehovKode: String?,
    val påkrevdRolle: List<Rolle>,
    val relevanteIdenter: RelevanteIdenter?,
)
