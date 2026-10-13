package org.opentibia.canary.creatures

import org.opentibia.canary.account.Account
import org.opentibia.canary.account.getTimeNow
import org.opentibia.canary.creatures.grouping.Group
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
