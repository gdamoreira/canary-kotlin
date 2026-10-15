package org.opentibia.canary.combat

import org.opentibia.canary.combat.condition.ConditionPlayer
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.monsters.Faction
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

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

    /**
     * `Tile::getCreatures()` (`combat.cpp:1536`, `1610`): the tile's creature
     * list consumed by `Combat::CombatFunc`. Defaults to empty so area-less
     * seams keep working.
     */
    fun getCreatures(): List<CombatActor> = emptyList()

    /** `Tile::getTopCreature()` (`combat.cpp:1537`, `1612`); empty tiles yield `null`. */
    fun getTopCreature(): CombatActor? = getCreatures().firstOrNull()
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

    /**
     * `Player::isFirstOnStack()` (`combat.cpp:1525`): the no-PvP world gate of
     * `Combat::CombatFunc` refuses a caster that is not the bottom player of its
     * tile. Defaults to true so non-player seams keep the gate inert.
     */
    fun isFirstOnStack(): Boolean = true

    /** `Player::sendCancelMessage(RETURNVALUE_NOTPOSSIBLE)` (`combat.cpp:1527`). */
    fun sendCancelMessage(ret: ReturnValue) {}

    /** `Player::sendMagicEffect(origin, CONST_ME_POFF)` (`combat.cpp:1528`). */
    fun sendMagicEffect(position: Position, effect: Int) {}

    /**
     * `Player::addInFightTicks()` (`combat.cpp:1221`): a PvP damaging field or
     * legacy blocking wall arms the caster's fight ticks.
     */
    fun addInFightTicks() {}
}

/**
 * Injected view of the `g_game()` calls the `Combat::CombatFunc` consumption
 * tail performs (`combat.cpp:1254-1262`, `1277-1310`): the magic/distance
 * effects, the double/single sound fanout and the field-item creation of
 * `Combat::combatTileEffects`.
 *
 * Every method transcribes the exact `g_game()`/`Item::CreateItem` call of the
 * C++ tail; the default is the inert no-op so a partial host keeps the pure
 * target enumeration working.
 */
interface CombatEffects {
    /**
     * `g_game().addMagicEffect(position, effect, caster)` (`combat.cpp:1157`,
     * `1254`). PARITY GAP: `Combat::sendCombatEffect`'s Monk elemental-bond
     * effect swap (`combat.cpp:1143-1152`) stays with the weapons subsystem.
     */
    fun addMagicEffect(caster: CombatActor?, position: Position, effect: Int) {}

    /** `g_game().sendDoubleSoundEffect(position, cast, impact, caster)` (`combat.cpp:1260`, `1291`). */
    fun sendDoubleSoundEffect(position: Position, castEffect: Int, impactEffect: Int, caster: CombatActor?) {}

    /** `g_game().sendSingleSoundEffect(position, effect, caster)` (`combat.cpp:1262`, `1293`). */
    fun sendSingleSoundEffect(position: Position, effect: Int, caster: CombatActor?) {}

    /** `Combat::addDistanceEffect(caster, fromPos, toPos, effect)` (`combat.cpp:1265-1275`, `1277-1310`). */
    fun addDistanceEffect(caster: CombatActor, fromPos: Position, toPos: Position, effect: Int) {}

    /**
     * `Combat::combatTileEffects`'s field branch (`combat.cpp:1230-1240`):
     * `Item::CreateItem(itemId)`, the caster owner stamp, the
     * `g_game().internalAddItem(tile, item)` placement and `startDecaying`.
     */
    fun createFieldItem(caster: CombatActor?, tile: CombatTile, itemId: Int) {}
}
