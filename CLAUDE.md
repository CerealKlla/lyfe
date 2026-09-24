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
- Boot-smoke-tested via `runServer` with both mods loaded, clean.

Next: manual in-game playtest of the gathering behavior (chop/mine XP, speed feel, bonus yield, whole-structure clear on a tree/ore vein) — not yet done. After that: Cartographyr skill + Historian, blocked on Cartographyr gaining settlement-detection functionality first (design-document.md Section 14, Phase 3.5 — Cartographyr repo work, not Lyfe). Also worth eventually revisiting: the placeholder XP/speed/yield/whole-structure-chance magnitudes are all untuned guesses, flagged as such in code.

**Multi-mod dev testing**: `C:\Users\benja\MinecraftMods\sync-mods.sh` (outside both repos, not committed to either) builds every mod in the suite and cross-copies jars into each other's `run/mods/`, so any one project's `runClient`/`runServer` loads the whole suite together. Re-run it after code changes in either mod before a combined playtest.
