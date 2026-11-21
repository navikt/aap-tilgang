package tilgang.kontroll.regler

interface AdressebeskyttelseOppslag {
    suspend fun hentGraderinger(personIdenter: List<String>, callId: String): List<Adressegradering>
}
