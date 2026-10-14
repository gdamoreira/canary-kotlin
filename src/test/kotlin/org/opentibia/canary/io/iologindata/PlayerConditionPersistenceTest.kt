package org.opentibia.canary.io.iologindata

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionDamage
import org.opentibia.canary.combat.condition.ConditionGeneric
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionParam
import org.opentibia.canary.combat.condition.ConditionSpeed
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.io.PropWriteStream
import org.opentibia.canary.map.Position
import kotlin.random.Random

/**
 * Wave 59 A3 transcript tests for the `Condition::serialize` half of the
 * condition codec (`src/creatures/combat/condition.cpp:277-301` plus the
 * subclass overrides at :786-825, :1474-1488, :1652-1656, :1712-1720,
 * :1832-1848, :2510-2535, :2682-2686, :2862-2881) driven by the
 * `IOLoginDataSave::savePlayerFirst` loop
 * (src/io/functions/iologindata_save_player.cpp:279-291).
 *
 * The expected bytes are built with the same `PropWriteStream` the loader half
 * (`IOLoginDataSubloads`) reads, so a passing comparison proves the exact
 * Canary layout (attribute order, widths and `CONDITIONATTR_END` framing).
 */
class PlayerConditionPersistenceTest {

    private val persistence = PlayerConditionPersistence()

    private fun newPlayer(): Player = Player(
        id = 1L,
        name = "Condition Hero",
        maxHealth = 1,
        health = 1,
        position = Position(1, 1, 7),
    )

    @Test
    fun `savePlayerConditions serializes a persistent condition with the canary layout`() {
        val condition = Condition.createCondition(
            id = ConditionId.CONDITIONID_DEFAULT,
            type = ConditionType.CONDITION_INFIGHT,
            ticks = 5_000,
        )!!

        val blob = persistence.encodeConditions(listOf(condition))

        val expected = PropWriteStream().apply {
            writeU8(CONDITION_ATTR_TYPE)
            writeU8(ConditionType.CONDITION_INFIGHT.value)
            writeU8(CONDITION_ATTR_ID)
            writeU32(0xFFFFFFFFL)
            writeU8(CONDITION_ATTR_TICKS)
            writeI32(5_000)
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
            writeU8(CONDITION_ATTR_END)
        }.getStream()

        assertThat(blob).isEqualTo(expected)
    }

    @Test
    fun `savePlayerConditions serializes the speed payload`() {
        val speed = ConditionSpeed(
            id = ConditionId.CONDITIONID_COMBAT,
            type = ConditionType.CONDITION_HASTE,
            ticks = 10_000,
            buff = false,
            subId = 0,
            initChangeSpeed = 120,
            now = { 0L },
            random = Random(0),
        )
        speed.setFormulaVars(0.5f, 1.0f, 2.0f, 3.0f)
        speed.setParam(ConditionParam.CONDITION_PARAM_OWNER, 42)

        val blob = persistence.encodeConditions(listOf(speed))

        val expected = PropWriteStream().apply {
            writeU8(CONDITION_ATTR_TYPE)
            writeU8(ConditionType.CONDITION_HASTE.value)
            writeU8(CONDITION_ATTR_ID)
            writeU32(ConditionId.CONDITIONID_COMBAT.value.toLong())
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
            writeU8(CONDITION_ATTR_SPEEDDELTA)
            writeI32(120)
            writeU8(CONDITION_ATTR_FORMULA_MINA)
            writeU32(0.5f.toRawBits().toLong() and 0xFFFFFFFFL)
            writeU8(CONDITION_ATTR_FORMULA_MINB)
            writeU32(1.0f.toRawBits().toLong() and 0xFFFFFFFFL)
            writeU8(CONDITION_ATTR_FORMULA_MAXA)
            writeU32(2.0f.toRawBits().toLong() and 0xFFFFFFFFL)
            writeU8(CONDITION_ATTR_FORMULA_MAXB)
            writeU32(3.0f.toRawBits().toLong() and 0xFFFFFFFFL)
            writeU8(CONDITION_ATTR_OWNER)
            writeU32(42)
            writeU8(CONDITION_ATTR_END)
        }.getStream()

        assertThat(blob).isEqualTo(expected)
    }

    @Test
    fun `savePlayerConditions serializes the damage payload and interval rounds`() {
        val damage = ConditionDamage(
            id = ConditionId.CONDITIONID_COMBAT,
            type = ConditionType.CONDITION_POISON,
            buff = false,
            subId = 3,
            now = { 0L },
            random = Random(0),
        )
        damage.setParam(ConditionParam.CONDITION_PARAM_OWNER, 42)
        damage.setParam(ConditionParam.CONDITION_PARAM_DELAYED, 1)
        damage.addDamage(2, 2_000, -20)

        val blob = persistence.encodeConditions(listOf(damage))

        val expected = PropWriteStream().apply {
            writeU8(CONDITION_ATTR_TYPE)
            writeU8(ConditionType.CONDITION_POISON.value)
            writeU8(CONDITION_ATTR_ID)
            writeU32(ConditionId.CONDITIONID_COMBAT.value.toLong())
            writeU8(CONDITION_ATTR_TICKS)
            writeI32(4_000)
            writeU8(CONDITION_ATTR_ISBUFF)
            writeU8(0)
            writeU8(CONDITION_ATTR_SUBID)
            writeU32(3)
            writeU8(CONDITION_ATTR_TICKSOUND)
            writeU16(0)
            writeU8(CONDITION_ATTR_ADDSOUND)
            writeU16(0)
            writeU8(CONDITION_ATTR_PERSISTENT)
            writeU8(0)
            writeU8(CONDITION_ATTR_DELAYED)
            writeU8(1)
            writeU8(CONDITION_ATTR_PERIODDAMAGE)
            writeI32(0)
            writeU8(CONDITION_ATTR_OWNER)
            writeU32(42)
            repeat(2) {
                writeU8(CONDITION_ATTR_INTERVALDATA)
                writeI32(2_000)
                writeI32(-20)
                writeI32(2_000)
            }
            writeU8(CONDITION_ATTR_END)
        }.getStream()

        assertThat(blob).isEqualTo(expected)
    }

