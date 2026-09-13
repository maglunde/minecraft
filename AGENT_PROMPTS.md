# Agentprompter – voxel-fundament

Bruk promptene i nummerert rekkefølge fra `DEVELOPMENT_PLAN.md`. Hver prompt
forutsetter at alle tidligere steg er ferdige, testet og ligger i worktree.
Agenten skal alltid lese `AGENTS.md` og relevant kode før den gjør endringer.
Ingen prompt autoriserer commit; brukeren håndterer det etter gjennomgang.

## 1. Luna medium – regresjonstest for data-tap

```text
Du skal starte steg 1 i voxel-prosjektet: skriv en headless regresjonstest som
avdekker om spillerendringer i en unloaded chunk forsvinner ved neste save.

Les AGENTS.md, World, WorldSaveManager og eksisterende WorldSaveManagerTest
før du endrer noe. Ikke endre produksjonskode i dette steget.

Lag en test som:
1. Oppretter en verden i en midlertidig save-mappe med fast seed.
2. Laster chunk (0, 0), plasserer en tydelig markeringsblokk i den.
3. Laster spilleren langt nok bort til at chunken lastes ut.
4. Lagrer verden på nytt.
5. Oppretter en helt ny World-instans fra samme save-mappe.
6. Laster markerings-chunken og bekrefter at blokken fortsatt finnes.

Testen må være deterministisk, rydde opp sin egen midlertidige mappe og aldri
initialisere GLFW eller OpenGL. Ikke gjør rendering-, mesh- eller
generationrefaktorering. Kjør den nye testen og deretter mvn test. Rapporter
testresultat og git diff. Ikke commit.
```

## 2. Sol xhigh – robust lagring per chunk

```text
Steg 1 er ferdig og har en regresjonstest som viser data-tap ved at en unloaded
chunk mangler etter en ny lagring. Du skal fikse dette i steg 2.

Les AGENTS.md først, så World, WorldSaveManager, Chunk, Dimension og alle
eksisterende save-tester. Prosjektets regel er at world-koden må være headless.

Mål: Bytt fra en enkelt omskrevet chunks.dat til enkel, separat vedvarende
lagring per dimensjon og chunk. Når en chunk er blitt modifisert, skal dataene
kunne skrives trygt før unload og lastes direkte senere. En ny lagring må aldri
fjerne data fra tidligere unloaded chunks.

Krav:
- Behold world.dat og dets eksisterende ansvar.
- Bruk et enkelt, dokumenterbart filoppsett per dimensjon/chunk, med chunkX og
  chunkZ i filnavn eller katalogstruktur.
- Skriv atomisk: skriv komplett midlertidig fil og erstatt målfil først når
  skrivingen lykkes.
- Filen skal ha tydelig magic/version samt dimensjon, chunk-koordinater og
  forventet blokk-arraylengde; avvis feil data kontrollert.
- Nullstill ikke dirty/needsSave før skriving faktisk lykkes.
- Last én konkret chunk direkte ved behov, ikke ved å skanne eller laste hele
  verdenens chunk-data.
- Ved unload: ikke fjern en dirty chunk før den er persistert. Håndter
  lagringsfeil uten at verden krasjer eller data bevisst kastes.
- Uendrede chunks skal fortsatt kunne genereres deterministisk fra seed.
- Hvis gammel chunks.dat finnes, støtt en enkel kompatibel lesing eller en
  kontrollert engangsmigrering. Ikke slett den gamle filen automatisk.

Avgrensning:
- Ikke endre rendering, mesh, OpenGL-avhengigheter, block states, tråding,
  generationalgoritme eller spillmekanikk.
- Unngå nye rammeverk eller store abstraksjonslag.

Legg til produksjonsnære tester der det mangler, og behold testen fra steg 1.
Kjør relevante tester, mvn test og git diff --check. Rapporter formatet du
valgte, endrede filer, testresultat og eventuelle begrensninger. Ikke commit.
```

## 3. Luna medium – utvidet testdekning for lagring

