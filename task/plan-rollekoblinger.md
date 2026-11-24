# Plan: entydig kobling mellom grupper og roller

**Mål:** En konfigurert gruppe-ID skal peke på nøyaktig én `Rolle`. I dag velger `parseRoller` den første rollen hvis to roller har samme UUID. Tilgangen skal ikke avhenge av rekkefølgen i enumen.

1. **Valider koblingene ved oppstart.** Bygg ett oppslagskart fra gruppe-ID til `Rolle` i REST-hosten. Hvis to roller er konfigurert med samme UUID, stopp oppstarten med en feilmelding som navngir begge `AAP_<ROLLENAVN>`-nøklene. Behold dagens skille: en manglende nøkkel utelater rollen, også når alle mangler; en satt, men tom eller ugyldig verdi er feil.
2. **Bruk kartet i begge rollekallene.** La `/roller` og byggingen av `Tilgangskontekst` i `TilgangRoute.kt` bruke det samme kartet. I `routes/Roller.kt` kan `parseRoller` slå opp hver tokengruppe direkte i stedet for å søke gjennom listen og kalle `Rolle.valueOf`. Behold tokenets rekkefølge og eventuelle gjentakelser; ignorer ukjente grupper.
3. **Verifiser observerbar atferd.** Legg duplikattilfellet i `ConfigTest`: samme UUID for to roller skal gi oppstartsfeil som identifiserer begge. Dekk blandede tokengrupper i `TilgangApiTest`: `[gruppeB, ukjent, gruppeA, gruppeB]` skal gi `[rolleB, rolleA, rolleB]`. Behold tilfellene for manglende og ugyldig konfigurasjon.

Kjør de berørte testene med `./gradlew :app:test --tests tilgang.ConfigTest --tests tilgang.TilgangApiTest`. Endringen trenger ingen ny konfigurasjonsnøkkel og ingen endring i `Rolle`-enumen, REST-kontrakten eller regelmotoren.
