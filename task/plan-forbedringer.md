# Prioriterte forbedringer i aap-tilgang

Statisk gjennomgang av de fem Gradle-modulene, testene, byggoppsettet, GitHub Actions og NAIS-manifestene. Funnene beskriver kode og konfigurasjon, ikke observerte produksjonshendelser. [Planen for rollekoblinger](plan-rollekoblinger.md) står fortsatt for seg.

## Først

1. **La en manuell dev-release stoppe i dev.** I [release.yml](../.github/workflows/release.yml#L93-L108) er `main` alene nok til at prod-jobben velges, også når brukeren uttrykkelig har valgt `dev-gcp`. Skill push fra manuelt miljøvalg. Kontroller jobbvalget uten utrulling: en release utløst av push til `main` → dev og prod; manuelt dev-valg på `main` → bare dev; manuelt prod-valg fra en tillatt gren → prod først etter vellykket dev-jobb. Avklar hvilke grener som skal tillates for manuelt prod-valg; dagens vilkår begrenser ikke dette til `main`.

2. **Skill ufullstendige PDL-svar fra bekreftet tom adressebeskyttelse.** [PdlGateway.kt](../app/restService/src/main/kotlin/tilgang/integrasjoner/pdl/PdlGateway.kt#L44-L57) lager en tom graderingsliste ved manglende persondata og cacher raden i én time. [Adapteren](../app/restService/src/main/kotlin/tilgang/App.kt#L227-L240) sender graderingene videre uten radstatus. [Adressebeskyttelsesregelen](../tilgang-service/src/main/kotlin/regler/AdressebeskyttelseRegel.kt#L10-L16) godtar en tom liste; dette er én del av tilgangsvurderingen. Avklar med domeneeier hva `not_found` betyr for alternative identer. Bevar gyldige tomme svar, men ikke cache et uavklart oppslag som vellykket. Dekk også et delvis svar der en etterspurt ident mangler helt.

## Deretter – uavhengige endringer

3. **Hent alle gruppesider fra Microsoft Graph før caching.** [MsGraphGateway.kt](../app/restService/src/main/kotlin/tilgang/integrasjoner/msgraph/MsGraphGateway.kt#L26-L48) ber om 500 grupper, leser bare `value` og cacher første svar. En relevant gruppe på neste side blir derfor ikke med. Følg `@odata.nextLink` på den konfigurerte Graph-verten. Verifiser at en gruppe på side to blir med, og at feil på side to ikke gir et delvis cacheinnslag.

4. **Rett hvilke byggeendringer som inngår i bibliotekversjonen.** [Versjoneringen](../.github/workflows/release.yml#L49-L55) følger `buildSrc/`, men [prosjektet bruker `build-logic/`](../settings.gradle.kts#L3-L5). Felles byggelogikk kan dermed endre de publiserte bibliotekene uten at endringen inngår i versjonsberegningen. Bytt til riktig sti og vurder også endringer i `gradle/libs.versions.toml` og den importerte versjonskatalogen i `settings.gradle.kts`. Verifiser versjonsberegningen for en ren byggeendring og en ren hostendring uten å publisere noe.

5. **Gjør eierskapet til Redis eksplisitt.** [App.kt](../app/restService/src/main/kotlin/tilgang/App.kt#L115-L118) oppretter som standard en Redis-klient, men [nedstengingen](../app/restService/src/main/kotlin/tilgang/App.kt#L162-L173) lukker bare HTTP- og tokenressursene. [`Redis.close()`](../app/restService/src/main/kotlin/tilgang/redis/Redis.kt#L45-L48) lukker både forbindelse og klient. La hosten lukke instansen den selv opprettet, men ikke en lånt testinstans. Dekk begge eierskapstilfellene.

6. **Hold pluginens cache og målinger i samsvar.** [`initialiserPrometheus`](../plugin/src/main/kotlin/no/nav/aap/tilgang/TilgangGateway.kt#L62-L71) registrerer målere på fem konkrete cacher; [`disableCaching()`](../plugin/src/main/kotlin/no/nav/aap/tilgang/TilgangGateway.kt#L165-L191) erstatter deretter instansene. Hvis metodene kalles i den rekkefølgen, måles cacher som ikke lenger brukes. Behold det offentlige API-et og dagens TTL. Verifiser at deaktivering både stopper gjenbruk av tidligere svar og lar målingene gjelde de aktive cachene.

## To små rettelser

- [Gradle-konvensjonen](../build-logic/src/main/kotlin/aap.conventions.gradle.kts#L20-L23) halverer antall prosessorer og får `maxParallelForks = 0` på én CPU. Sett nedre grense til én; kontroller med én synlig prosessor.
- [Pluginens integrasjonstest](../plugin/src/test/kotlin/TilgangPluginTest.kt#L71-L72) reserverer port 8082, og [test-URL-en](../plugin/src/test/kotlin/TilgangPluginTest.kt#L157) gjentar den. Bruk en port tildelt av operativsystemet, slik [testens øvrige servere allerede gjør](../plugin/src/test/kotlin/Fakes.kt#L39-L40). Testen skal kunne kjøre selv om 8082 er opptatt.

## Gjennomført

Alle åtte tiltakene er implementert. PDL `not_found` er beholdt som tom gradering etter avklaring; manglende eller ugyldige svarrader feiler før noen av de nye radene caches. README har i tillegg et produksjonsdiagram, og `contracts/openapi.yaml` beskriver REST- og driftsrutene.

Verifisering: `./gradlew test --console=plain --no-daemon --no-configuration-cache --no-parallel --max-workers=1` med `-XX:ActiveProcessorCount=1`; 654 tester, 0 feil. OpenAPI-YAML, alle 53 lokale `$ref`-referanser og workflow-YAML ble også validert.
