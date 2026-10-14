package org.opentibia.canary.creatures.players

import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.items.Ammo_t
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.systems.proficiency.WeaponProficiencyPerfectShotBonus
import org.opentibia.canary.utils.Tools

/**
 * Player equipped-item combat bonus queries
 * (src/creatures/players/player.cpp:1051-1069, 7560-7600, 7683-7723).
 *
 * Canary keeps the `cleavePercent` and `perfectShot` fields directly on
 * `Player` (player.hpp:1901-1902) and inherits the `reflectPercent`/`reflectFlat`
 * arrays from `Creature` (creature.hpp:911-912). The state holder mirrors the
 * port's `PlayerMagicAffinityState` composition seam; every external dependency
 * is a constructor seam per AGENTS.md 1.1:
 * [getEquippedItems] replaces `Player::getEquippedItems` (player.cpp:5949),
 * [getCharges] replaces `Item::getCharges` (src/items/item.hpp:107),
 * [transformItemCharges] replaces
 * `g_game().transformItem(item, item->getID(), charges - 1)` (player.cpp:1065,
 * 7588, 7697, 7718), [perfectShotBonus] replaces
 * `WeaponProficiency::getPerfectShotBonus` (weapon_proficiency.cpp:1201) and
 * [baseReflectPercent]/[baseReflectFlat] replace the unported Creature arrays.
 *
 * The methods keep the exact `Player::` names of the C++ symbols they
 * transcribe, so every behavior is auditable against the C++ line references.
 */
class PlayerCombatBonusState(
    /** `Player::getEquippedItems()` (player.cpp:5949). */
    private val getEquippedItems: () -> List<Item> = { emptyList() },
    /** `Item::getCharges()` (src/items/item.hpp:107). */
    private val getCharges: (Item) -> Int = { 0 },
    /** `g_game().transformItem(item, item->getID(), charges - 1)`. */
    private val transformItemCharges: (Item) -> Unit = {},
    /** `WeaponProficiency::getPerfectShotBonus()` (weapon_proficiency.cpp:1201). */
    private val perfectShotBonus: () -> WeaponProficiencyPerfectShotBonus? = { null },
    /** `Creature::reflectPercent[combatTypeToIndex(combat)]` (creature.hpp:911). */
    private val baseReflectPercent: (Int) -> Double = { 0.0 },
    /** `Creature::reflectFlat[combatTypeToIndex(combat)]` (creature.hpp:912). */
    private val baseReflectFlat: (Int) -> Int = { 0 },
) {

    /** `Player::cleavePercent` (player.hpp:1901). */
    private var cleavePercent = 0

    /** `Player::perfectShot` (player.hpp:1902), keyed by range. */
    private val perfectShot = HashMap<Int, Int>()

    /** `Player::getCleavePercent` (player.cpp:1051-1069). */
    fun getCleavePercent(useCharges: Boolean = false): Int {
        var result = cleavePercent
        for (item in getEquippedItems()) {
            val abilities = item.type?.abilities ?: continue
            val cleave = abilities.cleavePercent
            if (cleave != 0) {
                result += cleave
                if (useCharges && getCharges(item) != 0) {
                    transformItemCharges(item)
                }
            }
        }

        return result
    }

    /** `Player::setCleavePercent` (player.cpp:7560-7562): `max(0, ...)`. */
    fun setCleavePercent(value: Int) {
        cleavePercent = maxOf(0, cleavePercent + value)
    }

    /** `Player::getPerfectShotDamage` (player.cpp:7564-7593). */
    fun getPerfectShotDamage(range: Int, useCharges: Boolean = false): Int {
        var result = perfectShot[range] ?: 0

        val bonus = perfectShotBonus()
        if (bonus != null && bonus.range == range) {
            result += Math.round(bonus.damage).toInt()
        }

        for (item in getEquippedItems()) {
            val abilities = item.type?.abilities ?: continue
            if (abilities.perfectShotRange == 0 || abilities.perfectShotDamage == 0) {
                continue
            }

            if (abilities.perfectShotRange == range) {
                result += abilities.perfectShotDamage
                if (useCharges && getCharges(item) != 0) {
                    transformItemCharges(item)
                }
            }
        }

        return result
    }

    /** `Player::setPerfectShotDamage` (player.cpp:7595-7600). */
    fun setPerfectShotDamage(range: Int, damage: Int) {
        val actualDamage = getPerfectShotDamage(range)
        val aboveZero = actualDamage != 0
        val newDamage = actualDamage + damage
        if (newDamage == 0 && aboveZero) {
            perfectShot.remove(range)
        } else {
            perfectShot[range] = newDamage
        }
    }

    /** `Player::getReflectPercent` (player.cpp:7683-7702). */
    fun getReflectPercent(combat: CombatType, useCharges: Boolean = false): Double {
        val combatIndex = Tools.combatTypeToIndex(combat)
        var result = baseReflectPercent(combatIndex)

        for (item in getEquippedItems()) {
            val abilities = item.type?.abilities ?: continue
            val reflectPercent = abilities.reflectPercent[combatIndex].toDouble()
            if (reflectPercent != 0.0) {
                result += reflectPercent
                if (useCharges && getCharges(item) != 0) {
                    transformItemCharges(item)
                }
            }
        }

        return result
    }

    /** `Player::getReflectFlat` (player.cpp:7704-7723). */
    fun getReflectFlat(combat: CombatType, useCharges: Boolean = false): Int {
        val combatIndex = Tools.combatTypeToIndex(combat)
        var result = baseReflectFlat(combatIndex)

        for (item in getEquippedItems()) {
            val abilities = item.type?.abilities ?: continue
            val reflectFlat = abilities.reflectFlat[combatIndex]
            if (reflectFlat != 0) {
                result += reflectFlat
                if (useCharges && getCharges(item) != 0) {
                    transformItemCharges(item)
                }
            }
        }

        return result
    }
}

