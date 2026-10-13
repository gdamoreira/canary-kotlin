package org.opentibia.canary.combat

import org.opentibia.canary.combat.condition.ConditionParam
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
 * input parameters (task constraint: do not edit Player.kt/Creature.kt) or by
 * the injected [CombatTarget] seam for the target-dispatch layer.
 *
 * Sources:
 *  - `Combat::getLevelFormula`: src/creatures/combat/combat.cpp:135-152
 *  - `Combat::getCombatDamage`: src/creatures/combat/combat.cpp:154-231
 *  - `Combat::doCombat`: src/creatures/combat/combat.cpp:1469-1483
 *  - `Combat::doCombatDefault`: src/creatures/combat/combat.cpp:1829-1864
 *  - `Combat::doCombatHealth`: src/creatures/combat/combat.cpp:1649-1711
 *  - `Combat::doCombatMana`: src/creatures/combat/combat.cpp:1722-1754
 *  - `Game::combatBlockHit`/`combatChangeHealth`/`combatChangeMana`:
 *    src/game/game.cpp:7874-8057, 8344-8770, 9063-9072
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
     * Port of the target overload of `Combat::doCombat`
     * (src/creatures/combat/combat.cpp:1469-1483).
     *
     * `params.combatType != COMBAT_NONE` dispatches manadrain damage to
     * [doCombatMana] and every other type to [doCombatHealth]
     * (combat.cpp:1474-1481) and reports true. Canary resolves the damage
     * through `Combat::getCombatDamage` from the `Combat` instance state; this
     * port keeps that resolution explicit (call [getCombatDamage] first) and
     * dispatches the supplied [damage].
     *
     * The `params.combatType == COMBAT_NONE` branch falls through to
     * [doCombatDefault] (combat.cpp:1480) and still reports true.
     */
    fun doCombat(
        caster: CombatTarget?,
        target: CombatTarget,
        damage: CombatDamage,
        params: CombatParams,
        random: Random,
        affected: Int = 1,
    ): Boolean {
        if (params.combatType == CombatType.NONE) {
            doCombatDefault(caster, target, params)
            return true
        }

        damage.affected = affected
        if (damage.primaryType != CombatType.MANADRAIN) {
            doCombatHealth(caster, target, damage, params, random)
        } else {
            doCombatMana(caster, target, damage, params, random)
        }
        return true
    }

    /**
     * Port of the single-target `Combat::doCombatHealth`
     * (src/creatures/combat/combat.cpp:1649-1711) including the
     * `Game::combatBlockHit` (src/game/game.cpp:7874-8057) and
     * `Game::combatChangeHealth` (src/game/game.cpp:8344-8770) state changes
     * it drives.
     *
     * Canary's sign convention is preserved: a positive primary value is
     * healing and bypasses blocking entirely (`game.cpp:7916`, `8347`); a
     * negative value runs the primary through [blockHit] with
     * `params.blockedByShield`/`params.blockedByArmor` and the secondary
     * through [blockHit] without defense/armor (`game.cpp:8009`), then
     * [mitigateDamage] (`creature.cpp:995-997`) before draining the target.
     *
     * PARITY GAPS: `canDoCombatWithExpertPvp` is reduced to
     * `!params.aggressive || caster !== target` (combat.cpp:1650); the healing
     * summon/player/monster restrictions (combat.cpp:1660-1685),
     * `Combat::applyExtensions` critical/fatal resolution, absorb/buff
     * modifiers, ghost mode, dodge/reflection, mana shield, creature events and
     * every protocol effect stay in their owning systems; the post-change
     * `CombatConditionFunc`/`CombatDispelFunc` call (combat.cpp:911-913) is not
     * wired into this damage path.
     */
    fun doCombatHealth(
        caster: CombatTarget?,
        target: CombatTarget,
        damage: CombatDamage,
        params: CombatParams,
        random: Random,
    ) {
        if (params.aggressive && caster === target) {
            return
        }

        if (damage.primaryValue > 0) {
            // game.cpp:8347-8350, 8412: healing skips blockHit.
            if (target.getHealth() <= 0) {
                return
            }
            target.addHealth(damage.primaryValue)
            return
        }

        // game.cpp:7875-7877: nothing to apply.
        if (damage.primaryType == CombatType.NONE && damage.secondaryType == CombatType.NONE) {
            return
        }

        if (damage.primaryType != CombatType.NONE && damage.primaryType != CombatType.AGONYDAMAGE) {
            damage.primaryValue = -blockDamage(
                combatType = damage.primaryType,
                positiveDamage = -damage.primaryValue,
                checkDefense = params.blockedByShield,
                checkArmor = params.blockedByArmor,
                target = target,
                random = random,
            )
        }

        if (damage.secondaryType != CombatType.NONE) {
            damage.secondaryValue = -blockDamage(
                combatType = damage.secondaryType,
                positiveDamage = -damage.secondaryValue,
                checkDefense = false,
                checkArmor = false,
                target = target,
                random = random,
            )
        }

        // game.cpp:8387-8395: the block stage already ran in Canary.
        if (!target.isAttackable()) {
            return
        }

        val healthChange = damage.primaryValue + damage.secondaryValue
        if (healthChange >= 0) {
            return
        }

        // game.cpp:8722: realDamage = min(targetHealth, primary + secondary).
        val realDamage = minOf(target.getHealth(), -healthChange)
        if (realDamage == 0) {
            return
        }
        target.drainHealth(realDamage)
    }

    /**
     * Port of the single-target `Combat::doCombatMana`
     * (src/creatures/combat/combat.cpp:1722-1754) including the
     * `Game::combatChangeMana` change (src/game/game.cpp:9063).
     *
     * Mana changes are never blocked, immune or mitigated: the summed primary
     * and secondary values go straight to `Creature::changeMana`
     * (`creature.cpp:869-875`), which clamps at 0 and `getMaxMana()`.
     *
     * PARITY GAPS: the `CombatManaFunc` black-skull player halving
     * (combat.cpp:984-996) needs the player/skull model; `applyExtensions`,
     * leech and protocol effects stay in their owning systems, and the
     * post-change condition/dispel call (combat.cpp:997-999) is not wired into
     * this mana path. [random] is accepted for signature parity with
     * [doCombatHealth].
     */
    fun doCombatMana(
        caster: CombatTarget?,
        target: CombatTarget,
        damage: CombatDamage,
        params: CombatParams,
        random: Random,
    ) {
        if (params.aggressive && caster === target) {
            return
        }

        target.changeMana(damage.primaryValue + damage.secondaryValue)
    }

    /**
     * Reduced port of `Combat::doCombatDefault`
     * (src/creatures/combat/combat.cpp:1829-1864).
     *
     * Applies `params.conditionList` to the target through [combatConditionFunc]
     * after the `canCombat` gate `!params.aggressive || caster != target`
     * (combat.cpp:1834); `canDoCombatWithExpertPvp` is reduced exactly as in
     * [doCombatHealth].
     *
     * PARITY GAPS: tile/magic effects, target callbacks and sounds stay in their
     * owning systems, and `CombatDispelFunc` (`removeCombatCondition`) is not
     * part of this condition-application slice.
     */
    fun doCombatDefault(
        caster: CombatTarget?,
        target: CombatTarget,
        params: CombatParams,
    ) {
        if (params.aggressive && caster === target) {
            return
        }

        combatConditionFunc(caster, target, params)
    }

    /**
     * Port of `Combat::CombatConditionFunc`
     * (src/creatures/combat/combat.cpp:1031-1092) reduced to the condition-list
     * path. `CombatNullFunc` (combat.cpp:1100-1103) drives it with no damage
     * data, so the `ORIGIN_MELEE` zero-damage gate (combat.cpp:1032) does not
     * apply; the player cleanse/fear gates (combat.cpp:1039-1077) need the
     * charm/cleanse model.
     *
     * For every template condition Canary clones it and stamps the caster owner,
     * then adds it via
     * `Creature::addCombatCondition(copy, caster->getPlayer() != nullptr)`
     * (combat.cpp:1080-1088). A target immune to the condition type skips it
     * unless caster and target are the same creature (combat.cpp:1079).
     *
     * PARITY GAP: `setPositionParam(CONDITION_PARAM_CASTER_POSITION, ...)`
     * (combat.cpp:1083) needs the map `Position`, which the combat seam does not
     * carry.
     */
    private fun combatConditionFunc(
        caster: CombatTarget?,
        target: CombatTarget,
        params: CombatParams,
    ) {
        for (condition in params.conditionList) {
            if (caster !== target && target.isImmune(condition.type)) {
                continue
            }

            val conditionCopy = condition.clone()
            if (caster != null) {
                conditionCopy.setParam(ConditionParam.CONDITION_PARAM_OWNER, caster.getID())
            }

            target.addCombatCondition(conditionCopy, caster?.getPlayer() != null)
        }
    }

    /**
     * `Creature::blockHit` (src/creatures/creature.cpp:944-1009) composed with
     * `Creature::mitigateDamage` (`creature.cpp:995-997`), taking the
     * positive-damage form Canary uses after its sign flip (`game.cpp:7911`).
     */
    private fun blockDamage(
        combatType: CombatType,
        positiveDamage: Int,
        checkDefense: Boolean,
        checkArmor: Boolean,
        target: CombatTarget,
        random: Random,
    ): Int {
        val (blocked, blockType) = blockHit(
            damage = positiveDamage,
            random = random,
            checkDefense = checkDefense,
            checkArmor = checkArmor,
            hasDefense = target.hasDefense(),
            canUseDefense = target.canUseDefense(),
            defense = target.getDefense(),
            armor = target.getArmor(),
            immune = target.isImmune(combatType),
        )

        if (blocked == 0) {
            return 0
        }
        return mitigateDamage(combatType, blocked, target.getMitigation(), blockType).first
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
