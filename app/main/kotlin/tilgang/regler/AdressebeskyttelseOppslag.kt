package tilgang.regler

interface AdressebeskyttelseOppslag {
    suspend fun hentGraderinger(personIdenter: List<String>, callId: String): List<Adressegradering>
}
