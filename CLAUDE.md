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

Project scaffolded (NeoForge 26.1.2.109 / JDK 25, mirroring Cartographyr's toolchain exactly). No real game mechanics implemented yet — the only runtime behavior so far is a debug-only login listener (`LyfeMod`) that grants oak signs, oak fences, and maps for manually testing the future sign/map mechanic. `neoforge.mods.toml` already declares Cartographyr as an optional soft dependency per Section 8.

Confirmed first implementation milestone: **Lumberjack and Miner** (Appendix B) — no external dependency, first real exercise of the core XP/leveling system (Phase 1, not yet started). Cartographyr skill + Historian come next, blocked on Cartographyr gaining settlement-detection functionality first (design-document.md Section 14, Phase 3.5 — that's Cartographyr repo work, not Lyfe).

Next: implement Phase 1 (core skill/XP/leveling data model), then Lumberjack and Miner.
