package tilgang.mapping

import no.nav.aap.tilgang.Operasjon as KontraktOperasjon
import no.nav.aap.tilgang.RelevanteIdenter as KontraktRelevanteIdenter
import no.nav.aap.tilgang.Rolle as KontraktRolle
import tilgang.kontroll.Operasjon
import tilgang.kontroll.RelevanteIdenter
import tilgang.kontroll.Rolle

fun KontraktRolle.tilDomene(): Rolle = when (this) {
    KontraktRolle.SAKSBEHANDLER_OPPFOLGING -> Rolle.SAKSBEHANDLER_OPPFOLGING
    KontraktRolle.SAKSBEHANDLER_NASJONAL -> Rolle.SAKSBEHANDLER_NASJONAL
    KontraktRolle.KVALITETSSIKRER -> Rolle.KVALITETSSIKRER
    KontraktRolle.BESLUTTER -> Rolle.BESLUTTER
    KontraktRolle.LES -> Rolle.LES
    KontraktRolle.PRODUKSJONSSTYRING -> Rolle.PRODUKSJONSSTYRING
    KontraktRolle.DRIFT -> Rolle.DRIFT
    KontraktRolle.DRIFT_LES -> Rolle.DRIFT_LES
}

fun KontraktOperasjon.tilDomene(): Operasjon = when (this) {
    KontraktOperasjon.SE -> Operasjon.SE
    KontraktOperasjon.SAKSBEHANDLE -> Operasjon.SAKSBEHANDLE
    KontraktOperasjon.DRIFTE -> Operasjon.DRIFTE
    KontraktOperasjon.DRIFT_LES -> Operasjon.DRIFT_LES
    KontraktOperasjon.DELEGERE -> Operasjon.DELEGERE
}

fun Operasjon.tilKontrakt(): KontraktOperasjon = when (this) {
    Operasjon.SE -> KontraktOperasjon.SE
    Operasjon.SAKSBEHANDLE -> KontraktOperasjon.SAKSBEHANDLE
    Operasjon.DRIFTE -> KontraktOperasjon.DRIFTE
    Operasjon.DRIFT_LES -> KontraktOperasjon.DRIFT_LES
    Operasjon.DELEGERE -> KontraktOperasjon.DELEGERE
}

fun KontraktRelevanteIdenter.tilDomene(): RelevanteIdenter = RelevanteIdenter(søker = søker, barn = barn)

fun Iterable<KontraktRolle>.tilDomene(): List<Rolle> = map { it.tilDomene() }

fun Map<Operasjon, Boolean>.tilKontrakt(): Map<KontraktOperasjon, Boolean> =
    mapKeys { (operasjon, _) -> operasjon.tilKontrakt() }
