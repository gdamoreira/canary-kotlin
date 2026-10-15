package org.opentibia.canary.io.iologindata

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Buff
import org.opentibia.canary.combat.condition.CombatType
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionAttributes
import org.opentibia.canary.combat.condition.ConditionDamage
import org.opentibia.canary.combat.condition.ConditionGeneric
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionParam
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.FakeConditionTarget
import org.opentibia.canary.combat.condition.Skill
import org.opentibia.canary.combat.condition.Stat
import org.opentibia.canary.io.PropWriteStream
import org.opentibia.canary.spells.SoundEffect
import kotlin.random.Random

/**
 * Wave 63 A3 transcript tests for the condition payload fidelity:
 * the `ConditionAttributes` flat arrays (`ConditionAttributes::serialize`
 * / `unserializeProp`, src/creatures/combat/condition.cpp:749-825), the
 * retained `tickSound`/`addSound` slots (`Condition::serialize`,
 * condition.cpp:277-301) and the `Condition::createCondition(PropStream&)` +
 * `Condition::unserialize` decode loop
 * (`IOLoginDataLoad::loadPlayerConditions`, iologindata_load_player.cpp:257-275).
 *
 * The encode half is the production [PlayerConditionPersistence]; the decode
 * half is its new [PlayerConditionPersistence.decodeConditions] counterpart.
 * A malformed stream must fail safely: the C++ array writes are unguarded
 * (`skills[currentSkill++]`, `condition.cpp:751`) and this port documents the
 * bounded rejection instead of the undefined write.
 */
class ConditionPayloadPersistenceTest {

    private val persistence = PlayerConditionPersistence()

    private fun attributes() = ConditionAttributes(
        id = ConditionId.CONDITIONID_DEFAULT,
        type = ConditionType.CONDITION_ATTRIBUTES,
        ticks = 10_000,
        now = { 0L },
    )

    private fun roundTrip(condition: Condition): Condition =
        persistence.decodeConditions(persistence.encodeConditions(listOf(condition))).single()

    // --- flat payload round trip ----------------------------------------------

    @Test
    fun `round trips the flat skill payload`() {
        val source = attributes()
        source.setParam(ConditionParam.CONDITION_PARAM_SKILL_SWORD, 12)
        source.setParam(ConditionParam.CONDITION_PARAM_SKILL_FISHING, -3)
        source.setParam(ConditionParam.CONDITION_PARAM_SKILL_CRITICAL_HIT_CHANCE, 5)

        val decoded = roundTrip(source) as ConditionAttributes

        val target = FakeConditionTarget()
        decoded.startCondition(target)
        assertThat(target.varSkills[Skill.SKILL_SWORD]).isEqualTo(12)
        assertThat(target.varSkills[Skill.SKILL_FISHING]).isEqualTo(-3)
        assertThat(target.varSkills[Skill.SKILL_CRITICAL_HIT_CHANCE]).isEqualTo(5)
    }

    @Test
    fun `round trips the flat stat payload including capacity`() {
        val source = attributes()
        source.setParam(ConditionParam.CONDITION_PARAM_STAT_MAXHITPOINTS, 25)
        source.setParam(ConditionParam.CONDITION_PARAM_STAT_MAXMANAPOINTS, 15)
        source.setParam(ConditionParam.CONDITION_PARAM_STAT_MAGICPOINTS, 4)
        source.setParam(ConditionParam.CONDITION_PARAM_STAT_CAPACITYPERCENT, 150)

        // `updatePercentStats` resolves the capacity percent into the flat
        // `stats[STAT_CAPACITY]` slot (condition.cpp:874-876).
        source.startCondition(FakeConditionTarget().apply { capacityValue = 400.0 })

        val decoded = roundTrip(source) as ConditionAttributes

        val target = FakeConditionTarget()
        decoded.startCondition(target)
        assertThat(target.varStats[Stat.STAT_MAXHITPOINTS]).isEqualTo(25)
        assertThat(target.varStats[Stat.STAT_MAXMANAPOINTS]).isEqualTo(15)
        assertThat(target.varStats[Stat.STAT_MAGICPOINTS]).isEqualTo(4)
        assertThat(target.varStats[Stat.STAT_CAPACITY]).isEqualTo(600)
    }

