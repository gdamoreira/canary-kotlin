package org.opentibia.canary.combat.condition

import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream
import kotlin.random.Random

/**
 * `ConditionSpeed` (`src/creatures/combat/condition.cpp:2461-2635`,
 * `condition.hpp:382-412`).
 *
 * Handles both `CONDITION_HASTE` and `CONDITION_PARALYZE`; Canary has no
 * separate `ConditionParalyze` class — a paralyze *is* a `ConditionSpeed`
 * whose `speedDelta` is negative (see [setParam]). The speed change is applied
 * through [ConditionTarget.setSpeed] (`g_game().changeSpeed`).
 *
 * The imbuement-vibrancy deflection block (`condition.cpp:2570-2582`) depends
 * on inventory item imbuements and is not part of this engine port (PARITY GAP).
 */
class ConditionSpeed(
    id: ConditionId,
    type: ConditionType,
    ticks: Int,
    buff: Boolean = false,
    subId: Int = 0,
    initChangeSpeed: Int = 0,
    now: () -> Long = { System.currentTimeMillis() },
    private val random: Random = Random.Default,
) : Condition(id, type, ticks, buff, subId, false, now) {

    /** `ConditionSpeed::speedDelta` (`condition.hpp:405`). */
    var speedDelta: Int = initChangeSpeed
        private set

    /** `ConditionSpeed::mina` (`condition.hpp:408`). */
    var mina: Float = 0.0f
        private set

    /** `ConditionSpeed::minb` (`condition.hpp:409`). */
    var minb: Float = 0.0f
        private set

    /** `ConditionSpeed::maxa` (`condition.hpp:410`). */
    var maxa: Float = 0.0f
        private set

    /** `ConditionSpeed::maxb` (`condition.hpp:411`). */
    var maxb: Float = 0.0f
        private set

    /** `ConditionSpeed::setFormulaVars` (`condition.cpp:2461-2466`). */
    fun setFormulaVars(newMina: Float, newMinb: Float, newMaxa: Float, newMaxb: Float) {
        mina = newMina
        minb = newMinb
        maxa = newMaxa
        maxb = newMaxb
    }

    /** `ConditionSpeed::getFormulaValues` (`condition.cpp:2468-2472`). */
    private fun getFormulaValues(variable: Int): Pair<Int, Int> {
        val difference = variable - 40
        val min = (mina * difference + minb).toInt()
        val max = (maxa * difference + maxb).toInt()
        return min to max
    }

    /** `ConditionSpeed::setParam` (`condition.cpp:2474-2491`). */
    override fun setParam(param: ConditionParam, value: Int): Boolean {
        super.setParam(param, value)
        if (param == ConditionParam.CONDITION_PARAM_OWNER) {
            return true
        }
        if (param != ConditionParam.CONDITION_PARAM_SPEED) {
            return false
        }

        speedDelta = value
        type = if (value > 0) ConditionType.CONDITION_HASTE else ConditionType.CONDITION_PARALYZE
        return true
    }

    /** `ConditionSpeed::startCondition` (`condition.cpp:2537-2555`). */
    override fun startCondition(target: ConditionTarget?): Boolean {
        if (!super.startCondition(target)) {
            return false
        }

        if (target == null) {
            return true
        }

        if (speedDelta == 0) {
            val baseSpeed = target.getBaseSpeed()
            val (min, max) = getFormulaValues(baseSpeed)
            speedDelta = uniformRandom(min, max) - baseSpeed
            if (type == ConditionType.CONDITION_PARALYZE && speedDelta < 40 - baseSpeed) {
                speedDelta = 40 - baseSpeed
            }
        }

        target.setSpeed(speedDelta)
        return true
    }

    /** `ConditionSpeed::executeCondition` (`condition.cpp:2557-2559`). */
    override fun executeCondition(target: ConditionTarget?, interval: Int): Boolean =
        super.executeCondition(target, interval)

    /** `ConditionSpeed::endCondition` (`condition.cpp:2561-2563`). */
    override fun endCondition(target: ConditionTarget?) {
        target?.setSpeed(-speedDelta)
    }

    /** `ConditionSpeed::addCondition` (`condition.cpp:2565-2614`). */
    override fun addCondition(target: ConditionTarget?, condition: Condition) {
        if (type != condition.type) {
            return
        }

        // Imbuement-vibrancy deflection (`condition.cpp:2570-2582`) is omitted.

        if (ticks == -1 && condition.ticks > 0) {
            return
        }

        setTicks(condition.ticks)

        val conditionSpeed = condition as ConditionSpeed
        val oldSpeedDelta = speedDelta
        speedDelta = conditionSpeed.speedDelta
        mina = conditionSpeed.mina
        maxa = conditionSpeed.maxa
        minb = conditionSpeed.minb
        maxb = conditionSpeed.maxb

        if (target != null && speedDelta == 0) {
            val baseSpeed = target.getBaseSpeed()
            val (min, max) = getFormulaValues(baseSpeed)
            speedDelta = uniformRandom(min, max) - baseSpeed

            if (type == ConditionType.CONDITION_PARALYZE && speedDelta < 40 - baseSpeed) {
                speedDelta = 40 - baseSpeed
            }
        }

        val newSpeedChange = speedDelta - oldSpeedDelta
        if (newSpeedChange != 0) {
            target?.setSpeed(newSpeedChange)
        }
    }

    /** `ConditionSpeed::serialize` (`condition.cpp:2510-2535`). */
    override fun serialize(stream: PropWriteStream) {
        super.serialize(stream)

        stream.writeU8(ConditionAttr.CONDITIONATTR_SPEEDDELTA.value)
        stream.writeI32(speedDelta)

        stream.writeU8(ConditionAttr.CONDITIONATTR_FORMULA_MINA.value)
        stream.writeU32(mina.toRawBits().toLong() and 0xFFFFFFFFL)

        stream.writeU8(ConditionAttr.CONDITIONATTR_FORMULA_MINB.value)
        stream.writeU32(minb.toRawBits().toLong() and 0xFFFFFFFFL)

        stream.writeU8(ConditionAttr.CONDITIONATTR_FORMULA_MAXA.value)
        stream.writeU32(maxa.toRawBits().toLong() and 0xFFFFFFFFL)

        stream.writeU8(ConditionAttr.CONDITIONATTR_FORMULA_MAXB.value)
        stream.writeU32(maxb.toRawBits().toLong() and 0xFFFFFFFFL)

        if (owner != 0) {
            stream.writeU8(ConditionAttr.CONDITIONATTR_OWNER.value)
            stream.writeU32(owner.toLong() and 0xFFFFFFFFL)
        }
    }

    /** `ConditionSpeed::unserializeProp` (`condition.cpp:2493-2508`). */
    override fun unserializeProp(attr: ConditionAttr, stream: PropStream): Boolean {
        when (attr) {
            ConditionAttr.CONDITIONATTR_SPEEDDELTA ->
                speedDelta = stream.readI32() ?: return false

            ConditionAttr.CONDITIONATTR_OWNER ->
                owner = stream.readU32()?.toInt() ?: return false

            ConditionAttr.CONDITIONATTR_FORMULA_MINA ->
                mina = Float.fromBits(stream.readI32() ?: return false)

            ConditionAttr.CONDITIONATTR_FORMULA_MINB ->
                minb = Float.fromBits(stream.readI32() ?: return false)

            ConditionAttr.CONDITIONATTR_FORMULA_MAXA ->
                maxa = Float.fromBits(stream.readI32() ?: return false)

            ConditionAttr.CONDITIONATTR_FORMULA_MAXB ->
                maxb = Float.fromBits(stream.readI32() ?: return false)

            else -> return super.unserializeProp(attr, stream)
        }
        return true
    }

    /** `ConditionSpeed::getIcons` (`condition.cpp:2616-2631`). */
    override fun getIcons(): Set<PlayerIcon> {
        val icons = super.getIcons().toMutableSet()
        when (type) {
            ConditionType.CONDITION_HASTE -> icons.add(PlayerIcon.Haste)
            ConditionType.CONDITION_PARALYZE -> icons.add(PlayerIcon.Paralyze)
            else -> Unit
        }
        return icons
    }

    /** `ConditionSpeed::clone` (`condition.cpp:2633-2635`). */
    override fun clone(): Condition {
        val copy = ConditionSpeed(id, type, ticks, isBuff, subId, speedDelta, now, random)
        copy.apply {
            endTime = this@ConditionSpeed.endTime
            owner = this@ConditionSpeed.owner
            tickSound = this@ConditionSpeed.tickSound
            addSound = this@ConditionSpeed.addSound
            mina = this@ConditionSpeed.mina
            minb = this@ConditionSpeed.minb
            maxa = this@ConditionSpeed.maxa
            maxb = this@ConditionSpeed.maxb
        }
        return copy
    }

    private fun uniformRandom(min: Int, max: Int): Int {
        if (max < min) {
            return min
        }
        return random.nextInt(min, max + 1)
    }
}
