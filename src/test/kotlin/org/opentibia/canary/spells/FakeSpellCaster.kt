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

/**
 * Test double for the injected [SpellCaster] seam. It records every mutation
 * the spell engine performs so Canary arithmetic can be asserted without a live
 * world; it is intentionally not Canary's `Player`.
 */
class FakeSpellCaster(
    private val id: Long = 1,
    private val name: String = "Tester",
    position: Position = Position(100, 100, 7),
    direction: Direction = Direction.South,
    var levelValue: Int = 1,
    var magicLevelValue: Int = 0,
    var manaValue: Int = 0,
    var maxManaValue: Int = 0,
    var soulValue: Int = 0,
    var vocationIdValue: Int = 0,
    var weaponTypeValue: WeaponType_t = WeaponType_t.WEAPON_FIST,
    var zoneTypeValue: ZoneType = ZoneType.ZONE_NORMAL,
    var skullValue: Skulls_t = Skulls_t.SKULL_NONE,
    var premiumValue: Boolean = false,
    var groupIdValue: Int = 1,
    var firstOnStackValue: Boolean = true,
) : SpellCaster {

    var currentPosition: Position = position
    var currentDirection: Direction = direction

    val book = SpellBook()
    val flags = mutableSetOf<PlayerFlags_t>()
    val conditions = mutableListOf<Condition>()
    val cancelMessages = mutableListOf<ReturnValue>()
    val fightTicks = mutableListOf<Int>()
    val aggressiveActions = mutableListOf<Int>()
    var manaSpent = 0
    var attackedTarget: SpellTarget? = null
    var addConditionResult = true

    fun setFlag(flag: PlayerFlags_t) {
        flags.add(flag)
    }

    fun removeFlag(flag: PlayerFlags_t) {
        flags.remove(flag)
    }

    override fun getID(): Long = id

    override fun getName(): String = name

    override fun getPosition(): Position = currentPosition

    override fun getDirection(): Direction = currentDirection

    override fun getLevel(): Int = levelValue

    override fun getMagicLevel(): Int = magicLevelValue

    override fun getMana(): Int = manaValue

    override fun getMaxMana(): Int = maxManaValue

    override fun getSoul(): Int = soulValue

    override fun getVocationId(): Int = vocationIdValue

    override fun getWeaponType(): WeaponType_t = weaponTypeValue

    override fun getZoneType(): ZoneType = zoneTypeValue

    override fun getSkull(): Skulls_t = skullValue

    override fun isPremium(): Boolean = premiumValue

    override fun getGroupId(): Int = groupIdValue

    override fun isAccessPlayer(): Boolean = false

    override fun isFirstOnStack(): Boolean = firstOnStackValue

    override fun hasFlag(flag: PlayerFlags_t): Boolean = flag in flags

    override fun hasCondition(type: ConditionType, subId: Int): Boolean =
        conditions.any { it.type == type && it.subId == subId }

    override fun getSpellBook(): SpellBook = book

    override fun getAttackedCreature(): SpellTarget? = attackedTarget

    override fun addCondition(condition: Condition): Boolean {
        if (addConditionResult) {
            conditions.add(condition)
        }
        return addConditionResult
    }

    override fun sendCancelMessage(ret: ReturnValue) {
        cancelMessages.add(ret)
    }

    override fun addManaSpent(manaCost: Int) {
        manaSpent += manaCost
    }

    override fun changeMana(delta: Int) {
        manaValue = maxOf(0, manaValue + delta)
    }

    override fun changeSoul(delta: Int) {
        soulValue += delta
    }

    override fun addInFightTicks() {
        fightTicks.add(1)
    }

    override fun updateLastAggressiveAction() {
        aggressiveActions.add(1)
    }
}
