# Parity Gaps & Next Steps — Ranked Roadmap

> Status snapshot: `main` @ wave 90 (`b697c919`), 835 suites / 8526 tests green.
> Marker census at that commit: **574 `PARITY GAP` markers — 98 actionable, 476 documented boundaries.**

This document ranks every open parity gap against the C++ oracle
(`../canary`) and lists the non-parity workstreams (live validation,
performance, world content). It is the working backlog for the remaining
byte-level-fidelity work; `docs/development/implementation-status.md` stays the
feature matrix and `CHANGELOG.md` the completed-wave record.

## 0. How this document is maintained

Regenerate the census at any commit:

```bash
# every marker
rg -n "PARITY GAP" src/main/kotlin --include='*.kt'

# the actionable subset (excludes "documented boundary" notes)
rg -n "PARITY GAP" src/main/kotlin --include='*.kt' | \
  rg -i "not ported|not modelled|unported|no-op|inert|stub|absent|missing|not implemented|no equivalent|not wired|uncovered|uncomposed|unapplied|not overridden|not sent|not parsed|not emitted|no seam"

# per-module counts
rg -n "PARITY GAP" src/main/kotlin --include='*.kt' | cut -d: -f1 | sed 's|src/main/kotlin/org/opentibia/canary/||' | awk -F/ '{print ($2=="" ? $1 : $1"/"$2)}' | sort | uniq -c | sort -rn

# the boundary notes (intentional confinement/modelling documentation)
rg -n "PARITY GAP" src/main/kotlin --include='*.kt' | rg -i "boundary|confinement|documented|recorded|reduced to the|no invented"
```

Rules:
- When a wave closes an item, strike it here (move to the wave's CHANGELOG
  phase) and refresh any code marker that claims otherwise.
- New markers must name the C++ symbol and file:line they transcribe
  (existing convention); a marker with a locatable owner call site belongs in
  the actionable list.
- Items resolved by wave 90 are already excluded (outfit window 0xD2,
  blessing window 0xCF, hotkey equip 0x77).

## 1. Priority ladder

| Priority | Meaning | Exit criterion |
| --- | --- | --- |
| **P0** | Blocks or visibly degrades the live playable stack; integrity/performance of the running world | Reproduced with the Docker stack + OTClient, fixed and re-verified live |
| **P1** | Feature or sub-behavior incomplete and reachable in normal play | Ported with tests against the C++ symbol and exercised through the protocol/router layer |
| **P2** | Fidelity/edge case: rare paths, structural modelling differences, upstream-inert branches | Ported or explicitly accepted with a rationale comment (no silent divergence) |
| **P3** | Documented boundaries: intentional confinement/modelling notes (no open work) | Reviewed and kept as maintenance documentation |

---

## 2. P0 — live-stack blockers and user-visible integrity

### P0-1. OTC login preamble and session `isOTCR` state
- **Gap:** `LoginFeatureWriter` (features 0x43 `sendFeatures`/`sendOTCRFeatures`,
  preamble 0x32 `sendClientLoginPreamble`, protocolgame.cpp:10682-10725,
  12361-12378) exists but is never composed into the session login tail; the
  port adds its own extended-opcode enable instead. The session never records
  `isOTCR` (`protocolgame.hpp:642`, set at protocolgame.cpp:12369), so the
  outfit-window custom block (`sendOutfitWindowCustomOTCR`,
  protocolgame.cpp:11880-11956 — wings/auras/effects/shaders lists) is never
  sent even to OTClient Redemption, and no OTCR feature negotiation happens.
- **Next step:** call `writeClientLoginPreamble` at the login tail for
  OTClient-family clients, track `isOTCR` on `NettyProtocolSink`/`PlayerSendSink`,
  and feed `OutfitWindowComposer`'s `customOtcr` lists from
  `AttachedEffects` (enabled wings/auras/effects/shaders).
- **Acceptance:** OTClient Redemption log shows the feature frames; the outfit
  window carries the OTCR block when the client is OTCR-capable; extended
  opcodes work without the port-specific enable.
- **Evidence:** `docs/development/wave-82-opcodes.md` (extended-opcode
  compatibility note); `network/protocol/login/LoginFeatureWriter.kt` unused;
  user-reported OTClient crash pending a macOS crash report
  (`~/Library/Logs/DiagnosticReports/OTClient-*.ips`).

