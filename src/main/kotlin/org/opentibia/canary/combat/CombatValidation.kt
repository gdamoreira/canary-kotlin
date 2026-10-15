package org.opentibia.canary.combat

import org.opentibia.canary.combat.condition.ConditionPlayer
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.monsters.Faction
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.map.Position

/**
 * Injected views of the `Creature`/`Player`/`Monster`/`Tile`/`GameMap` state
 * read by the target-validation and combat-area functions of `Combat`
 * (src/creatures/combat/combat.cpp:233-572, 2289-2325).
 *
 * The C++ code reaches through `g_game()`/`g_configManager()` and raw pointers;
 * per AGENTS.md 1.1 those are replaced by constructor/parameter injected seams.
 * Every member transcribes the exact C++ symbol it replaces so parity stays
 * auditable, mirroring the existing [CombatTarget] pattern.
 */

/** Injected view of `Monster` used by the faction checks and combat events. */
interface CombatMonster {
    /** `Monster::isEnemyFaction(faction)`. */
    fun isEnemyFaction(faction: Faction): Boolean

    /**
     * `Monster::onAttackedByPlayer(attackerPlayer)` (monster.cpp:600-625), fired
     * by `Combat::CombatHealthFunc` when a player damages the monster
     * (combat.cpp:895-899). No-op for monsters without a registered event.
     */
    fun onAttackedByPlayer(attackerPlayer: ConditionPlayer) {}
}

/**
 * Injected view of `Tile` used by `Combat::canDoCombat` and
 * `AreaCombat::getList`.
 */
interface CombatTile {
    /** `Tile::hasFlag(flag)`; the `TILESTATE_*` checks of combat.cpp:398-460, 2311. */
    fun hasFlag(flag: TileFlags): Boolean

    /**
     * `Tile::hasProperty(prop)` (tile.hpp:171); `CONST_PROP_BLOCKPROJECTILE`
     * (`combat.cpp:381`, `457`).
     */
    fun hasProperty(property: Int): Boolean

    /** `Tile::getPosition()` (`combat.cpp:408`). */
    fun getPosition(): Position

    /** `Tile::getTeleportItem()` (`combat.cpp:402`). */
    fun getTeleportItem(): Boolean

    /** The `Tile::getItemList()` item ids (`combat.cpp:384-391`, magic wall check). */
    fun getItemIds(): List<Int>
}

/**
 * Injected view of the map lookups `Combat::getCombatArea` and
 * `AreaCombat::getList` perform through `g_game().map` / `g_game()`.
 */
interface CombatMap {
    /** `GameMap::getTile(pos)` (`combat.cpp:2310`). */
    fun getTile(position: Position): CombatTile?

    /** `GameMap::getOrCreateTile(pos)` (`combat.cpp:241`, `2317`). */
    fun getOrCreateTile(position: Position): CombatTile

    /** `Game::isSightClear(from, to, true)` (`combat.cpp:2316`). */
    fun isSightClear(fromPos: Position, toPos: Position, checkFloor: Boolean): Boolean
}

/**
 * Injected view of a `Creature` attacker/target for `Combat::canTargetCreature`,
 * `Combat::canDoCombat`, `Combat::isPlayerCombat` and `Combat::isInPvpZone`.
 */
interface CombatActor {
    /** `Creature::getPlayer()` (combat.cpp:310, 454, 470, ...). */
    fun getPlayer(): CombatPlayer?

    /** `Creature::getMonster()` (combat.cpp:469, 472, ...). */
    fun getMonster(): CombatMonster?

    /** `Creature::getNpc()` non-null check (combat.cpp:547). */
    fun getNpc(): Boolean

    /** `Creature::getMaster()` (combat.cpp:467, 473). */
    fun getMaster(): CombatActor?

    /** `Creature::isSummon()` (combat.cpp:314, 526-527, 537, 559). */
    fun isSummon(): Boolean

    /** `Creature::isAttackable()` (combat.cpp:348). */
    fun isAttackable(): Boolean

    /** `Creature::getZoneType()` (combat.cpp:328-344, 429, 537). */
    fun getZoneType(): ZoneType

    /** `Creature::getFaction()` (combat.cpp:498, 520, 528, 541; `monster.cpp:260-265`). */
    fun getFaction(): Faction

    /** `Creature::getPosition()` (combat.cpp:407). */
    fun getPosition(): Position

    /** `Creature::getTile()` (combat.cpp:456, 480, 491). */
    fun getTile(): CombatTile?
}

/**
 * Injected view of a `Player` (`Creature::getPlayer()`), carrying the exact
 * accessors the target-validation functions call.
 */
interface CombatPlayer : CombatActor {
    /** `Player::hasFlag(flag)` (combat.cpp:326, 348, 476, 482, 505, 533). */
    fun hasFlag(flag: PlayerFlags_t): Boolean

    /** `Player::getLevel()` (combat.cpp:434). */
    fun getLevel(): Int

    /** `Player::getSkull()` (combat.cpp:442). */
    fun getSkull(): Skulls_t

    /** `Player::getSkullClient(creature)` (combat.cpp:368, 442). */
    fun getSkullClient(creature: CombatActor): Skulls_t

    /** `Player::hasSecureMode()` (combat.cpp:368). */
    fun hasSecureMode(): Boolean

    /** `Player::hasPermittedConditionInPZ()` (combat.cpp:461). */
    fun hasPermittedConditionInPZ(): Boolean

    /** `Player::getVocation()->canCombat()` (combat.cpp:438). */
    fun getVocationCanCombat(): Boolean

    /** `Player::getVocationId()` (combat.cpp:438). */
    fun getVocationId(): Int
}
