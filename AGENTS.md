# Retningslinjer og Arkitektur for AI-agenter (AGENTS.md)

Dette dokumentet definerer beste praksis, arkitekturkrav, minne- og ytelsesstandarder samt testregler for utvikling av **Minecraft Java Edition 1.16.1 Clone** i Java 21 og LWJGL. Alle AI-agenter som arbeider på dette prosjektet skal følge disse retningslinjene nøye.

---

## 1. Hovedmål og Referanseversjon

- **Referanseversjon:** **Minecraft Java Edition 1.16.1**
- Alle mekanikker (combat cooldown, knockback, fall-kritisk, verktøystatistikk, sult, smelting, crafting, blokkatferd, mob-AI, HUD/inventar-oppførsel) skal følge Java 1.16.1 nøyaktig.
- Offisielle teksturer og modeller skal ligge i Java 1.16.1-assetstrukturen under `src/main/resources/assets/`.

---

## 2. Arkitektur og Pakkestruktur

Koden skal være strengt modulær med tydelig ansvarsdeling (*Separation of Concerns*):

| Pakke | Ansvar | Regel / Avhengighetsbegrensning |
| :--- | :--- | :--- |
| `no.minecraft.world` | Verdensdata, chunks, voxel-lagring, belysning, biomer, lagring/lasting (`WorldSaveManager`), blokkdata (`FurnaceData`, `ChestData`). | Skal **aldri** kalle OpenGL/LWJGL direkte. Må kunne kjøres og testes fullstendig *headless*. |
| `no.minecraft.render` | LWJGL/OpenGL-rendering, shadere, teksturatlas, kamera, sky/sol/måne, HUD, UI-skjermer (`FurnaceScreen`, `InventoryScreen`, menyer). | Holder alt av GPU-ressurser (VAO, VBO, teksturer). Får data fra `World` og `Player`, men modifiserer ikke spillverden direkte. |
| `no.minecraft.physics` | AABB (aksejusterte kollisjonsbokser), raycasting, gravitasjon, kollisjonssjekk mot blokker. | Rent matematisk og fysikkfaglig. Ingen grafikkavhengigheter. |
| `no.minecraft.player` | Spillerlogikk, helse, sult, inventar (`Inventory`, `ItemStack`), crafting-oppskrifter (`CraftingRecipe`), bevegelse/input-tolkning. | Spillogisk tilstand som serialiseres og testes uten grafikkontakt. |
| `no.minecraft.entity` | Mobs (Zombie, Skeleton osv.), prosjektiler (Arrow, EnderPearl), båter (`Boat`), drops (`ItemDrop`). | Følger faste oppdateringsløkker (`update(dt, world, player)`). |
| `no.minecraft.sound` | OpenAL lydavspilling (`SoundManager`), lydeffekter for blokker, steg, combat. | Failsafe: Hvis lydkort/OpenAL mangler, skal spillet ikke krasje. |
| `no.minecraft.i18n` | Oversettelser og lokalisering (`I18n`). Støtte for standard Java-ressursbunter. | Bruk oversettelsesnøkler (f.eks. `container.furnace`) fremfor hardkodede strenger. |
| `no.minecraft.settings` | Nøkkelbindinger, grafikkinnstillinger, synsvinkel (FOV), render distance (`GameSettings`). | Persisteres i enkle konfigurasjonsfiler. |

---

## 3. Ytelse, Minnehåndtering og Variabelgjenbruk (GC-forebygging)

I et voxelspill i 60–144 FPS er **Garbage Collection (GC) pauses den største kilden til micro-stutter og hakking**. Agenter skal aktivt unngå unødvendige objektallokeringer i *hot paths*.

### 3.1 Gullregel for «Hot Paths»
> **Alloker ALDRI objekter (`new`) per blokk, per vertex, per kollisjonssjekk eller per frame i render-løkken!**

### 3.2 JOML og Vektorer (`Vector3f`, `Matrix4f`)
- **Feil (allokerer på heap hver frame/blokk):**
  ```java
  // DÅRLIG: skaper tusenvis av små objekter per sekund
  Vector3f dir = new Vector3f(target).sub(player.getPosition()).normalize();
  ```
- **Riktig (gjenbruk muterbare variabler eller primitive parametere):**
  ```java
  // BRA: Gjenbruk privat scratch-variabel eller send primitive koordinater
  private final Vector3f scratchVec = new Vector3f();
  
  public void updateDirection(Vector3f target, Vector3f out) {
      out.set(target).sub(position).normalize();
  }
  ```
- Send koordinater som primitive verdier (`float x, float y, float z` eller `int x, int y, int z`) der en full vektor ikke er påkrevd.

### 3.3 Fysikk og Kollisjon (`AABB`)
- Unngå å opprette `new AABB(...)` for hver nabo-blokk i kollisjonssjekk.
- Bruk primitive bounds-sjekker (`minX <= x && x <= maxX ...`) eller en gjenbrukbar/muterbar `AABB`-instans (`tempAABB.set(...)`).

### 3.4 Voxel-lagring og Datastrukturer
- Bruk primitive arrays (`byte[]`, `int[]`) for blokk-ID og lysverdier i chunks fremfor objektreferanser eller bokset typer (`Byte`, `Integer`).
- Bruk bit-operasjoner for pakkede koordinater og sammensatte verdier (f.eks. `Chunk.getIndex(x, y, z)` eller `World.blockPosKey(x, y, z)`).

### 3.5 Samlinger (Collections) og Buffere
- Gjenbruk lister i mesh-generering og UI-batching: kall `list.clear()` fremfor å allokere `new ArrayList<>()` hver frame.
- Angi hensiktsmessig initialkapasitet ved instansiering av samlinger (`new ArrayList<>(1024)`), for å unngå gjentatte reallokeringer og array-kopieringer.
- For OpenGL-opplastinger: gjenbruk direkte `FloatBuffer` / `ByteBuffer` i minnet.

