package org.opentibia.canary.systems.offlinetraining

import kotlin.math.floor
import kotlin.math.pow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OfflineTrainingTest {

    private fun newTraining() = OfflineTraining(TestVocation())

    @Test
    fun `offline training defaults match canary player fields`() {
        val training = newTraining()
        assertThat(training.getOfflineTrainingSkill()).isEqualTo(Skill.SKILL_NONE)
        assertThat(training.getOfflineTrainingTime()).isEqualTo(0)
    }

    @Test
    fun `add offline training time caps at twelve hours`() {
        val training = newTraining()
        training.addOfflineTrainingTime(1_000)
        assertThat(training.getOfflineTrainingTime()).isEqualTo(1_000)

        training.addOfflineTrainingTime(12 * 3600 * 1000)
        assertThat(training.getOfflineTrainingTime()).isEqualTo(12 * 3600 * 1000)
        assertThat(training.getOfflineTrainingTime()).isEqualTo(43_200_000)
    }

    @Test
    fun `remove offline training time floors at zero`() {
        val training = newTraining()
        training.addOfflineTrainingTime(5_000)
        training.removeOfflineTrainingTime(2_000)
        assertThat(training.getOfflineTrainingTime()).isEqualTo(3_000)

        training.removeOfflineTrainingTime(50_000_000)
        assertThat(training.getOfflineTrainingTime()).isEqualTo(0)
    }

    @Test
    fun `on think accrues offline training time per interval`() {
        val training = newTraining()
        training.onThink(60_000)
        assertThat(training.getOfflineTrainingTime()).isEqualTo(60_000)
        training.onThink(60_000)
        assertThat(training.getOfflineTrainingTime()).isEqualTo(120_000)
    }

    @Test
    fun `set offline training skill stores the chosen skill`() {
        val training = newTraining()
        training.setOfflineTrainingSkill(Skill.SKILL_SWORD)
        assertThat(training.getOfflineTrainingSkill()).isEqualTo(Skill.SKILL_SWORD)
    }

    @Test
    fun `add offline training tries rejects zero tries and level skill`() {
        val training = newTraining()
        assertThat(training.addOfflineTrainingTries(Skill.SKILL_MAGLEVEL, 0)).isFalse()
        assertThat(training.addOfflineTrainingTries(Skill.SKILL_LEVEL, 100)).isFalse()
    }

    @Test
    fun `add offline training tries converts magic level tries`() {
        val training = newTraining()
        training.magLevel = 1
        training.manaSpent = 0
        training.magLevelPercent = 0

        val updated = training.addOfflineTrainingTries(Skill.SKILL_MAGLEVEL, 5_000)

        assertThat(updated).isTrue()
        assertThat(training.magLevel).isEqualTo(2)
        assertThat(training.manaSpent).isEqualTo(200)
        assertThat(training.magLevelPercent).isEqualTo(1)
    }

    @Test
    fun `add offline training tries converts weapon skill tries`() {
        val training = newTraining()
        training.skills[Skill.SKILL_SWORD] = SkillState(level = 10, tries = 0, percent = 0)

        val updated = training.addOfflineTrainingTries(Skill.SKILL_SWORD, 120)

        assertThat(updated).isTrue()
        assertThat(training.skills[Skill.SKILL_SWORD]!!.level).isEqualTo(12)
        assertThat(training.skills[Skill.SKILL_SWORD]!!.tries).isEqualTo(15)
        assertThat(training.skills[Skill.SKILL_SWORD]!!.percent).isEqualTo(25)
    }

    @Test
    fun `get percent level mirrors canary truncation and overflow guard`() {
        assertThat(OfflineTraining.getPercentLevel(0, 0)).isEqualTo(0.0)
        assertThat(OfflineTraining.getPercentLevel(200, 14_400)).isEqualTo(1.39)
        assertThat(OfflineTraining.getPercentLevel(200, 100)).isEqualTo(0.0)
    }

    /**
     * Test double for Canary `Vocation::getReqSkillTries` / `Vocation::getReqMana`
     * (src/creatures/players/vocations/vocation.cpp:265,295) using the real Knight
     * values from data/XML/vocations.xml (skillBase = {50,50,50,50,30,100,20},
     * minSkillLevel = 10, manaMultiplier = 3.0).
     */
    private class TestVocation(
        private val skillMultipliers: DoubleArray = doubleArrayOf(1.1, 1.1, 1.1, 1.1, 1.4, 1.1, 1.1),
        private val manaMultiplier: Double = 3.0,
    ) : VocationRequirements {
        override fun getReqSkillTries(skill: Skill, level: Int): Long {
            if (skill.value > Skill.SKILL_FISHING.value || level <= MIN_SKILL_LEVEL) {
                return 0
            }
            return floor(SKILL_BASE[skill.value] * skillMultipliers[skill.value].pow(level - (MIN_SKILL_LEVEL + 1))).toLong()
        }

        override fun getReqMana(magLevel: Int): Long {
            if (magLevel == 0) {
                return 0
            }
            return floor(1600.0 * manaMultiplier.pow(magLevel - 1)).toLong()
        }

        companion object {
            const val MIN_SKILL_LEVEL = 10
            val SKILL_BASE = intArrayOf(50, 50, 50, 50, 30, 100, 20)
        }
    }
}
