package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.FightMode
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.systems.wheel.WheelStat
import kotlin.math.ceil

/**
 * `Player::getShieldAndWeapon`'s two `shared_ptr<Item>&` out-parameters
 * (player.cpp:721-747) folded into a value. Either member is `null` when the
 * corresponding hand is empty.
 */
data class ShieldAndWeapon(val shield: Item?, val weapon: Item?)

/**
 * Player equipment/weapon skill helpers
 * (src/creatures/players/player.cpp:396-870, 3887-3897, 5949-5969,
 * 12923-12941) as extensions over the port's [Player].
 *
 * The port's `Player` already owns the inventory (`getInventoryItem`), the
 * skills (`getSkillLevel`), the fight mode and the vocation registry, so these
 * helpers need no parallel state. The dependencies Canary resolves through
 * globals or unported components stay explicit seams:
 *
 * - `weaponProficiency().getStat(WEAPON_SHIELD_MODIFIER/DEFENSE_BONUS)` and the
 *   wheel major-state defense (`m_wheelPlayer.getMajorStatConditional`) are the
 *   `proficiencyDefense` / `wheelDefense` parameters;
 * - `m_wheelPlayer.calculateMitigation` is the [getMitigation] lambda;
 * - the weapon-proficiency skill (`getWeaponSkill`) reads the port's skill
 *   table like Canary does.
 */
private val ARMOR_SLOTS = listOf(
    Slots_t.CONST_SLOT_HEAD,
    Slots_t.CONST_SLOT_NECKLACE,
    Slots_t.CONST_SLOT_ARMOR,
    Slots_t.CONST_SLOT_LEGS,
    Slots_t.CONST_SLOT_FEET,
    Slots_t.CONST_SLOT_RING,
    Slots_t.CONST_SLOT_AMMO,
)

/** `Player::getEquippedItems` valid slot order (player.cpp:5950-5960). */
private val EQUIPPED_SLOTS = listOf(
    Slots_t.CONST_SLOT_HEAD,
    Slots_t.CONST_SLOT_NECKLACE,
    Slots_t.CONST_SLOT_BACKPACK,
    Slots_t.CONST_SLOT_ARMOR,
    Slots_t.CONST_SLOT_RIGHT,
    Slots_t.CONST_SLOT_LEFT,
    Slots_t.CONST_SLOT_LEGS,
    Slots_t.CONST_SLOT_FEET,
    Slots_t.CONST_SLOT_RING,
)

/** `uint16_t` wrap of the C++ `getAttackSkill`/`getDistanceAttackSkill` casts. */
private const val UINT16_MAX = 0xFFFF

/** `Player::getWeaponType` (player.cpp:466-472). */
fun Player.getWeaponType(): WeaponType_t = getWeapon()?.getWeaponType() ?: WeaponType_t.WEAPON_NONE

/** `Player::getWeaponSkill` (player.cpp:474-514). */
fun Player.getWeaponSkill(item: Item?): Int {
    if (item == null) {
        return getSkillLevel(skills_t.SKILL_FIST)
    }

    return when (item.getWeaponType()) {
        WeaponType_t.WEAPON_FIST -> getSkillLevel(skills_t.SKILL_FIST)
        WeaponType_t.WEAPON_SWORD -> getSkillLevel(skills_t.SKILL_SWORD)
        WeaponType_t.WEAPON_CLUB -> getSkillLevel(skills_t.SKILL_CLUB)
        WeaponType_t.WEAPON_AXE -> getSkillLevel(skills_t.SKILL_AXE)
        WeaponType_t.WEAPON_MISSILE, WeaponType_t.WEAPON_DISTANCE -> getSkillLevel(skills_t.SKILL_DISTANCE)
        else -> 0
    }
}

/** `Player::getDistanceAttackSkill` (player.cpp:516-520). */
fun Player.getDistanceAttackSkill(attackSkill: Int, weaponAttack: Int): Int {
    val skillFactor = (attackSkill + 4) / 28.0
    return (weaponAttack * skillFactor - weaponAttack).toInt() and UINT16_MAX
}