    @Test
    fun `round trips the flat buff payload`() {
        val source = attributes()
        source.setParam(ConditionParam.CONDITION_PARAM_BUFF_HARMONYBONUS, 150)
        source.setParam(ConditionParam.CONDITION_PARAM_BUFF_DAMAGEDEALT, 50)

        // `updatePercentBuffs` materializes the flat slots
        // (condition.cpp:968-977): 20 * 50% = 10 and 10 * -50% = -5.
        source.startCondition(
            FakeConditionTarget().apply {
                buffValues[Buff.BUFF_HARMONYBONUS] = 20
                buffValues[Buff.BUFF_DAMAGEDEALT] = 10
            },
        )

        val decoded = roundTrip(source) as ConditionAttributes

        val target = FakeConditionTarget()
        decoded.startCondition(target)
        assertThat(target.buffValues[Buff.BUFF_HARMONYBONUS]).isEqualTo(10)
        assertThat(target.buffValues[Buff.BUFF_DAMAGEDEALT]).isEqualTo(-5)
    }

    @Test
    fun `round trips the absorb and increase payload by combat index`() {
        val source = attributes()
        source.setParam(ConditionParam.CONDITION_PARAM_ABSORB_FIREPERCENT, 30)
        source.setParam(ConditionParam.CONDITION_PARAM_ABSORB_DEATHPERCENT, 10)
        source.setParam(ConditionParam.CONDITION_PARAM_INCREASE_ENERGYPERCENT, 20)

        // `updatePercentAbsorbs`/`updatePercentIncreases` materialize the flat
        // arrays from the percent slots (condition.cpp:923-952).
        source.startCondition(FakeConditionTarget())

        val decoded = roundTrip(source) as ConditionAttributes

        val target = FakeConditionTarget()
        decoded.startCondition(target)
        assertThat(target.absorbValues[CombatType.COMBAT_FIREDAMAGE]).isEqualTo(30)
        assertThat(target.absorbValues[CombatType.COMBAT_DEATHDAMAGE]).isEqualTo(10)
        assertThat(target.increaseValues[CombatType.COMBAT_ENERGYDAMAGE]).isEqualTo(20)
        assertThat(target.absorbValues[CombatType.COMBAT_PHYSICALDAMAGE]).isNull()
        assertThat(target.increaseValues[CombatType.COMBAT_FIREDAMAGE]).isNull()
    }

    @Test
    fun `round trips the charm chance modifier with its sign`() {
        val source = attributes()
        source.setParam(ConditionParam.CONDITION_PARAM_CHARM_CHANCE_MODIFIER, -10)

        val decoded = roundTrip(source) as ConditionAttributes

        val target = FakeConditionTarget()
        decoded.startCondition(target)
        assertThat(target.charmModifier).isEqualTo(-10)
    }

    // --- sounds ----------------------------------------------------------------

    @Test
    fun `round trips the tick and add sounds`() {
        val source = ConditionGeneric(
            id = ConditionId.CONDITIONID_DEFAULT,
            type = ConditionType.CONDITION_INFIGHT,
            ticks = 5_000,
            now = { 0L },
        )
        assertThat(source.setParam(ConditionParam.CONDITION_PARAM_SOUND_TICK, SoundEffect.MELEE_ATK_SWORD.value)).isTrue()
        assertThat(source.setParam(ConditionParam.CONDITION_PARAM_SOUND_ADD, SoundEffect.SPELL_HASTE.value)).isTrue()

        val decoded = roundTrip(source)

        assertThat(decoded.tickSound).isEqualTo(SoundEffect.MELEE_ATK_SWORD)
        assertThat(decoded.addSound).isEqualTo(SoundEffect.SPELL_HASTE)
    }

    @Test
    fun `an unknown persisted sound id normalizes to silence`() {
        val blob = conditionBlob(
            type = ConditionType.CONDITION_INFIGHT,
            ticks = 5_000,
        ) {
            writeU8(CONDITION_ATTR_TICKSOUND)
            writeU16(60_000)
            writeU8(CONDITION_ATTR_ADDSOUND)
            writeU16(60_001)
        }

        val decoded = persistence.decodeConditions(blob).single()

        assertThat(decoded.tickSound).isEqualTo(SoundEffect.SILENCE)
        assertThat(decoded.addSound).isEqualTo(SoundEffect.SILENCE)
    }

    // --- persistence flag and interval data ------------------------------------

