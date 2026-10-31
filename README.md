# EntityGovernor

Forge 1.7.10 population diagnostics and, eventually, spawn regulation.

## Phase 1 — read-only census

The initial version **does not cull entities or change spawning**. It counts currently loaded entities by their registered identifier (or Java class fallback), sorted by count, using loaded dimensions from Forge's DimensionManager.

Operator commands:

- `/eg census` — current dimension, top 20 types
- `/eg census top 50` — current dimension, top 50 types
- `/eg census 0 top 30` — Overworld, top 30 types
- `/eg census all top 10` — each loaded dimension, top 10 types
- `/eg census export` — write all entity types for the current dimension to a timestamped UTF-8 text file
- `/eg census export all` — export all loaded dimensions to one report

Exports are written to `logs/EntityGovernor/census-YYYYMMDD-HHmmss-SSS.txt` in the instance/server directory. Reports include registered IDs, Java class names, counts, and per-dimension totals. Players are labeled `Player` rather than `unregistered`. Exports always include all types; `top N` only limits chat output.

Counts cover loaded entities, not mobs in unloaded chunks. Commands print to chat; large reports may be truncated by chat history.

## Build status

Source prototype targeting Forge 10.13.4.1614. **Not yet compiled or tested in-game.** The legacy ForgeGradle 1.2 build may require an older compatible Java/Gradle environment. A reproducible build wrapper is still to be established.

## Roadmap

1. Validate census against CoFH loaded-entity counts.
2. Introduce opt-in per-type and per-dimension natural spawn limits.
3. Add local density controls and safe exemptions.
