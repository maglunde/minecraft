# Minecraft Java Clone ⛏️

En 3D voxel Minecraft-klone bygget i Java 21 med LWJGL 3 og OpenGL.

**Målversjon:** Prosjektet etterligner **Minecraft Java Edition 1.16.1** så nøyaktig som mulig for alle spillmekanikker, oppskrifter, fysikk, combat, UI og blokk-/gjenstandsegenskaper.

## Innhold og funksjoner
- **3D Blokk-drops & Oppsamling (Minecraft-stil)**:
  - Når du hakker på en blokk, fjernes den fra terrenget og legger seg som en **roterende og svevende 3D-miniatyrblokk på bakken**.
  - Droppet spretter og faller med tyngdekraft mot bakken.
  - Når du beveger deg bort til blokken (magnet pickup innenfor 1.5–2 blokker), suges den mot deg og plukkes automatisk opp i inventoryet.
- **Ekte ressursbegrensning & Stack-størrelse**:
  - Du starter med **helt tom inventory i Survival**.
  - Du kan kun plassere nøyaktig de blokkene du har plukket opp (høyreklikk trekker 1 fra stacken; har du 0 kan du ikke bygge).
  - Maksimal stack-størrelse er **64** per slot.
- **Autentisk Minecraft HUD & GUI**:
  - **In-Game HUD**: 10 pikselerte hjerter (20 HP), 10 kyllinglår (sultbar), grønn XP-bar og 9-spors metallisk hotbar med hvite 3×5 pikseltall for antall.
  - **Inventory & Crafting GUI (`E`)**: Ekte Minecraft-panel med rustnings-slots, 2D Steve figur, $2\times2$ Crafting Grid, $➔$-pil, resultat-slot, grønn Recipe Book og full $3\times9$ inventory-oversikt.
- **Voxel Terrenggenerator**: Uendelig, dynamisk 3D-terreng med åser, strender og eiketrær.
- **Spillerfysikk**: Kollisjonsdeteksjon i alle akser, tyngdekraft, sprint-hopping med momentum og jump buffer.

---

## Kontroller

| Tast / Mus | Handling |
|---|---|
| **W, A, S, D** | Bevegelse (gange) |
| **Dobbelt-trykk W** | **Sprint** (Minecraft-stil: trykk W to ganger raskt og hold) |
| **Venstre Shift / Tab / R / Ctrl** | Hold inne for å **Sprinte** |
| **Mellomrom (Space)** | Hopp (hold inne mens du sprinter for **sprint-hopping**) |
| **C / Venstre Alt** | Sneak / fly ned (i flygemodus) |
| **E** | **Åpne / Lukk Crafting & Inventory** |
| **G** | **Veksle mellom Survival Mode og Creative Mode** |
| **Venstreklikk** | Hakke blokk (spawner 3D blokk-drop på bakken) |
| **Gå over blokk-drop** | **Plukk opp gjenstanden automatisk** inn i inventoryet |
| **Høyreklikk** | Plasser valgt blokk fra inventoryet (opp til maks 64) |
| **1 – 9 / Scrollhjul** | Velg blokk i hotbaren |
| **F** | Slå av/på flygemodus (*kun i Creative Mode*) |
| **P** | Respawn / tilbakestill spillerposisjon |
| **ESC** | Lukk meny / Frigi eller lås musepekeren |

---

## Hvordan starte spillet

Kjør oppstartsskriptet i terminalen:
```bash
./run.sh
```

Eller direkte med Maven:
```bash
mvn compile exec:exec
```