    @Test
    fun `round trips the persistent flag`() {
        val bakragore = Condition.createCondition(
            id = ConditionId.CONDITIONID_DEFAULT,
            type = ConditionType.CONDITION_BAKRAGORE,
            ticks = 1_000,
            isPersistent = true,
        )!!

        val decoded = roundTrip(bakragore)

        assertThat(decoded.isPersistentFlag).isTrue()
        assertThat(decoded.isPersistent()).isTrue()
    }

    @Test
    fun `preserves the interval damage timeLeft on reload`() {
        val damage = ConditionDamage(
            id = ConditionId.CONDITIONID_COMBAT,
            type = ConditionType.CONDITION_POISON,
            now = { 0L },
            random = Random(0),
        )
        damage.addDamage(2, 2_000, -20)
        // One 500 ms tick leaves the first round with timeLeft 1500
        // (`ConditionDamage::executeCondition`, condition.cpp:1956-1958).
        damage.executeCondition(FakeConditionTarget(), 500)

        val decoded = roundTrip(damage) as ConditionDamage

        // `ConditionDamage::unserializeProp` re-appends every stored round with
        // its own timeLeft and adds the interval back onto the ticks
        // (condition.cpp:1816-1824). The damage factory drops the header ticks
        // (`ConditionDamage` starts at 0, condition.cpp:1763-1764), so the two
        // rounds rebuild 2 x 2000 like the wave-59 decode did.
        assertThat(decoded.scheduledDamage.map { it.timeLeft }).containsExactly(1_500, 2_000)
        assertThat(decoded.scheduledDamage.map { it.interval }).containsExactly(2_000, 2_000)
        assertThat(decoded.scheduledDamage.map { it.value }).containsExactly(-20, -20)
        assertThat(decoded.ticks).isEqualTo(4_000)
    }

    // --- malformed streams ------------------------------------------------------

    @Test
    fun `decodeConditions returns an empty list for a truncated header`() {
        assertThat(persistence.decodeConditions(byteArrayOf(1, 20, 2))).isEmpty()
    }

    @Test
    fun `decodeConditions drops a condition with an unknown attribute`() {
        val blob = conditionBlob(type = ConditionType.CONDITION_INFIGHT, ticks = 5_000) {
            writeU8(200)
            writeI32(1)
        }

        assertThat(persistence.decodeConditions(blob)).isEmpty()
    }

    @Test
    fun `decodeConditions fails safely on an overflowed skill index`() {
        // The C++ `skills[currentSkill++]` write (condition.cpp:751) is out of
        // bounds for the fourteenth SKILLS record; the port rejects the record
        // instead of writing past the array.
        val blob = conditionBlob(type = ConditionType.CONDITION_ATTRIBUTES, ticks = 10_000) {
            repeat(14) {
                writeU8(CONDITION_ATTR_SKILLS)
                writeI32(1)
            }
        }

        assertThat(persistence.decodeConditions(blob)).isEmpty()
    }

    @Test
    fun `decodeConditions skips an out-of-range absorb index like canary`() {
        val blob = conditionBlob(type = ConditionType.CONDITION_ATTRIBUTES, ticks = 10_000) {
            writeU8(CONDITION_ATTR_ABSORBS)
            for (index in 0 until 14) {
                writeU8(if (index == 0) 200 else index)
                writeI32(if (index == 1) 30 else 0)
            }
        }

        val decoded = persistence.decodeConditions(blob).single() as ConditionAttributes

        // `setAbsorb` catches the out-of-range index and keeps going
        // (condition.cpp:1360-1366).
        val target = FakeConditionTarget()
        decoded.startCondition(target)
        assertThat(target.absorbValues[CombatType.COMBAT_FIREDAMAGE]).isEqualTo(30)
    }

    // --- canary layout ----------------------------------------------------------

