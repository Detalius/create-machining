# Create: Machining

Precision machining for [Create](https://github.com/Creators-of-Create/Create): CNC lathes, mills, tooling, and the hum of a spindle at speed.

**Targets:** Minecraft 1.21.1 · NeoForge · Create 6.0.10+

## What's in v0.2

### CNC Lathe
Takes rotational power through the back of the headstock. Needs **stock** and a **Carbide Insert** loaded (right-click, belt, funnel or hopper). Pull finished parts out by right-clicking with an empty hand, or with a funnel/hopper.

| Recipe | Ideal spindle speed |
|---|---|
| Iron ingot → 2 Iron Bushings (+50% Iron Swarf) | 32–96 RPM |
| Brass ingot → 2 Brass Bushings (+50% Brass Swarf) | 64–192 RPM |

Higher RPM = shorter cycle, but running outside the band costs quality. Stress impact: 4 SU/RPM. Engineer's Goggles show insert life and whether you're in band.

### Part quality
Every machined part leaves the lathe **uninspected**, carrying a hidden process score out of 95:

- **Speeds**: 1.0 inside the band, dropping 60% per 100% you're outside it (floor 0.2). The worst speed during the cycle counts.
- **Tool wear**: gentle wear across the first 75% of insert life (1.0 → 0.9), then a breakdown zone (0.9 → 0.5). Overspeeding wears inserts twice as fast.
- `score = round(95 × speed × tool)`

The **Inspection Station** measures parts one at a time (2 s each) and reports the **gage %** = score + a random 0–5. A perfect setup gages anywhere from 95% to 100%. Parts only stack with identical readings, so lots from different setups stay separate.

### Other
- Swarf → Crushing Wheels → nuggets.
- Carbide Inserts: compact iron ingot + coal + flint on a heated Blaze Burner → 2 inserts (64 cuts each).
- All the tunable quality numbers live in `content/quality/QualityModel.java`.

## Testing

- `./gradlew test` runs the unit tests for the quality math.
- `./gradlew runGameTestServer` boots a headless world and runs the in-game tests (real lathe, Creative Motor, inspection station).

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
  content/inspection/           the inspection station
  content/quality/              quality math (pure Java, unit tested)
  gametest/                     in-game automated tests
  client/                       rendering setup (the spinning spindle)
src/main/resources/
  assets/create_machining/      models, textures, translations (client-side looks)
  data/create_machining/        recipes, loot tables (server-side rules)
```
