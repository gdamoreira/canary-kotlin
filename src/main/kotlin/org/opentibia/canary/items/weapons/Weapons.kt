package org.opentibia.canary.items.weapons

import org.opentibia.canary.combat.BlockType
import org.opentibia.canary.combat.CombatDamage
import org.opentibia.canary.combat.CombatParams
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.normalRandom
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.game.movement.getDistanceX
import org.opentibia.canary.game.movement.getDistanceY
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.WeaponAction_t
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.proficiency.WeaponProficiency
import org.opentibia.canary.systems.proficiency.WeaponProficiencyBonus
import java.util.Random
import kotlin.math.ceil

/**
 * Injected view of a `Player` for the weapon paths (AGENTS.md 1.1). Every
 * method mirrors the exact C++ `Player` accessor `weapons.cpp` calls.
 */
interface WeaponPlayer {
    /** `Player::weaponProficiency()` (weapons.cpp:136, 646, 901, 831). */
    fun weaponProficiency(): WeaponProficiency

    /** `Creature::getPosition()` (weapons.cpp:138-139, 221, 539-540, 708-709). */
    fun getPosition(): Position

    /** `Player::hasFlag(PlayerFlags_t)` (weapons.cpp:148, 248, 336, 359). */
    fun hasFlag(flag: PlayerFlags_t): Boolean

    /** `Player::getMana()` (weapons.cpp:153). */
    fun getMana(): Int

    /** `Player::getMaxMana()` (weapons.cpp:403). */
    fun getMaxMana(): Int

    /** `Player::getHealth()` (weapons.cpp:157). */
    fun getHealth(): Int

    /** `Player::getMaxHealth()` (weapons.cpp:415). */
    fun getMaxHealth(): Int

    /** `Player::getSoul()` (weapons.cpp:161). */
    fun getSoul(): Int

    /** `Player::isPremium()` (weapons.cpp:165). */
    fun isPremium(): Boolean

    /** `Player::getVocationId()` (weapons.cpp:169). */
    fun getVocationId(): Int

    /** `Player::getLevel()` (weapons.cpp:174, 199, 633, 653, 881, 920). */
    fun getLevel(): Int

    /** `Player::getMagicLevel()` (weapons.cpp:178). */
    fun getMagicLevel(): Int

    /** `Player::getWeaponSkill(item)` (weapons.cpp:202, 482, 630, 647). */
    fun getWeaponSkill(item: WeaponItem?): Int

    /** `Player::getAttackFactor()` (weapons.cpp:203, 483, 632, 652, 879, 918). */
    fun getAttackFactor(): Float

    /** `Player::getVocation()` (weapons.cpp:210, 638, 655, 892, 939). */
    fun getVocation(): Vocation

    /** `Player::getSkillLevel(SKILL_DISTANCE)` (weapons.cpp:731, 878, 917). */
    fun getSkillLevel(skill: skills_t): Int

    /** `Player::getAddAttackSkill()` (weapons.cpp:248, 592, 945). */
    fun getAddAttackSkill(): Boolean

    /** `Player::getLastAttackBlockType()` (weapons.cpp:592, 946). */
    fun getLastAttackBlockType(): BlockType

    /** `Player::getWeapon(ignoreAmmo)` (weapons.cpp:459, 691, 828, 871, 905). */
    fun getWeapon(ignoreAmmo: Boolean): WeaponItem?
}

/** Injected view of the `Item` accessors the weapon paths read. */
interface WeaponItem {
    /** `Item::getID()` (weapons.cpp:49, 380, 444, 478). */
    fun getID(): Int

    /** `Item::getAttack()` (weapons.cpp:201, 283, 457, 648, 900, 913). */
    fun getAttack(): Int

    /** `Item::getShootRange()` (weapons.cpp:188, 694, 699). */
    fun getShootRange(): Int

    /** `Item::getWeaponType()` (weapons.cpp:269, 278, 458, 598, 714, 827, 870, 904). */
    fun getWeaponType(): WeaponType_t

    /** `Item::getItemCount()` (weapons.cpp:442). */
    fun getItemCount(): Int

    /** `Item::getCharges()` (weapons.cpp:379). */
    fun getCharges(): Int

    /** `Item::getName()` (weapons.cpp:349, 363). */
    fun getName(): String

    /** `Item::getHitChance()` (weapons.cpp:829). */
    fun getHitChance(): Int

    /** `Item::getPerfectShotRange()` (weapons.cpp:717-720). */
    fun getPerfectShotRange(): Int

