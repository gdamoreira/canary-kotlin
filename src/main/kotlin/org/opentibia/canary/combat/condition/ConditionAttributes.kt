package org.opentibia.canary.combat.condition

import kotlin.math.roundToInt

/**
 * `ConditionAttributes` (`src/creatures/combat/condition.cpp:698-1417`,
 * `condition.hpp:151-213`).
 *
 * Applies flat and percent deltas to skills, stats, buffs, elemental
 * absorbs/increases and the charm-chance modifier. Percent values are resolved
 * against the current player/creature values at apply time; [endCondition]
 * subtracts exactly the stored absolute deltas, so start and end are mirrored.
 */
class ConditionAttributes(
    id: ConditionId,
    type: ConditionType,
    ticks: Int,
    buff: Boolean = false,
    subId: Int = 0,
    now: () -> Long = { System.currentTimeMillis() },
) : ConditionGeneric(id, type, ticks, buff, subId, false, now) {

    /** `ConditionAttributes::skills` (`condition.hpp:179`). */
    private val skills = IntArray(Skill.LAST + 1)

    /** `ConditionAttributes::skillsPercent` (`condition.hpp:180`). */
    private val skillsPercent = IntArray(Skill.LAST + 1)

    /** `ConditionAttributes::stats` (`condition.hpp:181`). */
    private val stats = IntArray(Stat.LAST + 1)

    /** `ConditionAttributes::statsPercent` (`condition.hpp:182`). */
    private val statsPercent = IntArray(Stat.LAST + 1)

    /** `ConditionAttributes::buffsPercent` (`condition.hpp:183`). */
    private val buffsPercent = IntArray(Buff.LAST + 1)

    /** `ConditionAttributes::buffs` (`condition.hpp:184`). */
    private val buffs = IntArray(Buff.LAST + 1)

    /** `ConditionAttributes::charmChanceModifier` (`condition.hpp:190`). */
    private var charmChanceModifier: Int = 0

    // 12.72 mechanics (`condition.hpp:193-196`).

    /** `ConditionAttributes::absorbs` (`condition.hpp:193`). */
    private val absorbs = IntArray(COMBAT_COUNT)

    /** `ConditionAttributes::absorbsPercent` (`condition.hpp:194`). */
    private val absorbsPercent = IntArray(COMBAT_COUNT)

    /** `ConditionAttributes::increases` (`condition.hpp:195`). */
    private val increases = IntArray(COMBAT_COUNT)

    /** `ConditionAttributes::increasesPercent` (`condition.hpp:196`). */
    private val increasesPercent = IntArray(COMBAT_COUNT)

    /** `ConditionAttributes::disableDefense` (`condition.hpp:198`). */
    private var disableDefense: Boolean = false

    /** `ConditionAttributes::startCondition` (`condition.cpp:830-852`). */
    override fun startCondition(target: ConditionTarget?): Boolean {
        if (!super.startCondition(target)) {
            return false
        }

        if (target == null) {
            return true
        }

        target.setUseDefense(!disableDefense)
        updatePercentBuffs(target)
        updateBuffs(target)
        // 12.72 mechanics
        updatePercentAbsorbs(target)
        updateAbsorbs(target)
        updatePercentIncreases(target)
        updateIncreases(target)
        updateCharmChanceModifier(target)

        val player = target.player
        if (player != null) {
            updatePercentSkills(player)
            updateSkills(player)
            updatePercentStats(player)
            updateStats(player)
        }

        return true
    }

    /** `ConditionAttributes::addCondition` (`condition.cpp:702-747`). */
    override fun addCondition(target: ConditionTarget?, condition: Condition) {
        if (target == null) {
            return
        }

        if (updateCondition(condition)) {
            setTicks(condition.ticks)

            val conditionAttrs = condition as ConditionAttributes
            // Remove the old condition, then apply the new one.
            endCondition(target)

            conditionAttrs.skills.copyInto(skills)
            conditionAttrs.skillsPercent.copyInto(skillsPercent)
            conditionAttrs.stats.copyInto(stats)
            conditionAttrs.statsPercent.copyInto(statsPercent)
            conditionAttrs.buffs.copyInto(buffs)
            conditionAttrs.buffsPercent.copyInto(buffsPercent)

            conditionAttrs.absorbs.copyInto(absorbs)
            conditionAttrs.absorbsPercent.copyInto(absorbsPercent)
            conditionAttrs.increases.copyInto(increases)
            conditionAttrs.increasesPercent.copyInto(increasesPercent)
            charmChanceModifier = conditionAttrs.charmChanceModifier

            updatePercentBuffs(target)
            updateBuffs(target)
            updatePercentAbsorbs(target)
            updateAbsorbs(target)
            updatePercentIncreases(target)
            updateIncreases(target)
            updateCharmChanceModifier(target)
            disableDefense = conditionAttrs.disableDefense

            val player = target.player
            if (player != null) {
                updatePercentSkills(player)
                updateSkills(player)
                updatePercentStats(player)
                updateStats(player)
            }
        }

        // `creature->setWheelOfDestinyDrainBodyDebuff(drainBodyStage)`
        // (`condition.cpp:744-746`) belongs to the wheel subsystem and has no
        // engine hook in this port (PARITY GAP).
    }

    /** `ConditionAttributes::executeCondition` (`condition.cpp:992-994`). */
    override fun executeCondition(target: ConditionTarget?, interval: Int): Boolean =
        super.executeCondition(target, interval)

    /** `ConditionAttributes::endCondition` (`condition.cpp:996-1047`). */
    override fun endCondition(target: ConditionTarget?) {
        if (target == null) {
            return
        }

        val player = target.player
        if (player != null) {
            var needUpdate = false

            for (skill in Skill.ALL) {
                if (skills[skill.value] != 0 || skillsPercent[skill.value] != 0) {
                    needUpdate = true
                    player.setVarSkill(skill, -skills[skill.value])
                }
            }

            for (stat in Stat.ALL) {
                if (stats[stat.value] != 0) {
                    needUpdate = true
                    player.setVarStats(stat, -stats[stat.value])
                }
            }

            player.setCharmChanceModifier(player.getCharmChanceModifier() - charmChanceModifier)

            if (needUpdate) {
                player.sendStats()
                player.sendSkills()
            }
        }

        for (buff in Buff.ALL) {
            if (buffs[buff.value] != 0) {
                target.setBuff(buff, -buffs[buff.value])
            }
        }

        for (i in 0 until COMBAT_COUNT) {
            val value = absorbs[i]
            if (value != 0) {
                target.setAbsorbPercent(combatTypeAt(i), -value)
            }
            val increaseValue = increases[i]
            if (increaseValue > 0) {
                target.setIncreasePercent(combatTypeAt(i), -increaseValue)
            }
        }

        if (disableDefense) {
            target.setUseDefense(true)
        }
    }

    /** `ConditionAttributes::setParam` (`condition.cpp:1049-1345`). */
    override fun setParam(param: ConditionParam, value: Int): Boolean {
        val ret = super.setParam(param, value)

        return when (param) {
            ConditionParam.CONDITION_PARAM_SKILL_MELEE -> {
                skills[Skill.SKILL_CLUB.value] = value
                skills[Skill.SKILL_AXE.value] = value
                skills[Skill.SKILL_SWORD.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_MELEEPERCENT -> {
                skillsPercent[Skill.SKILL_CLUB.value] = value
                skillsPercent[Skill.SKILL_AXE.value] = value
                skillsPercent[Skill.SKILL_SWORD.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_FIST -> {
                skills[Skill.SKILL_FIST.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_FISTPERCENT -> {
                skillsPercent[Skill.SKILL_FIST.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_CLUB -> {
                skills[Skill.SKILL_CLUB.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_CLUBPERCENT -> {
                skillsPercent[Skill.SKILL_CLUB.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_SWORD -> {
                skills[Skill.SKILL_SWORD.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_SWORDPERCENT -> {
                skillsPercent[Skill.SKILL_SWORD.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_AXE -> {
                skills[Skill.SKILL_AXE.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_AXEPERCENT -> {
                skillsPercent[Skill.SKILL_AXE.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_DISTANCE -> {
                skills[Skill.SKILL_DISTANCE.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_DISTANCEPERCENT -> {
                skillsPercent[Skill.SKILL_DISTANCE.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_SHIELD -> {
                skills[Skill.SKILL_SHIELD.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_SHIELDPERCENT -> {
                skillsPercent[Skill.SKILL_SHIELD.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_FISHING -> {
                skills[Skill.SKILL_FISHING.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_FISHINGPERCENT -> {
                skillsPercent[Skill.SKILL_FISHING.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_CRITICAL_HIT_CHANCE -> {
                skills[Skill.SKILL_CRITICAL_HIT_CHANCE.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_CRITICAL_HIT_DAMAGE -> {
                skills[Skill.SKILL_CRITICAL_HIT_DAMAGE.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_LIFE_LEECH_CHANCE -> {
                skills[Skill.SKILL_LIFE_LEECH_CHANCE.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_LIFE_LEECH_AMOUNT -> {
                skills[Skill.SKILL_LIFE_LEECH_AMOUNT.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_MANA_LEECH_CHANCE -> {
                skills[Skill.SKILL_MANA_LEECH_CHANCE.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_SKILL_MANA_LEECH_AMOUNT -> {
                skills[Skill.SKILL_MANA_LEECH_AMOUNT.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_STAT_MAXHITPOINTS -> {
                stats[Stat.STAT_MAXHITPOINTS.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_STAT_MAXMANAPOINTS -> {
                stats[Stat.STAT_MAXMANAPOINTS.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_STAT_MAGICPOINTS -> {
                stats[Stat.STAT_MAGICPOINTS.value] = value
                true
            }

            ConditionParam.CONDITION_PARAM_STAT_MAXHITPOINTSPERCENT -> {
                statsPercent[Stat.STAT_MAXHITPOINTS.value] = maxOf(0, value)
                true
            }

            ConditionParam.CONDITION_PARAM_STAT_MAXMANAPOINTSPERCENT -> {
                statsPercent[Stat.STAT_MAXMANAPOINTS.value] = maxOf(0, value)
                true
            }

            ConditionParam.CONDITION_PARAM_STAT_MAGICPOINTSPERCENT -> {
                statsPercent[Stat.STAT_MAGICPOINTS.value] = maxOf(0, value)
                true
            }

            ConditionParam.CONDITION_PARAM_DISABLE_DEFENSE -> {
                disableDefense = value != 0
                true
            }

            ConditionParam.CONDITION_PARAM_STAT_CAPACITYPERCENT -> {
                statsPercent[Stat.STAT_CAPACITY.value] = maxOf(0, value)
                true
            }

            ConditionParam.CONDITION_PARAM_BUFF_HARMONYBONUS -> {
                buffsPercent[Buff.BUFF_HARMONYBONUS.value] = maxOf(0, value)
                true
            }

            ConditionParam.CONDITION_PARAM_BUFF_HEALINGDEALT -> {
                buffsPercent[Buff.BUFF_HEALINGDEALT.value] = maxOf(0, value)
                true
            }

            ConditionParam.CONDITION_PARAM_BUFF_HEALINGRECEIVED -> {
                buffsPercent[Buff.BUFF_HEALINGRECEIVED.value] = maxOf(0, value)
                true
            }

            ConditionParam.CONDITION_PARAM_BUFF_AUTOATTACKDEALT -> {
                buffsPercent[Buff.BUFF_AUTOATTACKDEALT.value] = maxOf(0, value)
                true
            }

            ConditionParam.CONDITION_PARAM_BUFF_DAMAGEDEALT -> {
                buffsPercent[Buff.BUFF_DAMAGEDEALT.value] = maxOf(0, value)
                true
            }

            ConditionParam.CONDITION_PARAM_BUFF_DAMAGERECEIVED -> {
                buffsPercent[Buff.BUFF_DAMAGERECEIVED.value] = maxOf(0, value)
                true
            }

            ConditionParam.CONDITION_PARAM_ABSORB_PHYSICALPERCENT -> {
                setAbsorbPercent(CombatType.COMBAT_PHYSICALDAMAGE.value, value)
                true
            }

            ConditionParam.CONDITION_PARAM_ABSORB_FIREPERCENT -> {
                setAbsorbPercent(CombatType.COMBAT_FIREDAMAGE.value, value)
                true
            }

            ConditionParam.CONDITION_PARAM_ABSORB_ENERGYPERCENT -> {
                setAbsorbPercent(CombatType.COMBAT_ENERGYDAMAGE.value, value)
                true
            }

            ConditionParam.CONDITION_PARAM_ABSORB_ICEPERCENT -> {
                setAbsorbPercent(CombatType.COMBAT_ICEDAMAGE.value, value)
                true
            }

            ConditionParam.CONDITION_PARAM_ABSORB_EARTHPERCENT -> {
                setAbsorbPercent(CombatType.COMBAT_EARTHDAMAGE.value, value)
                true
            }

            ConditionParam.CONDITION_PARAM_ABSORB_DEATHPERCENT -> {
                setAbsorbPercent(CombatType.COMBAT_DEATHDAMAGE.value, value)
                true
            }

            ConditionParam.CONDITION_PARAM_ABSORB_HOLYPERCENT -> {
                setAbsorbPercent(CombatType.COMBAT_HOLYDAMAGE.value, value)
                true
            }

            ConditionParam.CONDITION_PARAM_ABSORB_LIFEDRAINPERCENT -> {
                setAbsorbPercent(CombatType.COMBAT_LIFEDRAIN.value, value)
                true
            }

            ConditionParam.CONDITION_PARAM_ABSORB_MANADRAINPERCENT -> {
                setAbsorbPercent(CombatType.COMBAT_MANADRAIN.value, value)
                true
            }

            ConditionParam.CONDITION_PARAM_ABSORB_DROWNPERCENT -> {
                setAbsorbPercent(CombatType.COMBAT_DROWNDAMAGE.value, value)
                true
            }

            ConditionParam.CONDITION_PARAM_INCREASE_PHYSICALPERCENT -> {
                setIncreasePercent(CombatType.COMBAT_PHYSICALDAMAGE.value, value)
                true
            }

            ConditionParam.CONDITION_PARAM_INCREASE_FIREPERCENT -> {
                setIncreasePercent(CombatType.COMBAT_FIREDAMAGE.value, value)
                true
            }

            ConditionParam.CONDITION_PARAM_INCREASE_ENERGYPERCENT -> {
                setIncreasePercent(CombatType.COMBAT_ENERGYDAMAGE.value, value)
                true
            }

            ConditionParam.CONDITION_PARAM_INCREASE_ICEPERCENT -> {
                setIncreasePercent(CombatType.COMBAT_ICEDAMAGE.value, value)
                true
            }

            ConditionParam.CONDITION_PARAM_INCREASE_EARTHPERCENT -> {
                setIncreasePercent(CombatType.COMBAT_EARTHDAMAGE.value, value)
                true
            }

            ConditionParam.CONDITION_PARAM_INCREASE_DEATHPERCENT -> {
                setIncreasePercent(CombatType.COMBAT_DEATHDAMAGE.value, value)
                true
            }

            ConditionParam.CONDITION_PARAM_INCREASE_HOLYPERCENT -> {
                setIncreasePercent(CombatType.COMBAT_HOLYDAMAGE.value, value)
                true
            }

            ConditionParam.CONDITION_PARAM_INCREASE_LIFEDRAINPERCENT -> {
                setIncreasePercent(CombatType.COMBAT_LIFEDRAIN.value, value)
                true
            }

            ConditionParam.CONDITION_PARAM_INCREASE_MANADRAINPERCENT -> {
                setIncreasePercent(CombatType.COMBAT_MANADRAIN.value, value)
                true
            }

            ConditionParam.CONDITION_PARAM_INCREASE_DROWNPERCENT -> {
                setIncreasePercent(CombatType.COMBAT_DROWNDAMAGE.value, value)
                true
            }

            ConditionParam.CONDITION_PARAM_CHARM_CHANCE_MODIFIER -> {
                charmChanceModifier = value.toByte().toInt()
                true
            }

            else -> ret
        }
    }

    /** `ConditionAttributes::updatePercentStats` (`condition.cpp:854-878`). */
    private fun updatePercentStats(player: ConditionPlayer) {
        for (stat in Stat.ALL) {
            val percent = statsPercent[stat.value]
            if (percent == 0) {
                continue
            }

            when (stat) {
                Stat.STAT_MAXHITPOINTS ->
                    stats[stat.value] = (player.getMaxHealth() * ((percent - 100) / 100f)).toInt()

                Stat.STAT_MAXMANAPOINTS ->
                    stats[stat.value] = (player.getMaxMana() * ((percent - 100) / 100f)).toInt()

                Stat.STAT_MAGICPOINTS ->
                    stats[stat.value] = (player.getBaseMagicLevel() * ((percent - 100) / 100f)).toInt()

                Stat.STAT_CAPACITY ->
                    stats[stat.value] = (player.getCapacity() * (percent / 100f)).toInt()

                Stat.STAT_SOULPOINTS -> Unit
            }
        }
    }

    /** `ConditionAttributes::updateStats` (`condition.cpp:880-894`). */
    private fun updateStats(player: ConditionPlayer) {
        var needUpdate = false

        for (stat in Stat.ALL) {
            if (stats[stat.value] != 0) {
                needUpdate = true
                player.setVarStats(stat, stats[stat.value])
            }
        }

        if (needUpdate) {
            player.sendStats()
            player.sendSkills()
        }
    }

    /** `ConditionAttributes::updatePercentSkills` (`condition.cpp:896-906`). */
    private fun updatePercentSkills(player: ConditionPlayer) {
        for (skill in Skill.ALL) {
            val percent = skillsPercent[skill.value]
            if (percent == 0) {
                continue
            }

            val unmodifiedSkill = player.getBaseSkill(skill)
            skills[skill.value] = (unmodifiedSkill * ((percent - 100) / 100f)).toInt()
        }
    }

    /** `ConditionAttributes::updateSkills` (`condition.cpp:908-921`). */
    private fun updateSkills(player: ConditionPlayer) {
        var needUpdateSkills = false

        for (skill in Skill.ALL) {
            if (skills[skill.value] != 0) {
                needUpdateSkills = true
                player.setVarSkill(skill, skills[skill.value])
            }
        }

        if (needUpdateSkills) {
            player.sendSkills()
        }
    }

    /** `ConditionAttributes::updatePercentAbsorbs` (`condition.cpp:923-931`). */
    private fun updatePercentAbsorbs(target: ConditionTarget) {
        for (i in 0 until COMBAT_COUNT) {
            val value = absorbsPercent[i]
            if (value == 0) {
                continue
            }
            setAbsorb(i, ((100 - target.getAbsorbPercent(combatTypeAt(i))) * value / 100f).roundToInt())
        }
    }

    /** `ConditionAttributes::updateAbsorbs` (`condition.cpp:933-942`). */
    private fun updateAbsorbs(target: ConditionTarget) {
        for (i in 0 until COMBAT_COUNT) {
            val value = absorbs[i]
            if (value == 0) {
                continue
            }
            target.setAbsorbPercent(combatTypeAt(i), value)
        }
    }

    /** `ConditionAttributes::updatePercentIncreases` (`condition.cpp:944-952`). */
    private fun updatePercentIncreases(target: ConditionTarget) {
        for (i in 0 until COMBAT_COUNT) {
            val increasePercentValue = increasesPercent[i]
            if (increasePercentValue == 0) {
                continue
            }
            setIncrease(i, ((100 - target.getIncreasePercent(combatTypeAt(i))) * increasePercentValue / 100f).roundToInt())
        }
    }

    /** `ConditionAttributes::updateIncreases` (`condition.cpp:954-962`). */
    private fun updateIncreases(target: ConditionTarget) {
        for (i in 0 until COMBAT_COUNT) {
            val increaseValue = increases[i]
            if (increaseValue == 0) {
                continue
            }
            target.setIncreasePercent(combatTypeAt(i), increaseValue)
        }
    }

    /** `ConditionAttributes::updateCharmChanceModifier` (`condition.cpp:964-966`). */
    private fun updateCharmChanceModifier(target: ConditionTarget) {
        target.setCharmChanceModifier(target.getCharmChanceModifier() + charmChanceModifier)
    }

    /** `ConditionAttributes::updatePercentBuffs` (`condition.cpp:968-977`). */
    private fun updatePercentBuffs(target: ConditionTarget) {
        for (buff in Buff.ALL) {
            val percent = buffsPercent[buff.value]
            if (percent == 0) {
                continue
            }

            val actualBuff = target.getBuff(buff)
            buffs[buff.value] = (actualBuff * ((percent - 100) / 100f)).toInt()
        }
    }

    /** `ConditionAttributes::updateBuffs` (`condition.cpp:979-990`). */
    private fun updateBuffs(target: ConditionTarget) {
        for (buff in Buff.ALL) {
            if (buffs[buff.value] != 0) {
                target.setBuff(buff, buffs[buff.value])
            }
        }
        // `g_game().updateCreatureIcon(creature)` for monsters
        // (`condition.cpp:987-989`) is presentation-only.
    }

    /** `ConditionAttributes::clone` (`condition.cpp:1347-1349`). */
    override fun clone(): Condition {
        val copy = ConditionAttributes(id, type, ticks, isBuff, subId, now)
        copy.apply {
            endTime = this@ConditionAttributes.endTime
            owner = this@ConditionAttributes.owner
            this@ConditionAttributes.skills.copyInto(skills)
            this@ConditionAttributes.skillsPercent.copyInto(skillsPercent)
            this@ConditionAttributes.stats.copyInto(stats)
            this@ConditionAttributes.statsPercent.copyInto(statsPercent)
            this@ConditionAttributes.buffs.copyInto(buffs)
            this@ConditionAttributes.buffsPercent.copyInto(buffsPercent)
            this@ConditionAttributes.absorbs.copyInto(absorbs)
            this@ConditionAttributes.absorbsPercent.copyInto(absorbsPercent)
            this@ConditionAttributes.increases.copyInto(increases)
            this@ConditionAttributes.increasesPercent.copyInto(increasesPercent)
            charmChanceModifier = this@ConditionAttributes.charmChanceModifier
            disableDefense = this@ConditionAttributes.disableDefense
            drainBodyStage = this@ConditionAttributes.drainBodyStage
        }
        return copy
    }

    private fun getAbsorbByIndex(index: Int): Int = absorbs.getOrElse(index) { 0 }

    private fun setAbsorb(index: Int, value: Int) {
        if (index in absorbs.indices) {
            absorbs[index] = value
        }
    }

    private fun getAbsorbPercentByIndex(index: Int): Int = absorbsPercent.getOrElse(index) { 0 }

    private fun setAbsorbPercent(index: Int, value: Int) {
        if (index in absorbsPercent.indices) {
            absorbsPercent[index] = value
        }
    }

    private fun getIncreaseByIndex(index: Int): Int = increases.getOrElse(index) { 0 }

    private fun setIncrease(index: Int, value: Int) {
        if (index in increases.indices) {
            increases[index] = value
        }
    }

    private fun getIncreasePercentById(index: Int): Int = increasesPercent.getOrElse(index) { 0 }

    private fun setIncreasePercent(index: Int, value: Int) {
        if (index in increasesPercent.indices) {
            increasesPercent[index] = value
        }
    }

    private companion object {
        /** `COMBAT_COUNT` (`creatures_definitions.hpp:849`). */
        const val COMBAT_COUNT = 14

        /** `indexToCombatType` / `combatTypeToIndex` are identity (`tools.cpp:1215-1243`). */
        fun combatTypeAt(index: Int): CombatType =
            CombatType.entries.firstOrNull { it.value == index } ?: CombatType.COMBAT_NONE
    }
}
