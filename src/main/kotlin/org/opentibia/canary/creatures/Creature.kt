package org.opentibia.canary.creatures

import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountType
import org.opentibia.canary.account.getTimeNow
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionTarget
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.combat.condition.Stat
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.creatures.grouping.PartyShields_t
import org.opentibia.canary.creatures.players.components.PlayerSex_t
import org.opentibia.canary.social.guild.Guild
import org.opentibia.canary.social.guild.GuildRank
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.creatures.vocations.VocationRequirementsAdapter
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue
import kotlin.math.max

abstract class Creature(
    val id: Long,
    val name: String,
    var maxHealth: Int,
    var health: Int,
    var maxMana: Int = 0,
    var mana: Int = 0,
    speed: Int = 200,
    var position: Position,
    var direction: org.opentibia.canary.game.movement.Direction = org.opentibia.canary.game.movement.Direction.South
) {
    val isDead: Boolean get() = health <= 0

    private var internalRemoved = false

    /** `Creature::setRemoved()` (src/creatures/creature.hpp:152). */
    fun setRemoved() {
        internalRemoved = true
    }

    /** `Creature::isRemoved()` (src/creatures/creature.hpp:210). */
    fun isRemoved(): Boolean = internalRemoved

    fun drainHealth(amount: Int) {
        health = max(0, health - amount)
    }

    fun addHealth(amount: Int) {
        health = kotlin.math.min(maxHealth, health + amount)
    }

    /**
     * `Creature::changeHealth` (`creature.cpp:845-876`). The
     * `g_game().addCreatureHealth` broadcast (`sendHealthChange`) is a world
     * side effect owned by the protocol senders, and Canary queues `onDeath`
     * through the dispatcher (`creature.cpp:862-874`); the port runs confined on
     * the world lane and invokes [onDeath] directly when the lifeless guard lets
     * the transition through.
     */
    open fun changeHealth(healthChange: Int, sendHealthChange: Boolean = true) {
        if (isDead) {
            return
        }

        if (healthChange > 0) {
            health += minOf(healthChange, maxHealth - health)
        } else {
            health = maxOf(0, health + healthChange)
        }

        if (sendHealthChange) {
            // `g_game().addCreatureHealth` protocol broadcast is not ported.
        }

        if (health <= 0 && !isRemoved()) {
            onDeath()
        }
    }

    /** `Creature::gainHealth` (`creature.cpp:886-891`). */
    fun gainHealth(healer: Creature?, healthGain: Int) {
        changeHealth(healthGain)
        healer?.onTargetCreatureGainHealth(this, healthGain)
    }

    /**
     * `Creature::baseSpeed` (creature.hpp:901) seeded from the constructor's
     * `speed`; `Creature::varSpeed` (creature.hpp:903) starts at zero.
     */
    private var baseSpeedValue: Int = speed.coerceIn(0, PLAYER_MAX_SPEED)
    private var varSpeedValue: Int = 0

    /** `Creature::getSpeed` (`creature.cpp:1643-1645`). */
    fun getSpeed(): Int = (baseSpeedValue + varSpeedValue).coerceIn(0, PLAYER_MAX_SPEED)

    /**
     * `Creature::setSpeed` (`creature.cpp:1647-1664`). The walk rescheduling side
     * effects (`stopEventWalk`/`addEventWalk`/`updateCalculatedStepSpeed`) belong
     * to the movement subsystem in this port (PARITY GAP); the variance state is
     * applied exactly.
     */
    fun setSpeed(varSpeedDelta: Int) {
        if (getSpeed() >= PLAYER_MAX_SPEED) {
            return
        }
        varSpeedValue = varSpeedDelta
    }

    /** `Creature::getBaseSpeed` (creature.hpp:267-269). */
    fun getBaseSpeed(): Int = baseSpeedValue

    /**
     * `Creature::setBaseSpeed` (creature.hpp:264-266). Canary stores into a
     * `uint16_t` (implicit wrap through `Game::setCreatureSpeed`); this port
     * clamps to the representable range instead of wrapping (PARITY GAP).
     */
    fun setBaseSpeed(newBaseSpeed: Int) {
        baseSpeedValue = newBaseSpeed.coerceIn(0, PLAYER_MAX_SPEED)
    }

    /** `Creature::getStepSpeed` (creature.hpp:258-260); `Player` overrides it. */
    open fun getStepSpeed(): Int = getSpeed()

    /**
     * Port seam kept for the existing storage/Lua call sites: reads map to
     * [getSpeed] and writes map to [setBaseSpeed]
     * (`g_game().setCreatureSpeed`, game.cpp:7759-7761). The accessors are
     * renamed at the JVM level so they do not clash with the Canary-parity
     * `getSpeed`/`setSpeed` methods.
     */
    @get:JvmName("getSpeedValue")
    @set:JvmName("setSpeedValue")
    var speed: Int
        get() = getSpeed()
        set(value) {
            setBaseSpeed(value)
        }

    /** `Creature::currentOutfit` / `Creature::defaultOutfit` (creature.hpp:918-919). */
    private var currentOutfit = Outfit()
    private var defaultOutfit = Outfit()

    /** `Creature::getCurrentOutfit` (creature.hpp:364-366). */
    fun getCurrentOutfit(): Outfit = currentOutfit.copy()

    /** `Creature::setCurrentOutfit` (creature.hpp:367-369). */
    fun setCurrentOutfit(outfit: Outfit) {
        currentOutfit = outfit.copy()
    }

    /** `Creature::getDefaultOutfit` (creature.hpp:370-372). */
    fun getDefaultOutfit(): Outfit = defaultOutfit.copy()

    /** `Creature::setDefaultOutfit` (creature.hpp:373-375). */
    fun setDefaultOutfit(outfit: Outfit) {
        defaultOutfit = outfit.copy()
    }

    /** `Creature::isWearingSupportOutfit` (creature.hpp:376-379). */
    fun isWearingSupportOutfit(): Boolean =
        currentOutfit.lookType == 75 || currentOutfit.lookType == 266 || currentOutfit.lookType == 302

    /** `Creature::internalLight` (creature.hpp:922); default color 215. */
    private var internalLight: LightInfo = LightInfo()

    /** `Creature::getCreatureLight` (`creature.cpp:1639-1641`). */
    open fun getCreatureLight(): LightInfo = internalLight.copy()

    /** `Creature::setCreatureLight` (`creature.cpp:1666-1668`). */
    fun setCreatureLight(lightInfo: LightInfo) {
        internalLight = lightInfo.copy()
    }

    /** `Creature::setNormalCreatureLight` (`creature.cpp:1670-1672`). */
    open fun setNormalCreatureLight() {
        internalLight = LightInfo()
    }

    /** `Creature::skull` (creature.hpp:925), default `SKULL_NONE`. */
    private var skull: Skulls_t = Skulls_t.SKULL_NONE

    /** `Creature::getSkull` (creature.hpp:168-170). */
    open fun getSkull(): Skulls_t = skull

    /**
     * `Creature::setSkull` (creature.cpp:100-103); the
     * `g_game().updateCreatureSkull` broadcast is a world side effect that lands
     * with the protocol senders.
     */
    fun setSkull(newSkull: Skulls_t) {
        skull = newSkull
    }

    /** `Creature::canSee(pos)` (`creature.cpp:89-91`). */
    open fun canSee(pos: Position): Boolean =
        canSee(position, pos, MAP_MAX_VIEW_PORT_X, MAP_MAX_VIEW_PORT_Y)

    /** `Creature::canSeeCreature` (`creature.cpp:93-98`). */
    open fun canSeeCreature(creature: Creature): Boolean =
        !(!canSeeInvisibility() && creature.isInvisible())

    /** `Creature::canSeeInvisibility` (creature.hpp:213-215); base is false. */
    open fun canSeeInvisibility(): Boolean = false

    /**
     * `Creature::isInvisible` (`creature.cpp:1849-1853`): any stored
     * `CONDITION_INVISIBLE`, without an end-time check.
     */
    fun isInvisible(): Boolean = conditionList.any { it.type == ConditionType.CONDITION_INVISIBLE }

    /**
     * `Creature::m_master` / `Creature::m_summons` (creature.hpp:879, 482-484)
     * and the `summoned` latch (creature.hpp:890). Canary holds the master as a
     * `std::weak_ptr`; the port keeps strong references because world entities
     * are released through explicit removal (`setRemoved`), not GC reachability
     * (PARITY GAP).
     */
    private var master: Creature? = null
    private val summonList = mutableListOf<Creature>()
    private var summoned = false

    /**
     * `Creature::setMaster` (`creature.cpp:1339-1369`). The `reloadCreature`
     * branch (`setFollowCreature`/`setDropLoot`/`setSkillLoss`/`reloadCreature`)
     * belongs to the follow and protocol subsystems (PARITY GAP).
     */
    fun setMaster(newMaster: Creature?, reloadCreature: Boolean = false): Boolean {
        summoned = true
        val oldMaster = master

        if (newMaster == null && oldMaster == null) {
            return false
        }

        if (newMaster != null) {
            newMaster.summonList.add(this)
        }

        master = newMaster

        if (oldMaster != null) {
            oldMaster.summonList.remove(this)
        }

        return true
    }

    /** `Creature::removeMaster` (creature.hpp:462-466) — only resets the pointer. */
    fun removeMaster() {
        if (master != null) {
            master = null
        }
    }

    /** `Creature::isSummon` (creature.hpp:468-470). */
    fun isSummon(): Boolean = master != null

    /** `Creature::hasBeenSummoned` (creature.hpp:475-477). */
    fun hasBeenSummoned(): Boolean = summoned

    /** `Creature::getMaster` (creature.hpp:478-480). */
    fun getMaster(): Creature? = master

    /** `Creature::getSummons` (creature.hpp:482-484). */
    fun getSummons(): List<Creature> = summonList

    /** `Creature::getSummonCount` (creature.hpp:624-626). */
    fun getSummonCount(): Int = summonList.size

    /** `Creature::hasSummons` (creature.hpp:634-639). */
    fun hasSummons(): Boolean = summonList.isNotEmpty()

    /** `Creature::m_attackedCreature` (creature.hpp:878). */
    private var attackedCreature: Creature? = null

    /** `Creature::getAttackedCreature` (creature.hpp:438-440). */
    fun getAttackedCreature(): Creature? = attackedCreature

    /**
     * `Creature::setAttackedCreature` (`creature.cpp:1011-1036`). The
     * familiar-in-protection-zone rejection (`creature.cpp:1013-1017`) needs the
     * tile and monster-type state and is not ported (PARITY GAP).
     */
    open fun setAttackedCreature(creature: Creature?): Boolean {
        if (creature != null) {
            if (creature.position.z != position.z || !canSee(creature.position)) {
                attackedCreature = null
                return false
            }

            attackedCreature = creature
            onAttackedCreature(creature)
            creature.onAttacked()
        } else {
            attackedCreature = null
        }

        for (summon in summonList.toList()) {
            summon.setAttackedCreature(creature)
        }

        return true
    }

    /**
     * `Creature::iconChanged` (`creature.cpp:2020-2029`); the spectator
     * broadcast (`sendCreatureIcon`) is a world side effect owned by the
     * protocol senders.
     */
    open fun iconChanged() {
        // No behaviour without a world/protocol layer.
    }

    /** `Creature::creatureIcons` (creature.hpp:948) — a `std::map`, key ordered. */
    private val creatureIcons: MutableMap<String, CreatureIcon> = sortedMapOf()

    /** `Creature::getIcons` (creature.hpp:325-334). */
    open fun getIcons(): List<CreatureIcon> = creatureIcons.values.filter { it.isSet() }

    /** `Creature::getIcon` (creature.hpp:336-341). */
    open fun getIcon(key: String): CreatureIcon = creatureIcons[key] ?: CreatureIcon()

    /** `Creature::hasIcon` (creature.hpp:343-345). */
    fun hasIcon(key: String): Boolean = creatureIcons.containsKey(key)

    /** `Creature::setIcon` (creature.hpp:347-350). */
    fun setIcon(key: String, icon: CreatureIcon) {
        creatureIcons[key] = icon
        iconChanged()
    }

    /** `Creature::removeIcon` (creature.hpp:352-355). */
    fun removeIcon(key: String) {
        creatureIcons.remove(key)
        iconChanged()
    }

    /** `Creature::clearIcons` (creature.hpp:357-360). */
    fun clearIcons() {
        creatureIcons.clear()
        iconChanged()
    }

    /** `Creature::conditions` (creature.hpp:843) — kept in insertion order. */
    private val conditionList = mutableListOf<Condition>()

    /** Read-only view of the stored conditions for inspecting systems. */
    val conditions: List<Condition> get() = conditionList

    /**
     * `OTSYS_TIME()` millisecond clock. Injected per AGENTS.md 1.1 so condition
     * lifetime checks are deterministic; conditions keep the clock they were
     * created with.
     */
    open var now: () -> Long = { System.currentTimeMillis() }

    /**
     * `Creature::getCreature()` self-view handed to the condition engine.
     * Injected per AGENTS.md 1.1: when null, conditions still attach, tick and
     * expire, but engine side effects (healing, speed, icons) are skipped.
     */
    open var conditionTarget: ConditionTarget? = null

    /**
     * `Creature::addCondition` (creature.cpp:1372-1395). A condition with the
     * same `(type, id, subId)` already stored is refreshed through the stored
     * instance; otherwise it must `startCondition` before it is retained.
     */
    fun addCondition(condition: Condition?, attackerPlayer: Boolean = false): Boolean {
        if (condition == null) {
            return false
        }

        val type = condition.type
        if (isSuppress(type, attackerPlayer)) {
            return false
        }

        val prevCond = getCondition(type, condition.id, condition.subId)
        if (prevCond != null) {
            prevCond.addCondition(conditionTarget, condition)
            return true
        }

        if (condition.startCondition(conditionTarget)) {
            conditionList.add(condition)
            onAddCondition(type)
            return true
        }

        return false
    }

    /**
     * `Creature::isSuppress` (creature.hpp:525-527) — the base creature never
     * suppresses. `Player::isSuppress` suppression counts and the
     * `MIN_DELAY_BETWEEN_CONDITIONS` gate (player.cpp:356-364) are not ported
     * (PARITY GAP).
     */
    open fun isSuppress(type: ConditionType, attackerPlayer: Boolean): Boolean = false

    /**
     * `Creature::onAddCondition` (creature.cpp:1216-1222): haste and paralyze
     * are mutually exclusive.
     */
    open fun onAddCondition(type: ConditionType) {
        if (type == ConditionType.CONDITION_PARALYZE && hasCondition(ConditionType.CONDITION_HASTE)) {
            removeCondition(ConditionType.CONDITION_HASTE)
        } else if (type == ConditionType.CONDITION_HASTE && hasCondition(ConditionType.CONDITION_PARALYZE)) {
            removeCondition(ConditionType.CONDITION_PARALYZE)
        }
    }

    /** `Creature::onEndCondition` (creature.cpp:1228-1230) — base no-op hook. */
    open fun onEndCondition(type: ConditionType) {
        // No behaviour.
    }

    /** `Creature::removeCondition(type)` (creature.cpp:1412-1433). */
    fun removeCondition(type: ConditionType) {
        val iterator = conditionList.iterator()
        while (iterator.hasNext()) {
            val condition = iterator.next()
            if (condition.type != type) {
                continue
            }

            iterator.remove()
            condition.endCondition(conditionTarget)
            onEndCondition(type)
        }
    }

    /**
     * `Creature::removeCondition(type, conditionId, force)` (creature.cpp:1435-1467).
     *
     * The Canary paralysed walk-delay reschedule (`g_dispatcher().scheduleEvent`,
     * 1450-1458) is not ported, so removal always applies immediately; `force`
     * is kept for signature parity (PARITY GAP).
     */
    fun removeCondition(conditionType: ConditionType, conditionId: ConditionId, force: Boolean = false) {
        val iterator = conditionList.iterator()
        while (iterator.hasNext()) {
            val condition = iterator.next()
            if (condition.type != conditionType || condition.id != conditionId) {
                continue
            }

            iterator.remove()
            condition.endCondition(conditionTarget)
            onEndCondition(conditionType)
        }
    }

    /** `Creature::removeCondition(condition)` (creature.cpp:1486-1498). */
    fun removeCondition(condition: Condition) {
        if (!conditionList.remove(condition)) {
            return
        }

        condition.endCondition(conditionTarget)
        onEndCondition(condition.type)
    }

    /** `Creature::getCondition(type)` (creature.cpp:1500-1511). */
    fun getCondition(type: ConditionType): Condition? = conditionList.firstOrNull { it.type == type }

    /** `Creature::getCondition(type, conditionId, subId)` (creature.cpp:1513-1525). */
    fun getCondition(type: ConditionType, conditionId: ConditionId, subId: Int = 0): Condition? =
        conditionList.firstOrNull { it.type == type && it.id == conditionId && it.subId == subId }

    /** `Creature::getConditionsByType(type)` (creature.cpp:1551-1563). */
    fun getConditionsByType(type: ConditionType): List<Condition> =
        conditionList.filter { it.type == type }

    /**
     * `Creature::executeConditions(interval)` (creature.cpp:1565-1583). Every
     * stored condition ticks; a rejected tick ends and drops it.
     */
    fun executeConditions(interval: Int) {
        val iterator = conditionList.iterator()
        while (iterator.hasNext()) {
            val condition = iterator.next()
            if (!condition.executeCondition(conditionTarget, interval)) {
                iterator.remove()
                condition.endCondition(conditionTarget)
                onEndCondition(condition.type)
            }
        }
    }

    /** `Creature::hasCondition(type, subId)` (creature.cpp:1585-1605). */
    fun hasCondition(type: ConditionType, subId: Int = 0): Boolean {
        if (isSuppress(type, false)) {
            return false
        }

        val timeNow = now()
        return conditionList.any {
            it.type == type && it.subId == subId && (it.endTime >= timeNow || it.ticks == -1)
        }
    }

    /**
     * `Creature::onThink` (creature.cpp:128-172) folded with the condition tick
     * `Game::checkCreatures` performs on the creature right after it
     * (game.cpp:7726-7733). The follow/block/scripting steps of the C++ body
     * belong to their own subsystems in this port.
     */
    open fun onThink(interval: Int) {
        executeConditions(interval)
    }

    // --- combat event hooks (creature.hpp:552-566) --------------------------------

    /**
     * Listener seams replacing the scripting dispatch Canary performs around the
     * virtual hooks (AGENTS.md 1.1): the C++ base bodies are empty
     * (`creature.cpp:1273-1275`, creature.hpp:558, 561) and the Lua layer is
     * driven through `ScriptingDispatch`; the creature itself only owns the
     * registration the port needs to observe them.
     */
    private val onAttackedHandlers = mutableListOf<(Creature) -> Unit>()
    private val onAttackedCreatureHandlers = mutableListOf<(Creature, Creature?) -> Unit>()
    private val onTargetCreatureGainHealthHandlers = mutableListOf<(Creature, Creature?, Int) -> Unit>()
    private val onDeathHandlers = mutableListOf<(Creature) -> Unit>()

    /** Registers a listener for [onAttacked]. */
    fun registerOnAttackedHandler(handler: (Creature) -> Unit) {
        onAttackedHandlers.add(handler)
    }

    /** Removes a listener registered with [registerOnAttackedHandler]. */
    fun unregisterOnAttackedHandler(handler: (Creature) -> Unit) {
        onAttackedHandlers.remove(handler)
    }

    /** Registers a listener for [onAttackedCreature]. */
    fun registerOnAttackedCreatureHandler(handler: (Creature, Creature?) -> Unit) {
        onAttackedCreatureHandlers.add(handler)
    }

    /** Removes a listener registered with [registerOnAttackedCreatureHandler]. */
    fun unregisterOnAttackedCreatureHandler(handler: (Creature, Creature?) -> Unit) {
        onAttackedCreatureHandlers.remove(handler)
    }

    /** Registers a listener for [onTargetCreatureGainHealth]. */
    fun registerOnTargetCreatureGainHealthHandler(handler: (Creature, Creature?, Int) -> Unit) {
        onTargetCreatureGainHealthHandlers.add(handler)
    }

    /** Removes a listener registered with [registerOnTargetCreatureGainHealthHandler]. */
    fun unregisterOnTargetCreatureGainHealthHandler(handler: (Creature, Creature?, Int) -> Unit) {
        onTargetCreatureGainHealthHandlers.remove(handler)
    }

    /** Registers a listener for [onDeath]. */
    fun registerOnDeathHandler(handler: (Creature) -> Unit) {
        onDeathHandlers.add(handler)
    }

    /** Removes a listener registered with [registerOnDeathHandler]. */
    fun unregisterOnDeathHandler(handler: (Creature) -> Unit) {
        onDeathHandlers.remove(handler)
    }

    /** `Creature::onAttacked` (`creature.cpp:1273-1275`). */
    open fun onAttacked() {
        onAttackedHandlers.toList().forEach { it(this) }
    }

    /** `Creature::onAttackedCreature` (creature.hpp:558) — the target hook. */
    open fun onAttackedCreature(target: Creature?) {
        onAttackedCreatureHandlers.toList().forEach { it(this, target) }
    }

    /** `Creature::onTargetCreatureGainHealth` (creature.hpp:561). */
    open fun onTargetCreatureGainHealth(target: Creature?, points: Int) {
        onTargetCreatureGainHealthHandlers.toList().forEach { it(this, target, points) }
    }

    /**
     * `Creature::onDeath` (`creature.cpp:579-692`). The portable tail runs the
     * registered death listeners (the scripting `onDeath` events fire while the
     * corpse exists, `creature.cpp:819-824`), the `death(lastHitCreature)` hook
     * and `removeMaster()` (`creature.cpp:671-673`). Damage-map killer
     * resolution, corpse/loot and bestiary handling stay with their owning
     * subsystems (PARITY GAP).
     */
    fun onDeath() {
        onDeathHandlers.toList().forEach { it(this) }
        death(null)
        if (getMaster() != null) {
            removeMaster()
        }
    }

    /** `Creature::death` (creature.hpp:968) — virtual base no-op hook. */
    open fun death(lastHitCreature: Creature? = null) {
        // No behaviour.
    }

    companion object {
        /** `MAP_MAX_CLIENT_VIEW_PORT_X/Y` (`src/map/map_const.hpp:12-13`). */
        const val MAP_MAX_CLIENT_VIEW_PORT_X = 8
        const val MAP_MAX_CLIENT_VIEW_PORT_Y = 6

        /** `MAP_MAX_VIEW_PORT_X/Y` (`src/map/map_const.hpp:14-15`). */
        const val MAP_MAX_VIEW_PORT_X = MAP_MAX_CLIENT_VIEW_PORT_X + 3
        const val MAP_MAX_VIEW_PORT_Y = MAP_MAX_CLIENT_VIEW_PORT_Y + 5

        /** `MAP_INIT_SURFACE_LAYER` (`src/map/map_const.hpp:18`). */
        const val MAP_INIT_SURFACE_LAYER = 7

        /** `MAP_LAYER_VIEW_LIMIT` (`src/map/map_const.hpp:19`). */
        const val MAP_LAYER_VIEW_LIMIT = 2

        /** `PLAYER_MAX_SPEED` (`src/creatures/players/player.hpp:125`). */
        const val PLAYER_MAX_SPEED = 65535

        /**
         * `Creature::canSee(myPos, pos, viewRangeX, viewRangeY)`
         * (`creature.cpp:68-87`).
         */
        fun canSee(myPos: Position, pos: Position, viewRangeX: Int, viewRangeY: Int): Boolean {
            if (myPos.z <= MAP_INIT_SURFACE_LAYER) {
                // On ground level or above (7 -> 0) the view only reaches down
                // to the surface layer.
                if (pos.z > MAP_INIT_SURFACE_LAYER) {
                    return false
                }
            } else if (myPos.z >= MAP_INIT_SURFACE_LAYER + 1) {
                // Underground the view is +/- MAP_LAYER_VIEW_LIMIT floors.
                if (kotlin.math.abs(myPos.z - pos.z) > MAP_LAYER_VIEW_LIMIT) {
                    return false
                }
            }

            val offsetz = myPos.z - pos.z
            return pos.x >= myPos.x - viewRangeX + offsetz &&
                pos.x <= myPos.x + viewRangeX + offsetz &&
                pos.y >= myPos.y - viewRangeY + offsetz &&
                pos.y <= myPos.y + viewRangeY + offsetz
        }
    }
}

