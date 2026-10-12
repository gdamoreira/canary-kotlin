package org.opentibia.canary.combat

import java.util.Random

/**
 * Faithful port of the deterministic damage core of Canary `Combat`
 * (src/creatures/combat/combat.cpp) plus the creature-side block/mitigation
 * math it drives.
 *
 * Canary has no `Combat::getMaxDamage` / `Combat::calculateDamage`; the real
 * max-damage helpers live on `Weapons` (see [Weapons]) and formula resolution
 * lives in `Combat::getCombatDamage`. This object only ports functions that
 * exist in Canary, with the `Player`/`Creature` state replaced by explicit
 * input parameters (task constraint: do not edit Player.kt/Creature.kt).
 *
 * Sources:
 *  - `Combat::getLevelFormula`: src/creatures/combat/combat.cpp:135-152
 *  - `Combat::getCombatDamage`: src/creatures/combat/combat.cpp:154-231
 *  - `Creature::blockHit` (armor/defense branch): src/creatures/creature.cpp:944-1009
 *  - `Creature::mitigateDamage`: src/creatures/creature.cpp:910-922
 *  - `Combat::applyExtensions` (critical/fatal core): src/creatures/combat/combat.cpp:2647-2781
 */
object Combat {
    /**
     * `player->getLevel() * 2 + (player->getMagicLevel() + specializedMagicLevel) * 3`
     * (src/creatures/combat/combat.cpp:150-151).
     *
     * The Wheel of Destiny "Runic Mastery" branch (combat.cpp:141-148) depends on
     * spell registries and is intentionally not modelled here.
     */
    fun getLevelFormula(level: Int, magicLevel: Int, specializedMagicLevel: Int): Int =
        level * 2 + (magicLevel + specializedMagicLevel) * 3

    /**
     * Reduced port of `Combat::getCombatDamage` (src/creatures/combat/combat.cpp:154-231).
     *
     * Resolves the three damage formulas Canary supports:
     *  - `COMBAT_FORMULA_DAMAGE`: `normal_random(mina, maxa)` (combat.cpp:181-185)
     *  - `COMBAT_FORMULA_LEVELMAGIC`: `normal_random(levelFormula * mina + minb, levelFormula * maxa + maxb)`
     *    (combat.cpp:193-198)
     *  - `COMBAT_FORMULA_SKILL`: `normal_random(minb, weaponDamage * maxa + maxb)` with the weapon
     *    element as secondary, or `normal_random(minb, maxb)` without a weapon (combat.cpp:199-222)
     *
     * Callback/wheel/monk-elemental-bond branches remain in their owning systems.
     */
    fun getCombatDamage(
        formulaType: FormulaType,
        random: Random,
        mina: Double = 0.0,
        minb: Double = 0.0,
        maxa: Double = 0.0,
        maxb: Double = 0.0,
        combatType: CombatType = CombatType.NONE,
        origin: CombatOrigin = CombatOrigin.NONE,
        levelFormula: Int = 0,
        hasWeapon: Boolean = false,
        weaponDamage: Int = 0,
        elementType: CombatType = CombatType.NONE,
        elementValue: Int = 0,
        instantSpellName: String = "",
        runeSpellName: String = "",
    ): CombatDamage {
        val damage = CombatDamage()
        damage.origin = origin
        damage.primaryType = combatType
        damage.instantSpellName = instantSpellName
        damage.runeSpellName = runeSpellName

        when (formulaType) {
            FormulaType.DAMAGE -> {
                damage.primaryValue = normalRandom(mina.toInt(), maxa.toInt(), random)
            }

            FormulaType.LEVELMAGIC -> {
                val min = (levelFormula * mina + minb).toInt()
                val max = (levelFormula * maxa + maxb).toInt()
                damage.primaryValue = normalRandom(min, max, random)
            }

            FormulaType.SKILL -> {
                if (hasWeapon) {
                    val max = (weaponDamage * maxa + maxb).toInt()
                    damage.primaryValue = normalRandom(minb.toInt(), max, random)
                    damage.secondaryType = elementType
                    damage.secondaryValue = elementValue
                } else {
                    damage.primaryValue = normalRandom(minb.toInt(), maxb.toInt(), random)
                }
            }

            FormulaType.UNDEFINED -> Unit
        }

        return damage
    }