    @Test
    fun `serializes the attributes payload in the canary layout`() {
        val source = attributes()
        source.setParam(ConditionParam.CONDITION_PARAM_SKILL_SWORD, 7)
        source.setParam(ConditionParam.CONDITION_PARAM_STAT_MAXHITPOINTS, 25)
        source.setParam(ConditionParam.CONDITION_PARAM_BUFF_HARMONYBONUS, 150)
        source.setParam(ConditionParam.CONDITION_PARAM_ABSORB_FIREPERCENT, 30)
        source.setParam(ConditionParam.CONDITION_PARAM_INCREASE_ENERGYPERCENT, 20)
        source.setParam(ConditionParam.CONDITION_PARAM_CHARM_CHANCE_MODIFIER, 25)
        source.startCondition(
            FakeConditionTarget().apply {
                buffValues[Buff.BUFF_HARMONYBONUS] = 20
            },
        )

        val blob = persistence.encodeConditions(listOf(source))

        val expected = PropWriteStream().apply {
            writeU8(CONDITION_ATTR_TYPE)
            writeU8(ConditionType.CONDITION_ATTRIBUTES.value)
            writeU8(CONDITION_ATTR_ID)
            writeU32(0xFFFFFFFFL)
            writeU8(CONDITION_ATTR_TICKS)
            writeI32(10_000)
            writeU8(CONDITION_ATTR_ISBUFF)
            writeU8(0)
            writeU8(CONDITION_ATTR_SUBID)
            writeU32(0)
            writeU8(CONDITION_ATTR_TICKSOUND)
            writeU16(0)
            writeU8(CONDITION_ATTR_ADDSOUND)
            writeU16(0)
            writeU8(CONDITION_ATTR_PERSISTENT)
            writeU8(0)

            for (index in 0..12) {
                writeU8(CONDITION_ATTR_SKILLS)
                writeI32(if (index == Skill.SKILL_SWORD.value) 7 else 0)
            }
            for (index in 0..4) {
                writeU8(CONDITION_ATTR_STATS)
                writeI32(if (index == Stat.STAT_MAXHITPOINTS.value) 25 else 0)
            }
            for (index in 0..6) {
                writeU8(CONDITION_ATTR_BUFFS)
                writeI32(if (index == Buff.BUFF_HARMONYBONUS.value) 10 else 0)
            }

            writeU8(CONDITION_ATTR_ABSORBS)
            for (index in 0 until 14) {
                writeU8(index)
                writeI32(if (index == CombatType.COMBAT_FIREDAMAGE.value) 30 else 0)
            }

            writeU8(CONDITION_ATTR_INCREASES)
            for (index in 0 until 14) {
                writeU8(index)
                writeI32(if (index == CombatType.COMBAT_ENERGYDAMAGE.value) 20 else 0)
            }

            writeU8(CONDITION_ATTR_CHARM_CHANCE_MODIFIER)
            writeU8(25)
            writeU8(CONDITION_ATTR_END)
        }.getStream()

        assertThat(blob).isEqualTo(expected)
    }

    // --- fixtures ---------------------------------------------------------------

    private fun conditionBlob(
        type: ConditionType,
        ticks: Int,
        id: ConditionId = ConditionId.CONDITIONID_DEFAULT,
        body: PropWriteStream.() -> Unit,
    ): ByteArray = PropWriteStream().apply {
        writeU8(CONDITION_ATTR_TYPE)
        writeU8(type.value)
        writeU8(CONDITION_ATTR_ID)
        writeU32(id.value.toLong() and 0xFFFFFFFFL)
        writeU8(CONDITION_ATTR_TICKS)
        writeI32(ticks)
        writeU8(CONDITION_ATTR_ISBUFF)
        writeU8(0)
        writeU8(CONDITION_ATTR_SUBID)
        writeU32(0)
        writeU8(CONDITION_ATTR_TICKSOUND)
        writeU16(0)
        writeU8(CONDITION_ATTR_ADDSOUND)
        writeU16(0)
        writeU8(CONDITION_ATTR_PERSISTENT)
        writeU8(0)
        body()
        writeU8(CONDITION_ATTR_END)
    }.getStream()

    private companion object {
        const val CONDITION_ATTR_TYPE = 1
        const val CONDITION_ATTR_ID = 2
        const val CONDITION_ATTR_TICKS = 3
        const val CONDITION_ATTR_SKILLS = 22
        const val CONDITION_ATTR_STATS = 23
        const val CONDITION_ATTR_BUFFS = 24
        const val CONDITION_ATTR_ISBUFF = 27
        const val CONDITION_ATTR_SUBID = 28
        const val CONDITION_ATTR_ADDSOUND = 30
        const val CONDITION_ATTR_TICKSOUND = 31
        const val CONDITION_ATTR_ABSORBS = 32
        const val CONDITION_ATTR_INCREASES = 33
        const val CONDITION_ATTR_CHARM_CHANCE_MODIFIER = 34
        const val CONDITION_ATTR_PERSISTENT = 35
        const val CONDITION_ATTR_END = 254
    }
}