    /** `Item::getPerfectShotDamage()` (weapons.cpp:718-720). */
    fun getPerfectShotDamage(): Int
}

/** Injected view of the `Creature` target accessors the weapon paths read. */
interface WeaponTarget {
    /** `Creature::getPosition()` (weapons.cpp:139, 539, 709). */
    fun getPosition(): Position

    /** `Creature::getPlayer()` (weapons.cpp:885, 926). */
    fun getPlayer(): Any?
}

/**
 * `Weapon::getSkillType` out-parameters (weapons.cpp:591-623, 942-966).
 * Kotlin has no reference parameters, so the pair is returned.
 */
data class SkillType(
    val skill: skills_t,
    val skillpoint: Int,
)

/**
 * `Weapons` registry and static damage helpers
 * (src/items/weapons/weapons.hpp:34-55, weapons.cpp:37-100).
 *
 * The C++ singleton (`Weapons::getInstance`) is replaced by constructor
 * injection (AGENTS.md 1.1). `registerLuaEvent` keeps the exact Lua-event seam:
 * the weapon is stored and returned `true`; the Lua binding itself stays a
 * scripting-layer concern (AGENTS.md 1.4).
 */
class Weapons {
    private val weapons = linkedMapOf<Int, Weapon>()

    /** `Weapons::getWeapon` (weapons.cpp:44-54). */
    fun getWeapon(item: WeaponItem?): Weapon? {
        if (item == null) {
            return null
        }

        return weapons[item.getID()]
    }

    /** `Weapons::registerLuaEvent` (weapons.cpp:79-85). */
    fun registerLuaEvent(event: Weapon, fromXML: Boolean = false): Boolean {
        weapons[event.getID()] = event
        if (fromXML) {
            event.setFromXML(true)
        }
        return true
    }

    /** `Weapons::clear` (weapons.cpp:56-77). */
    fun clear(isFromXML: Boolean = false) {
        if (isFromXML) {
            weapons.values.removeAll { it.isFromXML() }
            return
        }

        weapons.clear()
    }

    companion object {
        /** `Weapons::getMaxMeleeDamage` (weapons.cpp:88-91). */
        fun getMaxMeleeDamage(attackSkill: Int, attackValue: Int): Int =
            ceil((attackSkill * (attackValue * 0.05)) + (attackValue * 0.5)).toInt()

        /** `Weapons::getMaxWeaponDamage` (weapons.cpp:94-100). */
        fun getMaxWeaponDamage(level: Int, attackSkill: Int, attackValue: Int, attackFactor: Float, isMelee: Boolean): Int =
            if (isMelee) {
                if (attackValue > 0) {
                    Math.round((0.085 * attackFactor * attackValue * attackSkill) + (level / 5)).toInt()
                } else {
                    0
                }
            } else {
                Math.round((0.09 * attackFactor * attackValue * attackSkill) + (level / 5)).toInt()
            }
    }
}

/**
 * `class Weapon` (src/items/weapons/weapons.hpp:59-267).
 *
 * The C++ pure-virtual combat entry points (`useWeapon`, `internalUseWeapon`,
 * `onUsedWeapon`, `useFist`) and the Lua script interface
 * (`loadScriptId`/`getScriptInterface`/`executeUseWeapon`) are scripting/game
 * seams and stay out of this items-layer port (PARITY GAP, AGENTS.md 1.4).
 * `m_combat` has no Kotlin counterpart yet because `Combat` was ported as a
 * stateless object; the chain callback seam is documented on [CombatParams].
 */
