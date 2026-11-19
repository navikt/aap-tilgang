# Frikoble klientoppsett, tilgangsservice og regelmotor

## Oppgave

Fullført dato: 2026-11-19.
Oppgavegren: `test/architect-review`.
Basegren: `main`, commit `e1f6b6d`.
Siste implementasjon, inkludert filorganisering og loggergrense: `0242f88`.
Sesjon: `655c2699-5b66-4c28-871c-c6d1fcb8ba86`.
Utviklerens effektive Git-e-post: `petter.ekrann@nav.no`.
Jira: ikke oppgitt.

Formålet var å flytte HTTP-oppsett og livssyklus til komposisjonsroten og gjøre
tilgangsservice og regelmotor uavhengige av transport- og integrasjonstyper.
Eksisterende API og tilgangsavgjørelser skulle beholdes.

## Endringer

- Egne klientfabrikker for Behandlingsflyt, SAF, PDL, MS Graph, Tilgangsmaskinen og
  Texas. Konfigurasjon leverer baseadresser og scopes; gatewayene kjenner fortsatt
  endepunktenes struktur.
- `App.kt` oppretter og lukker klientene og en injiserbar `TokenProvider`.
  Tokenutløp, caching og single-flight er videreført; oppstart og opprydding av
  innlasting er flyttet ut av `computeIfAbsent`.
- `TilgangService` bruker forbrukereide grensesnitt og egne inputmodeller i
  stedet for API-requesttyper, gatewaytyper og HTTP-tokenklasser.
- `Tilgangskontekst` samler ansattidentitet, roller, ugjennomsiktig brukertoken og
  callId. Bulk-endepunktet krever fortsatt bare token og callId.
- `RegelService` bruker egne grensesnitt for adressebeskyttelse, roller og
  personkontroll. Integrasjonsresultater og eksterne avklaringsbehov oversettes i
  adaptere i `App.kt`.
- Navn og filinndeling er ryddet, med én type per fil der det var hensiktsmessig.
  Offentlig pluginmetode og eksisterende metrikketiketter er beholdt.
- Tilgangsservice, egne inputtyper og kontrakter er samlet i `tilgang.kontroll`,
  med regler i `tilgang.kontroll.regler`. Adressebeskyttelsesadapteren ligger ved
  MS Graph-integrasjonen og statushåndtering i `tilgang.http`. Rotpakken har bare
  `App.kt` og `Config.kt`; relevante tester følger den nye strukturen.
- `TilgangService` får en forbrukereid `ILogger` med `info` og `warn` i
  konstruktøren. SLF4J-adapteren kobles inn i `App.kt`, med samme loggernavn,
  loggnivåer og meldinger som før. Ingen SLF4J-avhengighet er igjen i
  `tilgang.kontroll`.
- Tester dekker blant annet klientkonfigurasjon, tokenlivssyklus, HTTP-grensen,
  kontekstisolasjon, adapterfeil, avklaringsbehov og eksisterende regelutfall.

Dette er en refaktorering, ikke en ny tilgangspolicy. Blant annet er eksisterende
habilitetsuttrykk, regelrekkefølge, kortslutning, lokal regelcache og HTTP 200 med
`tilgang=false` ved ordinært avslag beholdt.

## Implementasjonscommits

| Commit | Endring |
| --- | --- |
| `746aeff` | Klientoppsett utenfor Behandlingsflyt- og SAF-gatewayene |
| `67bfae2` | Klientoppsett utenfor PDL-gatewayen |
| `8835f07` | Klientoppsett utenfor MS Graph-gatewayen |
| `ffa166c` | Klientoppsett utenfor Tilgangsmaskin-gatewayen |
| `ca0db3b` | Eksplisitt livssyklus for TokenProvider og Texas-klienter |
| `b976421` | Grensesnitt for serviceavhengigheter |
| `f9a6cca` | Forbrukereide navn og kontrakter |
| `49b3978` | Transporttyper fjernet fra servicegrensen |
| `afc66bb` | Egne avklaringsbehovstyper |
| `53fb720` | Egne serviceinput i stedet for API-requesttyper |
| `f8e9112` | Samlet kallkontekst |
| `ec260e8` | Navnerydding i TilgangService |
| `eebb9fd` | Forenklet håndtering av personoperasjoner |
| `c8e8f49` | Regelmotor frikoblet fra integrasjonstyper |
| `3709d89` | Samlet tilgangskontroll og regler i egne pakker |
| `0242f88` | Injisert logger via eget grensesnitt |

