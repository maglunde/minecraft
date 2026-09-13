# Utviklingsplan – voxel-fundament

Denne planen følger kodegjennomgangen. Rekkefølgen er bevisst: først beskyttes
spillerdata, deretter ryddes den viktigste arkitekturgrensen, og først etterpå
gjøres streaming og ytelsesarbeid.

Statusen beskriver situasjonen da planen ble skrevet. Oppdater den når et steg
er verifisert med testene som står under.

## 1. Regresjonstest for data-tap

- **Ansvarlig:** Luna, medium
- **Status:** Ferdig
- **Mål:** Bevis at en blokk i en chunk ikke forsvinner når chunken lastes ut og
  verden lagres på nytt.
- **Resultat:** Testen `testSecondSavePreservesEvictedChunkData` ble lagt til i
  `WorldSaveManagerTest` og avdekket den opprinnelige feilen.

## 2. Gjør chunk-lagring robust

- **Ansvarlig:** Sol, xhigh (Astra low er også et rimelig alternativ fordi
  oppgaven nå er godt avgrenset av steg 1)
- **Status:** Neste / pågår
- **Mål:** Erstatt én omskrevet, global `chunks.dat` med enkel lagring per
  chunk og dimensjon. En chunk må være lagret før den kan lastes ut, og den må
  kunne lastes direkte ved behov.
- **Krav:** Atomisk skriving, koordinat- og versjonssjekk, behold `world.dat`,
  støtt lesing/migrering av gammel lagring dersom den finnes, og behold dirty-
  flagg ved lagringsfeil.
- **Ikke gjør:** Renderingrefaktor, tråding, greedy meshing eller nye
  spillfunksjoner.
- **Verifisering:** Den eksisterende regresjonstesten samt hele `mvn test`.

## 3. Utvid tester for chunk-lagring

- **Ansvarlig:** Luna, medium
- **Status:** Klar etter steg 2
- **Mål:** Sikre at lagringsløsningen tåler realistisk streaming.
- **Tester:** To modifiserte chunks overlever flere lagringer og ny World-
  instans; negative koordinater; dimensjonsseparasjon hvis formatet støtter
  flere dimensjoner; uendrede chunks kan regenereres fra seed; korrupt eller
  ufullstendig chunk-fil krasjer ikke hele lasting.
- **Ikke gjør:** Produksjonsrefaktor utover små, konkrete feil som testene
  avdekker.
- **Verifisering:** Relevante tester og hele `mvn test`.

## 4. Skill world/chunk fra OpenGL-rendering

- **Ansvarlig:** Sol, xhigh
- **Status:** Implementert og automatisk verifisert; visuell kontroll gjenstår
- **Mål:** `no.minecraft.world` blir fullstendig headless. `Chunk` skal ikke
  eie VAO/VBO-er, OpenGL-kall eller GPU-cleanup; renderer-laget skal eie mesh-
  og GPU-ressurser.
- **Retning:** En enkel render-eid map fra chunk til renderressurs. Lag eller
  oppdater GPU-mesh bare ved rendering av dirty chunks. Rydd en ressurs når
  dens chunk ikke lenger finnes i World, mens OpenGL-kontekst er aktiv.
- **Ikke gjør:** Greedy meshing, tråding, nytt ECS/DI-lag, save-formatendring
  eller omfattende endring av lys.
- **Verifisering:** Headless World/Chunk-tester, `mvn test`,
  `mvn compile dependency:copy-dependencies -DincludeScope=runtime -q`, og
  kort manuell oppstart dersom miljøet tillater det.
- **Resultat:** `ChunkMesh` eier meshbygging, VAO/VBO, opplasting og draw-kall.
  `ChunkRenderer` eier ressurser per chunk-identitet og rydder ved unload,
  dimensjonsbytte og world-reset på neste render, samt før GL-konteksten lukkes.
  World/Chunk beholder blokkdata, lys og dirty-flagg; Main/HUD bruker rendererens
  chunk-teller. Meshgeometri og face-culling er bevart.