```text
Steg 2 er ferdig: chunk-lagring er nå per chunk/dimensjon og regresjonstesten
fra steg 1 passerer. Du skal utføre steg 3: utvide testdekningen, uten bred
produksjonsrefaktorering.

Les AGENTS.md, den nåværende WorldSaveManager-implementasjonen og alle
eksisterende save-tester. Arbeid primært i WorldSaveManagerTest.

Legg til headless tester som beviser:
1. To modifiserte chunks bevares når de lastes ut, verden lagres flere ganger,
   og den lastes inn i en helt ny World-instans.
2. En modifisert chunk på negative world- og chunk-koordinater bevares.
3. Modifiserte chunks fra ulike dimensjoner ikke kolliderer, dersom dagens
   format støtter flere dimensjoner.
4. En uendret chunk kan lastes ut og regenereres deterministisk med samme
   seed, uten at den må persisteres som spillerdata.
5. Korrupt eller avkortet chunk-fil ikke krasjer hele world-load; forventet
   kontrollert fallback/logging skal verifiseres etter dagens API.

Bruk separate midlertidige save-mapper. Ikke test kun i samme World-objekt;
opprett en ny instans når data skal verifiseres. Velg posisjoner som ikke kan
forveksles med naturlig terrain. Ikke endre rendering, mesh, generation eller
formatet bare for å gjøre testene enklere. Små konkrete produksjonsfeil som
testene avslører kan rettes, men forklar dem.

Kjør relevante tester og mvn test, deretter git diff --check. Rapporter nye
scenarier, resultater og eventuelle konkrete feilfunn. Ikke commit.
```

## 4. Sol xhigh – skil world-data fra OpenGL

```text
Steg 1–3 er ferdige og chunk-persistens er testet. Du skal nå gjøre steg 4:
skille World/Chunk fra OpenGL-rendering, uten å endre synlig spilloppførsel.

Les AGENTS.md grundig. Undersøk Chunk, World, Main og alle relevante klasser i
no.minecraft.render før endring. Arkitekturkravet er absolutt:
no.minecraft.world skal ikke importere eller kalle LWJGL/OpenGL og må kunne
opprettes, oppdateres, lagres og testes uten grafikkontekst.

Mål:
- Chunk eier bare world-data og eventuell CPU-meshdata som ikke kjenner OpenGL:
  koordinater, blokker, lys, dirty-flagg og nødvendige mesh-inputdata.
- Render-pakken eier VAO/VBO, direkte GPU-buffere, gl*-kall, opplastinger,
  draw-calls og GPU-cleanup.
- Renderer-laget holder en enkel mapping fra aktiv chunk til renderressurs.
- Ressurser opprettes bare når en chunk faktisk trenger å meshes/renderes.
- Når World har unloaded en chunk, rydder renderer-laget den tilhørende
  GPU-ressursen mens OpenGL-kontekst er aktiv.
- En dirty chunk får oppdatert renderressurs ved behov, uten at World selv
  eier GPU-livssyklusen.

Velg en liten, konkret løsning, eksempelvis ChunkRenderer + ChunkMesh. Ikke
flytt World til render-pakken og ikke innfør ECS, dependency injection eller
generelle renderer-rammeverk.

Ikke gjør greedy meshing, tråding, streaming-budsjetter, save-formatendring
eller stor lysrefaktorering. Bevar nåværende face-culling og visuelt resultat
så langt det er mulig.

Legg til/oppdater headless tester som viser at World og Chunk fungerer uten
OpenGL ved konstruksjon, blokkendring, loading og cleanup. Kjør mvn test og
mvn compile dependency:copy-dependencies -DincludeScope=runtime -q. Start
spillet kort dersom miljøet tillater det, og kontroller at verden rendres og
block-endringer gir meshoppdatering. Kjør git diff --check.

Rapporter ny ansvarsdeling, endrede filer, testresultat, eventuell manuell
verifisering og avgrensede oppfølgingspunkter. Ikke commit.
```

## 5. Terra medium – gradvis chunk-livssyklus

