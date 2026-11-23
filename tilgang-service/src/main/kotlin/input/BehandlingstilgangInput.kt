package tilgang.kontroll

import java.util.UUID

data class BehandlingstilgangInput(
    val behandlingsreferanse: UUID,
    val avklaringsbehovKode: String?,
    val påkrevdRolle: List<Rolle>,
    val relevanteIdenter: RelevanteIdenter?,
)