    @Test
    fun `savePlayerConditions writes the persistent flag attribute`() {
        val bakragore = Condition.createCondition(
            id = ConditionId.CONDITIONID_DEFAULT,
            type = ConditionType.CONDITION_BAKRAGORE,
            ticks = 1_000,
            isPersistent = true,
        )!!

        val blob = persistence.encodeConditions(listOf(bakragore))
        val stream = PropWriteStream().apply {
            writeU8(CONDITION_ATTR_TYPE)
            writeU8(ConditionType.CONDITION_BAKRAGORE.value)
            writeU8(CONDITION_ATTR_ID)
            writeU32(0xFFFFFFFFL)
            writeU8(CONDITION_ATTR_TICKS)
            writeI32(1_000)
            writeU8(CONDITION_ATTR_ISBUFF)
            writeU8(0)
            writeU8(CONDITION_ATTR_SUBID)
            writeU32(0)
            writeU8(CONDITION_ATTR_TICKSOUND)
            writeU16(0)
            writeU8(CONDITION_ATTR_ADDSOUND)
            writeU16(0)
            writeU8(CONDITION_ATTR_PERSISTENT)
            writeU8(1)
            writeU8(CONDITION_ATTR_END)
        }.getStream()

        assertThat(blob).isEqualTo(stream)
    }

    @Test
    fun `savePlayerConditions skips the non-persistent conditions`() {
        val immortal = ConditionGeneric(
            id = ConditionId.CONDITIONID_DEFAULT,
            type = ConditionType.CONDITION_INFIGHT,
            ticks = -1,
        )
        val ringCondition = ConditionGeneric(
            id = ConditionId.CONDITIONID_RING,
            type = ConditionType.CONDITION_INFIGHT,
            ticks = 1_000,
        )
        val combat = ConditionGeneric(
            id = ConditionId.CONDITIONID_COMBAT,
            type = ConditionType.CONDITION_INFIGHT,
            ticks = 1_000,
        )

        val blob = persistence.encodeConditions(listOf(immortal, ringCondition, combat))

        assertThat(blob).isEqualTo(persistence.encodeConditions(listOf(combat)))
    }

    @Test
    fun `savePlayerConditions concatenates every persistent condition with its own end marker`() {
        val first = Condition.createCondition(
            id = ConditionId.CONDITIONID_DEFAULT,
            type = ConditionType.CONDITION_INFIGHT,
            ticks = 5_000,
        )!!
        val second = Condition.createCondition(
            id = ConditionId.CONDITIONID_COMBAT,
            type = ConditionType.CONDITION_EXHAUST,
            ticks = 7_000,
        )!!

        val blob = persistence.encodeConditions(listOf(first, second))

        assertThat(blob).isEqualTo(persistence.encodeConditions(listOf(first)) + persistence.encodeConditions(listOf(second)))
    }

    @Test
    fun `savePlayerConditions writes an empty blob without conditions`() {
        assertThat(persistence.savePlayerConditions(newPlayer())).isEmpty()
    }

    @Test
    fun `savePlayerConditions serializes both the loaded and the active conditions`() {
        val player = newPlayer()
        val loaded = Condition.createCondition(
            id = ConditionId.CONDITIONID_DEFAULT,
            type = ConditionType.CONDITION_INFIGHT,
            ticks = 5_000,
        )!!
        val active = Condition.createCondition(
            id = ConditionId.CONDITIONID_COMBAT,
            type = ConditionType.CONDITION_EXHAUST,
            ticks = 7_000,
        )!!
        player.storedConditionList.add(loaded)
        player.addCondition(active)

        val blob = persistence.savePlayerConditions(player)

        assertThat(blob).isEqualTo(persistence.encodeConditions(listOf(loaded, active)))
    }

    private companion object {
        const val CONDITION_ATTR_TYPE = 1
        const val CONDITION_ATTR_ID = 2
        const val CONDITION_ATTR_TICKS = 3
        const val CONDITION_ATTR_DELAYED = 8
        const val CONDITION_ATTR_OWNER = 9
        const val CONDITION_ATTR_INTERVALDATA = 10
        const val CONDITION_ATTR_SPEEDDELTA = 11
        const val CONDITION_ATTR_FORMULA_MINA = 12
        const val CONDITION_ATTR_FORMULA_MINB = 13
        const val CONDITION_ATTR_FORMULA_MAXA = 14
        const val CONDITION_ATTR_FORMULA_MAXB = 15
        const val CONDITION_ATTR_PERIODDAMAGE = 26
        const val CONDITION_ATTR_ISBUFF = 27
        const val CONDITION_ATTR_SUBID = 28
        const val CONDITION_ATTR_ADDSOUND = 30
        const val CONDITION_ATTR_TICKSOUND = 31
        const val CONDITION_ATTR_PERSISTENT = 35
        const val CONDITION_ATTR_END = 254
    }
}
