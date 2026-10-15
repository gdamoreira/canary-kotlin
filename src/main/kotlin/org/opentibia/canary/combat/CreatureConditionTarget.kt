package org.opentibia.canary.combat

import org.opentibia.canary.combat.condition.Buff
import org.opentibia.canary.combat.condition.CombatType
import org.opentibia.canary.combat.condition.ConditionPlayer
import org.opentibia.canary.combat.condition.ConditionTarget
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.combat.condition.ZoneType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.kv.ValueWrapper
import org.opentibia.canary.map.Position

/**
 * Injected view of the `g_game()`/`Player` state the production
 * [CreatureConditionTarget] adapter reads.
 *
 * The C++ conditions reach the game layer through the `g_game()` singleton
 * (`getPlayerByGUID`/`getCreatureByID`, `changeSpeed`,
 * `internalCreatureChangeVisible`/`internalCreatureChangeOutfit`,
 * `changeLight`); per AGENTS.md 1.1 those calls are replaced by this injected
 * seam. Every member cites the C++ symbol it replaces. The defaults keep an
 * embedded host without a world inert.
 */
interface ConditionTargetState {
    /** `Creature::isAttackable()` (`game.cpp:8387`). */
    fun isAttackable(creature: Creature): Boolean = true

    /**
     * `g_game().getPlayerByGUID(owner)` / `g_game().getCreatureByID(owner)`
     * owner resolution (`condition.cpp:2000-2004`): the player catalog first,
     * the creature id catalog second.
     */
    fun getCreatureFromOwner(owner: Int): Creature? = null

    /**
     * `g_game().changeSpeed(creature, delta)` (`condition.cpp:2553`,
     * `game.cpp:7747-7757`): the variance update core; the same-floor spectator
     * fanout stays with the world host.
     */
    fun changeSpeed(creature: Creature, delta: Int) {
        val varSpeed = creature.getSpeed() - creature.getBaseSpeed() + delta
        creature.setSpeed(varSpeed)
    }

    /** `g_game().internalCreatureChangeVisible(creature, visible)` (`condition.cpp:2649`). */
    fun internalCreatureChangeVisible(creature: Creature, visible: Boolean) {}

    /** `g_game().internalCreatureChangeOutfit(creature, outfit)` (`condition.cpp:2712`). */
    fun internalCreatureChangeOutfit(creature: Creature, outfit: Outfit) {}

    /** `g_game().changeLight(creature)` (`condition.cpp:2769`). */
    fun changeLight(creature: Creature) {}

    companion object {
        /** The inert state used when a host injects no game seams. */
        val Inert: ConditionTargetState = object : ConditionTargetState {}
    }
}

/**
 * Production [ConditionTarget] adapter over a live game [Creature].
 *
 * In C++ the condition engine receives the raw `std::shared_ptr<Creature>`; the
 * port replaces it with the injected [ConditionTarget] seam (AGENTS.md 1.1), so
 * the world installs this adapter as `Creature::conditionTarget`
 * (`Game::addPlayer` for session players, `Game::internalPlaceCreature` for
 * grid creatures). Every member transcribes the exact `Creature`/`Player`
 * symbol it replaces; the game-owned calls come from the injected [state].
 *
 * The adapter is deliberately per-call and stateless: it holds no deferred
 * references, so a creature removed or replaced between calls cannot leak
 * state (AGENTS.md deferred-callback lifetime safety).
 */