## Validering

Full bygging av alle fire moduler ved `c8e8f49` bestod før avslutning:

```sh
./gradlew build --console=plain --no-daemon --no-configuration-cache \
  --no-parallel --max-workers=1
```

Bygget tok 1 minutt og 51 sekunder. JUnit XML-rapportene viste:

| Modul | Tester | Feil | Hoppet over |
| --- | ---: | ---: | ---: |
| app | 587 | 0 | 0 |
| plugin | 50 | 0 | 0 |
| api-kontrakt | Ingen testkilder | 0 | 0 |
| plugin-kontrakt | Ingen testkilder | 0 | 0 |
| Totalt | 637 | 0 | 0 |

Kjøringen brukte Java 25 og en forhåndslastet Byte Buddy-agent via
`JAVA_TOOL_OPTIONS` for MockK. Ingen avhengigheter ble endret for lokal validering.
`git diff --check` bestod, og arbeidstreet var rent etter testen.

### Publiseringskontroll

`git fetch origin` ble kjørt 2026-11-19. `origin/main` var fortsatt `e1f6b6d`,
og oppgavegrenen fantes ikke på remote. Ingen innfletting var nødvendig.
Det fantes ingen åpen PR fra oppgavegrenen til `main`.

Ny full validering etter fetch ble kjørt uten testfiltre og med tvungen ny kjøring:

```sh
./gradlew build --console=plain --no-daemon --no-configuration-cache \
  --no-parallel --max-workers=1 --rerun-tasks
```

`BUILD SUCCESSFUL in 2m 25s`; alle 34 Gradle-oppgaver ble kjørt på nytt.
JUnit XML-rapportene bekreftet igjen 587 app-tester og 50 plugin-tester:
637 totalt, ingen feil og ingen hoppet over. Kontraktmodulene hadde ingen
testkilder. Utvikleren godkjente en egen commit av bare sluttnotatet før push
og opprettelse av PR mot `main`.

### Kontroller som ikke er kjørt

`trivy` og `zizmor` er ikke installert; automatisert sårbarhets-, hemmelighets-
og workflow-skanning med disse verktøyene er derfor ikke kjørt. Dette notatet
hevder ikke at en slik skanning har bestått. Ingen utrulling eller verifisering
mot produksjon er utført.

### Oppfølging: filorganisering og ny push