/** `Player::getAttackSkill` (player.cpp:522-566). */
fun Player.getAttackSkill(item: Item?): Int {
    if (item == null) {
        return getSkillLevel(skills_t.SKILL_FIST)
    }

    val attackSkill = when (item.getWeaponType()) {
        WeaponType_t.WEAPON_FIST -> getSkillLevel(skills_t.SKILL_FIST)
        WeaponType_t.WEAPON_SWORD -> getSkillLevel(skills_t.SKILL_SWORD)
        WeaponType_t.WEAPON_CLUB -> getSkillLevel(skills_t.SKILL_CLUB)
        WeaponType_t.WEAPON_AXE -> getSkillLevel(skills_t.SKILL_AXE)
        WeaponType_t.WEAPON_MISSILE, WeaponType_t.WEAPON_DISTANCE -> getSkillLevel(skills_t.SKILL_DISTANCE)
        else -> 0
    }

    val skillFactor = (attackSkill + 4) / 28.0
    val weaponAttack = item.getAttack()
    return (weaponAttack * skillFactor - weaponAttack).toInt() and UINT16_MAX
}

/** `Player::getWeaponSkillId` (player.cpp:568-586). */
fun Player.getWeaponSkillId(item: Item): Int = when (item.getWeaponType()) {
    WeaponType_t.WEAPON_SWORD -> 8
    WeaponType_t.WEAPON_CLUB -> 9
    WeaponType_t.WEAPON_AXE -> 10
    else -> 11
}

/** `Player::hasQuiverEquipped` (player.cpp:426-429). */
fun Player.hasQuiverEquipped(): Boolean {
    val quiver = getInventoryItem(Slots_t.CONST_SLOT_RIGHT) ?: return false
    return quiver.isQuiver() && quiver.getContainer() != null
}

/** `Player::hasWeaponDistanceEquipped` (player.cpp:431-434). */
fun Player.hasWeaponDistanceEquipped(): Boolean =
    getInventoryItem(Slots_t.CONST_SLOT_LEFT)?.getWeaponType() == WeaponType_t.WEAPON_DISTANCE

/**
 * `Player::getQuiverAmmoOfType` (player.cpp:436-451). The `Item::items[id]`
 * lookup of the ammo minimum level reads the item's own port [ItemType].
 */
fun Player.getQuiverAmmoOfType(itemType: ItemType): Item? {
    if (!hasQuiverEquipped()) {
        return null
    }

    val quiver = getInventoryItem(Slots_t.CONST_SLOT_RIGHT) ?: return null
    val container = quiver.getContainer() ?: return null
    for (ammoItem in container.getItemList()) {
        if (ammoItem.getAmmoType().value == itemType.ammoType) {
            if (level.toLong() >= (ammoItem.type?.minReqLevel ?: 0L)) {
                return ammoItem
            }
        }
    }
    return null
}

/** `Player::getArmor` (player.cpp:658-671). */
fun Player.getArmor(): Int {
    var armor = 0
    for (slot in ARMOR_SLOTS) {
        armor += getInventoryItem(slot)?.getArmor() ?: 0
    }
    val armorMultiplier = vocations?.getVocation(vocationId)?.armorMultiplier?.toInt() ?: 1
    return armor * armorMultiplier
}

/** `Player::getMantra` (player.cpp:671-694). */
fun Player.getMantra(): Int {
    var mantra = 0
    for (slot in ARMOR_SLOTS) {
        mantra += getInventoryItem(slot)?.getMantra() ?: 0
    }
    return mantra
}

/**
 * `Player::getPartyMantra` (player.cpp:696-710). `getBuff(BUFF_MANTRA)`
 * (player.cpp:706) is the `buffMantra` parameter; `0` means no buff, exactly
 * like the C++ `if (buff)` guard.
 */
fun Player.getPartyMantra(buffMantra: Int = 0): Int {
    val itemMantra = getMantra()
    if (itemMantra > 0) {
        return itemMantra
    }

    if (getParty() != null && buffMantra != 0) {
        return buffMantra - 100
    }

    return 0
}

/**
 * `Player::updatePartyMantra` (player.cpp:713-719). `party->updateMantraHolder()`
 * is not ported on the grouping [org.opentibia.canary.creatures.grouping.Party],
 * so the call is the [updateMantraHolder] seam.
 */