class CreatureConditionTarget(
    /** The adapted creature. */
    val creature: Creature,
    private val state: ConditionTargetState = ConditionTargetState.Inert,
) : ConditionTarget {

    /** `Creature::getPlayer()`; null for monsters and NPCs. */
    override val player: ConditionPlayer? get() = (creature as? Player)?.let { PlayerConditionTarget(it) }

    /** `Creature::getPosition()`. */
    override fun getPosition(): Position = creature.position

    /** `Creature::getHealth()`. */
    override fun getHealth(): Int = creature.health

    /** `Creature::changeHealth(positive)` (`condition.cpp:1507-1510`). */
    override fun addHealth(amount: Int): Int {
        creature.addHealth(amount)
        return creature.health
    }

    /**
     * `Game::combatChangeHealth(attacker, creature, damage)` negative path
     * (`condition.cpp:2008`) reduced to its `changeHealth(-amount, false)` core.
     */
    override fun drainHealth(amount: Int): Int {
        creature.drainHealth(amount)
        return creature.health
    }

    /** `condition.cpp:2000-2004` owner resolution over the injected [state]. */
    override fun getCreatureFromOwner(owner: Int): ConditionTarget? =
        state.getCreatureFromOwner(owner)?.let { CreatureConditionTarget(it, state) }

    /**
     * `Game::combatChangeHealth(attacker, creature, damage)` negative path with
     * the resolved attacker (`condition.cpp:2030`): like [drainHealth], plus the
     * attacker `onAttackedCreatureDrainHealth` credit
     * (`creature.cpp:893-899`) in the target's damage map. The C++ attacker is
     * the raw `std::shared_ptr<Creature>`; the [ConditionTarget] view is
     * unwrapped through this adapter (the production owner resolution always
     * produces it), so a seam-only attacker without a live creature records
     * nothing.
     */
    override fun drainHealth(attacker: ConditionTarget?, amount: Int): Int {
        creature.drainHealth((attacker as? CreatureConditionTarget)?.creature, amount)
        return creature.health
    }

    /** `Creature::getMaxHealth()`. */
    override fun getMaxHealth(): Int = creature.maxHealth

    /** `Creature::getMana()`. */
    override fun getMana(): Int = creature.mana

    /** `Creature::changeMana(amount)` (`condition.cpp:1509`, `1543`). */
    override fun addMana(amount: Int): Int {
        creature.changeMana(amount)
        return creature.mana
    }

    /** `Creature::getMaxMana()`. */
    override fun getMaxMana(): Int = creature.maxMana

    /** `Player::changeSoul(amount)` (`condition.cpp:1729`). */
    override fun changeSoul(amount: Int) {
        (creature as? Player)?.features?.vitals?.changeSoul(amount)
    }

    /** `Creature::getBaseSpeed()`. */
    override fun getBaseSpeed(): Int = creature.getBaseSpeed()

    /** `g_game().changeSpeed(creature, delta)` (`condition.cpp:2553`, `2562`, `2612`). */
    override fun setSpeed(delta: Int) {
        state.changeSpeed(creature, delta)
    }

    /**
     * `Creature::getZoneType()` (`condition.cpp:1501`, `1506`, `1537`, `1540`,
     * `1726`). The condition engine's `ZoneType_t` transcription only
     * distinguishes `ZONE_PROTECTION` (`condition.cpp:1492-1540`); the domain
     * zone kinds map onto it (PARITY GAP: the condition-package enum is a stale
     * transcription without `ZONE_NOLOGOUT`).
     */
    override fun getZoneType(): ZoneType = toConditionZoneType(creature.getZoneType())

    /**
     * `Player::kv()->scoped("daily-reward")->get(key)`
     * (`condition.cpp:1496-1499`); null when unset or for a non-player.
     */
    override fun getStorageValue(key: String): Long? =
        (creature as? Player)?.kv?.scoped("daily-reward")?.get(key)?.getNumber()?.toLong()

    /** `Player::kv()->scoped("daily-reward")->set(key, value)`. */
    override fun setStorageValue(key: String, value: Long) {
        (creature as? Player)?.kv?.scoped("daily-reward")?.set(key, ValueWrapper(value.toInt()))
    }

    /** `Creature::isSuppress(type, attackerIsPlayer)` (`condition.cpp:2002`). */
    override fun isSuppress(type: ConditionType, attackerIsPlayer: Boolean): Boolean =
        creature.isSuppress(type, attackerIsPlayer)

    /** `Creature::isAttackable()` (`condition.cpp:2015`). */
    override fun isAttackable(): Boolean = state.isAttackable(creature)

    /** `Creature::getBuff(index)` (`condition.cpp:974`). */
    override fun getBuff(buff: Buff): Int = creature.getBuff(buff.value)

    /** `Creature::setBuff(index, value)` (`condition.cpp:984`, `1026`). */
    override fun setBuff(buff: Buff, value: Int) {
        creature.setBuff(buff, value)
    }

    /** `Creature::getAbsorbPercent(combat)` (`condition.cpp:929`). */
    override fun getAbsorbPercent(combat: CombatType): Int =
        creature.getAbsorbPercent(toCreatureCombatType(combat))

    /** `Creature::setAbsorbPercent(combat, value)` (`condition.cpp:940`, `1032`). */
    override fun setAbsorbPercent(combat: CombatType, value: Int) {
        creature.setAbsorbPercent(toCreatureCombatType(combat), value)
    }

    /** `Creature::getIncreasePercent(combat)` (`condition.cpp:950`). */
    override fun getIncreasePercent(combat: CombatType): Int =
        creature.getIncreasePercent(toCreatureCombatType(combat))

    /** `Creature::setIncreasePercent(combat, value)` (`condition.cpp:960`, `1036`). */
    override fun setIncreasePercent(combat: CombatType, value: Int) {
        creature.setIncreasePercent(toCreatureCombatType(combat), value)
    }

    /** `Creature::getCharmChanceModifier()` (`condition.cpp:965`). */
    override fun getCharmChanceModifier(): Int = creature.getCharmChanceModifier()

    /** `Creature::setCharmChanceModifier(value)` (`condition.cpp:965`). */
    override fun setCharmChanceModifier(value: Int) {
        creature.setCharmChanceModifier(value)
    }

    /** `Creature::setUseDefense(value)` (`condition.cpp:835`, `1045`). */
    override fun setUseDefense(value: Boolean) {
        creature.setUseDefense(value)
    }

    /** `Creature::setManaShield(value)` (`condition.cpp:1612`, `1623`, `1637`). */
    override fun setManaShield(value: Int) {
        creature.setManaShield(value)
    }

    /** `Creature::setMaxManaShield(value)` (`condition.cpp:1613`, `1624`, `1638`). */
    override fun setMaxManaShield(value: Int) {
        creature.setMaxManaShield(value)
    }

    /** `g_game().internalCreatureChangeVisible(creature, visible)` (`condition.cpp:2649`). */
    override fun internalCreatureChangeVisible(visible: Boolean) {
        state.internalCreatureChangeVisible(creature, visible)
    }

    /** `Creature::isInvisible()`. */
    override fun isInvisible(): Boolean = creature.isInvisible()

    /** `g_game().internalCreatureChangeOutfit(creature, outfit)` (`condition.cpp:2712`). */
    override fun internalCreatureChangeOutfit(outfit: Outfit) {
        state.internalCreatureChangeOutfit(creature, outfit)
    }

    /** `Creature::getDefaultOutfit()`. */
    override fun getDefaultOutfit(): Outfit = creature.getDefaultOutfit()

    /** `Creature::getCreatureLight()`. */
    override fun getCreatureLight(): LightInfo = creature.getCreatureLight()

    /** `Creature::setCreatureLight(lightInfo)`. */
    override fun setCreatureLight(lightInfo: LightInfo) {
        creature.setCreatureLight(lightInfo)
    }

    /** `Creature::setNormalCreatureLight()`. */
    override fun setNormalCreatureLight() {
        creature.setNormalCreatureLight()
    }

    /** `g_game().changeLight(creature)` (`condition.cpp:2769`). */
    override fun changeLight() {
        state.changeLight(creature)
    }

    /**
     * Bridges the condition engine's `CombatType_t` transcription
     * (`combat.condition.CombatType`) onto the creature/combat one
     * (`combat.CombatType`); both enumerate `COMBAT_*` at the same values
     * (`creatures_definitions.hpp:833-853`). The `COMBAT_NONE` sentinel maps to
     * the domain `COMBAT_NONE`, which the out-of-range array guards absorb.
     */
    private fun toCreatureCombatType(combat: CombatType): org.opentibia.canary.combat.CombatType =
        org.opentibia.canary.combat.CombatType.entries.firstOrNull { it.value == combat.value }
            ?: org.opentibia.canary.combat.CombatType.NONE

    /** Bridges `game.zones.ZoneType` (`ZoneType_t`, items_definitions.hpp:479-485) onto the condition transcription. */
    private fun toConditionZoneType(zone: org.opentibia.canary.game.zones.ZoneType): ZoneType = when (zone) {
        org.opentibia.canary.game.zones.ZoneType.ZONE_PROTECTION -> ZoneType.ZONE_PROTECTION
        org.opentibia.canary.game.zones.ZoneType.ZONE_NOPVP -> ZoneType.ZONE_NO_PVP
        org.opentibia.canary.game.zones.ZoneType.ZONE_PVP -> ZoneType.ZONE_PVP
        org.opentibia.canary.game.zones.ZoneType.ZONE_NOLOGOUT -> ZoneType.ZONE_NORMAL
        org.opentibia.canary.game.zones.ZoneType.ZONE_NORMAL -> ZoneType.ZONE_NORMAL
    }
}
