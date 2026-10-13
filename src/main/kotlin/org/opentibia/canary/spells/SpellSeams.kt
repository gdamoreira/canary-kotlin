package org.opentibia.canary.spells

import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.ZoneType
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/** `WorldType_t` (`src/game/game_definitions.hpp:22-26`). */
enum class WorldType(val value: Int) {
    NO_PVP(1),
    PVP(2),
    PVP_ENFORCED(3),
}

/**
 * Injected view of a creature targeted by the spell pipeline.
 *
 * Transcribes the `Creature` calls `spells.cpp` performs on a target, with the
 * owning C++ symbol cited so parity is auditable.
 */
interface SpellTarget {
    /** `Creature::getID()`. */
    fun getID(): Long

    /** `Creature::getName()`. */
    fun getName(): String

    /** `Creature::getPosition()`. */
    fun getPosition(): Position

    /** `Creature::getHealth()`. */
    fun getHealth(): Int

    /** `Creature::isRemoved()`. */
    fun isRemoved(): Boolean

    /** `Player::isAccessPlayer()` (`spells.cpp:1195`, `1260`). */
    fun isAccessPlayer(): Boolean
}

/**
 * Injected view of the casting player.
 *
 * Canary reaches into `Player`/`Creature` directly; per AGENTS.md 1.1 those
 * lookups are constructor/parameter injected. Every member transcribes the
 * exact C++ method the ported spell pipeline calls.
 */
interface SpellCaster {
    /** `Creature::getID()`. */
    fun getID(): Long

    /** `Creature::getName()`. */
    fun getName(): String

    /** `Creature::getPosition()`. */
    fun getPosition(): Position

    /** `Creature::getDirection()`. */
    fun getDirection(): Direction

    /** `Player::getLevel()` (`spells.cpp:538`). */
    fun getLevel(): Int

    /** `Player::getMagicLevel()` (`spells.cpp:544`). */
    fun getMagicLevel(): Int

    /** `Player::getMana()` (`spells.cpp:550`). */
    fun getMana(): Int

    /** `Player::getMaxMana()` (`spells.cpp:968`). */
    fun getMaxMana(): Int

    /** `Player::getSoul()` (`spells.cpp:556`). */
    fun getSoul(): Int

    /** `Player::getVocationId()` (`spells.cpp:577`). */
    fun getVocationId(): Int

    /** `Player::getWeaponType()` (`spells.cpp:585`). */
    fun getWeaponType(): WeaponType_t

    /** `Player::getZoneType()` (`spells.cpp:523`). */
    fun getZoneType(): ZoneType

    /** `Player::getSkull()` (`spells.cpp:512`). */
    fun getSkull(): Skulls_t

    /** `Player::isPremium()` (`spells.cpp:600`). */
    fun isPremium(): Boolean

    /** `Player::getGroup()->id` (`spells.cpp:577`). */
    fun getGroupId(): Int

    /** `Player::isAccessPlayer()` (`spells.cpp:1195`, `1260`). */
    fun isAccessPlayer(): Boolean

    /** `Player::isFirstOnStack()` (`spells.cpp:606`). */
    fun isFirstOnStack(): Boolean

    /** `Player::hasFlag(flag)` (spells.cpp, passim). */
    fun hasFlag(flag: PlayerFlags_t): Boolean

    /** `Creature::hasCondition(type, subId)` (`spells.cpp:528`, `562-604`). */
    fun hasCondition(type: ConditionType, subId: Int = 0): Boolean

    /** `Player::learnedInstantSpellList` storage (`player.hpp:1696`). */
    fun getSpellBook(): SpellBook?

    /** `Player::getAttackedCreature()` (`spells.cpp:1188`, `1216`). */
    fun getAttackedCreature(): SpellTarget?

    /** `Creature::addCondition(condition)` (`spells.cpp:858`, `869`, `882`). */
    fun addCondition(condition: Condition): Boolean

    /** `Player::sendCancelMessage(ret)` (spells.cpp, passim). */
    fun sendCancelMessage(ret: ReturnValue)

    /** `Player::addManaSpent(manaCost)` (`spells.cpp:937`). */
    fun addManaSpent(manaCost: Int)

    /** `Player::changeMana(delta)` (`spells.cpp:938`). */
    fun changeMana(delta: Int)

    /** `Player::changeSoul(delta)` (`spells.cpp:943`). */
    fun changeSoul(delta: Int)

    /** `Player::addInFightTicks()` (`spells.cpp:921`, `1287`). */
    fun addInFightTicks()

    /** `Player::updateLastAggressiveAction()` (`spells.cpp:922`, `1288`). */
    fun updateLastAggressiveAction()
}

/**
 * Injected world/config view replacing the `g_game()`/`g_configManager()`
 * lookups of `spells.cpp`.
 */
interface SpellWorld {
    /** `g_configManager().getBoolean(LEARN_SPELLS)` (`spells.cpp:562`). */
    val learnSpells: Boolean

    /** `g_configManager().getBoolean(REMOVE_RUNE_CHARGES)` (`spells.cpp:1546`). */
    val removeRuneCharges: Boolean

    /** `g_game().getWorldType()` (`spells.cpp:606`, `1286`, `1552`). */
    val worldType: WorldType

    /** `g_configManager().getFloat(RATE_SPELL_COOLDOWN)` (`spells.cpp:826`). */
    val rateSpellCooldown: Double

    /** `Game::sendDoubleSoundEffect(pos, cast, impact, player)` (`spells.cpp:926`). */
    fun sendDoubleSoundEffect(
        position: Position,
        castEffect: SoundEffect,
        impactEffect: SoundEffect,
        spectator: SpellCaster,
    )

    /** `Tile::hasFlag(TILESTATE_BLOCKSOLID)` (`spells.cpp:647`, `698`). */
    fun isTileBlockingSolid(position: Position): Boolean

    /** `Tile::getBottomVisibleCreature(creature)` (`spells.cpp:641`, `697`). */
    fun getBottomVisibleCreatureId(position: Position): Long?

    /**
     * `Game::canThrowObjectTo(from, to, sightLine, range)` (`spells.cpp:684`,
     * `1306`).
     */
    fun canThrowObjectTo(from: Position, to: Position, checkSightLine: Boolean, range: Int): Boolean

    /** `Game::getPlayerByNameWildcard(name, target)` (`spells.cpp:1193`, `1250`). */
    fun getPlayerByNameWildcard(name: String): SpellTarget?
}

/**
 * Lua bridge seam for `onCastSpell`.
 *
 * Canary `InstantSpell::executeCastSpell`/`RuneSpell::executeCastSpell` push the
 * caster and [LuaVariant] into the spell script; the scripts stay Lua, so the
 * engine hands them to this seam instead (AGENTS.md 1.4).
 */
interface SpellScriptRunner {
    /** `InstantSpell::executeCastSpell` (spells.cpp:1351-1373). */
    fun executeCastSpell(caster: SpellCaster, spell: InstantSpell, variant: LuaVariant): Boolean

    /** `RuneSpell::executeCastSpell` (spells.cpp:1587-1611). */
    fun executeCastSpell(caster: SpellCaster, rune: RuneSpell, variant: LuaVariant, isHotkey: Boolean): Boolean
}