fun Player.updatePartyMantra(updateMantraHolder: () -> Unit = {}) {
    if (getPlayerVocationEnum() == Vocation.VOCATION_MONK_CIP && getParty() != null) {
        updateMantraHolder()
    }
}

/** `Player::getShieldAndWeapon` (player.cpp:721-747). */
fun Player.getShieldAndWeapon(): ShieldAndWeapon {
    var shield: Item? = null
    var weapon: Item? = null

    for (slot in listOf(Slots_t.CONST_SLOT_RIGHT, Slots_t.CONST_SLOT_LEFT)) {
        val item = getInventoryItem(slot) ?: continue
        when (item.getWeaponType()) {
            WeaponType_t.WEAPON_NONE -> Unit
            WeaponType_t.WEAPON_SHIELD -> {
                val currentShield = shield
                if (currentShield == null || item.getDefense() > currentShield.getDefense()) {
                    shield = item
                }
            }
            else -> weapon = item
        }
    }

    return ShieldAndWeapon(shield, weapon)
}

/**
 * `Player::getDefense` (player.cpp:776-815). The wheel Combat Mastery defense
 * (`m_wheelPlayer.getMajorStatConditional`, player.cpp:791-793) and the
 * weapon-proficiency defense bonuses (player.cpp:797-798) are the
 * [wheelDefense] / [proficiencyDefense] parameters.
 */
fun Player.getDefense(
    sendToClient: Boolean = false,
    wheelDefense: Int = 0,
    proficiencyDefense: Double = 0.0,
): Int {
    var defenseSkill = getSkillLevel(skills_t.SKILL_FIST)
    var defenseValue = 7
    val shieldAndWeapon = getShieldAndWeapon()
    val shield = shieldAndWeapon.shield
    val weapon = shieldAndWeapon.weapon

    if (weapon != null) {
        defenseValue = weapon.getDefense() + weapon.getExtraDefense()
        defenseSkill = getWeaponSkill(weapon)
    }

    if (shield != null) {
        defenseValue = if (weapon != null) {
            shield.getDefense() + weapon.getExtraDefense()
        } else {
            shield.getDefense()
        }
        if (shield.getDefense() > 0) {
            defenseValue += wheelDefense
        }
        defenseSkill = getSkillLevel(skills_t.SKILL_SHIELD)
    }

    defenseValue += proficiencyDefense.toInt()

    if (defenseSkill == 0) {
        return when (fightMode) {
            FightMode.Attack, FightMode.Balanced -> 1
            FightMode.Defense -> 2
        }
    }

    val defenseScalingFactor = when {
        shield != null -> 0.16f
        weapon != null && weapon.getDefense() > 0 -> 0.146f
        else -> 0.15f
    }
    val defenseMultiplier = vocations?.getVocation(vocationId)?.defenseMultiplier ?: 1.0f

    val damage = (defenseSkill / 4.0 + 2.23) * defenseValue * getDefenseFactor(sendToClient) *
        defenseScalingFactor * defenseMultiplier
    return damage.toInt()
}

/** `Player::getDefenseEquipment` (player.cpp:817-838). */
fun Player.getDefenseEquipment(wheelDefense: Int = 0, proficiencyDefense: Double = 0.0): Int {
    var defenseValue = 6
    val shieldAndWeapon = getShieldAndWeapon()
    val shield = shieldAndWeapon.shield
    val weapon = shieldAndWeapon.weapon

    if (weapon != null) {
        defenseValue = weapon.getDefense() + weapon.getExtraDefense()
    }

    if (shield != null) {
        defenseValue = if (weapon != null) {
            shield.getDefense() + weapon.getExtraDefense()
        } else {
            shield.getDefense()
        }
        if (shield.getDefense() > 0) {
            defenseValue += wheelDefense
        }
    }

    defenseValue += proficiencyDefense.toInt()
    return defenseValue and UINT16_MAX
}

/** `Player::getAttackFactor` (player.cpp:840-851). */
fun Player.getAttackFactor(): Float = when (fightMode) {
    FightMode.Attack -> 1.0f
    FightMode.Balanced -> 0.75f
    FightMode.Defense -> 0.5f
}

