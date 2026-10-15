package org.opentibia.canary.combat.condition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream
import org.opentibia.canary.spells.SoundEffect
import kotlin.random.Random

/**
 * Wave 63 A3 transcript tests for the class-level `Condition::serialize` /
 * `Condition::unserialize` / `Condition::unserializeProp` family
 * (src/creatures/combat/condition.cpp:189-301 and the subclass overrides) and
 * the `Condition::createCondition(PropStream&)` factory
 * (condition.cpp:414-462).
 *
 * The round trip writes through the condition's own `serialize` override and
 * reads through `createCondition` + `unserialize`, exactly like Canary's
 * `IOLoginDataLoad::loadPlayerConditions` loop
 * (iologindata_load_player.cpp:257-275).
 */
class ConditionStreamCodecTest {

    private fun roundTrip(condition: Condition): Condition {
        val writer = PropWriteStream()
        condition.serialize(writer)
        writer.writeU8(CONDITION_ATTR_END)

        val reader = PropStream(writer.getStream())
        val decoded = Condition.createCondition(reader) ?: error("createCondition returned null")
        assertThat(decoded.unserialize(reader)).isTrue()
        return decoded
    }

    @Test
    fun `setParam retains the configured sound ids`() {
        val condition = ConditionGeneric(
            id = ConditionId.CONDITIONID_DEFAULT,
            type = ConditionType.CONDITION_INFIGHT,
            ticks = 5_000,
            now = { 0L },
        )

        assertThat(condition.setParam(ConditionParam.CONDITION_PARAM_SOUND_TICK, SoundEffect.MELEE_ATK_SWORD.value)).isTrue()
        assertThat(condition.setParam(ConditionParam.CONDITION_PARAM_SOUND_ADD, SoundEffect.SPELL_HASTE.value)).isTrue()

        assertThat(condition.tickSound).isEqualTo(SoundEffect.MELEE_ATK_SWORD)
        assertThat(condition.addSound).isEqualTo(SoundEffect.SPELL_HASTE)
    }

    @Test
    fun `clone keeps the configured sounds`() {
        val condition = ConditionGeneric(
            id = ConditionId.CONDITIONID_DEFAULT,
            type = ConditionType.CONDITION_INFIGHT,
            ticks = 5_000,
            now = { 0L },
        )
        condition.setParam(ConditionParam.CONDITION_PARAM_SOUND_TICK, SoundEffect.MELEE_ATK_AXE.value)
        condition.setParam(ConditionParam.CONDITION_PARAM_SOUND_ADD, SoundEffect.SPELL_HASTE.value)

        val copy = condition.clone()

        assertThat(copy.tickSound).isEqualTo(SoundEffect.MELEE_ATK_AXE)
        assertThat(copy.addSound).isEqualTo(SoundEffect.SPELL_HASTE)
    }

    @Test
    fun `round trips the base condition fields and sounds`() {
        val condition = ConditionGeneric(
            id = ConditionId.CONDITIONID_DEFAULT,
            type = ConditionType.CONDITION_INFIGHT,
            ticks = 5_000,
            buff = true,
            subId = 7,
            now = { 0L },
        )
        condition.setParam(ConditionParam.CONDITION_PARAM_SOUND_TICK, SoundEffect.MELEE_ATK_AXE.value)
        condition.setParam(ConditionParam.CONDITION_PARAM_SOUND_ADD, SoundEffect.SPELL_HASTE.value)

        val decoded = roundTrip(condition)

        assertThat(decoded.id).isEqualTo(ConditionId.CONDITIONID_DEFAULT)
        assertThat(decoded.type).isEqualTo(ConditionType.CONDITION_INFIGHT)
        assertThat(decoded.ticks).isEqualTo(5_000)
        assertThat(decoded.isBuff).isTrue()
        assertThat(decoded.subId).isEqualTo(7)
        assertThat(decoded.tickSound).isEqualTo(SoundEffect.MELEE_ATK_AXE)
        assertThat(decoded.addSound).isEqualTo(SoundEffect.SPELL_HASTE)
    }

