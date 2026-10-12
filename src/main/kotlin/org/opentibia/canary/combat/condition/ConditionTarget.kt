package org.opentibia.canary.combat.condition

/**
 * Injected view of the affected [Creature] used by the condition engine.
 *
 * The C++ conditions call `g_game()`/`g_configManager()` singletons and reach
 * into `Creature`/`Player`. Per AGENTS.md 1.1 those globals are replaced by
 * constructor injected state: every member below transcribes the exact
 * `Creature`/`Player` method the ported conditions call, with the owning C++
 * symbol cited so parity is auditable. Methods are named after the C++ symbol
 * they replace wherever Kotlin syntax allows.
 */
interface ConditionTarget {
    /**
     * `Creature::getPlayer()` — null when the target is a monster/NPC. Used by
     * regeneration, attributes, speed and the daily-reward zone gate.
     */
    val player: ConditionPlayer?

    /** `Creature::getHealth()` (`condition.cpp:1505`, `1511`). */
    fun getHealth(): Int

    /**
     * `Creature::changeHealth(positive)` (`condition.cpp:1507-1510`).
     * Returns the health after the change.
     */
    fun addHealth(amount: Int): Int

    /**
     * `g_game().combatChangeHealth` negative path
     * (`condition.cpp:2008`, `2030`). Returns the health after the hit.
     */
    fun drainHealth(amount: Int): Int

    /** `Creature::getMaxHealth()` (`condition.cpp:862`). */
    fun getMaxHealth(): Int

    /** `Creature::getMana()` (`condition.cpp`, regeneration). */
    fun getMana(): Int

    /** `Creature::changeMana(amount)` (`condition.cpp:1509`, `1543`). */
    fun addMana(amount: Int): Int

    /** `Creature::getMaxMana()` (`condition.cpp:866`). */
    fun getMaxMana(): Int

    /** `Player::changeSoul(amount)` (`condition.cpp:1729`). */
    fun changeSoul(amount: Int)

    /** `Creature::getBaseSpeed()` (`condition.cpp:2545`, `2601`). */
    fun getBaseSpeed(): Int

    /** `g_game().changeSpeed(creature, delta)` (`condition.cpp:2553`, `2562`, `2612`). */
    fun setSpeed(delta: Int)

    /** `Creature::getZoneType()` (`condition.cpp:1501`, `1506`, `1537`, `1540`, `1726`). */
    fun getZoneType(): ZoneType

    /**
     * `Player::kv()->scoped("daily-reward")->get("streak")`
     * (`condition.cpp:1496-1499`). Returns null when unset.
     */
    fun getStorageValue(key: String): Long?

    /** `Player::kv()->scoped("daily-reward")->set(key, value)` storage write. */
    fun setStorageValue(key: String, value: Long)

    /** `Creature::isSuppress(type, attackerIsPlayer)` (`condition.cpp:2002`). */
    fun isSuppress(type: ConditionType, attackerIsPlayer: Boolean): Boolean

    /** `Creature::isAttackable()` (`condition.cpp:2015`). */
    fun isAttackable(): Boolean

    /** `Creature::getBuff(index)` (`condition.cpp:974`). */
    fun getBuff(buff: Buff): Int

    /** `Creature::setBuff(index, value)` (`condition.cpp:984`, `1026`). */
    fun setBuff(buff: Buff, value: Int)

    /** `Creature::getAbsorbPercent(combat)` (`condition.cpp:929`). */
    fun getAbsorbPercent(combat: CombatType): Int

    /** `Creature::setAbsorbPercent(combat, value)` (`condition.cpp:940`, `1032`). */
    fun setAbsorbPercent(combat: CombatType, value: Int)

    /** `Creature::getIncreasePercent(combat)` (`condition.cpp:950`). */
    fun getIncreasePercent(combat: CombatType): Int

    /** `Creature::setIncreasePercent(combat, value)` (`condition.cpp:960`, `1036`). */
    fun setIncreasePercent(combat: CombatType, value: Int)

    /** `Creature::getCharmChanceModifier()` (`condition.cpp:965`). */
    fun getCharmChanceModifier(): Int

    /** `Creature::setCharmChanceModifier(value)` (`condition.cpp:965`). */
    fun setCharmChanceModifier(value: Int)

    /** `Creature::setUseDefense(value)` (`condition.cpp:835`, `1045`). */
    fun setUseDefense(value: Boolean)

    /** `Creature::setManaShield(value)` (`condition.cpp:1612`, `1623`, `1637`). */
    fun setManaShield(value: Int)

    /** `Creature::setMaxManaShield(value)` (`condition.cpp:1613`, `1624`, `1638`). */
    fun setMaxManaShield(value: Int)
}

/**
 * Injected view of the affected `Player` (`Creature::getPlayer()`), used only
 * by the player-specific branches of the ported conditions.
 */
interface ConditionPlayer {
    /** `Player::getMaxHealth()` (`condition.cpp:862`). */
    fun getMaxHealth(): Int

    /** `Player::getMaxMana()` (`condition.cpp:866`). */
    fun getMaxMana(): Int

    /** `Player::getBaseMagicLevel()` (`condition.cpp:870`). */
    fun getBaseMagicLevel(): Int

    /** `Player::getCapacity()` (`condition.cpp:874`). */
    fun getCapacity(): Double

    /** `Player::getBaseSkill(skill)` (`condition.cpp:903`). */
    fun getBaseSkill(skill: Skill): Int

    /** `Player::setVarSkill(skill, value)` (`condition.cpp:914`, `1004`). */
    fun setVarSkill(skill: Skill, value: Int)

    /** `Player::setVarStats(stat, value)` (`condition.cpp:886`, `1011`). */
    fun setVarStats(stat: Stat, value: Int)

    /** `Player::getCharmChanceModifier()` (`condition.cpp:1015`). */
    fun getCharmChanceModifier(): Int

    /** `Player::setCharmChanceModifier(value)` (`condition.cpp:1015`). */
    fun setCharmChanceModifier(value: Int)

    /** `Player::sendStats()` (`condition.cpp:891`, `1018`, `1432`, ...). */
    fun sendStats()

    /** `Player::sendSkills()` (`condition.cpp:892`, `1019`, ...). */
    fun sendSkills()
}