abstract class Weapon(
    protected val vocationIdProvider: (String) -> Int = { -1 },
    protected val random: Random = Random(),
    protected val itemTypeProvider: (Int) -> ItemType? = { null },
) {
    private var scriptId: Int = 0

    private var id: Int = 0
    private var level: Int = 0
    private var magLevel: Int = 0
    private var mana: Int = 0
    private var manaPercent: Int = 0
    private var health: Int = 0
    private var healthPercent: Int = 0
    private var soul: Int = 0
    private var wieldInfo: Int = 0
    private var chainSkillValue: Double = 0.0
    private var breakChance: Int = 0
    private var enabled: Boolean = true
    private var premium: Boolean = false
    private var wieldUnproperly: Boolean = false
    private var chainDisabled: Boolean = false
    private var vocationString: String = ""
    private var fromXML: Boolean = false

    /** `Weapon::action` (weapons.hpp:253); written by the item parse (`item_parse.cpp:1182-1186`). */
    internal var action: WeaponAction_t = WeaponAction_t.WEAPONACTION_NONE

    /** `Weapon::params` (weapons.hpp:254). */
    internal val params: CombatParams = CombatParams()

    /** `Weapon::weaponType` (weapons.hpp:255). */
    var weaponType: WeaponType_t = WeaponType_t.WEAPON_NONE

    /** `std::map<uint16_t, bool> vocWeaponMap` (weapons.hpp:256). */
    private val vocWeaponMap = linkedMapOf<Int, Boolean>()

    /** `Weapon::configureWeapon` (weapons.cpp:131-133). */
    open fun configureWeapon(it: ItemType) {
        id = it.id
    }

    /** `Weapon::interruptSwing` (weapons.hpp:63-65). */
    open fun interruptSwing(): Boolean = false

    // ---- required by playerWeaponCheck (weapons.cpp:135-185) -----------------

    /** `Weapon::getManaCost` (weapons.cpp:394-404). */
    private fun getManaCost(player: WeaponPlayer): Int {
        if (mana != 0) {
            return mana
        }

        if (manaPercent == 0) {
            return 0
        }

        return (player.getMaxMana() * manaPercent) / 100
    }

    /** `Weapon::getHealthCost` (weapons.cpp:406-416). */
    private fun getHealthCost(player: WeaponPlayer): Int {
        if (health != 0) {
            return health
        }

        if (healthPercent == 0) {
            return 0
        }

        return (player.getMaxHealth() * healthPercent) / 100
    }

    /** `Weapon::playerWeaponCheck` (weapons.cpp:135-185). */
    fun playerWeaponCheck(player: WeaponPlayer, target: WeaponTarget, shootRange: Int): Int {
        val range = shootRange + player.weaponProficiency().getStat(WeaponProficiencyBonus.ATTACK_RANGE).toInt()

        val playerPos = player.getPosition()
        val targetPos = target.getPosition()
        if (playerPos.z != targetPos.z) {
            return 0
        }

        if (maxOf(getDistanceX(playerPos, targetPos), getDistanceY(playerPos, targetPos)) > range) {
            return 0
        }

        if (!player.hasFlag(PlayerFlags_t.IgnoreWeaponCheck)) {
            if (!enabled) {
                return 0
            }

            if (player.getMana() < getManaCost(player)) {
                return 0
            }

            if (player.getHealth() < getHealthCost(player)) {
                return 0
            }

            if (player.getSoul() < soul) {
                return 0
            }

            if (isPremium() && !player.isPremium()) {
                return 0
            }

            if (vocWeaponMap.isNotEmpty() && !vocWeaponMap.containsKey(player.getVocationId())) {
                return 0
            }

            var damageModifier = 100
            if (player.getLevel() < getReqLevel()) {
                damageModifier = if (isWieldedUnproperly()) damageModifier / 2 else 0
            }

            if (player.getMagicLevel() < getReqMagLv()) {
                damageModifier = if (isWieldedUnproperly()) damageModifier / 2 else 0
            }
            return damageModifier
        }

        return 100
    }

    /** `Weapon::getCombatDamage` (weapons.cpp:197-218). */
    fun getCombatDamage(combat: CombatDamage, player: WeaponPlayer, item: WeaponItem, damageModifier: Int): CombatDamage {
        val level = player.getLevel()
        val elementalAttack = getElementDamageValue()
        val weaponAttack = maxOf(0, item.getAttack())
        val playerSkill = player.getWeaponSkill(item)
        val attackFactor = player.getAttackFactor()

        val totalAttack = elementalAttack + weaponAttack
        val weaponAttackProportion = weaponAttack.toDouble() / totalAttack.toDouble()

        val maxDamage = (Weapons.getMaxWeaponDamage(level, playerSkill, totalAttack, attackFactor, true) *
            player.getVocation().meleeDamageMultiplier * damageModifier / 100).toInt()
        val minDamage = level / 5
        val realDamage = normalRandom(minDamage, maxDamage, random)

        combat.primaryValue = (realDamage * weaponAttackProportion).toInt()
        combat.secondaryValue = (realDamage * (1 - weaponAttackProportion)).toInt()
        return combat
    }

    /** `Weapon::addVocWeaponMap` (weapons.cpp:487-492). */
    fun addVocWeaponMap(vocName: String) {
        val vocationId = vocationIdProvider(vocName)
        if (vocationId != -1) {
            vocWeaponMap[vocationId] = true
        }
    }

    /** `Weapon::getSkillType` out-parameter form (weapons.hpp:223-225). */
    internal open fun getSkillType(player: WeaponPlayer, item: WeaponItem): SkillType? = null

    abstract fun getWeaponDamage(player: WeaponPlayer, target: WeaponTarget, item: WeaponItem, maxDamage: Boolean = false): Int

    abstract fun getElementDamage(player: WeaponPlayer, target: WeaponTarget, item: WeaponItem): Int

    abstract fun getElementType(): CombatType

    abstract fun getElementDamageValue(): Int

    // ---- accessors (weapons.hpp:76-206) --------------------------------------

    fun getID(): Int = id

    fun setID(newId: Int) {
        id = newId
    }

    fun getReqLevel(): Int = level

    fun setRequiredLevel(reqlvl: Int) {
        level = reqlvl
    }

    fun getReqMagLv(): Int = magLevel

    fun setRequiredMagLevel(reqlvl: Int) {
        magLevel = reqlvl
    }

    fun isPremium(): Boolean = premium

    fun setNeedPremium(prem: Boolean) {
        premium = prem
    }

    fun isWieldedUnproperly(): Boolean = wieldUnproperly

    fun setWieldUnproperly(unproperly: Boolean) {
        wieldUnproperly = unproperly
    }

    fun getMana(): Int = mana

    fun setMana(m: Int) {
        mana = m
    }

    fun getManaPercent(): Int = manaPercent

    fun setManaPercent(m: Int) {
        manaPercent = m
    }

    fun getHealth(): Int = health

    fun setHealth(h: Int) {
        health = h
    }

    fun getHealthPercent(): Int = healthPercent

    fun setHealthPercent(m: Int) {
        healthPercent = m
    }

    fun getSoul(): Int = soul

    fun setSoul(s: Int) {
        soul = s
    }

    fun getBreakChance(): Int = breakChance

    fun setBreakChance(b: Int) {
        breakChance = b
    }

    fun isEnabled(): Boolean = enabled

    fun setIsEnabled(e: Boolean) {
        enabled = e
    }

    fun getWieldInfo(): Int = wieldInfo

    fun setWieldInfo(info: Int) {
        wieldInfo = wieldInfo or info
    }

    fun getVocationString(): String = vocationString

    fun setVocationString(str: String) {
        vocationString = str
    }

    fun setFromXML(newFromXML: Boolean) {
        fromXML = newFromXML
    }

    fun isFromXML(): Boolean = fromXML

    fun setChainSkillValue(value: Double) {
        chainSkillValue = value
    }

    fun getChainSkillValue(): Double = chainSkillValue

    fun setDisabledChain() {
        chainDisabled = true
    }

    fun isChainDisabled(): Boolean = chainDisabled

    fun getScriptId(): Int = scriptId

    fun setScriptId(newScriptId: Int) {
        scriptId = newScriptId
    }

    fun isLoadedScriptId(): Boolean = scriptId != 0
}