/**
 * `Player::getDefenseFactor` (player.cpp:853-870). [attackSpeed] replaces
 * `Player::getAttackSpeed()` and [lastAttack]/[now] replace the `lastAttack`
 * field and `OTSYS_TIME()` so the cooldown branch stays testable; the defaults
 * reproduce Canary's never-attacked `lastAttack == 0` behavior.
 */
fun Player.getDefenseFactor(
    sendToClient: Boolean = false,
    attackSpeed: Int = getAttackSpeed(),
    lastAttack: Long = 0L,
    now: Long = System.currentTimeMillis(),
): Float = when (fightMode) {
    FightMode.Attack -> if (sendToClient) 0.5f else if ((now - lastAttack) < attackSpeed) 0.5f else 1.0f
    FightMode.Balanced -> if (sendToClient) 0.75f else if ((now - lastAttack) < attackSpeed) 0.75f else 1.0f
    FightMode.Defense -> 1.0f
}

/**
 * `Player::getDodgeChance` (player.cpp:12923-12941). The `uint16_t` casts of
 * the C++ `static_cast` are reproduced with 16-bit wrapping.
 */
fun Player.getDodgeChance(): Int {
    val playerArmor = getInventoryItem(Slots_t.CONST_SLOT_ARMOR)
    val wheelDodge = wheel().getStat(WheelStat.DODGE)
    if (playerArmor == null || playerArmor.getTier() == 0) {
        return wheelDodge
    }

    var chance = (playerArmor.getDodgeChance() * 100).toInt() and UINT16_MAX

    val playerBoots = getInventoryItem(Slots_t.CONST_SLOT_FEET)
    if (playerBoots != null && playerBoots.getTier() > 0) {
        val amplificationChance = playerBoots.getAmplificationChance() / 100.0
        val amplValue = chance * amplificationChance
        chance += amplValue.toInt() and UINT16_MAX
    }

    chance += wheelDodge
    return chance and UINT16_MAX
}

/** `Player::hasShield` (player.cpp:3887-3897). */
fun Player.hasShield(): Boolean {
    val itemLeft = getInventoryItem(Slots_t.CONST_SLOT_LEFT)
    if (itemLeft != null && itemLeft.getWeaponType() == WeaponType_t.WEAPON_SHIELD) {
        return true
    }

    val itemRight = getInventoryItem(Slots_t.CONST_SLOT_RIGHT)
    return itemRight != null && itemRight.getWeaponType() == WeaponType_t.WEAPON_SHIELD
}

/** `Player::getEquippedItems` (player.cpp:5949-5969). */
fun Player.getEquippedItems(): List<Item> = EQUIPPED_SLOTS.mapNotNull { slot -> getInventoryItem(slot) }

/** `Player::getAttackSpeed` (player.cpp:7346-7348); `0` without a vocation. */
fun Player.getAttackSpeed(): Int = vocations?.getVocation(vocationId)?.getAttackSpeed() ?: 0

/**
 * `Player::calculateFlatDamageHealing` (player.cpp:588-610): the tiered
 * level-scaling healing baseline, capped at the `uint16_t` maximum.
 */
fun Player.calculateFlatDamageHealing(): Int {
    var previousLevelsAggregatedBaseline = 0.0
    var currentLevelBaseline = 0
    var currentLevelFactor = 1.0 / 5.0

    var threshold = 500
    var thresholdStep = 600
    var tierIndex = 1

    while (level >= threshold) {
        currentLevelBaseline = threshold
        currentLevelFactor = 1.0 / (5.0 + tierIndex)
        previousLevelsAggregatedBaseline += threshold * (1.0 / (5.0 + tierIndex - 1))

        ++tierIndex
        threshold += thresholdStep
        thresholdStep += 100
    }

    val computed = ceil(previousLevelsAggregatedBaseline + (level - currentLevelBaseline) * currentLevelFactor).toInt()
    return minOf(computed, UINT16_MAX)
}

/** `Player::getMitigation` (player.cpp:750-752) over the wheel mitigation seam. */
fun Player.getMitigation(wheelMitigation: () -> Float = { 0f }): Float = wheelMitigation()

/** `Player::getCombatTacticsMitigation` (player.cpp:754-772). */
fun Player.getCombatTacticsMitigation(): Double = when (fightMode) {
    FightMode.Attack -> 0.8
    FightMode.Balanced -> 1.0
    FightMode.Defense -> 1.2
}
