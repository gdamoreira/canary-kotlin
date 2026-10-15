package org.opentibia.canary.combat

import org.opentibia.canary.combat.condition.Buff
import org.opentibia.canary.combat.condition.ConditionParam
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.monsters.Faction
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue
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
 * input parameters or by the injected [CombatTarget] (damage dispatch) and
 * [CombatActor]/[CombatPlayer]/[CombatTile]/[CombatMap] (target validation and
 * area enumeration) seams — see CombatValidation.kt.
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
 *  - `Creature::applyAbsorbDamageModifications`: src/creatures/creature.cpp:924-942
 *  - `Creature::blockHit` (absorb/armor/defense branch): src/creatures/creature.cpp:944-1009
 *  - `Creature::mitigateDamage`: src/creatures/creature.cpp:910-922
 *  - `Combat::applyMantraAbsorb`: src/creatures/combat/combat.cpp:739-759
 *  - `Combat::applyExtensions` (critical/fatal core): src/creatures/combat/combat.cpp:2647-2781
 *  - `Combat::canTargetCreature`/`canDoCombat`/`isProtected`/`isInPvpZone`:
 *    src/creatures/combat/combat.cpp:309-572
 *  - `Combat::getCombatArea`: src/creatures/combat/combat.cpp:233-243
 *  - `AreaCombat::getList`: src/creatures/combat/combat.cpp:2289-2325
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
     * Resolves the damage formula matrix Canary supports:
     *  - `COMBAT_FORMULA_DAMAGE`: `normal_random(mina, maxa)` (combat.cpp:181-185)
     *  - monster/runtime combat values: `normal_random(min, max)` from
     *    `Creature::getCombatValues` (combat.cpp:186-190)
     *  - `COMBAT_FORMULA_LEVELMAGIC`: `normal_random(levelFormula * mina + minb, levelFormula * maxa + maxb)`
     *    (combat.cpp:193-198)
     *  - `COMBAT_FORMULA_SKILL`: `normal_random(minb, weaponDamage * maxa + maxb)` with the weapon
     *    element as secondary, or `normal_random(minb, maxb)` without a weapon (combat.cpp:199-222)
     *
     * The Monk elemental bond override (`combat.cpp:161-174`) runs before the
     * formulas: a Monk CIP caster with a valid instant spell, a non-healing
     * params type and an equipped weapon whose `elementalBond` is set uses the
     * bond as primary type.
     *
     * When the caster carries no combat values, the player branch resolves the
     * damage through `params.valueCallback->getMinMaxValues` (combat.cpp:191-192)
     * before the formula matrix; [valueCallback] carries the two numbers the Lua
     * callback returned and [useCharges] is the `params.useCharges` argument.
     *
     * PARITY GAPS: the Wheel of Destiny `getCombatDataSpell`/`getCombatDataAugment`
     * and weapon-proficiency spell augments (combat.cpp:178-179, 224-227) stay in
     * their owning systems; [combatValues] carries `getCombatValues` in their
     * place. The callback's `COMBAT_FORMULA_SKILL` operand calculation (the
     * weapon `calculateSkillFormula` push, combat.cpp:2017-2027) is not modelled,
     * so [valueCallback] is resolved for the level/magic callback contract.
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
        combatValues: Pair<Int, Int>? = null,
        casterVocation: Int = Vocation.VOCATION_NONE,
        weaponElementalBond: CombatType = CombatType.NONE,
        valueCallback: CombatValueCallback? = null,
        useCharges: Boolean = false,
    ): CombatDamage {
        val damage = CombatDamage()
        damage.origin = origin
        damage.primaryType = combatType
        damage.instantSpellName = instantSpellName
        damage.runeSpellName = runeSpellName

        // combat.cpp:161-174: Monk CIP elemental bond override. The weapon
        // branch without a valid bond (or without a weapon) keeps params.combatType.
        if (casterVocation == Vocation.VOCATION_MONK_CIP &&
            instantSpellName.isNotEmpty() &&
            combatType != CombatType.HEALING &&
            hasWeapon &&
            weaponElementalBond != CombatType.NONE
        ) {
            damage.primaryType = weaponElementalBond
        }

        if (formulaType == FormulaType.DAMAGE) {
            damage.primaryValue = normalRandom(mina.toInt(), maxa.toInt(), random)
        } else if (combatValues != null) {
            damage.primaryValue = normalRandom(combatValues.first, combatValues.second, random)
        } else if (valueCallback != null) {
            // combat.cpp:191-192: `params.valueCallback->getMinMaxValues(player, damage, params.useCharges)`;
            // the callback branch sets the primary from `normal_random` and clears
            // the secondary (combat.cpp:2048-2063).
            valueCallback.getMinMaxValues(useCharges)?.let { (min, max) ->
                damage.primaryValue = normalRandom(min, max, random)
            }
        } else if (formulaType == FormulaType.LEVELMAGIC) {
            val min = (levelFormula * mina + minb).toInt()
            val max = (levelFormula * maxa + maxb).toInt()
            damage.primaryValue = normalRandom(min, max, random)
        } else if (formulaType == FormulaType.SKILL) {
            if (hasWeapon) {
                val max = (weaponDamage * maxa + maxb).toInt()
                damage.primaryValue = normalRandom(minb.toInt(), max, random)
                damage.secondaryType = elementType
                damage.secondaryValue = elementValue
            } else {
                damage.primaryValue = normalRandom(minb.toInt(), maxb.toInt(), random)
            }
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
     * Target absorb/flat-absorb and attacker increase percentages are read
     * through [CombatTarget] and applied by
     * [applyAbsorbDamageModifications] (`creature.cpp:924-942`).
     *
     * `Combat::CombatHealthFunc`'s player-attacking-monster branch
     * (combat.cpp:895-899) runs before the block/change stages: a player cast
     * on a monster target fires `Monster::onAttackedByPlayer` even when the hit
     * is fully blocked.
     *
     * PARITY GAPS: `canDoCombatWithExpertPvp` is reduced to
     * `!params.aggressive || caster !== target` (combat.cpp:1650); the healing
     * summon/player/monster restrictions (combat.cpp:1660-1685),
     * `Combat::applyExtensions` critical/fatal resolution, damage-dealt/received
     * buffs, ghost mode, dodge/reflection, mana shield, creature events and
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

        // combat.cpp:895-899: `if (attackerPlayer && targetMonster) targetMonster->onAttackedByPlayer(attackerPlayer)`,
        // before the block/change stages. The player-heals-monster branch
        // (combat.cpp:1668-1670) returns before `CombatHealthFunc` for the
        // healing type.
        val attackerPlayer = caster?.getPlayer()
        val targetMonster = target.getMonster()
        if (attackerPlayer != null && targetMonster != null && params.combatType != CombatType.HEALING) {
            targetMonster.onAttackedByPlayer(attackerPlayer)
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
                caster = caster,
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
                caster = caster,
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
     * `CombatManaFunc`'s black-skull halving (combat.cpp:988-996) applies first:
     * a negative primary drain from a player caster is halved unless the target
     * is a black-skulled player.
     *
     * PARITY GAPS: `applyExtensions`, leech and protocol effects stay in their
     * owning systems, and the post-change condition/dispel call
     * (combat.cpp:997-999) is not wired into this mana path. [random] is
     * accepted for signature parity with [doCombatHealth].
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

        // combat.cpp:988-996: `if (damage.primary.value < 0) { if (caster && target && caster->getPlayer()
        // && target->getSkull() != SKULL_BLACK && target->getPlayer()) damage.primary.value /= 2; }`
        if (damage.primaryValue < 0 &&
            caster?.getPlayer() != null &&
            target.getSkull() != Skulls_t.SKULL_BLACK &&
            target.getPlayer() != null
        ) {
            damage.primaryValue /= 2
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
     * `Game::combatBlockHit`'s per-type pipeline (src/game/game.cpp:7910-8014)
     * over `Creature::blockHit` (src/creatures/creature.cpp:944-1009) and
     * `Creature::mitigateDamage` (`creature.cpp:995-997`), taking the
     * positive-damage form Canary uses after its sign flip (`game.cpp:7911`).
     *
     * Order is exactly Canary's: `Combat::applyMantraAbsorb` and its `max(0)`
     * clamp (`game.cpp:7932-7933`), then `blockHit`'s own
     * `applyAbsorbDamageModifications` (`creature.cpp:948`), immunity, defense,
     * armor and finally mitigation (`creature.cpp:1000-1002`).
     *
     * Note the faithful sign quirk: Canary flips the damage positive at
     * `game.cpp:7911`, and [applyMantraAbsorb] only reduces `value < 0`
     * (`combat.cpp:755`), so the call site never actually absorbs. The port
     * keeps the call and the exact guard.
     */
    private fun blockDamage(
        combatType: CombatType,
        positiveDamage: Int,
        checkDefense: Boolean,
        checkArmor: Boolean,
        target: CombatTarget,
        caster: CombatTarget?,
        random: Random,
    ): Int {
        val mantraAdjusted = maxOf(applyMantraAbsorb(target, combatType, positiveDamage), 0)

        val (blocked, blockType) = blockHit(
            damage = mantraAdjusted,
            random = random,
            checkDefense = checkDefense,
            checkArmor = checkArmor,
            hasDefense = target.hasDefense(),
            canUseDefense = target.canUseDefense(),
            defense = target.getDefense(),
            armor = target.getArmor(),
            immune = target.isImmune(combatType),
            combatType = combatType,
            absorbPercent = target.getAbsorbPercent(combatType),
            absorbFlat = target.getAbsorbFlat(combatType),
            increasePercent = caster?.getIncreasePercent(combatType) ?: 0,
        )

        if (blocked == 0) {
            return 0
        }
        return mitigateDamage(combatType, blocked, target.getMitigation(), blockType).first
    }

    /**
     * Faithful port of `Combat::applyMantraAbsorb` (src/creatures/combat/combat.cpp:739-759).
     *
     * `mantra = player->getMantra() + (player->getBuff(BUFF_MANTRA) - 100)`; a
     * positive total absorbs up to that many points of a negative
     * fire/ice/energy/earth value (`value += min(mantra, -value)`). Every other
     * type and non-positive value is returned unchanged, and a non-player
     * [target] contributes zero mantra.
     */
    fun applyMantraAbsorb(
        target: CombatTarget?,
        combatType: CombatType,
        value: Int,
    ): Int {
        if (target == null) {
            return value
        }

        val sharedMantra = target.getBuff(Buff.BUFF_MANTRA) - 100
        val mantra = target.getMantra() + sharedMantra
        if (mantra <= 0) {
            return value
        }

        if (combatType == CombatType.FIREDAMAGE ||
            combatType == CombatType.ICEDAMAGE ||
            combatType == CombatType.ENERGYDAMAGE ||
            combatType == CombatType.EARTHDAMAGE
        ) {
            if (value < 0) {
                return value + minOf(mantra, -value)
            }
        }

        return value
    }

    /**
     * Faithful port of `Creature::applyAbsorbDamageModifications`
     * (src/creatures/creature.cpp:924-942).
     *
     * For every type but healing and a non-zero damage:
     *  - `absorbPercent`: `damage -= round(damage * percent / 100)`
     *  - `absorbFlat`: `damage = max(0, damage + flat)`
     *  - attacker `increasePercent`: `damage += round(damage * percent / 100)`
     *
     * `std::round`'s half-away-from-zero behavior matches `Math.round` on the
     * positive damage this runs on after `Game::combatBlockHit`'s sign flip.
     */
    fun applyAbsorbDamageModifications(
        combatType: CombatType,
        damage: Int,
        absorbPercent: Int = 0,
        absorbFlat: Int = 0,
        increasePercent: Int = 0,
    ): Int {
        if (combatType == CombatType.HEALING || damage == 0) {
            return damage
        }

        var result = damage
        if (absorbPercent != 0) {
            result -= Math.round(result * absorbPercent / 100f)
        }
        if (absorbFlat != 0) {
            result = maxOf(0, result + absorbFlat)
        }
        if (increasePercent != 0) {
            result += Math.round(result * increasePercent / 100f)
        }
        return result
    }

    /**
     * Reduced port of `Creature::blockHit` (src/creatures/creature.cpp:944-1009).
     *
     * Returns the mutated damage and the resulting [BlockType]:
     *  - absorb/increase damage modifications run first (creature.cpp:948)
     *  - immunity zeroes the damage and reports `BLOCK_IMMUNITY` (creature.cpp:955-957)
     *  - defense: `damage -= uniform_random(defense / 2, defense)`, clamped to 0 with
     *    `BLOCK_DEFENSE` (creature.cpp:966-974)
     *  - armor `> 3`: `damage -= uniform_random(armor / 2, armor - (armor % 2 + 1))`;
     *    armor in `(0, 3]`: single point reduction; clamped to 0 with `BLOCK_ARMOR`
     *    (creature.cpp:976-988)
     *
     * `blockCount` state, monster armor config and the attacker callbacks are
     * dependencies of the creature object and are not modelled here; the
     * absorb/increase values are passed explicitly (see
     * [applyAbsorbDamageModifications]).
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
        combatType: CombatType = CombatType.NONE,
        absorbPercent: Int = 0,
        absorbFlat: Int = 0,
        increasePercent: Int = 0,
    ): Pair<Int, BlockType> {
        var result = applyAbsorbDamageModifications(
            combatType = combatType,
            damage = damage,
            absorbPercent = absorbPercent,
            absorbFlat = absorbFlat,
            increasePercent = increasePercent,
        )
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

    /**
     * `Combat::ConditionToDamageType` (src/creatures/combat/combat.cpp:245-276):
     * maps the damaging condition types to their combat types, `COMBAT_NONE`
     * otherwise.
     */
    fun ConditionToDamageType(type: ConditionType): CombatType = when (type) {
        ConditionType.CONDITION_FIRE -> CombatType.FIREDAMAGE
        ConditionType.CONDITION_ENERGY -> CombatType.ENERGYDAMAGE
        ConditionType.CONDITION_BLEEDING -> CombatType.PHYSICALDAMAGE
        ConditionType.CONDITION_DROWN -> CombatType.DROWNDAMAGE
        ConditionType.CONDITION_POISON -> CombatType.EARTHDAMAGE
        ConditionType.CONDITION_FREEZING -> CombatType.ICEDAMAGE
        ConditionType.CONDITION_DAZZLED -> CombatType.HOLYDAMAGE
        ConditionType.CONDITION_CURSED -> CombatType.DEATHDAMAGE
        else -> CombatType.NONE
    }

    /**
     * `Combat::DamageToConditionType` (src/creatures/combat/combat.cpp:278-307):
     * maps the elemental combat types back to their condition types,
     * `CONDITION_NONE` otherwise.
     */
    fun DamageToConditionType(type: CombatType): ConditionType = when (type) {
        CombatType.FIREDAMAGE -> ConditionType.CONDITION_FIRE
        CombatType.ENERGYDAMAGE -> ConditionType.CONDITION_ENERGY
        CombatType.DROWNDAMAGE -> ConditionType.CONDITION_DROWN
        CombatType.EARTHDAMAGE -> ConditionType.CONDITION_POISON
        CombatType.ICEDAMAGE -> ConditionType.CONDITION_FREEZING
        CombatType.HOLYDAMAGE -> ConditionType.CONDITION_DAZZLED
        CombatType.DEATHDAMAGE -> ConditionType.CONDITION_CURSED
        CombatType.PHYSICALDAMAGE -> ConditionType.CONDITION_BLEEDING
        else -> ConditionType.CONDITION_NONE
    }

    /**
     * `Combat::isPlayerCombat` (src/creatures/combat/combat.cpp:309-319): true
     * for players and for summons whose master is a player.
     */
    fun isPlayerCombat(target: CombatActor): Boolean {
        if (target.getPlayer() != null) {
            return true
        }

        if (target.isSummon() && target.getMaster()?.getPlayer() != null) {
            return true
        }

        return false
    }

    /** `Combat::isInPvpZone` (src/creatures/combat/combat.cpp:428-430). */
    fun isInPvpZone(attacker: CombatActor, target: CombatActor): Boolean =
        attacker.getZoneType() == ZoneType.ZONE_PVP && target.getZoneType() == ZoneType.ZONE_PVP

    /**
     * `Combat::isProtected` (src/creatures/combat/combat.cpp:432-447), with the
     * `PROTECTION_LEVEL` config value injected as [protectionLevel].
     *
     * True when either player is below the protection level, when a
     * non-combat vocation meets `VOCATION_NONE`, or when a black-skulled
     * attacker has `SKULL_NONE` client-side against the target.
     */
    fun isProtected(attacker: CombatPlayer, target: CombatPlayer, protectionLevel: Int): Boolean {
        if (target.getLevel() < protectionLevel || attacker.getLevel() < protectionLevel) {
            return true
        }

        if ((!attacker.getVocationCanCombat() || !target.getVocationCanCombat()) &&
            (attacker.getVocationId() == Vocation.VOCATION_NONE || target.getVocationId() == Vocation.VOCATION_NONE)
        ) {
            return true
        }

        if (attacker.getSkull() == Skulls_t.SKULL_BLACK && attacker.getSkullClient(target) == Skulls_t.SKULL_NONE) {
            return true
        }

        return false
    }

    /**
     * `Combat::canTargetCreature` (src/creatures/combat/combat.cpp:321-374).
     *
     * Runs the self/protection-zone/player-combat-zone gates, the
     * cannot-use-combat and attackable gates, the [isProtected] check and the
     * secure-mode gate, then delegates to [canDoCombat]. `PROTECTION_LEVEL`,
     * `ExpertPvp::isEnabled()`, the world type and
     * `MONSTER_PERF_TEST_FRIENDLY_FIRE` are injected as parameters.
     *
     * PARITY GAPS: `getExpertPvpCombatReturnValue(ExpertPvpActionKind.DirectAttack,
     * ...)` (combat.cpp:362-365) needs the ExpertPvp relation context and is not
     * evaluated here; [expertPvpEnabled] only disables the secure-mode gate.
     */
    fun canTargetCreature(
        attacker: CombatPlayer,
        target: CombatActor,
        protectionLevel: Int = 0,
        expertPvpEnabled: Boolean = false,
        noPvpWorld: Boolean = false,
        monsterPerfTestFriendlyFire: Boolean = false,
    ): ReturnValue {
        if (attacker === target) {
            return ReturnValue.YOUMAYNOTATTACKTHISPLAYER
        }

        if (!attacker.hasFlag(PlayerFlags_t.IgnoreProtectionZone)) {
            if (attacker.getZoneType() == ZoneType.ZONE_PROTECTION) {
                return ReturnValue.ACTIONNOTPERMITTEDINPROTECTIONZONE
            }

            if (target.getZoneType() == ZoneType.ZONE_PROTECTION) {
                return ReturnValue.ACTIONNOTPERMITTEDINPROTECTIONZONE
            }

            if (isPlayerCombat(target)) {
                if (attacker.getZoneType() == ZoneType.ZONE_NOPVP) {
                    return ReturnValue.ACTIONNOTPERMITTEDINANOPVPZONE
                }

                if (target.getZoneType() == ZoneType.ZONE_NOPVP) {
                    return ReturnValue.YOUMAYNOTATTACKAPERSONINPROTECTIONZONE
                }
            }
        }

        if (attacker.hasFlag(PlayerFlags_t.CannotUseCombat) || !target.isAttackable()) {
            return if (target.getPlayer() != null) {
                ReturnValue.YOUMAYNOTATTACKTHISPLAYER
            } else {
                ReturnValue.YOUMAYNOTATTACKTHISCREATURE
            }
        }

        val targetPlayer = target.getPlayer()
        if (targetPlayer != null && isProtected(attacker, targetPlayer, protectionLevel)) {
            return ReturnValue.YOUMAYNOTATTACKTHISPLAYER
        }

        if (targetPlayer != null &&
            !expertPvpEnabled &&
            attacker.hasSecureMode() &&
            !isInPvpZone(attacker, target) &&
            attacker.getSkullClient(target) == Skulls_t.SKULL_NONE
        ) {
            return ReturnValue.TURNSECUREMODETOATTACKUNMARKEDPLAYERS
        }

        return canDoCombat(
            attacker = attacker,
            target = target,
            aggressive = true,
            protectionLevel = protectionLevel,
            noPvpWorld = noPvpWorld,
            monsterPerfTestFriendlyFire = monsterPerfTestFriendlyFire,
        )
    }

    /**
     * `Combat::canDoCombat(attacker, target, aggressive)`
     * (src/creatures/combat/combat.cpp:449-572).
     *
     * PARITY GAPS: the trailing
     * `eventCreatureOnTargetCombat`/`creatureOnTargetCombat` callbacks
     * (combat.cpp:567-570) are Lua/event extension points and return
     * `RETURNVALUE_NOERROR` here; `isProtected` uses the injected
     * [protectionLevel], `g_game().getWorldType()` the [noPvpWorld] flag and
     * `MONSTER_PERF_TEST_FRIENDLY_FIRE` the [monsterPerfTestFriendlyFire] flag.
     */
    fun canDoCombat(
        attacker: CombatActor?,
        target: CombatActor?,
        aggressive: Boolean,
        protectionLevel: Int = 0,
        noPvpWorld: Boolean = false,
        monsterPerfTestFriendlyFire: Boolean = false,
    ): ReturnValue {
        if (!aggressive) {
            return ReturnValue.NOERROR
        }

        val targetPlayer = target?.getPlayer()
        if (target != null) {
            val tile = target.getTile()
            if (tile != null && tile.hasProperty(CONST_PROP_BLOCKPROJECTILE)) {
                return ReturnValue.NOTENOUGHROOM
            }

            if (targetPlayer != null && tile != null && tile.hasFlag(TileFlags.TILESTATE_PROTECTIONZONE)) {
                return if (targetPlayer.hasPermittedConditionInPZ()) {
                    ReturnValue.NOERROR
                } else {
                    ReturnValue.ACTIONNOTPERMITTEDINPROTECTIONZONE
                }
            }
        }

        if (attacker != null) {
            val attackerMaster = attacker.getMaster()
            val masterAttackerPlayer = attackerMaster?.getPlayer()
            val masterAttackerMonster = attackerMaster?.getMonster()
            val attackerPlayer = attacker.getPlayer()
            val attackerMonster = attacker.getMonster()
            val targetMonster = target?.getMonster()
            val targetMaster = target?.getMaster()
            val targetMasterPlayer = targetMaster?.getPlayer()

            if (targetPlayer != null) {
                if (targetPlayer.hasFlag(PlayerFlags_t.CannotBeAttacked)) {
                    return ReturnValue.YOUMAYNOTATTACKTHISPLAYER
                }

                val targetPlayerTile = target.getTile()
                if (attackerPlayer != null) {
                    if (attackerPlayer.hasFlag(PlayerFlags_t.CannotAttackPlayer)) {
                        return ReturnValue.YOUMAYNOTATTACKTHISPLAYER
                    }

                    if (isProtected(attackerPlayer, targetPlayer, protectionLevel)) {
                        return ReturnValue.YOUMAYNOTATTACKTHISPLAYER
                    }

                    // nopvp-zone
                    val attackerTile = attacker.getTile()
                    if (targetPlayerTile != null && targetPlayerTile.hasFlag(TileFlags.TILESTATE_NOPVPZONE)) {
                        return ReturnValue.ACTIONNOTPERMITTEDINANOPVPZONE
                    } else if (attackerTile != null &&
                        attackerTile.hasFlag(TileFlags.TILESTATE_NOPVPZONE) &&
                        targetPlayerTile != null &&
                        !targetPlayerTile.hasFlag(TileFlags.TILESTATE_NOPVPZONE) &&
                        !targetPlayerTile.hasFlag(TileFlags.TILESTATE_PROTECTIONZONE)
                    ) {
                        return ReturnValue.ACTIONNOTPERMITTEDINANOPVPZONE
                    }

                    if (attackerPlayer.getFaction() != Faction.Default &&
                        attackerPlayer.getFaction() != Faction.Player &&
                        attackerPlayer.getFaction() == targetPlayer.getFaction()
                    ) {
                        return ReturnValue.YOUMAYNOTATTACKTHISPLAYER
                    }
                }

                if (attackerMaster != null && masterAttackerPlayer != null) {
                    if (masterAttackerPlayer.hasFlag(PlayerFlags_t.CannotAttackPlayer)) {
                        return ReturnValue.YOUMAYNOTATTACKTHISPLAYER
                    }

                    if (targetPlayerTile != null && targetPlayerTile.hasFlag(TileFlags.TILESTATE_NOPVPZONE)) {
                        return ReturnValue.ACTIONNOTPERMITTEDINANOPVPZONE
                    }

                    if (isProtected(masterAttackerPlayer, targetPlayer, protectionLevel)) {
                        return ReturnValue.YOUMAYNOTATTACKTHISPLAYER
                    }
                }

                if (attackerMonster != null && (attackerMaster == null || masterAttackerMonster != null)) {
                    if (attacker.getFaction() != Faction.Default &&
                        !attackerMonster.isEnemyFaction(targetPlayer.getFaction())
                    ) {
                        return ReturnValue.YOUMAYNOTATTACKTHISPLAYER
                    }
                }
            } else if (targetMonster != null) {
                val allowMonsterPerfTestFriendlyFire = monsterPerfTestFriendlyFire &&
                    attackerMonster != null &&
                    attacker !== target &&
                    !attacker.isSummon() &&
                    !target.isSummon()

                if (!allowMonsterPerfTestFriendlyFire &&
                    attacker.getFaction() != Faction.Default &&
                    attacker.getFaction() != Faction.Player &&
                    attackerMonster != null &&
                    !attackerMonster.isEnemyFaction(target.getFaction())
                ) {
                    return ReturnValue.YOUMAYNOTATTACKTHISCREATURE
                }

                if (attackerPlayer != null) {
                    if (attackerPlayer.hasFlag(PlayerFlags_t.CannotAttackMonster)) {
                        return ReturnValue.YOUMAYNOTATTACKTHISCREATURE
                    }

                    if (target.isSummon() && targetMasterPlayer != null && target.getZoneType() == ZoneType.ZONE_NOPVP) {
                        return ReturnValue.ACTIONNOTPERMITTEDINANOPVPZONE
                    }
                } else if (attackerMonster != null && !allowMonsterPerfTestFriendlyFire) {
                    if ((targetMaster == null || targetMasterPlayer == null) && attacker.getFaction() == Faction.Default) {
                        if (attackerMaster == null || masterAttackerPlayer == null) {
                            return ReturnValue.YOUMAYNOTATTACKTHISCREATURE
                        }
                    }
                }
            } else if (target != null && target.getNpc()) {
                return ReturnValue.YOUMAYNOTATTACKTHISCREATURE
            }

            if (noPvpWorld) {
                if (attackerPlayer != null || masterAttackerPlayer != null) {
                    if (targetPlayer != null) {
                        if (!isInPvpZone(attacker, target)) {
                            return ReturnValue.YOUMAYNOTATTACKTHISPLAYER
                        }
                    }

                    if (target != null && target.isSummon() && targetMasterPlayer != null) {
                        if (!isInPvpZone(attacker, target)) {
                            return ReturnValue.YOUMAYNOTATTACKTHISCREATURE
                        }
                    }
                }
            }
        }

        return ReturnValue.NOERROR
    }

    /**
     * `Combat::canDoCombat(caster, tile, aggressive)`
     * (src/creatures/combat/combat.cpp:376-426).
     *
     * PARITY GAPS: the trailing `eventCreatureOnAreaCombat`/`creatureOnAreaCombat`
     * callbacks (combat.cpp:421-424) are Lua/event extension points and return
     * `RETURNVALUE_NOERROR` here.
     */
    fun canDoCombat(caster: CombatActor?, tile: CombatTile?, aggressive: Boolean): ReturnValue {
        if (!aggressive || tile == null) {
            return ReturnValue.NOERROR
        }

        if (tile.hasProperty(CONST_PROP_BLOCKPROJECTILE)) {
            val canThrow = tile.getItemIds().any { it == ITEM_MAGICWALL || it == ITEM_MAGICWALL_SAFE }
            if (!canThrow) {
                return ReturnValue.CANNOTTHROW
            }
        }

        if (tile.hasFlag(TileFlags.TILESTATE_PROTECTIONZONE)) {
            return ReturnValue.ACTIONNOTPERMITTEDINPROTECTIONZONE
        }

        if (tile.getTeleportItem()) {
            return ReturnValue.CANNOTTHROW
        }

        if (caster != null) {
            val casterPosition = caster.getPosition()
            val tilePosition = tile.getPosition()
            if (casterPosition.z < tilePosition.z) {
                return ReturnValue.FIRSTGODOWNSTAIRS
            } else if (casterPosition.z > tilePosition.z) {
                return ReturnValue.FIRSTGOUPSTAIRS
            }

            val player = caster.getPlayer()
            if (player != null && player.hasFlag(PlayerFlags_t.IgnoreProtectionZone)) {
                return ReturnValue.NOERROR
            }
        }

        return ReturnValue.NOERROR
    }

    /**
     * `Combat::getCombatArea` (src/creatures/combat/combat.cpp:233-243).
     *
     * Positions on or above [MAP_MAX_LAYERS] are ignored; without an [area] the
     * bare target tile is created, otherwise the selected [AreaCombat] matrix is
     * walked through [CombatMap].
     *
     * PARITY GAP: the area dispatch that consumes the list
     * (`Combat::CombatFunc`, combat.cpp:1507-1643: spectator ranges,
     * `canDoCombat(caster, tile, ...)` filtering, `targetCasterOrTopMost`,
     * creature combat-damage overrides and the effect/protocol calls) is not
     * wired yet; only the tile enumeration is ported.
     */
    fun getCombatArea(
        centerPos: Position,
        targetPos: Position,
        area: AreaCombat?,
        map: CombatMap,
        list: MutableList<CombatTile>,
    ) {
        if (targetPos.z >= MAP_MAX_LAYERS) {
            return
        }

        if (area != null) {
            area.getList(centerPos, targetPos, list, getDirectionTo(targetPos, centerPos), map)
        } else {
            list.add(map.getOrCreateTile(targetPos))
        }
    }

    /**
     * Faithful port of `getDirectionTo(from, to, exactDiagonalOnly = true)`
     * (src/utils/tools.cpp:626-690 minus the unused non-exact branch): returns
     * null for `DIRECTION_NONE`. Only an exact diagonal (|dx| == |dy|) yields a
     * diagonal direction; otherwise the dominant axis wins.
     */
    private fun getDirectionTo(from: Position, to: Position): Direction? {
        if (from == to) {
            return null
        }

        val dx = from.x - to.x
        val dy = from.y - to.y

        val absDx = kotlin.math.abs(dx)
        val absDy = kotlin.math.abs(dy)

        if (absDx > absDy) {
            return if (dx < 0) Direction.East else Direction.West
        }
        if (absDx < absDy) {
            return if (dy > 0) Direction.North else Direction.South
        }

        if (dx < 0) {
            if (dy < 0) {
                return Direction.SouthEast
            }
            if (dy > 0) {
                return Direction.NorthEast
            }
            return Direction.East
        }

        if (dx > 0) {
            if (dy < 0) {
                return Direction.SouthWest
            }
            if (dy > 0) {
                return Direction.NorthWest
            }
            return Direction.West
        }

        return if (dy > 0) Direction.North else Direction.South
    }

    /** `CONST_PROP_BLOCKPROJECTILE` (src/items/items_definitions.hpp:18). */
    const val CONST_PROP_BLOCKPROJECTILE = 2

    /** `ITEM_MAGICWALL` (src/utils/utils_definitions.hpp:596). */
    const val ITEM_MAGICWALL = 2128

    /** `ITEM_MAGICWALL_SAFE` (src/utils/utils_definitions.hpp:598). */
    const val ITEM_MAGICWALL_SAFE = 10181

    /** `MAP_MAX_LAYERS` (src/map/map_const.hpp:17). */
    const val MAP_MAX_LAYERS = 16
}