class Monster(
    id: Long,
    name: String,
    maxHealth: Int,
    health: Int,
    maxMana: Int = 0,
    mana: Int = 0,
    speed: Int = 200,
    position: Position
) : Creature(id, name, maxHealth, health, maxMana, mana, speed, position)

enum class FightMode {
    Attack,
    Balanced,
    Defense
}

enum class ChaseMode {
    StandStill,
    Follow
}

class Player(
    id: Long,
    name: String,
    maxHealth: Int,
    health: Int,
    maxMana: Int = 0,
    mana: Int = 0,
    var capacity: Double = 400.0,
    speed: Int = 220,
    position: Position,
    var experience: Long = 0,
    var level: Int = 1,
    var fightMode: FightMode = FightMode.Attack,
    var chaseMode: ChaseMode = ChaseMode.StandStill,
    var secureMode: Boolean = true,
    var targetCreatureId: Long? = null,
    var inCombat: Boolean = false
) : Creature(id, name, maxHealth, health, maxMana, mana, speed, position) {

    /**
     * `Player::vocation` (player.hpp). Canary stores the internal [Vocations] id
     * (0..10); without a wired registry this Kotlin port historically held a CIP
     * id directly, so [getPlayerVocationEnum] passes it through unchanged.
     */
    var vocationId: Int = 0

    /**
     * `Vocations` registry used by [getPlayerVocationEnum] to normalize the
     * internal vocation id to the CIP client id. Canary resolves this through the
     * global `g_vocations()`; per AGENTS.md 1.1 it is injected here and defaults to
     * absent, which keeps [getPlayerVocationEnum] a pass-through.
     */
    var vocations: Vocations? = null

    /** Port's `PlayerFlags_t::IsAlwaysPremium` seam; see [isPremium] (player.cpp:7533). */
    var premium: Boolean = false

    /** `Player::account` (player.hpp:1756); premium time is derived from it. */
    var account: Account? = null

    /** `g_configManager().getBoolean(FREE_PREMIUM)` (configmanager.cpp:103). */
    var freePremium: Boolean = false

    /** `std::shared_ptr<Group> group` (player.hpp:1759); `Player::isAccessPlayer()` reads `group->access`. */
    var group: Group? = null

    val features: PlayerFeatures = PlayerFeatures(
        playerId = id,
        levelProvider = { level },
        vocationIdProvider = { vocationId },
        isPremiumProvider = { isPremium() },
        hasAccessProvider = { isAccessPlayer() },
        vocationRequirementsProvider = {
            vocations?.getVocation(vocationId)
                ?.let { VocationRequirementsAdapter(it) }
                ?: UnwiredVocationRequirements
        },
    )

    /**
     * `Player::getPlayerVocationEnum()` (player.cpp:13030): maps the internal
     * vocation id through the [Vocations] registry's `clientid` to the CIP
     * `Vocation_t` id (1..5). Without a wired registry the raw [vocationId] is
     * returned unchanged, preserving the previous behavior.
     */
    fun getPlayerVocationEnum(): Int {
        val registry = vocations ?: return vocationId
        val vocation = registry.getVocation(vocationId) ?: return Vocation.VOCATION_NONE
        return when (vocation.getClientId()) {
            1, 11 -> Vocation.VOCATION_KNIGHT_CIP
            2, 12 -> Vocation.VOCATION_PALADIN_CIP
            3, 13 -> Vocation.VOCATION_SORCERER_CIP
            4, 14 -> Vocation.VOCATION_DRUID_CIP
            5, 15 -> Vocation.VOCATION_MONK_CIP
            else -> Vocation.VOCATION_NONE
        }
    }

    /**
     * `Player::isPremium()` (player.cpp:7532). Mirrors Canary: the FREE_PREMIUM
     * config and the always-premium flag short-circuit true, otherwise the
     * account's premium time decides. [premium] is the port's
     * `PlayerFlags_t::IsAlwaysPremium` seam.
     */
    fun isPremium(now: Long = getTimeNow()): Boolean {
        if (freePremium || premium) {
            return true
        }
        val account = account ?: return false
        return account.getPremiumRemainingDays(now) > 0 || account.getPremiumLastDay() > now
    }

    /** `Player::isAccessPlayer()` (player.cpp:7524). */
    fun isAccessPlayer(): Boolean = group?.access ?: false

    /** `Player::canSeeInvisibility()` (player.cpp:12989-12991). */
    override fun canSeeInvisibility(): Boolean =
        hasFlag(PlayerFlags_t.CanSenseInvisibility) || isAccessPlayer()

    /**
     * `Player::getStepSpeed()` (player.hpp:1909-1913):
     * `max(PLAYER_MIN_SPEED, min(maxStepSpeed, getSpeed()))` with the
     * `PLAYER_MAX_STAFF_SPEED` cap (both max constants are 65535,
     * player.hpp:125-127).
     */
    override fun getStepSpeed(): Int {
        val maxStepSpeed =
            if (hasFlag(PlayerFlags_t.SetMaxSpeed)) PLAYER_MAX_STAFF_SPEED else PLAYER_MAX_SPEED
        return maxOf(PLAYER_MIN_SPEED, minOf(maxStepSpeed, getSpeed()))
    }

    /** `Player::getAccountType()` (player.cpp:12627-12629): `NORMAL` without an account. */
    fun getAccountType(): AccountType = account?.accountType ?: AccountType.ACCOUNT_TYPE_NORMAL

    /** `Player::getGUID()`; the port's identity is [id]. */
    fun getGUID(): Long = id

    /** `Player::sex` (player.hpp:1853), default `PLAYERSEX_FEMALE`. */
    private var sex: PlayerSex_t = PlayerSex_t.FEMALE

    /** `Player::getSex()` (player.hpp:610-612). */
    fun getSex(): PlayerSex_t = sex

    /** `Player::setSex()` (player.cpp:7108-7110). */
    fun setSex(newSex: PlayerSex_t) {
        sex = newSex
    }

    /** `Player::pronoun`; `PLAYERPRONOUN_UNSET` until set. */
    private var pronoun: PlayerPronoun_t = PlayerPronoun_t.PLAYERPRONOUN_UNSET

    /** `Player::getPronoun()` (player.hpp:613-615). */
    fun getPronoun(): PlayerPronoun_t = pronoun

    /** `Player::setPronoun()` (player.cpp:7112-7114). */
    fun setPronoun(newPronoun: PlayerPronoun_t) {
        pronoun = newPronoun
    }

    /**
     * `Player::getPossessivePronoun()` (player.cpp:7096-7098) through
     * `getPlayerPossessivePronoun` (tools.cpp:1929-1944). Read by the private
     * chat channel invite message (chat.cpp:49).
     */
    fun getPossessivePronoun(): String = when (pronoun) {
        PlayerPronoun_t.PLAYERPRONOUN_THEY -> "their"
        PlayerPronoun_t.PLAYERPRONOUN_SHE -> "her"
        PlayerPronoun_t.PLAYERPRONOUN_HE -> "his"
        PlayerPronoun_t.PLAYERPRONOUN_ZE -> "zir"
        PlayerPronoun_t.PLAYERPRONOUN_NAME -> "$name's"
        PlayerPronoun_t.PLAYERPRONOUN_UNSET -> if (sex == PlayerSex_t.FEMALE) "her" else "his"
    }

    /** `Player::guild` (player.hpp:1851). */
    private var mGuild: Guild? = null

    /** `Player::getGuild()` (player.cpp:9312-9314). */
    fun getGuild(): Guild? = mGuild

    /**
     * `Player::setGuild()` (player.cpp:9316-9347). The `ExpertPvp` broadcast is
     * not ported (PARITY GAP); the port resolves the level-1 rank through the
     * injected [Guild] instead of Canary's data-loaded rank table.
     */
    fun setGuild(newGuild: Guild?) {
        if (newGuild === mGuild) {
            return
        }

        mGuild?.removeMember(this)
        mGuild = null
        mGuildRank = null

        if (newGuild != null) {
            val rank = newGuild.getRankByLevel(1) ?: return
            mGuild = newGuild
            mGuildRank = rank
            newGuild.addMember(this, rank)
        }
    }

    /** `Player::guildRank` (player.hpp:1852). */
    private var mGuildRank: GuildRank? = null

    /** `Player::getGuildRank()` (player.cpp:9349-9351). */
    fun getGuildRank(): GuildRank? = mGuildRank

    /** `Player::setGuildRank()` (player.cpp:9353-9355). */
    fun setGuildRank(newGuildRank: GuildRank?) {
        mGuildRank = newGuildRank
    }

    /**
     * `Player::isMuted()` (player.cpp:3494-3506): the longest remaining
     * `CONDITION_MUTED` in seconds, or 0 for `CannotBeMuted` players.
     */
    fun isMuted(): Long {
        if (hasFlag(PlayerFlags_t.CannotBeMuted)) {
            return 0
        }

        var muteTicks = 0
        for (condition in conditions) {
            if (condition.type == ConditionType.CONDITION_MUTED && condition.ticks > muteTicks) {
                muteTicks = condition.ticks
            }
        }
        return (muteTicks / 1000).toLong()
    }

    /** `Player::m_party` (player.hpp:1767). */
    private var mParty: Party? = null

    /**
     * `Player::setParty` (player.cpp:1038-1045); the
     * `ExpertPvp::refreshAllVisibleSituationMarks()` side effect is not ported.
     */
    fun setParty(newParty: Party?) {
        if (mParty === newParty) {
            return
        }
        mParty = newParty
    }

    /** `Player::getParty` (player.cpp:1047-1049). */
    fun getParty(): Party? = mParty

    /** `Player::invitePartyList` (player.hpp:1694); read-only view for inspecting systems. */
    private val partyInvitations = mutableListOf<Party>()

    /** Read-only view of the pending `Player::invitePartyList` entries. */
    val invitePartyList: List<Party> get() = partyInvitations

    /** `Player::addPartyInvitation` (player.cpp:7802-7810). */
    fun addPartyInvitation(newParty: Party): Boolean {
        if (partyInvitations.contains(newParty)) {
            return false
        }
        partyInvitations.add(newParty)
        return true
    }

    /** `Player::removePartyInvitation` (player.cpp:7812-7814). */
    fun removePartyInvitation(remParty: Party) {
        partyInvitations.remove(remParty)
    }

    /** `Player::isInviting` (player.cpp:7783-7788). */
    fun isInviting(player: Player?): Boolean {
        val party = mParty
        if (player == null || party == null || party.getLeader() !== this) {
            return false
        }
        return party.isPlayerInvited(player)
    }

    /**
     * `Player::getPartyShield` (player.cpp:7729-7781): the party shield shown
     * for [player], derived from the party shared-experience state and the
     * pending invitations.
     */
    fun getPartyShield(player: Player?): PartyShields_t {
        if (player == null) {
            return PartyShields_t.SHIELD_NONE
        }

        val party = mParty
        if (party != null) {
            if (party.getLeader() === player) {
                if (party.isSharedExperienceActive()) {
                    if (party.isSharedExperienceEnabled()) {
                        return PartyShields_t.SHIELD_YELLOW_SHAREDEXP
                    }

                    if (party.canUseSharedExperience(player)) {
                        return PartyShields_t.SHIELD_YELLOW_NOSHAREDEXP
                    }

                    return PartyShields_t.SHIELD_YELLOW_NOSHAREDEXP_BLINK
                }

                return PartyShields_t.SHIELD_YELLOW
            }

            if (player.getParty() === party) {
                if (party.isSharedExperienceActive()) {
                    if (party.isSharedExperienceEnabled()) {
                        return PartyShields_t.SHIELD_BLUE_SHAREDEXP
                    }

                    if (party.canUseSharedExperience(player)) {
                        return PartyShields_t.SHIELD_BLUE_NOSHAREDEXP
                    }

                    return PartyShields_t.SHIELD_BLUE_NOSHAREDEXP_BLINK
                }

                return PartyShields_t.SHIELD_BLUE
            }

            if (isInviting(player)) {
                return PartyShields_t.SHIELD_WHITEBLUE
            }
        }

        if (player.isInviting(this)) {
            return PartyShields_t.SHIELD_WHITEYELLOW
        }

        if (player.getParty() != null) {
            return PartyShields_t.SHIELD_GRAY
        }

        return PartyShields_t.SHIELD_NONE
    }

    /**
     * `Player::hasFlag` (player.cpp:7853-7855) — port of the group flag lookup.
     * Canary resolves flags from `Group::flags` (`groups.hpp:16`); the port's
     * [Group] does not model the array yet, so the flags are held on the player.
     */
    private val playerFlags = mutableSetOf<PlayerFlags_t>()

    /** `Player::hasFlag` (player.cpp:7853-7855). */
    fun hasFlag(flag: PlayerFlags_t): Boolean = flag in playerFlags

    /** `Player::setFlag` (player.cpp:7857-7859). */
    fun setFlag(flag: PlayerFlags_t) {
        playerFlags.add(flag)
    }

    /** `Player::removeFlag` (player.cpp:7861-7863). */
    fun removeFlag(flag: PlayerFlags_t) {
        playerFlags.remove(flag)
    }

    /** `Player::getSkull` (player.cpp:7116-7121): `NotGainInFight` hides the skull. */
    override fun getSkull(): Skulls_t =
        if (hasFlag(PlayerFlags_t.NotGainInFight)) Skulls_t.SKULL_NONE else super.getSkull()

    /** `Player::magLevel` (player.hpp:1787). */
    var magLevel: Int = 0

    /** `Player::varStats[STAT_LAST + 1]` (player.hpp:1802). */
    val varStats = IntArray(Stat.LAST + 1)

    /** `Player::setVarStats` (player.cpp:1251-1253) — accumulates the modifier. */
    fun setVarStats(stat: Stat, modifier: Int) {
        varStats[stat.value] += modifier
    }

    /** `Player::getBaseMagicLevel` (player.hpp:585-587). */
    fun getBaseMagicLevel(): Int = magLevel

    /**
     * `Player::getMagicLevel` (player.cpp:7420-7427):
     * `max(0, getLoyaltyMagicLevel() + varStats[STAT_MAGICPOINTS])`. The Wheel
     * of Destiny and weapon-proficiency terms are unported subsystems
     * (PARITY GAP).
     */
    fun getMagicLevel(): Int = maxOf(0, getLoyaltyMagicLevel() + varStats[Stat.STAT_MAGICPOINTS.value])

    /**
     * `Player::getLoyaltyMagicLevel` (player.cpp:7429-7455). The account loyalty
     * bonus and `manaSpent` progression are not ported (PARITY GAP); without a
     * loyalty bonus Canary's loop cannot advance either, so the base magic level
     * is returned.
     */
    fun getLoyaltyMagicLevel(): Int = magLevel

    /**
     * `Item::items[id].weaponType` lookup (`Item::getWeaponType`, src/items/item.hpp:340).
     * `ItemType` has no weapon classification in this port yet, so per
     * AGENTS.md 1.1 the lookup is injected; unwired, every item resolves
     * `WEAPON_NONE`.
     */
    var weaponTypeProvider: (Int) -> WeaponType_t = { _ -> WeaponType_t.WEAPON_NONE }

    /** `Player::getWeaponId` (player.cpp:396-403). */
    fun getWeaponId(ignoreAmmo: Boolean = false): Int = getWeapon(ignoreAmmo)?.id ?: 0

    /** `Player::getWeapon(ignoreAmmo)` (player.cpp:453-464) — left hand first. */
    fun getWeapon(ignoreAmmo: Boolean = false): Item? =
        getWeapon(Slots_t.CONST_SLOT_LEFT, ignoreAmmo) ?: getWeapon(Slots_t.CONST_SLOT_RIGHT, ignoreAmmo)

    /**
     * `Player::getWeapon(slot, ignoreAmmo)` (player.cpp:405-424). Shields and
     * ammo are never weapons.
     */
    fun getWeapon(slot: Slots_t, ignoreAmmo: Boolean = false): Item? {
        val item = getInventoryItem(slot) ?: return null
        val weaponType = weaponTypeProvider(item.id)
        if (weaponType == WeaponType_t.WEAPON_NONE ||
            weaponType == WeaponType_t.WEAPON_SHIELD ||
            weaponType == WeaponType_t.WEAPON_AMMO
        ) {
            return null
        }

        if (!ignoreAmmo &&
            (weaponType == WeaponType_t.WEAPON_DISTANCE || weaponType == WeaponType_t.WEAPON_MISSILE)
        ) {
            // `getQuiverAmmoOfType` (player.cpp:436-451) is not ported (PARITY
            // GAP): Canary returns the matching quiver ammo here, the port
            // returns the equipped weapon itself.
        }

        return item
    }

    /** `Player::inventory[CONST_SLOT_LAST + 1]` (player.hpp:1763). */
    private val inventory = arrayOfNulls<Item>(Slots_t.LAST + 1)

    /** `Player::skills[SKILL_LAST + 1]` (player.hpp:1711). */
    private val skills = Array(skills_t.LAST + 1) { SkillState() }

    /** `Player::getInventoryItem` (player.cpp:337). */
    fun getInventoryItem(slot: Slots_t): Item? {
        if (slot.value < Slots_t.FIRST || slot.value > Slots_t.LAST) {
            return null
        }
        return inventory[slot.value]
    }

    /**
     * Inventory insertion, mirroring `Game::internalPlayerAddItem` / `Game::internalAddItem`
     * (game.cpp:2693-3003) for the carried-in-inventory case.
     */
    fun addItem(item: Item, slot: Slots_t = Slots_t.CONST_SLOT_WHEREEVER): ReturnValue {
        val target = when {
            slot == Slots_t.CONST_SLOT_WHEREEVER -> firstFreeSlot() ?: return ReturnValue.NOTENOUGHROOM
            slot.value < Slots_t.FIRST || slot.value > Slots_t.LAST -> return ReturnValue.NOTPOSSIBLE
            else -> slot
        }
        if (inventory[target.value] != null) {
            return ReturnValue.NOTENOUGHROOM
        }
        if (itemWeight(item) > getFreeCapacity()) {
            return ReturnValue.NOTENOUGHCAPACITY
        }
        inventory[target.value] = item
        return ReturnValue.NOERROR
    }

    /** `Player::removeItem` (player.cpp:10251-10281). */
    fun removeItem(item: Item, count: Int = 0): ReturnValue {
        val slot = inventory.indexOfFirst { it === item }
        if (slot < 0) {
            return ReturnValue.NOTPOSSIBLE
        }
        val toRemove = if (count == 0 || count > item.count) item.count else count
        item.count -= toRemove
        if (item.count <= 0) {
            inventory[slot] = null
        }
        return ReturnValue.NOERROR
    }

    /** `Player::getItemTypeCount` (player.cpp:5151-5179); Lua `player:getItemCount`. */
    fun getItemCount(
        itemId: Int,
        subType: Int = -1,
        ignoreEquipped: Boolean = false,
        ignoreStoreInbox: Boolean = false,
    ): Long {
        var count = 0L
        for (slot in Slots_t.FIRST..Slots_t.LAST) {
            if (ignoreStoreInbox && slot == Slots_t.CONST_SLOT_STORE_INBOX.value) {
                continue
            }
            val item = inventory[slot] ?: continue
            if (!ignoreEquipped && item.id == itemId) {
                count += countByType(item, subType)
            }
            if (item is Container) {
                count += countContainer(item, itemId, subType)
            }
        }
        return count
    }

    /** `Player::getFreeCapacity` (player.cpp:5468-5476). */
    fun getFreeCapacity(): Double = max(0.0, capacity - inventoryWeight())

    /** `Player::getSkillLevel` (player.cpp:7468). */
    fun getSkillLevel(skill: skills_t): Int {
        if (skill.value < skills_t.FIRST || skill.value > skills_t.LAST) {
            return 0
        }
        return skills[skill.value].level
    }

    /** Lua `player:addSkillTries` -> `Player::addSkillAdvance` (player.cpp:1190-1249). */
    fun addSkillTries(skill: skills_t, tries: Long) {
        if (skill.value < skills_t.FIRST || skill.value > skills_t.LAST) {
            return
        }
        skills[skill.value].tries += tries
    }

    /** Lua `player:getSkillTries` (player_functions.cpp:1662-1672). */
    fun getSkillTries(skill: skills_t): Long {
        if (skill.value < skills_t.FIRST || skill.value > skills_t.LAST) {
            return 0L
        }
        return skills[skill.value].tries
    }

    private fun firstFreeSlot(): Slots_t? =
        Slots_t.entries.firstOrNull { it.value in Slots_t.FIRST..Slots_t.LAST && inventory[it.value] == null }

    private fun inventoryWeight(): Double =
        (Slots_t.FIRST..Slots_t.LAST).sumOf { slot -> inventory[slot]?.let { itemWeight(it) } ?: 0.0 }

    private fun itemWeight(item: Item): Double = if (item is Container) item.totalWeight else item.weight

    private fun countByType(item: Item, subType: Int): Long {
        if (subType >= 0 && item.count != subType) {
            return 0L
        }
        return item.count.toLong()
    }

    private fun countContainer(container: Container, itemId: Int, subType: Int): Long {
        var count = 0L
        for (item in container.items) {
            if (item.id == itemId) {
                count += countByType(item, subType)
            }
            if (item is Container) {
                count += countContainer(item, itemId, subType)
            }
        }
        return count
    }

    fun addExperience(exp: Long) {
        experience += exp
        updateLevel()
    }

    /**
     * `Player::staminaXpBoost` (player.hpp:1824, default 100);
     * `Player::setStaminaXpBoost` (player.cpp:3252-3254) clamps to the uint16
     * maximum and `getStaminaXpBoost` (player.cpp:3248-3250) reads it back.
     */
    var staminaXpBoost: Int = 100
        set(value) {
            field = minOf(65535, value)
        }

    /**
     * `Player::onGainSharedExperience` (player.cpp:6724-6726) credits the shared
     * party experience through `Player::gainExperience`; the port folds that into
     * [addExperience] (rate/message/stat machinery is a PARITY GAP).
     */
    fun onGainSharedExperience(gainExp: Long, target: Creature? = null) {
        addExperience(gainExp)
    }

    private fun updateLevel() {
        while (experience >= getExpForLevel(level + 1)) {
            level++
        }
    }

    companion object {
        /** `PLAYER_MAX_STAFF_SPEED` (`src/creatures/players/player.hpp:126`). */
        const val PLAYER_MAX_STAFF_SPEED = 65535

        /** `PLAYER_MIN_SPEED` (`src/creatures/players/player.hpp:127`). */
        const val PLAYER_MIN_SPEED = 10

        /**
         * Matches Canary / OpenTibia formula:
         * (50 * n^3 - 300 * n^2 + 850 * n - 600) / 3
         */
        fun getExpForLevel(n: Int): Long {
            if (n <= 1) return 0L
            val l = n.toLong()
            return (50L * l * l * l - 300L * l * l + 850L * l - 600L) / 3L
        }
    }
}