### P0-2. Live validation of the wave-77..90 fixes
- **Gap:** walk pump, depot search, creature/position bindings, inventory
  frames, outfit/blessing windows and the MariaDB-default stack are committed
  and unit-green but not yet re-verified against the running Docker stack and
  a real client ("slow / walk problems / no creatures / disconnection" was the
  last live report).
- **Next step (checklist):**
  1. `docker compose up -d --build` (repo root; MariaDB is the default; the
     game applies pending Flyway migrations, e.g. V25 `players_online`).
  2. `docker exec -i canary-kotlin-mariadb mariadb -uroot -proot canary < scripts/god-loadout.sql`
     once (level 4000 + gear is already applied; re-running is idempotent).
  3. OTClient: log in as `@god`/`god`; verify walk (no rubber-banding),
     sewers rats spawn, depot chest opens (`stepin` script), outfit window
     (Ctrl+O) and blessing window open, relog keeps position, inventory shows
     the falcon/sanguine set, character list shows level 4000.
  4. If OTClient still closes on extended opcodes, capture
     `~/Library/Logs/DiagnosticReports/OTClient-*.ips` and `/tmp/otclient-live.log`.
- **Acceptance:** no client disconnects, no lost steps, all windows open.

### P0-3. Spawn placement failures and an unregistered monster type
- **Gap:** otservbr-global boot places 82,991 monsters but **276 placements
  fail**; "Beholder" is the only spawn name with no registered monster type.
  Failed placements silently reduce world content.
- **Next step:** log-and-inspect batch (already logs first failures through
  `GameMonsterFactory`); decide per cause (blocked tiles vs missing types vs
  radius overflow) and fix the top two causes; register/alias `Beholder` if
  the datapack expects it.
- **Acceptance:** boot log shows the failure count drop and names every
  remaining cause; parity with canary's own failure set.

### P0-4. Monster think CPU envelope
- **Gap:** sustained ~150% CPU with 82,991 monsters and no players (boot burst
  ~15 cores for a few seconds). Canary's own tick budget is the baseline.
