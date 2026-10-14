# Systems

> Adapted from canary/docs/systems/README.md.
> Canary-Kotlin status: index adapted for the ported system contracts. Each
> document states its own Canary-Kotlin status; C++ file paths refer to the
> upstream engine at `../.projects/canary`.

System documentation covers cross-cutting server features that span multiple
modules, message handlers, scripts, database state, or external integration
points.

- [Client 15.25 compatibility update](client-15-25-compatibility-update.md) —
  protocol profile model ported; Task Board remains Lua
- [Content reference auditor](content-reference-auditor.md) — upstream C++
  tooling reference (not ported)
- [Expert PvP porting plan](expert-pvp-porting-plan.md) — upstream engine
  reference; decision model partially ported
- [Expert PvP implementation roadmap](expert-pvp-implementation-roadmap.md) —
  upstream engine reference for the remaining work
- [Livestream](livestream/README.md) — engine core ported
- [Lua API documentation generator](lua-api-docgen.md) — upstream generator
  reference; the generated Lua API docs are ported
- [Lua shared userdata ownership](lua-shared-userdata.md) — upstream C++
  ownership contract with Kotlin LuaJ notes
- [Multiprotocol runtime profiles](multiprotocol.md) — profile model ported
- [Performance and lifetime roadmap](performance-lifetime/README.md) — upstream
  C++ engine reference
- [Parallel monster AI and dispatcher fairness roadmap](performance-lifetime/parallel-monster-ai-roadmap.md)
  — upstream C++ engine reference
- [Task Board module](taskboard-module.md) — Lua module (Lua stays Lua)
- [Weapon Proficiency](weapon-proficiency.md) — implemented by the Kotlin port
  (development/done/060)