```text
Steg 1–4 er ferdige. World er headless, og renderer-laget eier GPU-ressurser.
Du skal gjøre steg 5: fjern synkrone lastestopp når render-avstanden lastes.

Les AGENTS.md, World, chunk-livssyklus, save-manager og render-integrasjonen.
Mål: Last/generer bare et lite, fast antall chunks per tick eller frame, med
prioritet nær spillerens nåværende chunk. Verden må fortsatt kunne lagre før
dirty chunks lastes ut.

Krav:
- Behold korrekt floorDiv/modulo-håndtering for negative koordinater.
- Hold en enkel kø eller prioritert samling over ønskede chunks nær spilleren.
- Unngå å legge inn samme chunk flere ganger.
- Begrens generering til et lite, lett forståelig budsjett per oppdatering.
- Last ut chunks utenfor ønsket område kun når dirty data er trygt persistert.
- Renderer-ressurser for unloaded chunks skal fortsatt ryddes av render-laget.
- Hold API-et enkelt og synkront.

Ikke implementer tråding, executor, futures eller jobbsystem. Ikke endre
generationalgoritme, meshformat eller save-format uten et konkret nødvendig
samspill.

Skriv headless tester for prioritering, ingen duplikater, unload etter lagring
og negative koordinater. Kjør mvn test og git diff --check. Rapporter valgt
budsjett, livssyklus, testresultat og eventuelle synlige kompromisser. Ikke
commit.
```

## 6. Terra medium – frustum før meshing og mindre GC

```text
Steg 1–5 er ferdige. Chunk-livssyklusen er gradvis, og renderer-laget eier
OpenGL. Du skal gjøre steg 6: små renderforbedringer med dokumenterbar effekt.

Les AGENTS.md og profiler/undersøk faktisk render- og meshflyt før endringer.
Mål først: Ikke bygg eller last opp mesh for chunks som er utenfor kameraets
frustum. Utfør frustumtest før dyr meshbygging, men pass på at dirty-status
beholdes til chunken blir synlig igjen.

Deretter: fjern tydelige GC-kilder i eksisterende meshbygging uten å endre
meshresultat, særlig boksede Float-samlinger, nye UV-arrays per face og nye
direktebuffere ved hver rebuild. Bruk primitive, gjenbrukbare buffere eller
enkle dynamiske primitive arrays der det passer dagens struktur.

Bevar korrekt face-culling ved chunk-grenser. Ikke implementer greedy meshing,
tråding, ny materialarkitektur eller omfattende transparent-rendering. Ikke
optimaliser basert på antakelser; rapporter hva du målte eller hvilken konkret
hot path du eliminerte.

Kjør mvn test, compile-kommandoen fra AGENTS.md, git diff --check og en kort
manuell renderkontroll hvis mulig. Rapporter før/etter-måling hvis tilgjengelig,
ellers presist hvilke allokeringer og kall som ble fjernet. Ikke commit.
```

## 7. Terra medium – én deterministisk generator

```text
Steg 1–6 er ferdige. Du skal gjøre steg 7: samle world generation slik at det
bare finnes én produksjonskilde for chunk-generering.

Les AGENTS.md, World, OverworldGenerator, tester og alle kall som genererer
chunks. Det finnes/har eksistert overlapp mellom generation i World og en
separat generator; fjern denne risikoen uten å endre terrenget unødvendig.

Mål:
- Én tydelig seedet generator per relevant dimensjon.
- Samme seed, dimensjon, chunkX og chunkZ gir samme resultat uansett hvilken
  rekkefølge chunks etterspørres i.
- setSeed/reset erstatter eller nullstiller generator-tilstand korrekt.
- Generation kan på sikt kjøres i bakgrunnen fordi den ikke avhenger av global,
  muterbar World-tilstand under selve beregningen.

Hold løsningen enkel og synkron. Ikke innfør tråding, biomer, ny noise-stack
eller store strategi-/registryhierarkier. Bevar eksisterende verdensutseende
dersom det ikke finnes en dokumentert bug som krever endring.

Legg til determinisme-tester, rekkefølge-uavhengighetstester og seed-reset-test.
Kjør mvn test og git diff --check. Rapporter hva som nå er eneste
produksjons-generator og eventuelle bevisste kompatibilitetskonsekvenser. Ikke
commit.
```

## 8. Sol xhigh – kompakt block state og tile-data-eierskap