    @Test
    fun `round trips the regeneration payload`() {
        val condition = ConditionRegeneration(
            id = ConditionId.CONDITIONID_COMBAT,
            type = ConditionType.CONDITION_REGENERATION,
            ticks = 10_000,
            now = { 0L },
        )
        condition.setParam(ConditionParam.CONDITION_PARAM_HEALTHTICKS, 1_500)
        condition.setParam(ConditionParam.CONDITION_PARAM_HEALTHGAIN, 30)
        condition.setParam(ConditionParam.CONDITION_PARAM_MANATICKS, 900)
        condition.setParam(ConditionParam.CONDITION_PARAM_MANAGAIN, 15)

        val decoded = roundTrip(condition) as ConditionRegeneration

        assertThat(decoded.healthTicks).isEqualTo(1_500)
        assertThat(decoded.healthGain).isEqualTo(30)
        assertThat(decoded.manaTicks).isEqualTo(900)
        assertThat(decoded.manaGain).isEqualTo(15)
    }

    @Test
    fun `round trips the soul payload`() {
        val condition = ConditionSoul(
            id = ConditionId.CONDITIONID_COMBAT,
            type = ConditionType.CONDITION_SOUL,
            ticks = 10_000,
            now = { 0L },
        )
        condition.setParam(ConditionParam.CONDITION_PARAM_SOULGAIN, 2)
        condition.setParam(ConditionParam.CONDITION_PARAM_SOULTICKS, 2_000)

        val decoded = roundTrip(condition) as ConditionSoul

        assertThat(decoded.soulGain).isEqualTo(2)
        assertThat(decoded.soulTicks).isEqualTo(2_000)
    }

    @Test
    fun `round trips the mana shield payload`() {
        val condition = ConditionManaShield(
            id = ConditionId.CONDITIONID_COMBAT,
            type = ConditionType.CONDITION_MANASHIELD,
            ticks = 10_000,
            now = { 0L },
        )
        condition.setParam(ConditionParam.CONDITION_PARAM_MANASHIELD, 500)

        val decoded = roundTrip(condition) as ConditionManaShield

        assertThat(decoded.manaShield).isEqualTo(500)
    }

    @Test
    fun `round trips the speed payload with the formula bits and owner`() {
        val condition = ConditionSpeed(
            id = ConditionId.CONDITIONID_COMBAT,
            type = ConditionType.CONDITION_HASTE,
            ticks = 10_000,
            initChangeSpeed = 120,
            now = { 0L },
            random = Random(0),
        )
        condition.setFormulaVars(0.5f, 1.0f, 2.0f, 3.0f)
        condition.setParam(ConditionParam.CONDITION_PARAM_OWNER, 42)

        val decoded = roundTrip(condition) as ConditionSpeed

        assertThat(decoded.speedDelta).isEqualTo(120)
        assertThat(decoded.mina).isEqualTo(0.5f)
        assertThat(decoded.minb).isEqualTo(1.0f)
        assertThat(decoded.maxa).isEqualTo(2.0f)
        assertThat(decoded.maxb).isEqualTo(3.0f)
        assertThat(decoded.owner).isEqualTo(42)
    }

    @Test
    fun `round trips the outfit payload`() {
        val condition = ConditionOutfit(
            id = ConditionId.CONDITIONID_COMBAT,
            type = ConditionType.CONDITION_OUTFIT,
            ticks = 10_000,
            now = { 0L },
        )
        condition.setOutfit(
            Outfit(
                lookType = 136,
                lookTypeEx = 7,
                lookMount = 20,
                lookHead = 10,
                lookBody = 20,
                lookLegs = 30,
                lookFeet = 40,
                lookAddons = 3,
                lookMountHead = 1,
                lookMountBody = 2,
                lookMountLegs = 3,
                lookMountFeet = 4,
                lookFamiliarsType = 5,
                lookWing = 6,
                lookAura = 7,
                lookEffect = 8,
                lookShader = 9,
            ),
        )

        val decoded = roundTrip(condition) as ConditionOutfit

        assertThat(decoded.outfit.lookType).isEqualTo(136)
        assertThat(decoded.outfit.lookTypeEx).isEqualTo(7)
        assertThat(decoded.outfit.lookMount).isEqualTo(20)
        assertThat(decoded.outfit.lookHead).isEqualTo(10)
        assertThat(decoded.outfit.lookBody).isEqualTo(20)
        assertThat(decoded.outfit.lookLegs).isEqualTo(30)
        assertThat(decoded.outfit.lookFeet).isEqualTo(40)
        assertThat(decoded.outfit.lookAddons).isEqualTo(3)
        assertThat(decoded.outfit.lookMountHead).isEqualTo(1)
        assertThat(decoded.outfit.lookMountBody).isEqualTo(2)
        assertThat(decoded.outfit.lookMountLegs).isEqualTo(3)
        assertThat(decoded.outfit.lookMountFeet).isEqualTo(4)
        assertThat(decoded.outfit.lookFamiliarsType).isEqualTo(5)
        assertThat(decoded.outfit.lookWing).isEqualTo(6)
        assertThat(decoded.outfit.lookAura).isEqualTo(7)
        assertThat(decoded.outfit.lookEffect).isEqualTo(8)
        assertThat(decoded.outfit.lookShader).isEqualTo(9)
    }