    /**
     * Reduced port of the armor/defense branch of `Creature::blockHit`
     * (src/creatures/creature.cpp:944-1009).
     *
     * Returns the mutated damage and the resulting [BlockType]:
     *  - immunity zeroes the damage and reports `BLOCK_IMMUNITY` (creature.cpp:955-957)
     *  - defense: `damage -= uniform_random(defense / 2, defense)`, clamped to 0 with
     *    `BLOCK_DEFENSE` (creature.cpp:966-974)
     *  - armor `> 3`: `damage -= uniform_random(armor / 2, armor - (armor % 2 + 1))`;
     *    armor in `(0, 3]`: single point reduction; clamped to 0 with `BLOCK_ARMOR`
     *    (creature.cpp:976-988)
     *
     * `blockCount` state, absorb modifications, monster armor config and the
     * attacker callbacks are dependencies of the creature object and are not
     * modelled here.
     */
    fun blockHit(
        damage: Int,
        random: Random,
        checkDefense: Boolean = false,
        checkArmor: Boolean = false,
        hasDefense: Boolean = true,
        canUseDefense: Boolean = true,
        defense: Int = 0,
        armor: Int = 0,
        immune: Boolean = false,
    ): Pair<Int, BlockType> {
        var result = damage
        var blockType = BlockType.NONE

        if (immune) {
            result = 0
            blockType = BlockType.IMMUNITY
        } else if (checkDefense || checkArmor) {
            var checkArmorNow = checkArmor

            if (checkDefense && hasDefense && canUseDefense) {
                result -= uniformRandom(defense / 2, defense, random)
                if (result <= 0) {
                    result = 0
                    blockType = BlockType.DEFENSE
                    checkArmorNow = false
                }
            }

            if (checkArmorNow) {
                if (armor > 3) {
                    result -= uniformRandom(armor / 2, armor - (armor % 2 + 1), random)
                } else if (armor > 0) {
                    --result
                }

                if (result <= 0) {
                    result = 0
                    blockType = BlockType.ARMOR
                }
            }
        }

        return result to blockType
    }

    /**
     * Faithful port of `Creature::mitigateDamage` (src/creatures/creature.cpp:910-922).
     *
     * `damage -= (damage * mitigation) / 100.;` for all types except mana drain,
     * life drain and agony damage. A result `<= 0` clamps to 0 and reports
     * `BLOCK_ARMOR`, otherwise the incoming block type is preserved.
     */
    fun mitigateDamage(
        combatType: CombatType,
        damage: Int,
        mitigation: Double,
        blockType: BlockType = BlockType.NONE,
    ): Pair<Int, BlockType> {
        if (combatType == CombatType.MANADRAIN || combatType == CombatType.LIFEDRAIN || combatType == CombatType.AGONYDAMAGE) {
            return damage to blockType
        }

        val reduced = (damage - damage * mitigation / 100.0).toInt()
        return if (reduced <= 0) {
            0 to BlockType.ARMOR
        } else {
            reduced to blockType
        }
    }

    /**
     * Reduced port of the critical/fatal core of `Combat::applyExtensions`
     * (src/creatures/combat/combat.cpp:2647-2781).
     *
     * `canApplyCritical = baseChance != 0 && uniform_random(1, 10000) <= baseChance`
     * (combat.cpp:2672, 2772) and `targetMultiplier = 1.0 + baseBonus / 10000.0`
     * (combat.cpp:2744). A critical hit multiplies both components; fatal adds
     * `round(value * 0.6)` (combat.cpp:2752-2756). Extension and healing damage are
     * returned unchanged (combat.cpp:2649-2651).
     *
     * `baseChance`/`baseBonus` are the pre-resolved skill + weapon-proficiency +
     * charm values (combat.cpp:2657-2668); the player/monster lookups stay in
     * their owning systems.
     */
    fun applyExtensions(
        damage: CombatDamage,
        baseChance: Int,
        baseBonus: Int,
        random: Random,
        canApplyFatal: Boolean = false,
    ): CombatDamage {
        if (damage.extension || damage.primaryType == CombatType.HEALING) {
            return damage
        }

        val result = damage.copy()
        val isCritical = baseChance != 0 && uniformRandom(1, 10000, random) <= baseChance
        val multiplier = 1.0 + baseBonus / 10000.0

        if (isCritical) {
            result.critical = true
            result.primaryValue = (result.primaryValue * multiplier).toInt()
            result.secondaryValue = (result.secondaryValue * multiplier).toInt()
        }

        if (canApplyFatal) {
            result.fatal = true
            result.primaryValue += Math.round(result.primaryValue * 0.6).toInt()
            result.secondaryValue += Math.round(result.secondaryValue * 0.6).toInt()
        }

        return result
    }
}