/**
 * `WeaponMelee` (weapons.hpp:269-288, weapons.cpp:511-664).
 */
class WeaponMelee(
    vocationIdProvider: (String) -> Int = { -1 },
    random: Random = Random(),
    itemTypeProvider: (Int) -> ItemType? = { null },
) : Weapon(vocationIdProvider, random, itemTypeProvider) {
    private var elementDamage: Int = 0
    private var elementType: CombatType = CombatType.NONE

    init {
        params.blockedByArmor = true
        params.blockedByShield = true
        params.combatType = CombatType.PHYSICALDAMAGE
    }

    /** `WeaponMelee::configureWeapon` (weapons.cpp:518-529). */
    override fun configureWeapon(it: ItemType) {
        val abilities = it.abilities
        if (abilities != null) {
            elementType = abilities.elementType
            elementDamage = abilities.elementDamage
            params.aggressive = true
            params.useCharges = true
        } else {
            elementType = CombatType.NONE
            elementDamage = 0
        }
        super.configureWeapon(it)
    }

    /** `WeaponMelee::getSkillType` (weapons.cpp:591-623). */
    override fun getSkillType(player: WeaponPlayer, item: WeaponItem): SkillType? {
        val skillpoint = if (player.getAddAttackSkill() && player.getLastAttackBlockType() != BlockType.IMMUNITY) 1 else 0

        return when (item.getWeaponType()) {
            WeaponType_t.WEAPON_FIST -> SkillType(skills_t.SKILL_FIST, skillpoint)
            WeaponType_t.WEAPON_SWORD -> SkillType(skills_t.SKILL_SWORD, skillpoint)
            WeaponType_t.WEAPON_CLUB -> SkillType(skills_t.SKILL_CLUB, skillpoint)
            WeaponType_t.WEAPON_AXE -> SkillType(skills_t.SKILL_AXE, skillpoint)
            else -> null
        }
    }

    /** `WeaponMelee::getElementDamage` (weapons.cpp:625-639). */
    override fun getElementDamage(player: WeaponPlayer, target: WeaponTarget, item: WeaponItem): Int {
        if (elementType == CombatType.NONE) {
            return 0
        }

        val attackSkill = player.getWeaponSkill(item)
        val attackValue = elementDamage
        val attackFactor = player.getAttackFactor()
        val level = player.getLevel()

        val maxValue = Weapons.getMaxWeaponDamage(level, attackSkill, attackValue, attackFactor, true)
        val minValue = level / 5

        return -normalRandom(minValue, (maxValue * player.getVocation().meleeDamageMultiplier).toInt(), random)
    }

    /** `WeaponMelee::getElementDamageValue` (weapons.cpp:641-643). */
    override fun getElementDamageValue(): Int = elementDamage.toShort().toInt()

    /** `WeaponMelee::getElementType` (weapons.hpp:279-281). */
    override fun getElementType(): CombatType = elementType

    /** `WeaponMelee::getWeaponDamage` (weapons.cpp:645-664). */
    override fun getWeaponDamage(player: WeaponPlayer, target: WeaponTarget, item: WeaponItem, maxDamage: Boolean): Int {
        val proficiencyAttack = player.weaponProficiency().getStat(WeaponProficiencyBonus.ATTACK_DAMAGE).toInt()
        val attackSkill = player.getWeaponSkill(item)
        val physicalAttack = maxOf(0, item.getAttack())
        val elementalAttack = getElementDamageValue()
        val combinedAttack = physicalAttack + elementalAttack + proficiencyAttack

        val attackFactor = player.getAttackFactor()
        val level = player.getLevel()

        val maxValue = (Weapons.getMaxWeaponDamage(level, attackSkill, combinedAttack, attackFactor, true) *
            player.getVocation().meleeDamageMultiplier).toInt()

        val minValue = if (physicalAttack > 0) level / 5 else 0

        if (maxDamage) {
            return -maxValue
        }

        return -normalRandom(minValue, maxValue, random)
    }
}