    @Test
    fun `round trips the light payload`() {
        val condition = ConditionLight(
            id = ConditionId.CONDITIONID_COMBAT,
            type = ConditionType.CONDITION_LIGHT,
            ticks = 6_000,
            initLightlevel = 6,
            initLightcolor = 215,
            now = { 0L },
        )
        condition.startCondition(FakeConditionTarget())
        condition.executeCondition(FakeConditionTarget(), 250)

        val decoded = roundTrip(condition) as ConditionLight

        assertThat(decoded.lightInfo.level).isEqualTo(6)
        assertThat(decoded.lightInfo.color).isEqualTo(215)
        assertThat(decoded.lightChangeInterval).isEqualTo(1_000)
        assertThat(decoded.internalLightTicks).isEqualTo(250)
    }

    @Test
    fun `unserialize keeps a condition when the stream ends at an attribute boundary`() {
        val writer = PropWriteStream()
        writer.writeU8(CONDITION_ATTR_TYPE)
        writer.writeU8(ConditionType.CONDITION_INFIGHT.value)
        writer.writeU8(CONDITION_ATTR_ID)
        writer.writeU32(0xFFFFFFFFL)
        writer.writeU8(CONDITION_ATTR_TICKS)
        writer.writeI32(5_000)
        writer.writeU8(CONDITION_ATTR_ISBUFF)
        writer.writeU8(0)
        writer.writeU8(CONDITION_ATTR_SUBID)
        writer.writeU32(0)
        writer.writeU8(CONDITION_ATTR_TICKSOUND)
        writer.writeU16(SoundEffect.MELEE_ATK_SWORD.value)
        writer.writeU8(CONDITION_ATTR_ADDSOUND)
        writer.writeU16(0)
        writer.writeU8(CONDITION_ATTR_PERSISTENT)
        writer.writeU8(1)
        // The stream ends before CONDITIONATTR_END: the C++ while loop exits on
        // the failed read and keeps the condition (condition.cpp:189-197).

        val reader = PropStream(writer.getStream())
        val decoded = Condition.createCondition(reader)!!
        assertThat(decoded.unserialize(reader)).isTrue()

        assertThat(decoded.tickSound).isEqualTo(SoundEffect.MELEE_ATK_SWORD)
        assertThat(decoded.isPersistentFlag).isTrue()
    }

    @Test
    fun `unserialize drops a condition on an unknown attribute`() {
        val writer = PropWriteStream()
        writer.writeU8(CONDITION_ATTR_TYPE)
        writer.writeU8(ConditionType.CONDITION_INFIGHT.value)
        writer.writeU8(CONDITION_ATTR_ID)
        writer.writeU32(0xFFFFFFFFL)
        writer.writeU8(CONDITION_ATTR_TICKS)
        writer.writeI32(5_000)
        writer.writeU8(CONDITION_ATTR_ISBUFF)
        writer.writeU8(0)
        writer.writeU8(CONDITION_ATTR_SUBID)
        writer.writeU32(0)
        writer.writeU8(200)
        writer.writeI32(1)
        writer.writeU8(CONDITION_ATTR_END)

        val reader = PropStream(writer.getStream())
        val decoded = Condition.createCondition(reader)!!

        assertThat(decoded.unserialize(reader)).isFalse()
    }

    @Test
    fun `createCondition returns null when the header attribute order is broken`() {
        val writer = PropWriteStream()
        writer.writeU8(CONDITION_ATTR_TYPE)
        writer.writeU8(ConditionType.CONDITION_INFIGHT.value)
        writer.writeU8(CONDITION_ATTR_TICKS)
        writer.writeI32(5_000)

        assertThat(Condition.createCondition(PropStream(writer.getStream()))).isNull()
    }

    private companion object {
        const val CONDITION_ATTR_TYPE = 1
        const val CONDITION_ATTR_ID = 2
        const val CONDITION_ATTR_TICKS = 3
        const val CONDITION_ATTR_ISBUFF = 27
        const val CONDITION_ATTR_SUBID = 28
        const val CONDITION_ATTR_ADDSOUND = 30
        const val CONDITION_ATTR_TICKSOUND = 31
        const val CONDITION_ATTR_PERSISTENT = 35
        const val CONDITION_ATTR_END = 254
    }
}