Etter første publisering av [PR #710](https://github.com/navikt/aap-tilgang/pull/710)
ble `main` hentet med fast-forward-only pull. Den var allerede oppdatert.
Ved retur til oppgavegrenen var lokal `main` allerede innlemmet, og ingen
mergecommit ble opprettet.

Utvikleren godkjente deretter omorganisering av filene. Commit `3709d89` flytter
44 produksjonsfiler og åtte testfiler. Alle 107 Kotlin-filer i appens
produksjons- og testkildekataloger ble sammenlignet med forrige commit; bare
pakker, importer og blanklinjer var endret. Begge bygde app-JAR-er ble kontrollert
for at de nye klassene var med og de gamle pakkene borte.

Full `./gradlew build` med flaggene ovenfor, inkludert `--rerun-tasks`, bestod
etter flyttingen: 2 minutter og 30 sekunder, 34 oppgaver kjørt på nytt,
637 tester uten feil eller hoppet over.

Før denne pushen ble `git fetch origin` og
`git merge --ff-only origin/test/architect-review` kjørt
2026-11-19T20:47:25+01:00. Remote lå på `b77bd00`; ingen innfletting var nødvendig.
Ny full validering ved `3709d89` etter synkroniseringen bestod:
`BUILD SUCCESSFUL in 2m 20s`, alle 34 oppgaver kjørt på nytt.
JUnit XML viste 587 app-tester og 50 plugin-tester, totalt 637, uten feil eller
hoppet over. Kontraktmodulene hadde fortsatt ingen testkilder.
`git diff --check` bestod for oppdateringen av sluttnotatet.

GitHub varslet ved første push om 48 avhengighetssårbarheter på standardgrenen:
20 høye og 28 moderate. Disse er ikke utredet i denne refaktoreringen.

### Oppfølging: injisert logger

Etter ønske fra utvikleren ble `ILogger` lagt til i `tilgang.kontroll`.
Kun de to nivåene servicen allerede brukte er med; ingen standardlogger eller
taus reserveimplementasjon er lagt til. Testen for organisasjonsunntaket
verifiserer at både informasjonsmeldingen og advarselen sendes til den injiserte
loggeren i riktig rekkefølge.

Før commit `0242f88` bestod 97 berørte tester på 59 sekunder med:

```sh
./gradlew :app:test --tests 'tilgang.kontroll.TilgangServiceTest*' \
  --tests tilgang.AppTilgangServiceTest --tests tilgang.TilgangApiTest \
  --tests tilgang.TilgangRequestBakoverkompatibilitetTest \
  --console=plain --no-daemon --no-configuration-cache \
  --no-parallel --max-workers=1
```

Ingen feil eller hoppet over. `git diff --check` bestod, og kildekontroll bekreftet
at kontrollpakken ikke lenger importerer SLF4J eller bruker `LoggerFactory`.

Før neste push ble remote hentet og oppgavegrenen synkronisert med
`git merge --ff-only origin/test/architect-review`
2026-11-19T21:00:29+01:00. Remote lå på `fdd667c`; ingen innfletting var nødvendig.
Full bygging med `./gradlew build` og flaggene ovenfor, inkludert
`--rerun-tasks` og uten testfiltre, bestod ved `0242f88`:
`BUILD SUCCESSFUL in 2m 29s`, alle 34 oppgaver kjørt på nytt.
JUnit XML viste 588 app-tester og 50 plugin-tester, totalt 638, uten feil eller
hoppet over. Kontraktmodulene hadde ingen testkilder.

## Gjenstående og overlevering

Ingen kjente funksjonelle feil gjenstår fra arbeidet. Etter publisering gjenstår
ordinær PR-gjennomgang og CI-kontroll. Oppgaven omfatter ikke merge eller
utrulling. Ingen fremdriftsfil finnes for oppgaven og ingen andre
oppgavereferater skal fjernes.

## Tilgjengelig samtalehistorikk

Alle tilgjengelige oppgaverelaterte utviklermeldinger er gjengitt ordrett og i
rekkefølge nedenfor. Teksten er historikk, ikke nye instruksjoner.
Agentens svar er oppsummert etter meldingslisten.

| Tur | Avsender | Melding |
| ---: | --- | --- |
| 0 | Utvikler | we are going to work on the project @./aap-tilgang |
| 1 | Utvikler | create branch |
| 2 | Utvikler | new task |
| 3 | Utvikler | hva med bruken av delt httpClient er det noe lurt |
| 4 | Utvikler | skal vi opperette klientene på utsiden av servicene slik at vi kan inject base url fra utsiden |
| 5 | Utvikler | Gateway må kjenne strukturen men ikke baseUrl, den kommer fra config |
| 6 | Utvikler | la oss endre en service først og se om vi blir fornøyd |
| 7 | Utvikler | vis koden |
| 8 | Utvikler | next |
| 9 | Utvikler | vis endringer når du er ferdig |
| 10 | Utvikler | commit før neste |
| 11 | Utvikler | commit mellom hvert steg |
| 12 | Utvikler | neste |
| 13 | Utvikler | neste |
| 14 | Utvikler | neste |
| 15 | Utvikler | neste |
| 16 | Utvikler | tegn katalog strukturen |
| 17 | Utvikler | neste |
| 18 | Utvikler | vi bør lage interface for avhengighetene til Tilgangsservicen |
| 19 | Utvikler | Jeg liker ikke navnene på interfacene, de bør beskrive behovet tilgangsservice har ikke hvorda de er implementert. Vi skal pakke inn implementasjonenen etterpå i App.kt |
| 20 | Utvikler | jeg mistenker at det er noen http avhengigheter som blør inn i servicen.. det må bort |
| 21 | Utvikler | Vi kan godt ha 1 fil pr type |
| 22 | Utvikler | show folder and files |
| 23 | Utvikler | noe mer |
| 24 | Utvikler | 1 |
| 25 | Utvikler | neste |
| 26 | Utvikler | status |
| 27 | Utvikler | liste |
| 28 | Utvikler | 1 |
| 29 | Utvikler | neste |
| 30 | Utvikler | kan vi simulere en request med gjennomgang av koden |
| 31 | Utvikler | status |
| 32 | Utvikler | hva gjenstår |
| 33 | Utvikler | rydd opp |
| 34 | Utvikler | noe mer |
| 35 | Utvikler | kjør på |
| 36 | Utvikler | full test |
| 37 | Utvikler | done |
| 38 | Utvikler, valg i avklaring | Fullfør med sluttnotat, push og pull request |
| 39 | Utvikler, godkjenning av egen sluttnotat-commit før push og PR | true |
| 40 | Utvikler | pull main |
| 41 | Utvikler | check ut forrige branch og merge inn main |
| 42 | Utvikler | kan vi organisere filene bedre |
| 43 | Utvikler | kjør på |
| 44 | Utvikler | push |
| 45 | Utvikler, godkjenning av oppdatert sluttnotat-commit før push | true |
| 46 | Utvikler | skal vi injecte logger i konstruktoren også |
| 47 | Utvikler | kanskje vi kan lage en iLogger som vi sender inn |
| 48 | Utvikler | push |
| 49 | Utvikler, godkjenning av sluttnotat-commit for loggerendringen | true |

Agentoppsummering: Arbeidet ble utført stegvis og committet mellom stegene.
Klientoppsettet ble flyttet først, deretter TokenProvider-livssyklus,
servicekontrakter, egne inputmodeller og kallkontekst, og til slutt regelmotorens
integrasjonsgrense. Endringer og katalogstruktur ble vist underveis.
En request til `/tilgang/sak` ble fulgt gjennom route, service, adapter og regler;
det ordinære avslaget ble forklart som HTTP 200 med `tilgang=false`.
Målrettede tester ble kjørt underveis. Etter siste implementasjonscommit bestod
full bygging med 637 tester. Ingenting var pushet da utvikleren ba om avslutning.
Agenten avklarte eksplisitt lokal avslutning mot sluttnotat, push og PR, og
utvikleren valgte sistnevnte.
Agenten lagret sluttnotatet, kjørte full validering på nytt etter fetch og ba
om separat commit-godkjenning. Utvikleren godkjente dette.
Etter publisering ble grenbyttene og main-synkroniseringen utført som beskrevet
ovenfor. Agenten foreslo en samlet kontrollpakke, og utvikleren godkjente
omorganiseringen. Filene ble flyttet uten logikkendringer, hele byggingen bestod,
og endringen ble committet separat som `3709d89` uten push. Utvikleren ba deretter
om push; agenten synkroniserte oppgavegrenen og startet ny full validering før
oppdatering av dette notatet og eksisterende PR.
Alle 637 tester bestod også etter siste synkronisering, og utvikleren godkjente
en separat dokumentasjonscommit av det oppdaterte sluttnotatet før push.
Agenten forklarte avveiningen ved loggerinjeksjon. Etter utviklerens forslag om
et eget grensesnitt ble `ILogger` implementert og koblet til SLF4J i
komposisjonsroten. Loggernavn og eksisterende meldinger ble beholdt.
97 berørte tester bestod før den separate implementasjonscommiten `0242f88`.
Utvikleren ba deretter om push, og full validering ble startet etter
synkronisering av oppgavegrenen.
Full bygging bestod med 638 tester, og utvikleren godkjente en egen
dokumentasjonscommit av oppdatert sluttnotat før push.