/**
 * `WeaponDistance` (weapons.hpp:290-313, weapons.cpp:666-966).
 */
class WeaponDistance(
    vocationIdProvider: (String) -> Int = { -1 },
    random: Random = Random(),
    itemTypeProvider: (Int) -> ItemType? = { null },
) : Weapon(vocationIdProvider, random, itemTypeProvider) {
    private var elementType: CombatType = CombatType.NONE
    private var elementDamage: Int = 0

    init {
        params.blockedByArmor = true
        params.combatType = CombatType.PHYSICALDAMAGE
    }

    /** `WeaponDistance::configureWeapon` (weapons.cpp:672-685). */
    override fun configureWeapon(it: ItemType) {
        params.distanceEffect = it.shootType
        val abilities = it.abilities
        if (abilities != null) {
            elementType = abilities.elementType
            elementDamage = abilities.elementDamage
            params.aggressive = true
            params.useCharges = true
        } else {
            elementType = CombatType.NONE
            elementDamage = 0
        }

        super.configureWeapon(it)
    }

    /** `WeaponDistance::interruptSwing` (weapons.hpp:295-297). */
    override fun interruptSwing(): Boolean = true

    /** `WeaponDistance::getElementDamage` (weapons.cpp:864-893). */
    override fun getElementDamage(player: WeaponPlayer, target: WeaponTarget, item: WeaponItem): Int {
        if (elementType == CombatType.NONE) {
            return 0
        }

        var attackValue = elementDamage
        if (item.getWeaponType() == WeaponType_t.WEAPON_AMMO) {
            val weapon = player.getWeapon(true)
            if (weapon != null) {
                attackValue += item.getAttack()
                attackValue += weapon.getAttack()
            }
        }

        val attackSkill = player.getSkillLevel(skills_t.SKILL_DISTANCE)
        val attackFactor = player.getAttackFactor()

        var minValue = player.getLevel() / 5
        val maxValue = Math.round((0.09f * attackFactor) * attackSkill * attackValue + minValue) / 2

        if (target.getPlayer() != null) {
            minValue /= 4
        } else {
            minValue /= 2
        }

        return -normalRandom(minValue, (maxValue * player.getVocation().distDamageMultiplier).toInt(), random)
    }

    /** `WeaponDistance::getElementDamageValue` (weapons.cpp:895-897). */
    override fun getElementDamageValue(): Int = elementDamage.toShort().toInt()

    /** `WeaponDistance::getElementType` (weapons.hpp:303-305). */
    override fun getElementType(): CombatType = elementType

    /** `WeaponDistance::getWeaponDamage` (weapons.cpp:899-940). */
    override fun getWeaponDamage(player: WeaponPlayer, target: WeaponTarget, item: WeaponItem, maxDamage: Boolean): Int {
        var attackValue = item.getAttack()
        attackValue += player.weaponProficiency().getStat(WeaponProficiencyBonus.ATTACK_DAMAGE).toInt()
        var hasElement = false

        if (item.getWeaponType() == WeaponType_t.WEAPON_AMMO) {
            val weapon = player.getWeapon(true)
            if (weapon != null) {
                val itemType = itemTypeProvider(item.getID())
                val abilities = itemType?.abilities
                if (abilities != null && abilities.elementDamage != 0) {
                    attackValue += abilities.elementDamage
                    hasElement = true
                }

                attackValue += weapon.getAttack()
            }
        }

        val attackSkill = player.getSkillLevel(skills_t.SKILL_DISTANCE)
        val attackFactor = player.getAttackFactor()

        var minValue = player.getLevel() / 5
        var maxValue = Math.round((0.09f * attackFactor) * attackSkill * attackValue + minValue)
        if (maxDamage) {
            return -maxValue
        }

        if (target.getPlayer() != null) {
            if (hasElement) {
                minValue /= 4
            } else {
                minValue /= 2
            }
        } else {
            if (hasElement) {
                maxValue /= 2
                minValue /= 2
            }
        }

        return -normalRandom(minValue, (maxValue * player.getVocation().distDamageMultiplier).toInt(), random)
    }

    /** `WeaponDistance::getSkillType` (weapons.cpp:942-966). */
    override fun getSkillType(player: WeaponPlayer, item: WeaponItem): SkillType {
        val skillpoint = if (player.getAddAttackSkill()) {
            when (player.getLastAttackBlockType()) {
                BlockType.NONE -> 2
                BlockType.DEFENSE, BlockType.ARMOR -> 1
                else -> 0
            }
        } else {
            0
        }
        return SkillType(skills_t.SKILL_DISTANCE, skillpoint)
    }
}

