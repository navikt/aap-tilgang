package tilgang.regler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tilgang.Rolle
import tilgang.geo.GeoRolle
import tilgang.geo.GeoType
import tilgang.integrasjoner.pdl.PdlGeoType
import tilgang.integrasjoner.pdl.Gradering
import tilgang.integrasjoner.pdl.HentGeografiskTilknytningResult
import tilgang.integrasjoner.pdl.PersonResultat
import tilgang.routes.Operasjon

class ReglerTest {
    @Test
    fun `Saksbehandler uten fortrolige-roller skal ikke kunne lese fortrolige adresser`() {
        val ident = "1234"
        val roller = Roller(listOf(GeoRolle(GeoType.KOMMUNE, "0301")), listOf(Rolle.VEILEDER, Rolle.SAKSBEHANDLER))
        val søkersGeografiskeTilknytning = HentGeografiskTilknytningResult(
            PdlGeoType.BYDEL, null, "030102", null
        )
        val personListe1 = listOf(PersonResultat("1000", listOf(Gradering.FORTROLIG), "kode"))
        val personerListe2 = listOf(PersonResultat("1234", listOf(Gradering.STRENGT_FORTROLIG), "kode"))
        val personerListe3 = listOf(PersonResultat("5678", listOf(Gradering.STRENGT_FORTROLIG_UTLAND), "kode"))

        assertFalse(harLesetilgang(ident, roller, personListe1, søkersGeografiskeTilknytning))
        assertFalse(harLesetilgang(ident, roller, personerListe2, søkersGeografiskeTilknytning))
        assertFalse(harLesetilgang(ident, roller, personerListe3, søkersGeografiskeTilknytning))
    }

    @Test
    fun `Rolle STRENGT_FORTROLIG_ADRESSE har tilgang til person med fortrolig og strengt fortrolig adresse`() {
        val ident = "1222"
        val roller = Roller(
            listOf(GeoRolle(GeoType.KOMMUNE, "0301")),
            listOf(Rolle.SAKSBEHANDLER, Rolle.STRENGT_FORTROLIG_ADRESSE)
        )
        val søkersGeografiskeTilknytning = HentGeografiskTilknytningResult(
            PdlGeoType.KOMMUNE, "0301", null, null
        )
        val personer = listOf(
            PersonResultat("1000", listOf(Gradering.FORTROLIG), "kode"),
            PersonResultat("1234", listOf(Gradering.STRENGT_FORTROLIG), "kode"),
            PersonResultat("5678", listOf(Gradering.STRENGT_FORTROLIG_UTLAND), "kode")
        )
        assertTrue(harLesetilgang(ident, roller, personer, søkersGeografiskeTilknytning))
    }

    @Test
    fun `Rolle FORTROLIG_ADRESSE har tilgang til person med fortrolig, men ikke strengt fortrolig, adresse`() {
        val ident = "1234"
        val roller = Roller(listOf(GeoRolle(GeoType.KOMMUNE, "0301")), listOf(Rolle.VEILEDER, Rolle.FORTROLIG_ADRESSE))
        val søkersGeografiskeTilknytning = HentGeografiskTilknytningResult(
            PdlGeoType.KOMMUNE, "0301", null, null
        )
        val personListe1 = listOf(PersonResultat("1000", listOf(Gradering.FORTROLIG), "kode"))
        val personListe2 = listOf(
            PersonResultat("1000", listOf(Gradering.FORTROLIG), "kode"),
            PersonResultat("1234", listOf(Gradering.STRENGT_FORTROLIG), "kode"),
            PersonResultat("5678", listOf(Gradering.STRENGT_FORTROLIG_UTLAND), "kode")
        )
        assertTrue(harLesetilgang(ident, roller, personListe1, søkersGeografiskeTilknytning))
        assertFalse(harLesetilgang(ident, roller, personListe2, søkersGeografiskeTilknytning))
    }

    @Test
    fun `Saksbehandler skal ikke ha tilgang til egen sak`() {
        val ident = "1234"
        val roller = Roller(listOf(GeoRolle(GeoType.KOMMUNE, "0301")), listOf(Rolle.VEILEDER, Rolle.SAKSBEHANDLER))
        val søkersGeografiskeTilknytning = HentGeografiskTilknytningResult(
            PdlGeoType.KOMMUNE, "0301", null, null
        )
        val personListe = listOf(PersonResultat("1234", emptyList(), "kode"))
        assertFalse(harLesetilgang(ident, roller, personListe, søkersGeografiskeTilknytning))
    }

    @Test
    fun `Utvikler skal kun ha tilgang til operasjon 'DRIFTE'`() {
        val ident = "1234"
        val roller = Roller(emptyList(), listOf(Rolle.UTVIKLER))
        val søkersGeografiskeTilknytning = HentGeografiskTilknytningResult(
            PdlGeoType.KOMMUNE, "0301", null, null
        )
        val personListe = listOf(PersonResultat("1000", emptyList(), "kode"))
        assertTrue(vurderTilgang(ident, roller, "1000", søkersGeografiskeTilknytning, personListe, "bref-123", null, Operasjon.DRIFTE))
        assertFalse(vurderTilgang(ident, roller, "1000", søkersGeografiskeTilknytning, personListe, "bref-123", null, Operasjon.SE))
        assertFalse(vurderTilgang(ident, roller, "1000", søkersGeografiskeTilknytning, personListe, "bref-123", null, Operasjon.SAKSBEHANDLE))
        assertFalse(vurderTilgang(ident, roller, "1000", søkersGeografiskeTilknytning, personListe, "bref-123", null, Operasjon.DELEGERE))
    }

    @Test
    fun `Saksbehandler, veileder og avdelingsleder skal ikke kunne lese saker som er utenfor deres geografiske rettigher`() {
            val ident = "1234"
            val roller = Roller(listOf(GeoRolle(GeoType.KOMMUNE, "0301")), listOf(Rolle.VEILEDER, Rolle.SAKSBEHANDLER))
            val søkersGeografiskeTilknytning = HentGeografiskTilknytningResult(
                PdlGeoType.BYDEL, null, "500101", null
            )

            val personListe = listOf(PersonResultat("1000", emptyList(), "kode"))

            assertFalse(harLesetilgang(ident, roller, personListe, søkersGeografiskeTilknytning))
    }

    @Test
    fun `Skal kunne lese bydel i kommune`() {
        val ident = "1234"
        val roller = Roller(listOf(GeoRolle(GeoType.KOMMUNE, "0301")), listOf(Rolle.VEILEDER, Rolle.SAKSBEHANDLER))
        val søkersGeografiskeTilknytning = HentGeografiskTilknytningResult(
            PdlGeoType.BYDEL, null, "030102", null
        )

        val personListe = listOf(PersonResultat("1000", emptyList(), "kode"))

        assertTrue(harLesetilgang(ident, roller, personListe, søkersGeografiskeTilknytning))
    }

}