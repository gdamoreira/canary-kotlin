# Wave 51 Plan — Depth Closure (Player, Host Bindings, Persistence, Barrier Lanes)

**Status:** In progress
**Process:** AGENTS.md §6. The tracked inventory is 100% IMPLEMENTED; this wave closes depth/composition gaps.

## File ownership

| Agent | Owns |
|---|---|
| A1 player-depth-8 | `creatures/players/**` |
| A2 host-bindings | `creatures/Creature.kt`, `network/protocol/GamePacketRouter.kt` (cyclopedia delegation only) |
| A3 persistence-depth-2 | `io/**` |
| A4 barrier-lanes | `game/scheduling/**`, `creatures/monsters/**` |

## Tasks

- **A1** remaining ~111 `Player::` methods toward 821 raw.
- **A2** host bindings: compose the player state holders (item abilities, boss points, cyclopedia trackers, aim-at-target, magic affinity) into the host `Player`/`PlayerFeatures`; `Game.playerSay`→`removeMessageBuffer`; delegate the cyclopedia router branch to `Game.playerCyclopediaCharacterInfo`; imbuement adapter stats (`setVarSkill`/`setBonusCapacity`/`sendStats`).
- **A3** persistence depth: live inventory save walk (`savePlayerItem` row builder from `inventory[slot]` + BFS `sid` assignment) and `OPENCONTAINER` re-arming from `getOpenContainers`.
- **A4** barrier-lane execution for creature async tasks (barrier-parallel buckets, per-slice budget, rejection rollback) replacing the WorldCommit mapping.

## Rules

- §1.1 parity, §1.2 WorldCommit confinement, §4 TDD, zero warnings.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

## Post-wave (§6)

Update §1–§3, `docs/game-play-features.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

## Completion

Completed: player depth 8 (733/821), host bindings, persistence depth (live save walk + OPENCONTAINER), barrier lanes. 643 suites / 7237 tests.