```text
Steg 1–7 er ferdige. Du skal gjøre steg 8 før prosjektet får mange blokker med
retning, væsker eller mer avansert lys: etabler et lite, kompakt fundament for
plass-spesifikk blokktilstand og dimensjonsriktig tile-data.

Les AGENTS.md, Block/BlockType, Chunk, World, WorldSaveManager, FurnaceData,
ChestData og plassering/kollisjon/render-kall. Kartlegg hva som i dag lagres i
enum/type og hva som egentlig varierer per blokkposisjon.

Mål:
- Skill blokkdefinisjon (egenskaper som tekstur, hardhet, gjennomsiktighet) fra
  plass-spesifikk state (for eksempel facing eller aktiv/inaktiv).
- Velg en liten, kompakt representasjon som kan lagres per blokk i chunk uten
  objekt per blokk. Behovet nå er bare dagens ovner/kister og framtidig enkel
  facing; ikke lag et komplett Minecraft block-state-registry.
- Nøkler for FurnaceData/ChestData må inkludere dimensjon slik at samme x/y/z
  i ulike dimensjoner ikke kolliderer.
- WorldSaveManager må serialisere den nye state/dataen kontrollert og beholde
  lastbare eksisterende verdener enten via versjonering/fallback eller tydelig
  migreringssti.
- Rendering, collision og plassering bruker den nye state bare der det trengs,
  uten å spre særlogikk gjennom World.

Avgrensning: Ikke implementer alle trapper, væsker, redstone, mod-støtte,
generisk ECS eller fullt data-driven registry. Ikke kombiner med større
renderer- eller generationrefaktor.

Legg til headless tester for state round-trip, dimensjonsseparert tile-data og
bakoverkompatibilitet/fallback. Kjør mvn test, compile-kommandoen og git diff
--check. Rapporter representasjonen, migreringsbeslutningen, endrede filer og
begrensninger. Ikke commit.
```

## 9. Terra medium – profileringsgrunnlag og beslutning

```text
Steg 1–8 er ferdige. Du skal gjøre steg 9: etablere et lite, nyttig
profileringsgrunnlag før større optimaliseringsprosjekter vurderes.

Les AGENTS.md og den faktiske game loop-, generation-, meshing- og renderkoden.
Ikke optimaliser store deler av motoren i denne oppgaven. Mål først.

Lag en enkel, av/på-styrt og lav-overhead måte å observere minst:
- frame-tid og eventuelt tick-tid
- genererte/lastede/unloadede chunks per tidsenhet
- antall dirty mesh-rebuilds og GPU-opplastinger
- synlige/renderte chunks og draw calls
- relevant minne/allokeringssignal dersom det kan samles uten tung profiler

Unngå logging per frame til konsoll. Samle data og vis/sammendrag med lav
frekvens, eller dokumenter en reproduserbar ekstern profileringsprosedyre.

Kjør en realistisk scene/render-avstand og lever en kort rapport med flaskehalser
og beslutning: om greedy meshing, mer culling, lysarbeid eller
bakgrunnsgenerering faktisk er begrunnet. Ikke implementer disse videre stegene
nå. Kjør mvn test og git diff --check. Ikke commit.
```

## 10. Terra medium – readiness-gjennomgang før større funksjoner

```text
Steg 1–9 er ferdige. Du skal ikke implementere en ny stor spillfunksjon; du
skal gjøre en kort, evidensbasert readiness-gjennomgang før entities, biomer,
avansert lys eller mange nye block-typer legges til.

Les AGENTS.md, DEVELOPMENT_PLAN.md, AGENT_PROMPTS.md, gjeldende tester og
endringene fra de forrige stegene. Bekreft at persistens, headless world,
chunk-livssyklus, renderinggrense, generator og block state har tester og
ingen kjent kritisk regresjon.

Kjør mvn test, compile-kommandoen fra AGENTS.md og git diff --check. Lever en
kort prioritert anbefaling for neste funksjonsområde, med konkrete gjenstående
risikoer. Ikke start implementasjon av entities/biomer/lighting i denne
oppgaven, og ikke commit.
```
