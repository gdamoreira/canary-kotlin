# Wave 66 Plan — Persistence Save Tails

**Status:** Completed (2026-10-15) — four workstreams merged; 733 suites / 7979 tests green
**Baseline:** `main` @ `ce1e82e4`; 729 suites / 7958 tests green; zero Kotlin warnings
**Source of scope:** `docs/development/implementation-status.md` §3 item 1.
**Convention:** documents live directly in `docs/development/` (AGENTS.md §5/§6).

---

## 1. Objective

Close the persistence/save tails so logout, guild lifecycle and exiva state
persist exactly like Canary.

## 2. Workstreams

### C1 — Logout tail (`onRemoveCreature(isLogout=true)`)

**Scope**
- Port the logout branch: guild `removeMember`, party leave, and the
  save-on-logout call at the disconnect path so a logged-out member leaves
  `Guild.membersOnline` (no duplicate relog entries) and the player is saved.

**Files (owner):** `game/Game.kt` (removePlayer/removeCreature region),
`social/guild/**` (member-online bookkeeping only), `creatures/grouping/Party.kt`,
`network/protocol/GameHandler.kt`.

**Canary sources:** `src/game/game.cpp` (`removeCreature`/`removePlayer`),
`src/creatures/players/player.cpp` (`onRemoveCreature`),
`src/creatures/grouping/guild.cpp` (`removeMember`).

**Tests:** logout removes the member from `membersOnline`; relog doesn't
duplicate; party leave fires; save invoked once on logout.

---

### C2 — Exiva save

**Scope**
- Port `IOLoginDataSave::savePlayerExivaRestrictions`
  (iologindata_save_player.cpp:885) and wire it into the save path so the
  loaded exiva flags/whitelists persist.

**Files (owner):** `io/iologindata/**` (save-side files; keep `IOLoginData.kt`
hunks localized — C4 also touches the load side).

**Canary sources:** `src/io/iologindata_save_player.cpp:885`.

**Tests:** round-trip of exiva flags + whitelist through save→load.

---

### C3 — Guild removal, saveGuild and map saver

**Scope**
- `removeGuild` callback when the last member leaves (guild erased from
  `Game.guilds`, `saveGuild` seam bound), and wire `SaveManager.mapSaver` so
  the rotation persists maps too.

**Files (owner):** `social/guild/**`, `game/scheduling/SaveManager.kt`,
`bootstrap/ServerBootstrap.kt` (its own wiring hunk).

**Canary sources:** `src/game/game.cpp` (`removeGuild`, `saveGuild`),
`src/game/scheduling/save_manager.cpp`.

**Tests:** emptying a guild removes it from `Game.guilds`; `saveGuild` invoked;
`saveAll` reaches the map saver.

---

### C4 — Player group at login

**Scope**
- Set `player.group` from the account/`group_id` at login (Canary
  `Player::setGroup` during load) so group-gated logic stops seeing the default.

**Files (owner):** `creatures/players/**`, `io/iologindata/IOLoginData*.kt`
(load-side hunks only).

**Canary sources:** `src/io/iologindata_load_player.cpp` (group load),
`src/creatures/players/player.cpp` (`setGroup`).

**Tests:** loaded player carries the account's group; default stays for group 1.

---

## 3. Conflict matrix

| File | Owners | Rule |
|---|---|---|
| `game/Game.kt` | C1 (remove region), C3 (guild region) | localized hunks |
| `io/iologindata/IOLoginData.kt` | C2 (save), C4 (load) | localized hunks |
| `bootstrap/ServerBootstrap.kt` | C3 | additive |

## 4. Verification gate

`export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew clean test` —
zero failures, zero warnings; `git merge-base --is-ancestor` per branch.

## 5. Documentation gate (AGENTS.md §6)

Update `docs/development/implementation-status.md`, `README.md`, `CHANGELOG.md`
(Phase 69), and this document in place.

## 6. Out of scope

Combat/monster depth → wave 67; deferred list unchanged.

---

## Completion (2026-10-15)

logout-tail (`9c6bb3ff`), exiva-save (`ecde5b95`), guild-removal-map-saver
(`95762a71`), player-group (`a64bda5a`) — integrated suite **733 suites / 7979
tests, 0 failures, zero Kotlin warnings**; ancestry verified. Remaining tails
move to wave 67 (combat/monster depth + login/boot bindings).
