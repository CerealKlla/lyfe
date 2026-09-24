# Lyfe

Player-centric RPG progression mod for a modular Minecraft project (Minecraft Java Edition). Part of the same suite as Cartographyr — owns the player (skills, XP, levels, subjective world knowledge); Cartographyr owns objective world truths. Lyfe treats Cartographyr as an optional soft dependency: most skills work standalone, but Cartographyr-requiring skills (named after that dependency, e.g. the "Cartographyr" skill) are entirely absent from the skill tree when Cartographyr isn't loaded.

## Context directory — read this first

`context/` is a **separate private repo** (https://github.com/CerealKlla/lyfe-context), not part of this one — it's listed in `.gitignore` here and must never be committed to this repo. It's cloned as a subdirectory at `context/` for local convenience. If this directory is missing (e.g. a fresh clone of just this repo), restore it with:

```
git clone https://github.com/CerealKlla/lyfe-context.git context
```

- `context/design-document.md` — the authoritative design spec. Start here for anything about intended shape or scope.
- `context/decisions.md` — dated log of decisions made during implementation that extend or override the design document, with rationale.
- `context/classes/` — one short markdown file per implemented class: public surface, key state, collaborators. Read the relevant file here before opening the actual source, and before editing a class update its file to match.

**Keep this system current as you work:**
- When a design decision is made that conflicts with or is absent from design-document.md, update design-document.md directly and add a dated entry to decisions.md explaining the change.
- When a class is added or its public surface changes, add or update its file in `context/classes/`.
- Don't let source and these docs drift — treat updating them as part of finishing the change, not optional cleanup.
- `context/` has its own git history, independent of this repo's commits. Commit and push changes there separately (`git -C context add . && git -C context commit -m "..." && git -C context push`) — editing the files alone doesn't back them up.

## Status

Project scaffolded (NeoForge 26.1.2.109 / JDK 25, mirroring Cartographyr's toolchain exactly). `neoforge.mods.toml` declares Cartographyr as an optional soft dependency per Section 8 (no `versionRange` field — an empty string there is an invalid range that makes FML reject an installed Cartographyr outright, not "accept any version"; confirmed via a failed boot test, 2026-09-24).

**Phase 1 (Core Skill Data Model) implemented, 2026-09-24** — see [context/decisions.md](context/decisions.md):
- `.skill` package: `SkillId`, `SkillCategory`, `EffectType`, `SkillEffect`, `XpCurve`, `SkillDefinition`, `SkillRegistry` (in-memory, soft-dependency-aware `available()` filtering for Section 8).
- `.data.PlayerSkills` — per-player XP, persisted via NeoForge's Data Attachment API, schema-versioned `MapCodec`.
- `.registration.ModAttachments` — the `DeferredRegister<AttachmentType<?>>`.
- `.api.Lyfe` — the stable public facade (`getXp`/`addXp`/`getLevel`/`getAvailableSkills`), mirrors Cartographyr's `Cartography` facade pattern.
- Lumberjack and Miner registered as `SkillDefinition`s (id/category/curve) with an intentionally empty effects list — the Section 6 tool-tier gates are Phase 2 work (need real vanilla `Tiers` mapping + block-break event hooks), not invented ahead of that.
- 12 unit tests (XP curve math, effect stacking, registry filtering, attachment codec round-trip). Boot-smoke-tested via `runServer` with both Cartographyr and Lyfe loaded.

**Phase 2 (Lumberjack & Miner) implemented 2026-09-24, minus tool-tier gating** — see [context/decisions.md](context/decisions.md):
- New `.gathering` package: `GatheringSkill` (block -> Lumberjack/Miner via `BlockTags.LOGS`/NeoForge's `Tags.Blocks.ORES`), `WholeStructureClear` (pure flood-fill, needs a live level so not unit-tested — same limitation as Cartographyr's `NaturalRegionDiscovery`), `GatheringListener` (the event wiring: `BlockDropsEvent` for XP/bonus-yield/whole-structure-clear, `PlayerEvent.BreakSpeed` for speed scaling).
- `PLAYER_SKILLS` attachment now also syncs to the client (`ModAttachments`) — needed because `Player#getDestroySpeed` runs both sides.
- **Tool-tier gating (Section 7) is deliberately NOT implemented** — deferred until the crafting overhaul (Section 10) has a concrete tool-tier design to gate against; `EffectType.TOOL_TIER_GATE` carries a doc comment explaining this so it doesn't read as an oversight.
- **Anti-farming (added 2026-09-24, surfaced by playtesting)**: `PlacedGatheringBlocks` tracks exact positions of player-placed logs/ores; breaking one still drops the item normally but grants no XP/bonus-yield/whole-structure-chance. Placed blocks are also immovable by pistons (scoped to tracked positions only, not a blanket rule) to close the piston-laundering variant.
- Boot-smoke-tested via `runServer` with both mods loaded, clean.

**`.location` package added 2026-09-24** (see [context/decisions.md](context/decisions.md)) — the persistent "Location: <name>" HUD overlay, moved here from Cartographyr at the user's request: a location readout is player knowledge/belief, not world truth, so it belongs in Lyfe. `LocationTracker` (tick-based detection/debounce, ported from Cartographyr's old code) reads Cartographyr's public `Cartography` API only; `LocationPayload`/`ClientLocationState`/`LocationOverlay` handle the sync+display, with a mostly-solid dark background per request. **First real compile-time dependency on Cartographyr's Java API** — `compileOnly files("../Cartographyr/build/libs/...")` in `build.gradle`, kept off the runtime classpath; actual optionality enforced by only constructing/registering `LocationTracker` behind `ModList.get().isLoaded("cartographyr")` in `LyfeMod`. Verified boot-clean both with and without Cartographyr present. Still an honest simplification — mirrors Cartographyr's live truth on a timer, not yet gated by/recorded into a real persisted player-knowledge store (Section 9.3, still just a design-doc table, no code yet).

**Section 10 (Survivalist & Cook) designed 2026-09-24, not yet implemented** — see [context/decisions.md](context/decisions.md). A "decoupled hunger" mechanism: vanilla's real `FoodData` keeps running untouched (still drives real depletion via its own exhaustion math), Lyfe mirrors real point-losses 1:1 (absolute, not scaled) into its own true hunger number (grows past vanilla's 20-point cap up to 60/30 icons as Survivalist levels), keeps vanilla's real value pinned at a safe-but-hungry level so vanilla's own sprint-lock/regen/starvation never fire on stale data, and enforces those same effects itself against the true number using vanilla's own fixed absolute thresholds (confirmed: sprint needs `foodLevel > 6`). This is what makes leveling a genuine capacity reward rather than a cosmetic rescale. Display: `RegisterGuiLayersEvent#replaceLayer(VanillaGuiLayers.FOOD_LEVEL, ...)` swaps out vanilla's bar entirely for a custom one, no mixin needed anywhere. Cook (renamed from the old "Chef" placeholder) boosts cooked-food restoration specifically, independent of Survivalist's capacity growth. This inserted a new Section 10 into design-document.md and renumbered everything after it (old 10→11 through 15→16) — see decisions.md for the full mapping before trusting an old cross-reference's section number.

Next: implement Section 10 (Phase 2.5, not yet ordered relative to Phase 3+) whenever picked up, or continue with Cartographyr skill + Historian, blocked on Cartographyr gaining settlement-detection functionality first (design-document.md Section 15, Phase 3.5 — Cartographyr repo work, not Lyfe). A noted-for-later idea worth remembering when that work starts: an "Explorer" skill (name TBD — may overlap with Expeditionist or the Cartographyr skill, not resolved) gaining XP per new world-knowledge entry, eventually evolving `.location` into a real minimap — see decisions.md. Also worth eventually revisiting: the placeholder XP/speed/yield/whole-structure-chance magnitudes are all untuned guesses, flagged as such in code.

**Multi-mod dev testing**: `C:\Users\benja\MinecraftMods\sync-mods.sh` (outside both repos, not committed to either) builds every mod in the suite and cross-copies jars into each other's `run/mods/`, so any one project's `runClient`/`runServer` loads the whole suite together. Re-run it after code changes in either mod before a combined playtest.
