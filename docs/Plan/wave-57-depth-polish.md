# Wave 57 Plan — Final Depth Polish

**Status:** In progress
**Process:** AGENTS.md §6.

## File ownership

| Agent | Owns |
|---|---|
| A1 monster-type-depth | `bootstrap/**`, `creatures/monsters/**`, `lua/**` (monster-type registration path) |
| A2 subloads-polish | `io/**` |
| A3 wheel-apis | `systems/wheel/**` |
| A4 storage-composition | `creatures/PlayerFeatures.kt`, `creatures/players/**` |

## Tasks

- **A1** populate the booted `Monsters` registry from the datapack: run the Lua monster-type registration path at boot (or parse the data pack's type definitions) so `g_monsters()` is non-empty; deepen the XML mapping where feasible.
- **A2** outfit sub-load + `Reward`-as-`Container` shape parity.
- **A3** `PlayerWheel` upgrade/additional-area/target/duration/`unlockScroll`/`onThink` APIs (the Lua extra-ops gaps).
- **A4** `Player::m_storage` host composition (PlayerStorage over outfits/familiars), host title/badge player reads, `AnimusMastery` blob persistence wiring.

## Rules

- §1.1 parity, §1.2 WorldCommit confinement, §2.1 Flyway discipline, §4 TDD, zero warnings.
- Commit on your branch (no merge/push).

## Post-wave (§6)

Update §1–§3, `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.
