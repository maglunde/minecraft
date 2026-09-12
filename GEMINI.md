# Prosjektinstruksjoner: Minecraft Java Edition 1.16.1 Clone

## Referanseversjon og Mål
- **Målversjon:** **Minecraft Java Edition 1.16.1**
- Når brukeren sier **"som i originalen"**, **"som i minecraft"**, eller ber om funksjonalitet uten å spesifisere versjon, er det **alltid Minecraft Java Edition 1.16.1** vi etterligner.
- Alle mekanikker, oppskrifter, blokk-egenskaper, verktøy-statistikk, fysikk, combat, UI/HUD, ovn/smelting og mob-atferd skal legge seg så tett opp til **Java Edition 1.16.1** som mulig.

## Retningslinjer for Implementasjon
- **Ressurser & Teksturer:** Bruk offisielle teksturer og modeller fra Java 1.16.1-assetstrukturen (`src/main/resources/assets/textures/`).
- **Crafting & Smelting:** Følg nøyaktige oppskrifter, smelte-tider og drivstoffverdier fra Java 1.16.1.
- **Combat & Bevegelse:** Java 1.16 combat og bevegelse (critical hits ved fall, våpenskade, knockback, båtfysikk med årer og passasjerer, sult/metthet).
- **Blokk-oppførsel:** Væsker (lava/vann) flyter og kan ikke mines/plasseres som faste blokker (krever bøtte), verktøykrav for drops, og korrekte hardhetsverdier.
- **Inventar & GUI:** Java 1.16-oppførsel for musedrag (høyreklikk for 1 per slot, venstreklikk for jevn fordeling), søk og kategorier i oppskriftsbok, samt korrekte stack-størrelser.