/**
 * `Player::getDamageAccuracy` (player.cpp:874-899): the per-distance accuracy
 * table used by the distance combat pipeline. Bolt and arrow ammo produce the
 * six-entry table capped at 90, every other ammo the seven-entry table capped
 * at 75. The C++ float literals are computed in `float` before the `double`
 * comparison, so the port promotes the same `Float` products.
 */
fun Player.getDamageAccuracy(
    /** `it` (player.cpp:874). */
    it: ItemType,
    /** `getSkillLevel(SKILL_DISTANCE)` (player.cpp:876). */
    distanceSkill: Int = getSkillLevel(skills_t.SKILL_DISTANCE),
): List<Double> {
    val accuracy = mutableListOf<Double>()

    if (it.ammoType == Ammo_t.AMMO_BOLT.value || it.ammoType == Ammo_t.AMMO_ARROW.value) {
        accuracy.add(minOf(90.0, (1.20f * (distanceSkill + 1)).toDouble()))
        accuracy.add(minOf(90.0, (3.20f * distanceSkill).toDouble()))
        accuracy.add(minOf(90.0, (2.00f * distanceSkill).toDouble()))
        accuracy.add(minOf(90.0, (1.55f * distanceSkill).toDouble()))
        accuracy.add(minOf(90.0, (1.20f * (distanceSkill + 1)).toDouble()))
        accuracy.add(minOf(90.0, distanceSkill.toDouble()))
    } else {
        accuracy.add(minOf(75.0, (distanceSkill + 1).toDouble()))
        accuracy.add(minOf(75.0, (2.40f * (distanceSkill + 8)).toDouble()))
        accuracy.add(minOf(75.0, (1.55f * (distanceSkill + 6)).toDouble()))
        accuracy.add(minOf(75.0, (1.25f * (distanceSkill + 3)).toDouble()))
        accuracy.add(minOf(75.0, (distanceSkill + 1).toDouble()))
        accuracy.add(minOf(75.0, (0.80f * (distanceSkill + 3)).toDouble()))
        accuracy.add(minOf(75.0, (0.70f * (distanceSkill + 2)).toDouble()))
    }

    return accuracy
}
