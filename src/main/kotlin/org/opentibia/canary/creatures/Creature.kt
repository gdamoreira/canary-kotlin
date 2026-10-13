package org.opentibia.canary.creatures

import org.opentibia.canary.account.Account
import org.opentibia.canary.account.getTimeNow
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionTarget
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.Stat
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.creatures.grouping.Party
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
    var speed: Int = 200,
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

    private fun updateLevel() {
        while (experience >= getExpForLevel(level + 1)) {
            level++
        }
    }

    companion object {
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