/**
 * `WeaponWand` (weapons.hpp:315-345, weapons.cpp:968-983).
 */
class WeaponWand(
    vocationIdProvider: (String) -> Int = { -1 },
    random: Random = Random(),
    itemTypeProvider: (Int) -> ItemType? = { null },
) : Weapon(vocationIdProvider, random, itemTypeProvider) {
    private var minChange: Int = 0
    private var maxChange: Int = 0

    /** `WeaponWand::configureWeapon` (weapons.cpp:970-975). */
    override fun configureWeapon(it: ItemType) {
        params.distanceEffect = it.shootType
        it.combatType = params.combatType
        it.maxHitChance = (minChange + maxChange) / 2
        super.configureWeapon(it)
    }

    /** `WeaponWand::setMinChange` (weapons.hpp:330-332). */
    fun setMinChange(change: Int) {
        minChange = change
    }

    /** `WeaponWand::setMaxChange` (weapons.hpp:334-336). */
    fun setMaxChange(change: Int) {
        maxChange = change
    }

    /** `WeaponWand::getWeaponDamage` (weapons.cpp:977-979). */
    override fun getWeaponDamage(player: WeaponPlayer, target: WeaponTarget, item: WeaponItem, maxDamage: Boolean): Int =
        if (maxDamage) -maxChange else -normalRandom(minChange, maxChange, random)

    /** `WeaponWand::getElementDamage` (weapons.hpp:322-325). */
    override fun getElementDamage(player: WeaponPlayer, target: WeaponTarget, item: WeaponItem): Int = 0

    /** `WeaponWand::getElementType` (weapons.hpp:326-328). */
    override fun getElementType(): CombatType = params.combatType

    /** `WeaponWand::getElementDamageValue` (weapons.cpp:981-983). */
    override fun getElementDamageValue(): Int = 0
}