- **Testresultat:** 214 tester bestått, inkludert nye headless livssyklus- og
  arkitekturtester. Kompilering/runtime-avhengigheter og diff-kontroll bestått.
  Oppstart forsøkt, men GLFW fant ingen støttet grafikkplattform i miljøet.
  Rendering og meshoppdatering ved blokkendring må derfor kontrolleres manuelt.

## 5. Innfør enkel, gradvis chunk-livssyklus

- **Ansvarlig:** Terra, medium
- **Status:** Gjør etter steg 4
- **Mål:** Unngå at hele render-avstanden genereres synkront når verden åpnes
  eller spilleren krysser en chunk-grense.
- **Retning:** Hold ønskede chunk-koordinater prioritert nær spilleren, og
  opprett/generer et lite, fast antall chunks per tick/frame. Last ut bare
  chunks som er utenfor avstand og allerede er trygt persistert.
- **Ikke gjør ennå:** Bakgrunnstråder eller jobbsystem. Den synkrone løsningen
  skal først være korrekt, målbar og enkel å teste.
- **Verifisering:** Tester for prioritert rekkefølge, unload etter lagring og
  korrekt håndtering av negative koordinater.

## 6. Billige renderforbedringer med tydelig effekt

- **Ansvarlig:** Terra, medium
- **Status:** Gjør når steg 5 fungerer stabilt
- **Mål:** Ikke bygg eller last opp mesh for chunks som uansett er utenfor
  kameraets frustum. Behold dirty-oppdateringer, men utfør frustum-test før
  mesharbeid for ikke-synlige chunks.
- **Deretter:** Fjern tydelige hot-path-allokeringer i den eksisterende
  mesheren, spesielt boksede `Float`-verdier, gjentatte UV-arrays og nye
  direktebuffere ved hver rebuild.
- **Ikke gjør ennå:** Greedy meshing; vurder det først med profileringsdata når
  vanlig face-culling og bufferhåndtering ikke er nok.

## 7. Samle world generation i én kilde

- **Ansvarlig:** Terra, medium
- **Status:** Gjør før biomer eller avansert generation
- **Mål:** World skal bruke én seedet, chunk-uavhengig generator i stedet for
  overlappende production- og testimplementasjoner.
- **Krav:** Samme `(seed, dimension, chunkX, chunkZ)` gir samme chunk uansett
  genereringsrekkefølge. Reset av seed må også resette alle relevante
  generator-cacher.
- **Ikke gjør ennå:** Multithreading eller komplisert biome-system.

## 8. Etabler enkel blokktilstand og dataeierskap

- **Ansvarlig:** Sol, xhigh
- **Status:** Gjør før mange retningsbestemte blokker, væsker eller lighting
- **Mål:** Planlegg og innfør en kompakt representasjon for block state som
  skiller blokkdefinisjon/type fra plass-spesifikk tilstand. Tile-data må være
  dimensjonsspesifikke og ikke kun nøkkes på globale x/y/z uten dimensjon.
- **Omfang:** Start bare med tilstanden dagens ovner/kister trenger. Ikke bygg
  full Minecraft-registry, mod-støtte eller generell komponentarkitektur.

## 9. Profilér før større optimaliseringer

- **Ansvarlig:** Terra, medium
- **Status:** Etter at streaming og rendering er stabile
- **Mål:** Mål frame-tid, chunk-generation, mesh-bygging, GPU-opplasting,
  allokeringer og draw calls med realistisk render-avstand.
- **Bruk data til å avgjøre:** Greedy meshing, mer aggressiv culling,
  lysoptimalisering og eventuelt bakgrunnsgenerering.

## 10. Før nye store spillfunksjoner

Før entities, biomer, avansert lys, collision-utvidelser eller mange nye
block-typer: sørg for at steg 2–5 er ferdige og at hele testsuiten er grønn.
Da er fundamentet godt nok til å bygge videre uten å sementere data-tap,
renderer-avhengighet eller synkrone lastestopp.

## Ting som bevisst ikke prioriteres ennå

- Greedy meshing
- Multithreading/jobbsystem for chunk-generation
- ECS eller dependency injection
- Uendelig avansert save-format som Minecraft Anvil
- Omfattende abstraksjonslag for materialer, biomer eller entities

Disse kan bli relevante, men først når profileringsdata eller konkrete
spillfunksjoner viser behovet.
