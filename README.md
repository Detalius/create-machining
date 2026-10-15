# Create: Machining

Precision machining for [Create](https://github.com/Creators-of-Create/Create): CNC lathes, mills, tooling, and the hum of a spindle at speed.

**Targets:** Minecraft 1.21.1 · NeoForge · Create 6.0.x

## What's in v0.1

- **CNC Lathe**: takes rotational power through the back of the headstock. Load bar stock (right-click, belt, funnel or hopper), it turns parts, and you pull them out the same ways.
  - Iron ingot → 2 Iron Bushings (+50% Iron Swarf)
  - Brass ingot → 2 Brass Bushings (+50% Brass Swarf)
  - Cycle time drops as RPM goes up. Stress impact is 4 SU/RPM.
- **Swarf recycling**: put swarf through Crushing Wheels to get nuggets back.
- Lathe recipe: brass sheet / precision mechanism / brass sheet over shaft / brass casing / shaft over 3 andesite alloy.

## Setting up (first time)

1. Install **IntelliJ IDEA Community Edition**.
2. **File → Open** and pick this folder. IntelliJ sees `build.gradle` and imports it as a Gradle project. If it asks for a JDK, have it download **Java 21** (any vendor, e.g. Temurin).
3. The first import is slow because it downloads Minecraft, NeoForge and Create. Let it finish.
4. Open the **Gradle** tab on the right side and run **create-machining → Tasks → mod development → runClient**. A dev copy of Minecraft opens with Create, JEI and our mod loaded.

To build a jar you can drop into a real CurseForge instance, run **build → build**. The jar ends up in `build/libs/`.

## Project layout

```
src/main/java/com/jessehandke/createmachining/
  CreateMachining.java          entry point: registers everything
  registry/                     lists of blocks, items, block entities, recipe types, creative tab
  content/lathe/                the lathe: block, block entity (the tick logic), turning recipe
  client/                       rendering setup (the spinning spindle)
src/main/resources/
  assets/create_machining/      models, textures, translations (client-side looks)
  data/create_machining/        recipes, loot tables (server-side rules)
```