---

## 4. Voxel-motor og Rendering Best Practices

1. **Chunk Dirty-flagg:**
   - Rekalkuler **aldri** mesh for en chunk med mindre blokkdata, lys eller en naboblokk faktisk er endret (`chunk.setBlock(...)` setter `isDirty = true`).
   - Oppdater kun nabo-chunks hvis en blokk på chunk-grensen (x=0/15 eller z=0/15) modifiseres.
2. **Face Culling (`shouldRenderFace`):**
   - Tegn aldri indre/skjulte flater mellom to ugjennomsiktige blokker.
   - Væsker og transparente blokker (glass, blader) krever tilpasset culling-logikk som tillater innsyn.
3. **Frustum Culling:**
   - Test chunkens AABB mot kameraets synsfrustum (`frustum.intersects(chunkAABB)`) før draw-calls sendes til OpenGL.
4. **Teksturatlas og Batching:**
   - Alle blokk- og UI-teksturer samles i atlas (`TextureAtlas`). Unngå hyppige `glBindTexture`-kall.
   - All 2D-rendering (HUD, menyer, inventar) samles i batcher (`UiBatch.addRect`).

---

## 5. Konvensjoner for Mekanikker og Blokk-tilstand

- **Tile Entities / Blokkdata:**
  - Blokker med ekstra tilstand (f.eks. `FURNACE`, `CHEST`) skal ha tilhørende dataklasser (`FurnaceData`, `ChestData`) registrert i `World`.
  - Tilstand som retning (`facing`), brenntid, inventarslots og smelting skal lagres via `WorldSaveManager` i henhold til versjonsformatet.
- **Plassering og Orientering:**
  - Blokker med retning (ovn, kiste, trapper) skal orienteres i forhold til spillerens kamera/blikkretning ved plassering (fronten vendt mot spilleren).
- **Sjekkliste ved implementasjon av nye blokker og gjenstander:**
  Når en ny blokk eller gjenstand legges til, skal den aldri bare eksistere som en isolert ID/tekstur, men integreres helhetlig i henhold til Java 1.16.1:
  1. **Crafting & Oppskrifter:**
     - Registrer oppskrift for å tilvirke blokken (hvis aktuelt).
     - Registrer oppskrifter *med* blokken som ingrediens (f.eks. om det lages en ny tresort/stamme, må den kunne craftes til tilhørende treplanker, pinner, båt osv.; ny malm må kunne smeltes til barre/ingot).
     - Registrer drivstoffverdi (`fuelTicks`) hvis blokken/gjenstanden er brennbar (stokker, planker, kull osv.).
     - Registrer eventuelle smelteoppskrifter (f.eks. stamme $\rightarrow$ trekull, cobble $\rightarrow$ stone).
  2. **Drops og Verktøykrav:**
     - Definer verktøykrav (øks, hakke, spade, saks osv.), verktøynivå (tre, stein, jern, diamant) og blokkhardhet.
     - Definer nøyaktige drops ved knusing (dropper seg selv, et annet element, erfaring, eller ingenting uten riktig verktøy/Silk Touch).
  3. **Lyd, Fysikk og Lys:**
     - Tilknytt korrekt lydtype (steg-, plasser- og knuselyder for tre, stein, sand, gress osv.).
     - Sett korrekte lys- og gjennomsiktighetsflagg for culling og lysutbredelse.
  4. **Navn og Lokalisering (`I18n`):**
     - Registrer oversettelsesnøkkel i ressursfilene slik at visningsnavnet vises riktig i UI, inventar og verktøytips fremfor en rå enum/ID.
  5. **Enhetstester:**
     - Verifiser crafting, drops og mekanikker med automatiserte, headless enhetstester under `src/test/java/`.

---

## 6. Bygging, Kjøring og Testkrav

### 6.1 Bygg og Avhengigheter
- **Kompiler og klargjør runtime-avhengigheter:**
  ```bash
  mvn compile dependency:copy-dependencies -DincludeScope=runtime -q
  ```
- **Kjør alle enhetstester:**
  ```bash
  mvn test
  ```
  *(Alle tester skal passere med 0 feil før arbeid regnes som fullført).*

### 6.2 Kjøring av Spillet
- **Windows (Native Java):** `run.bat` (bruker installert native 64-bit runtime for rå museinput og optimal GLFW/OpenGL-ytelse).
- **Linux / WSL:** `run.sh` (oppdager automatisk Windows runtime eller kjører via `mvn exec:exec`).

### 6.3 Headless Test-standard
- **Enhetstester må aldri initialisere GLFW eller OpenGL-vindu.**
- All logikk som testes (inventar, crafting, smelting, biome-generering, lagring, fysikk, kommandoer) skal isoleres fra rendering og kunne kjøre rent på en headless CI/CD-server eller i terminalen.

---

## 7. Kodekvalitet og Agent-atferd

1. **Ingen kode-regresjoner:** Verifiser alltid med `mvn test` etter endringer i kjerneklasser (`Chunk`, `World`, `Player`, `Inventory`).
2. **Ryddige diffs:** Ikke modifiser urelaterte formateringer, linjeskift eller eksisterende kommentarer.
3. **Null-sikkerhet:** Sjekk alltid for `null` ved henting av chunks, blokktyper, eller tile-data før bruk.
4. **Feilhåndtering:** Kritiske spill-løkker skal aldri krasje ubehandlet; loggfør feil og oppretthold stabilitet for spilleren.