- **Next step:** profile `MonsterThink`/`onThink` dispatch (event pump vs the
  port's per-monster pipeline host), batch the think slices like the C++
  `checkCreatures` vector walk, and re-measure with the same 184MB OTBM.
- **Acceptance:** sustained idle CPU comparable to C++ canary on the same
  world; no behavior change in monster tests.

---

## 3. P1 — reachable feature gaps

### 3.1 Protocol & network (`network/protocol/**`)

| # | Gap | Evidence | Next step |
| --- | --- | --- | --- |
| P1-1 | Party opcodes (invite/join/revoke/pass/leave/shared-exp) decode but never apply: the router context has no global player registry | `GamePacketRouter.kt:480` | Route `playerInviteToParty`…`playerEnableSharedPartyExperience` through `context.game` (`getPlayerByGUID`-style seam) |
| P1-2 | TeamFinder: member status/join against online members inert; `sendLeaderTeamFinder`/`sendTeamFinderList` senders unported | `GamePacketRouter.kt:838`, `TeamFinderPacketHandler.kt:113` | Resolve members via the game registry; wire the two senders through the sink |
| P1-3 | `Game::playerSetTyping` spectator broadcast `sendPlayerTyping` unported | `GamePacketRouter.kt:867` | Add the 0x?? sender + spectator fanout |
| P1-4 | `Game::playerSetMonsterPodium` writes podium custom attributes + tile resend — podium engine unported | `GamePacketRouter.kt:989` | Port podium item state (custom attributes dependency below) |
| P1-5 | `playerCancelAttackAndFollow` tail: `resetLoginProtection`, follow state machine, `stopWalk`, cancel-target send | `GamePacketRouter.kt:1203` | Port the missing three calls |
| P1-6 | Transaction details (`0xC9`) response sender unported (request is a read-and-discard) | `TransactionDetailsPacketHandler.kt:10` | Port `sendTransactionDetails` |
| P1-7 | Normal-client `0xCA` = `parseExivaRestrictions` unported (only the livestream-viewer branch exists) | `ContainerBrowsePacketHandler.kt:24` | Port the request/response pair (`sendExivaRestrictions` sink already exists from the login path) |
| P1-8 | QuickLoot `findNextAvailableContainer` nested-container search + `handleFallbackLogic` unported | `QuickLootPacketHandler.kt:620` | Port both branches of `Game::processLootItems` |
| P1-9 | Market: `lockerItems` ware-id map + `ItemType::maxItems` guard; pagination capacity checks + partial-insertion rollback | `MarketOfferApplication.kt:403,454` | Port the locker walk and the rollback path |
| P1-10 | Loot-pouch parent gate: `Player` is not a `Cylinder`, so `getParent()->getItem()` is always null | `GameHandler.kt:311` | Model the player's cylinder parent (structural, see P2-? Cyclinder item) |
| P1-11 | Inventory-imbuements window: `imbuementTrackerWindowOpen` flag + `sendInventoryImbuements` sender unported | `GamePacketRouter.kt` (0xD6-IV arm, unported marker) | Port the flag + sender |
| P1-12 | Protocol sub-behavior sweep: ~81 boundary notes in `network/protocol/**` (e.g. `OutfitPacketHandler` OTCR wing/aura reads, `TextWindowWriter` OTCR block) need a stale pass + batching | census | Batch-review; port or refresh each note |

### 3.2 Combat, charms, bestiary, spells

| # | Gap | Evidence | Next step |
| --- | --- | --- | --- |
| P1-13 | `Player::getCharmChanceModifier`/`setCharmChanceModifier` not modelled (bestiary charm tier rolls) | `CreatureCombatTarget.kt:291,294` | Add the field to `Player` + wire the charm rolls |
| P1-14 | `sendStats`/`sendSkills`/`sendMonkData` not reachable through the combat-target view | `CreatureCombatTarget.kt:297,300,303` | Wire through the sink |
| P1-15 | `Player::setImmuneFear` state lives in the unported soul-war engine | `CreatureCombatTarget.kt:316` | Decide: port the soul-war flag or keep reduced (document) |
| P1-16 | Defensive bestiary charm run (`game.cpp:9195-9223`), `CHARM_VOIDINVERSION` inversion tail, `updateInputAnalyzer` | `Game.kt:13025` | Port the defensive charm pass + analyzer update |
| P1-17 | `sendMessages` low-blow/savage charm notes | `Game.kt:13212` | Port the two note branches |
| P1-18 | `applyLifeLeech` `CHARM_VAMP` bonus; `applyManaLeech` `CHARM_VOID` bonus | `Game.kt:13370,13399` | Port the charm bonuses into the leech paths |
| P1-19 | `notifySpectators` (hazard dodge) cancel message | `Game.kt:13329` | Port the cancel |
| P1-20 | `Combat` critical: bestiary `lowBlow`/`savageBlow` + fatal item-tier roll; `canDoCombatWithExpertPvp` reduced to a two-clause gate; healing order | `Combat.kt:311` | Port the critical pipeline + expert-pvp predicate |
| P1-21 | `ConditionDamage`: `Combat::canDoCombat` gate + POFF magic effect reduced to `isAttackable` | `ConditionDamage.kt:283` | Port the C++ reduction |
| P1-22 | Wheel/augment cooldown + mana reductions unmodelled; `getMaxValuePerSkill` cap; wheel capacity stat; `SpellRegistry` wheel/proficiency/augment terms | `SpellEngine.kt:27,418`, `SpellRegistry.kt:16`, `Creature.kt:4526,4534` | Port the wheel/augment reduction terms |
| P1-23 | `PlayerSpellCaster` `lastAggressiveAction` write inert (host player does not own the attack state) | `PlayerSpellCaster.kt:54` | Compose `PlayerAttackState` into `Player` |
| P1-24 | `CombatPlayer` view: `sendMagicEffect` unwired; formula callback precedence reduced; `doAreaCombat*` inert without a wired map | `LuaCombatWorld.kt:153,331,844` | Wire the map/view seams |

### 3.3 Party system

| # | Gap | Evidence | Next step |
| --- | --- | --- | --- |
| P1-25 | `eventPartyOnDisband`/`eventPartyOnJoin` + `g_callbacks()` vetoes unported | `Party.kt:113,156` | Port the event/callback veto chain |
| P1-26 | `forceRemove` veto override has no observable effect (vetoes absent) | `Party.kt:233` | Follows P1-25 |
| P1-27 | `shareExperience` Lua callback that may mutate the amount | `Party.kt:274` | Port the `eventPartyOnShareExperience` hook |
| P1-28 | `updateAllPartyIcons()` broadcast | `Party.kt:310,354` | Port the 0x?? icon broadcast |
| P1-29 | Leader text messages on shared-exp toggle (`silent`, `getSharedExpReturnMessage`) | `Party.kt:310` | Port the messages |

### 3.4 Monster engine

| # | Gap | Evidence | Next step |
| --- | --- | --- | --- |
| P1-30 | `MonsterType`: `loadLoot`, `canSpawn`, `getHealth/Attack/DefenseMultiplier` | `MonsterType.kt:264` | Port (needs `RATE_*` config, item registry, day/night state) |
| P1-31 | `dropCorpse` Lua death events, loot message, auto-loot | `MonsterBehavior.kt:724` | Port the corpse tail |
| P1-32 | Loot-rate scaling, loot factor, gut charm, post-drop-loot callbacks (Lua-side) | `MonsterLoot.kt:84` | Port the scale/factor; keep Lua callbacks Lua |
| P1-33 | Legacy raid composition: no production `RaidSpawner`/`RaidMonster` bridge | `MonsterPipelinesHost.kt:691` | Port the bridge |
| P1-34 | The 50 custom-map spawn loops (`game.cpp:972-977`) unported | `Game.kt:12168,12278` | Port the spawn loops at INIT |

### 3.5 Lua surface (inert APIs)

| # | Gap | Evidence | Next step |
| --- | --- | --- | --- |
| P1-35 | `clearSpellCooldowns` has no `Player::clearCooldowns` | `PlayerFunctions.kt:2607` | Model the cooldown state |
| P1-36 | `resetOldCharms` not ported | `PlayerFunctions.kt:2754` | Port the charm reset |
| P1-37 | `getFaction`/`setFaction`: no faction field | `PlayerFunctions.kt:2892` | Add the field |
| P1-38 | `addMapMark` no marker protocol sink | `PlayerFunctions.kt:2911` | Port `sendAddMarker` |
| P1-39 | `setGhostMode`/`getGhostMode` no state; `setMapShader`/`getMapShader` no shader state; `setTraining`/`isTraining` no exercise state | `PlayerFunctions.kt:2915,2921,2927` | Model the three flags |
| P1-40 | `openStash` no stash-open sink; `sendContainer`/`sendUpdateContainer` no container sinks | `PlayerFunctions.kt:3000,3028,3031` | Wire the sinks |
| P1-41 | `Npc:move` no-op (no move engine wired); npc sell-items grant/trade feedback | `NpcFunctions.kt:525,546` | Wire `Game::internalMoveCreature`; port the grant/feedback |
| P1-42 | `PlayerFeatures` achievement sender seam inert (`sendCyclopediaCharacterAchievements`) | `PlayerFeatures.kt:365` | Wire to the sink |
| P1-43 | `removeExperience` per-level health/mana/capacity loss + client broadcast | `WorldLuaOperations.kt:1176` | Port the level-down tail |
| P1-44 | `getItemDescription` `g_spells()`/`g_vocations()`/`getObjectCategory` seams absent at the Lua layer | `WorldLuaOperations.kt:1649` | Compose the seams |
| P1-45 | Forge monster classification: icon update + `ForgeSystemMonster` event registration | `WorldLuaOperations.kt:2464` | Port the registration |
| P1-46 | `combat:setArea` `EVENT_ID_LOADING` guard unmodelled; `reInitState` `clearAreaObjects` needs the combat-area registry | `WorldLuaOperations.kt:3599`, `LuaScriptInterface.kt:110` | Port the area registry |
| P1-47 | `ScriptingDispatch` permissive global walk when a creature's attachment source is absent (think/death/modalwindow) | `ScriptingDispatch.kt:90,104,194` | Attach the per-creature event source at placement |

### 3.6 Player/creature model

| # | Gap | Evidence | Next step |
| --- | --- | --- | --- |
| P1-48 | `setAttackedCreature`: familiar-in-protection-zone rejection (needs tile + monster-type state) | `Creature.kt:1552` | Port the rejection |
| P1-49 | `setFollowCreature`: walk-queue reset branch belongs to the unported walk state | `Creature.kt:1593` | Port the reset |
| P1-50 | Loyalty bonus: `getLoyaltySkill`/`getLoyaltyMagicLevel` base-only (`manaSpent` progression, account loyalty) | `Creature.kt:4371,4556` | Port the loyalty accumulator |
| P1-51 | `removeItemOfType` inventory scan not ported (seam injected) | `Creature.kt:5580` | Port the scan |
| P1-52 | `onThinkEventsHost` call-site binding for every creature entering the world | `Creature.kt:1877` | Verify the binding now exists (wave 77+ `bindCreatureEventHosts`); refresh the marker if so |
| P1-53 | `Creature::onDeath` payload-less default (corpse/last-hit computation) | `GameHandler.kt:694`, `CanaryServer.kt:532` | Port the damage bookkeeping feeding the payload |
| P1-54 | Base `Creature::onCreatureSay` hook not ported | `Game.kt:4026` | Port the hook |
| P1-55 | Non-monster `Creature::onAttacking` call folded into the condition tick | `Game.kt:3074` | Split the two calls |
| P1-56 | `Player::getCorpse` reduced (player death path) | `Creature.kt:4072` | Port the corpse creation |
| P1-57 | `Player::setGuild` `ExpertPvp` broadcast unported; level-1 rank resolved through the injected `Guild` | `Creature.kt:2731` | Port the broadcast |
| P1-58 | `resetIdleTime` idle-logout clock not modelled | `Creature.kt:5678` | Model the clock |
| P1-59 | `PlayerSendState` bank-balance seam zero (`getBankBalance` only on the uncomposed `PlayerVitalsState`) | `PlayerSendState.kt:81` | Compose the vitals state |
| P1-60 | `PlayerFeatures.isRandomMounted()`/`sendOutfitWindow()` note is stale after wave 90 — refresh before the next sweep | `PlayerFeatures.kt:857` | Marker refresh |
| P1-61 | `setEditHouse` facade advances `windowTextId` but the player members it writes are missing | `Game.kt:6416` | Port the members |

### 3.7 Map, items, houses, containers

| # | Gap | Evidence | Next step |
| --- | --- | --- | --- |
| P1-62 | Item **custom attributes** store missing (blocks podium P1-4 and 2 markers) | census (`game/*`, `items/*`) | Port the attribute map |
| P1-63 | `MapCache`: `Item::addUniqueId` game registry not modelled | `MapCache.kt:725` | Port the registry |
| P1-64 | `Creature::getPlayer` not overridden by the port's `Player` (house kick paths) | `House.kt:448,614` | Override `getPlayer` |
| P1-65 | `Container` client-update layer (`Spectators()`/`onAddContainerItem`) no-ops | `Container.kt:1007` | Wire the spectator services |
| P1-66 | `Thing::getDepotChest` not modelled | `Thing.kt:14` | Port the accessor |
| P1-67 | `Item:weight` container contents not added | census (`items/Item.kt`) | Port the recursive weight |
| P1-68 | `Game::removeMoney` full coin-collection body over Cylinders unported (seam injected) | `Game.kt:1493` | Port the body |
| P1-69 | Cyclinder modelling: `Player` is not a `Cylinder` (loot-pouch parent P1-10, notification parents, `internalMoveItem` overloads) | census + wave-90 note | Structural decision item — port the `Cylinder` interface |

### 3.8 Game engine tails

| # | Gap | Evidence | Next step |
| --- | --- | --- | --- |
| P1-70 | `GLOBALEVENT_PERIODCHANGE` (global-event engine) unported | `Game.kt:3388` | Port the event engine |
| P1-71 | `Game::getNpcByID` registry: spawn pipeline must register every placed NPC through `addNpc`; ids fall back to a scan | `Game.kt:10997` | Register at placement, drop the fallback |
| P1-72 | `g_npcs()` production registry from datapack Lua (`Npcs::load(false, true)`) uncomposed | `CanaryServer.kt:177` | Compose the loader |
| P1-73 | `ChatClient` stays an exported no-op until a production session-sink client exists | `CanaryServer.kt:298` | Wire the chat client |

---

## 4. P2 — fidelity edge cases and structural notes

| # | Gap | Evidence | Next step |
| --- | --- | --- | --- |
| P2-1 | `DBTransaction` wrapper not modelled (`Database` owns transactions) | `Game.kt:15164` | Accept with rationale (documented) |
| P2-2 | Highscores per-row loyalty title stays empty (matches the C++ `// todo` branch) | `Game.kt:6988` | No action — upstream-inert |
| P2-3 | `setGuild` uses the injected `Guild` for the level-1 rank instead of the data-loaded rank table | `Creature.kt:2731` | Port the rank table |
| P2-4 | Condition-based heal block / `POFF` magic effect order (3 markers in `combat/condition`) | census | Port or accept |
| P2-5 | `LuaCombatWorld` callback-precedence reduction and `doAreaCombat` inert without a map | `LuaCombatWorld.kt:331,844` | Port with the map seam (overlaps P1-24) |
| P2-6 | `Thing`/`Tile` accessor shape differences (13 notes in `map/Tile.kt`) | census | Maintenance review |
| P2-7 | `spectator/notification parent` modelling (`null` player parent in item moves) | wave-90 `moveItemToPlayer` note | Follows P1-69 |
| P2-8 | `Player::getPlayerVocationEnum` raw-id fallback without a vocations registry | census | Covered when the registry is composed |
| P2-9 | ~390 remaining "documents-only" boundary notes across `game/`, `lua/`, `creatures/`, `items/` (confinement, reduced-model, no-invented-state rationale) | census | Periodic review in the P1-12 sweep |

---

## 5. Next steps (non-parity workstreams)

| # | Workstream | Detail | Owner |
| --- | --- | --- | --- |
| N-1 | **Live validation** (P0-2) | Docker stack on MariaDB default, loadout, OTClient checklist, crash-report capture | user + agent |
| N-2 | **Performance** (P0-4) | monster think profile, think batching, boot burst | agent |
| N-3 | **World content** (P0-3) | 276 placement failures, `Beholder` type | agent |
| N-4 | **Marker burn-down cadence** | P1-12 protocol sweep, then the P1 tables above in the listed order | agent, per wave |
| N-5 | **Marker hygiene** | refresh stale markers (P1-60 and any closed elsewhere); keep the census commands in §0 authoritative | agent, every wave |
| N-6 | **Docs gates** | wave docs in `docs/development/`, CHANGELOG phase per wave, README metrics, implementation-status links | agent, every wave |

Suggested execution order: P0-1 → P0-2 (after the user restarts) → P1-1…P1-12
(protocol sweep, highest user visibility) → P1-62/P1-69 (custom attributes +
Cylinder, unlock P1-4/P1-10/others) → remaining P1 → P2 reviews.

---

## Appendix A — marker census by module (commit `b697c919`)

| Module | Markers | Actionable |
| --- | ---: | ---: |
| `game/Game.kt` | 118 | 17 |
| `network/protocol/**` | 94 | 13 |
| `lua/bindings/**` | 74 | 15 |
| `creatures/Creature.kt` | 48 | 11 |
| `lua/runtime/**` | 36 | 8 |
| `creatures/monsters/**` | 20 | 4 |
| `creatures/players/**` | 16 | 1 |
| `map/Tile.kt` | 13 | 0 |
| `combat/Combat.kt` | 11 | 1 |
| `items/containers/**` | 11 | 1 |
| `combat/CreatureCombatTarget.kt` | 9 | 6 |
| `spells/SpellEngine.kt` | 9 | 2 |
| `creatures/spawns/**` | 9 | 0 |
| `creatures/grouping/**` | 8 | 6 |
| `creatures/Npc.kt` | 8 | 1 |
| `io/iologindata/**` | 7 | 0 |
| `bootstrap/ServerBootstrap.kt` | 6 | 0 |
| `map/GameMap.kt` | 6 | 0 |
| `items/Item.kt` | 6 | 0 |
| `creatures/PlayerFeatures.kt` | 6 | 2 |
| `chat/ChatManager.kt` | 5 | 0 |
| `network/CanaryServer.kt` | 5 | 3 |
| `map/house/**` | 5 | 2 |
| `combat/condition/**` | 4 | 1 |
| `systems/wheel/**` | 4 | 0 |
| `spells/PlayerSpellCaster.kt` | 3 | 1 |
| `map/cache/**` | 2 | 1 |
| `spells/SpellRegistry.kt` | 2 | 1 |
| `creatures/vocations/**` | 2 | 0 |
| `systems/mount/**` | 2 | 0 |
| other (1 each) | 24 | 3 |
| **Total** | **574** | **98** |

The 476 non-actionable markers are the documented boundaries (confinement
notes, "recorded, not invented" gaps, reduced-model rationale). They are
intentional and reviewed per the P2-9 cadence; regenerate the full list with
the §0 commands.

## Appendix B — actionable marker index (verbatim, commit `b697c919`)

```
combat/CreatureCombatTarget.kt:291   getCharmChanceModifier not modelled
combat/CreatureCombatTarget.kt:294   setCharmChanceModifier not modelled
combat/CreatureCombatTarget.kt:297   sendStats not wired through the view
combat/CreatureCombatTarget.kt:300   sendSkills not wired through the view
combat/CreatureCombatTarget.kt:303   sendMonkData not wired through the view
combat/CreatureCombatTarget.kt:316   setImmuneFear (soul-war engine unported)
combat/condition/ConditionDamage.kt:283  canDoCombat + POFF reduced to attackable
combat/Combat.kt:311                 lowBlow/savageBlow, fatal tier roll, expert-pvp reduced
network/protocol/ContainerBrowsePacketHandler.kt:24  0xCA parseExivaRestrictions unported
network/protocol/GamePacketRouter.kt:480             party opcodes stay unapplied
network/protocol/GamePacketRouter.kt:838             TeamFinder online-member resolution inert
network/protocol/GamePacketRouter.kt:867             playerSetTyping broadcast unported
network/protocol/GamePacketRouter.kt:989             playerSetMonsterPodium (podium engine)
network/protocol/GamePacketRouter.kt:1203            cancel-attack/follow tail
network/protocol/TransactionDetailsPacketHandler.kt:10  transaction-details sender
network/protocol/TeamFinderPacketHandler.kt:113      finder window/list senders
network/protocol/GameHandler.kt:311                  loot-pouch parent item (Cylinder)
network/protocol/GameHandler.kt:694                  payload-less Creature.onDeath
network/protocol/QuickLootPacketHandler.kt:620       processLootItems fallback
network/protocol/market/MarketOfferApplication.kt:403  lockerItems map + maxItems guard
network/protocol/market/MarketOfferApplication.kt:454  pagination + rollback
network/CanaryServer.kt:177                          g_npcs registry composition
network/CanaryServer.kt:298                          ChatClient no-op
network/CanaryServer.kt:532                          onDeath inert defaults
game/Game.kt:1493                                    removeMoney coin collection
game/Game.kt:3074                                    non-monster onAttacking
game/Game.kt:3388                                    GLOBALEVENT_PERIODCHANGE
game/Game.kt:4026                                    base onCreatureSay hook
game/Game.kt:6416                                    setEditHouse missing members
game/Game.kt:6988                                    loyalty title (upstream todo)
game/Game.kt:7284                                    party canOpenCorpse branch
game/Game.kt:9915                                    market metrics/save manager globals
game/Game.kt:10997                                   getNpcByID registry fallback
game/Game.kt:12168                                   custom-map spawn loops
game/Game.kt:12278                                   custom-map spawn loops
game/Game.kt:13025                                   defensive charm + updateInputAnalyzer
game/Game.kt:13212                                   sendMessages charm notes
game/Game.kt:13329                                   notifySpectators cancel
game/Game.kt:13370                                   CHARM_VAMP
game/Game.kt:13399                                   CHARM_VOID
game/Game.kt:15164                                   DBTransaction wrapper
lua/runtime/WorldLuaOperations.kt:1176               removeExperience level-down tail
lua/runtime/WorldLuaOperations.kt:1649               getItemDescription seams
lua/runtime/WorldLuaOperations.kt:2464               forge monster classification
lua/runtime/WorldLuaOperations.kt:3599               combat:setArea loading guard
lua/runtime/LuaScriptInterface.kt:110                reInitState clearAreaObjects
lua/runtime/ScriptingDispatch.kt:90                  permissive think walk
lua/runtime/ScriptingDispatch.kt:104                 permissive death walk
lua/runtime/ScriptingDispatch.kt:194                 permissive modalwindow walk
lua/bindings/core/PlayerFunctions.kt:2607            clearSpellCooldowns inert
lua/bindings/core/PlayerFunctions.kt:2754            resetOldCharms inert
lua/bindings/core/PlayerFunctions.kt:2892            faction field
lua/bindings/core/PlayerFunctions.kt:2911            addMapMark sink
lua/bindings/core/PlayerFunctions.kt:2915            setGhostMode
lua/bindings/core/PlayerFunctions.kt:2921            map shader state
lua/bindings/core/PlayerFunctions.kt:2927            exercise-training state
lua/bindings/core/PlayerFunctions.kt:3000            openStash sink
lua/bindings/core/PlayerFunctions.kt:3028            sendContainer sink
lua/bindings/core/PlayerFunctions.kt:3031            sendUpdateContainer sink
lua/bindings/NpcFunctions.kt:525                     Npc:move no-op
lua/bindings/NpcFunctions.kt:546                     npc sell grant/feedback
lua/bindings/LuaCombatWorld.kt:153                   sendMagicEffect unwired
lua/bindings/LuaCombatWorld.kt:331                   formula callback precedence
lua/bindings/LuaCombatWorld.kt:844                   doAreaCombat inert without map
map/cache/MapCache.kt:725                            addUniqueId registry
map/house/House.kt:448                               Creature::getPlayer override
map/house/House.kt:614                               Creature::getPlayer override
spells/SpellRegistry.kt:16                           wheel/proficiency/augment terms
spells/PlayerSpellCaster.kt:54                       lastAggressiveAction inert
spells/SpellEngine.kt:27                             wheel/proficiency/augment cooldowns
spells/SpellEngine.kt:418                            wheel/augment reductions
items/Thing.kt:14                                    getDepotChest
items/containers/Container.kt:1007                   client-update layer no-ops
creatures/players/PlayerSendState.kt:81              bank-balance seam
creatures/grouping/Party.kt:113                      disband vetoes
creatures/grouping/Party.kt:156                      join vetoes
creatures/grouping/Party.kt:233                      forceRemove veto override
creatures/grouping/Party.kt:274                      shareExperience callback
creatures/grouping/Party.kt:310                      updateAllPartyIcons
creatures/grouping/Party.kt:354                      updateAllPartyIcons
creatures/PlayerFeatures.kt:365                      achievement sender
creatures/PlayerFeatures.kt:857                      stale randomMounted/sendOutfitWindow note
creatures/Creature.kt:1552                           familiar-in-PZ rejection
creatures/Creature.kt:1593                           follow walk-queue reset
creatures/Creature.kt:1877                           onThinkEventsHost binding
creatures/Creature.kt:2731                           setGuild ExpertPvp broadcast
creatures/Creature.kt:4072                           player corpse reduced
creatures/Creature.kt:4371                           getLoyaltyMagicLevel
creatures/Creature.kt:4526                           wheel capacity stat
creatures/Creature.kt:4534                           skill caps/wheel/proficiency
creatures/Creature.kt:4556                           getLoyaltySkill
creatures/Creature.kt:5580                           removeItemOfType scan
creatures/Creature.kt:5678                           resetIdleTime clock
creatures/Npc.kt:598                                 gold-sale proceeds messages
creatures/monsters/MonsterPipelinesHost.kt:691       legacy raid bridge
creatures/monsters/MonsterLoot.kt:84                 loot scaling/gut charm/callbacks
creatures/monsters/MonsterBehavior.kt:724            dropCorpse Lua events/auto-loot
creatures/monsters/MonsterType.kt:264                loadLoot/canSpawn/multipliers
```
