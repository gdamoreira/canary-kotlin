package org.opentibia.canary.combat

import org.opentibia.canary.combat.condition.Buff
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionPlayer
import org.opentibia.canary.combat.condition.ConditionType

/**
 * Injected view of the target `Creature` used by the combat dispatch layer.
 *
 * The C++ `Combat::doCombatHealth`/`Combat::doCombatMana` chain calls
 * `g_game().combatBlockHit`/`combatChangeHealth`/`combatChangeMana`, which in
 * turn read `Creature`/`Player` state. Per AGENTS.md 1.1 the global `g_game()`
 * and the live creature are replaced by this constructor-injected seam; every
 * member below transcribes the exact `Creature` symbol it replaces, with the
 * owning C++ file cited so parity is auditable.
 */
interface CombatTarget {
    /** `Creature::getHealth()` (`creature.cpp`). */
    fun getHealth(): Int

    /** `Creature::getMaxHealth()` (`creature.cpp`); the `changeHealth` upper clamp. */
    fun getMaxHealth(): Int

    /**
     * `Creature::gainHealth` (`creature.cpp:886-891`). Returns the health after
     * the gain, clamped to `getMaxHealth()`.
     */
    fun addHealth(amount: Int): Int

    /**
     * `Creature::drainHealth` (`creature.cpp:893-899`). Returns the health
     * after the loss, clamped to 0.
     */
    fun drainHealth(amount: Int): Int

    /** `Creature::getMana()`. */
    fun getMana(): Int

    /** `Creature::getMaxMana()`; the `changeMana` upper clamp. */
    fun getMaxMana(): Int

    /**
     * `Creature::changeMana` (`creature.cpp:869-875`). Positive values restore
     * up to `getMaxMana()`, negative values drain down to 0. Returns the mana
     * after the change.
     */
    fun changeMana(amount: Int): Int

    /** `Creature::isAttackable()` (`game.cpp:8387`). */
    fun isAttackable(): Boolean

    /** `Creature::isImmune(combatType)` (`creature.cpp:955`). */
    fun isImmune(combatType: CombatType): Boolean

    /** `Creature::getDefense()` (`creature.cpp:970`). */
    fun getDefense(): Int

    /** `Creature::getArmor()` (`creature.cpp:978`). */
    fun getArmor(): Int

    /**
     * `hasDefense` local in `Creature::blockHit` (`creature.cpp:963-968`):
     * true while `blockCount > 0`.
     */
    fun hasDefense(): Boolean

    /** `Creature::canUseDefense` (`creature.cpp:969`). */
    fun canUseDefense(): Boolean

    /** `Creature::getMitigation()` (`creature.cpp:910-922`). */
    fun getMitigation(): Double

    /**
     * `Creature::getAbsorbPercent(combat)` (`creature.cpp:1979-1984`), read by
     * `Creature::applyAbsorbDamageModifications` (`creature.cpp:926`).
     *
     * Defaults to 0 so pre-existing implementors keep their behavior.
     */
    fun getAbsorbPercent(combatType: CombatType): Int = 0

    /**
     * `Creature::getAbsorbFlat(combat)` (`creature.cpp:1962-1967`), read by
     * `Creature::applyAbsorbDamageModifications` (`creature.cpp:930`).
     */
    fun getAbsorbFlat(combatType: CombatType): Int = 0

    /**
     * `Creature::getIncreasePercent(combat)` (`creature.cpp:1996-2001`), read
     * from the attacker by `Creature::applyAbsorbDamageModifications`
     * (`creature.cpp:936`).
     */
    fun getIncreasePercent(combatType: CombatType): Int = 0

    /**
     * `Player::getMantra()`, read by `Combat::applyMantraAbsorb`
     * (`combat.cpp:748`). Zero for non-players.
     */
    fun getMantra(): Int = 0

    /**
     * `Creature::getBuff(index)` (`creature.cpp`), read by
     * `Combat::applyMantraAbsorb` for `BUFF_MANTRA` (`combat.cpp:745`).
     */
    fun getBuff(buff: Buff): Int = 0

    /** `Creature::getID()` (`src/creatures/creature.hpp:156`); the owner stamped on conditions. */
    fun getID(): Int

    /**
     * `Creature::getPlayer()` (`src/creatures/creature.hpp:92`); non-null when
     * this creature is a player. `Combat::CombatConditionFunc` uses
     * `caster->getPlayer() != nullptr` as the `attackerPlayer` flag
     * (`combat.cpp:1088`).
     */
    fun getPlayer(): ConditionPlayer?

    /** `Creature::isImmune(ConditionType_t)` (`src/creatures/creature.hpp:522`). */
    fun isImmune(conditionType: ConditionType): Boolean

    /**
     * `Creature::addCombatCondition` (`src/creatures/creature.cpp:1397-1410`).
     * Returns whether the condition was added.
     */
    fun addCombatCondition(condition: Condition, attackerPlayer: Boolean): Boolean
}
