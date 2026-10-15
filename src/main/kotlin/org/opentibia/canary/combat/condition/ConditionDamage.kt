package org.opentibia.canary.combat.condition

import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.random.Random

/**
 * `IntervalInfo` (`src/creatures/creatures_definitions.hpp:1624-1628`).
 * The three int32 fields schedule one scheduled damage round.
 */
data class IntervalInfo(
    var timeLeft: Int,
    var value: Int,
    var interval: Int,
)

/**
 * `ConditionDamage` (`src/creatures/combat/condition.cpp:1761-2160`,
 * `condition.hpp:297-342`).
 *
 * Carries the damage-over-time engine: either a list of [IntervalInfo] rounds
 * (finite: one entry per round) or a single `periodDamage` repeating every
 * [tickInterval] (immortal, `ticks == -1`).
 */
class ConditionDamage(
    id: ConditionId,
    type: ConditionType,
    buff: Boolean = false,
    subId: Int = 0,
    now: () -> Long = { System.currentTimeMillis() },
    private val random: Random = Random.Default,
) : Condition(id, type, 0, buff, subId, false, now) {

    /** `ConditionDamage::maxDamage` (`condition.hpp:323`). */
    var maxDamage: Int = 0
        private set

    /** `ConditionDamage::minDamage` (`condition.hpp:324`). */
    var minDamage: Int = 0
        private set

    /** `ConditionDamage::startDamage` (`condition.hpp:325`). */
    var startDamage: Int = 0
        private set

    /** `ConditionDamage::periodDamage` (`condition.hpp:326`). */
    var periodDamage: Int = 0
        private set

    /** `ConditionDamage::periodDamageTick` (`condition.hpp:327`). */
    private var periodDamageTick: Int = 0

    /** `ConditionDamage::tickInterval` (`condition.hpp:328`), default 2000. */
    var tickInterval: Int = 2000
        private set

    /** `ConditionDamage::forceUpdate` (`condition.hpp:330`). */
    private var forceUpdate: Boolean = false

    /** `ConditionDamage::delayed` (`condition.hpp:331`). */
    var delayed: Boolean = false
        private set

    /** `ConditionDamage::field` (`condition.hpp:332`). */
    private var field: Boolean = false

    /** `ConditionDamage::damageList` (`condition.hpp:336`). */
    private val damageList: MutableList<IntervalInfo> = mutableListOf()

    /** Read-only view of the scheduled rounds, for engine tests. */
    val scheduledDamage: List<IntervalInfo> get() = damageList

    /** `ConditionDamage::setParam` (`condition.cpp:1761-1802`). */
    override fun setParam(param: ConditionParam, value: Int): Boolean {
        val ret = super.setParam(param, value)

        when (param) {
            ConditionParam.CONDITION_PARAM_FORCEUPDATE -> {
                forceUpdate = value != 0
                return true
            }

            ConditionParam.CONDITION_PARAM_DELAYED -> {
                delayed = value != 0
                return true
            }

            ConditionParam.CONDITION_PARAM_MAXVALUE -> maxDamage = abs(value)
            ConditionParam.CONDITION_PARAM_MINVALUE -> minDamage = abs(value)
            ConditionParam.CONDITION_PARAM_STARTVALUE -> startDamage = abs(value)
            ConditionParam.CONDITION_PARAM_TICKINTERVAL -> tickInterval = abs(value)
            ConditionParam.CONDITION_PARAM_PERIODICDAMAGE -> periodDamage = value
            ConditionParam.CONDITION_PARAM_FIELD -> field = value != 0

            else -> return ret
        }

        return ret
    }

    /** `ConditionDamage::updateCondition` (`condition.cpp:1852-1863`). */
    override fun updateCondition(addCondition: Condition): Boolean {
        val conditionDamage = addCondition as ConditionDamage
        if (conditionDamage.doForceUpdate()) {
            return true
        }

        if (ticks == -1 && conditionDamage.ticks > 0) {
            return false
        }

        return conditionDamage.getTotalDamage() > getTotalDamage()
    }

    /**
     * `ConditionDamage::addDamage` (`condition.cpp:1865-1894`).
     *
     * `rounds == -1` switches to periodic damage and forces an immortal
     * (`ticks == -1`) condition with [tickInterval] = [time].
     */
    fun addDamage(rounds: Int, time: Int, value: Int): Boolean {
        val interval = maxOf(time, EVENT_CREATURE_THINK_INTERVAL)
        if (rounds == -1) {
            periodDamage = value
            setParam(ConditionParam.CONDITION_PARAM_TICKINTERVAL, interval)
            setParam(ConditionParam.CONDITION_PARAM_TICKS, -1)
            return true
        }

        if (periodDamage > 0) {
            return false
        }

        repeat(rounds) {
            damageList.add(IntervalInfo(timeLeft = interval, value = value, interval = interval))
            if (ticks != -1) {
                setTicks(ticks + interval)
            }
        }

        return true
    }

    /** `ConditionDamage::doForceUpdate` (`condition.cpp:1896-1898`). */
    fun doForceUpdate(): Boolean = forceUpdate

    /** `ConditionDamage::init` (`condition.cpp:1900-1924`). */
    private fun init(): Boolean {
        if (periodDamage != 0) {
            return true
        }

        if (damageList.isEmpty()) {
            setTicks(0)

            val amount = uniformRandom(minDamage, maxDamage)
            if (amount != 0) {
                if (startDamage > maxDamage) {
                    startDamage = maxDamage
                } else if (startDamage == 0) {
                    startDamage = maxOf(1, ceil(amount / 20.0).toInt())
                }

                for (value in generateDamageList(amount, startDamage)) {
                    addDamage(1, tickInterval, -value)
                }
            }
        }

        return damageList.isNotEmpty()
    }

    /** `ConditionDamage::startCondition` (`condition.cpp:1926-1942`). */
    override fun startCondition(target: ConditionTarget?): Boolean {
        if (!super.startCondition(target)) {
            return false
        }

        if (!init()) {
            return false
        }

        if (!delayed) {
            val damage = getNextDamage()
            if (damage != null) {
                return doDamage(target, damage, true)
            }
        }
        return true
    }

    /** `ConditionDamage::executeCondition` (`condition.cpp:1944-1981`). */
    override fun executeCondition(target: ConditionTarget?, interval: Int): Boolean {
        var effectiveInterval = interval

        if (periodDamage != 0) {
            periodDamageTick += interval

            if (periodDamageTick >= tickInterval) {
                periodDamageTick = 0
                doDamage(target, periodDamage)
            }
        } else if (damageList.isNotEmpty()) {
            val damageInfo = damageList.first()

            // `creature->onTickCondition(getType(), bRemove)` (condition.cpp:1956)
            // is a player-client icon refresh; not part of this engine port.
            val bRemove = ticks != -1
            damageInfo.timeLeft -= interval

            if (damageInfo.timeLeft <= 0) {
                val damage = damageInfo.value

                if (bRemove) {
                    damageList.removeAt(0)
                } else {
                    damageInfo.timeLeft = damageInfo.interval
                }

                doDamage(target, damage)
            }

            if (!bRemove) {
                if (ticks > 0) {
                    endTime += interval
                }

                effectiveInterval = 0
            }
        }

        return super.executeCondition(target, effectiveInterval)
    }

    /** `ConditionDamage::getNextDamage` (`condition.cpp:1983-1996`). */
    private fun getNextDamage(): Int? {
        if (periodDamage != 0) {
            return periodDamage
        }

        if (damageList.isNotEmpty()) {
            val damageInfo = damageList.first()
            val damage = damageInfo.value
            if (ticks != -1) {
                damageList.removeAt(0)
            }
            return damage
        }

        return null
    }

    /** `ConditionDamage::doDamage` (`condition.cpp:1998-2031`). */
    private fun doDamage(target: ConditionTarget?, healthChange: Int, start: Boolean = false): Boolean {
        if (target == null) {
            return true
        }

        // `owner`-based attacker resolution (condition.cpp:2000-2004) requires
        // the game GUID catalog; without it the suppress check runs as PvE.
        if (target.isSuppress(type, false)) {
            return true
        }

        // `field && creature->getPlayer() && attackerPlayer` halving
        // (condition.cpp:2011-2013) needs a resolved attacker and is skipped.

        if (!target.isAttackable()) {
            // `g_game().addMagicEffect(..., CONST_ME_POFF)` (condition.cpp:2017)
            // is presentation-only.
            return false
        }

        if (healthChange < 0) {
            target.drainHealth(-healthChange)
        } else if (healthChange > 0) {
            target.addHealth(healthChange)
        }

        return true
    }

    /** `ConditionDamage::endCondition` (`condition.cpp:2033-2035`) — no-op. */
    override fun endCondition(target: ConditionTarget?) {
        // No behaviour.
    }

    /** `ConditionDamage::addCondition` (`condition.cpp:2037-2080`). */
    override fun addCondition(target: ConditionTarget?, condition: Condition) {
        if (condition.type != type) {
            return
        }

        if (!updateCondition(condition)) {
            return
        }

        val conditionDamage = condition as ConditionDamage

        setTicks(condition.ticks)
        owner = conditionDamage.owner
        maxDamage = conditionDamage.maxDamage
        minDamage = conditionDamage.minDamage
        startDamage = conditionDamage.startDamage
        tickInterval = conditionDamage.tickInterval
        periodDamage = conditionDamage.periodDamage

        var nextTimeLeft = tickInterval

        if (damageList.isNotEmpty()) {
            nextTimeLeft = damageList.first().timeLeft
            damageList.clear()
        }

        damageList.addAll(conditionDamage.damageList)

        if (init()) {
            if (damageList.isNotEmpty()) {
                damageList.first().timeLeft = nextTimeLeft
            }

            if (!delayed) {
                val damage = getNextDamage()
                if (damage != null) {
                    doDamage(target, damage, true)
                }
            }
        }
    }

    /** `ConditionDamage::getTotalDamage` (`condition.cpp:2082-2093`). */
    fun getTotalDamage(): Int {
        val result = if (damageList.isNotEmpty()) {
            damageList.sumOf { it.value }
        } else {
            minDamage + (maxDamage - minDamage) / 2
        }
        return abs(result)
    }

    /** `ConditionDamage::serialize` (`condition.cpp:1832-1848`). */
    override fun serialize(stream: PropWriteStream) {
        super.serialize(stream)

        stream.writeU8(ConditionAttr.CONDITIONATTR_DELAYED.value)
        stream.writeU8(if (delayed) 1 else 0)

        stream.writeU8(ConditionAttr.CONDITIONATTR_PERIODDAMAGE.value)
        stream.writeI32(periodDamage)

        if (owner != 0) {
            stream.writeU8(ConditionAttr.CONDITIONATTR_OWNER.value)
            stream.writeU32(owner.toLong() and 0xFFFFFFFFL)
        }

        for (intervalInfo in damageList) {
            stream.writeU8(ConditionAttr.CONDITIONATTR_INTERVALDATA.value)
            stream.writeI32(intervalInfo.timeLeft)
            stream.writeI32(intervalInfo.value)
            stream.writeI32(intervalInfo.interval)
        }
    }

    /**
     * `ConditionDamage::unserializeProp` (`condition.cpp:1804-1830`): every
     * stored round is re-appended with its own `timeLeft` and its interval is
     * added back onto `ticks` for finite conditions.
     */
    override fun unserializeProp(attr: ConditionAttr, stream: PropStream): Boolean {
        when (attr) {
            ConditionAttr.CONDITIONATTR_DELAYED -> {
                val value = stream.readU8() ?: return false
                delayed = value != 0
            }

            ConditionAttr.CONDITIONATTR_PERIODDAMAGE ->
                periodDamage = stream.readI32() ?: return false

            ConditionAttr.CONDITIONATTR_OWNER ->
                owner = stream.readU32()?.toInt() ?: return false

            ConditionAttr.CONDITIONATTR_INTERVALDATA -> {
                val timeLeft = stream.readI32() ?: return false
                val value = stream.readI32() ?: return false
                val interval = stream.readI32() ?: return false

                damageList.add(IntervalInfo(timeLeft = timeLeft, value = value, interval = interval))
                if (ticks != -1) {
                    setTicks(ticks + interval)
                }
            }

            else -> return super.unserializeProp(attr, stream)
        }
        return true
    }

    /** `ConditionDamage::getIcons` (`condition.cpp:2095-2134`). */
    override fun getIcons(): Set<PlayerIcon> {
        val icons = super.getIcons().toMutableSet()
        when (type) {
            ConditionType.CONDITION_FIRE -> icons.add(PlayerIcon.Burn)
            ConditionType.CONDITION_ENERGY -> icons.add(PlayerIcon.Energy)
            ConditionType.CONDITION_DROWN -> icons.add(PlayerIcon.Drowning)
            ConditionType.CONDITION_POISON -> icons.add(PlayerIcon.Poison)
            ConditionType.CONDITION_FREEZING -> icons.add(PlayerIcon.Freezing)
            ConditionType.CONDITION_DAZZLED -> icons.add(PlayerIcon.Dazzled)
            ConditionType.CONDITION_CURSED -> icons.add(PlayerIcon.Cursed)
            ConditionType.CONDITION_BLEEDING -> icons.add(PlayerIcon.Bleeding)
            else -> Unit
        }
        return icons
    }

    /** `ConditionDamage::clone` (`condition.cpp:2136-2138`). */
    override fun clone(): Condition {
        val copy = ConditionDamage(id, type, isBuff, subId, now, random)
        copy.apply {
            ticksValue = this@ConditionDamage.ticksValue
            endTime = this@ConditionDamage.endTime
            owner = this@ConditionDamage.owner
            drainBodyStage = this@ConditionDamage.drainBodyStage
            tickSound = this@ConditionDamage.tickSound
            addSound = this@ConditionDamage.addSound
            isPersistentFlag = this@ConditionDamage.isPersistentFlag
            maxDamage = this@ConditionDamage.maxDamage
            minDamage = this@ConditionDamage.minDamage
            startDamage = this@ConditionDamage.startDamage
            periodDamage = this@ConditionDamage.periodDamage
            periodDamageTick = this@ConditionDamage.periodDamageTick
            tickInterval = this@ConditionDamage.tickInterval
            forceUpdate = this@ConditionDamage.forceUpdate
            delayed = this@ConditionDamage.delayed
            field = this@ConditionDamage.field
            damageList.addAll(this@ConditionDamage.damageList.map { it.copy() })
        }
        return copy
    }

    private fun uniformRandom(min: Int, max: Int): Int {
        if (max < min) {
            return min
        }
        return random.nextInt(min, max + 1)
    }

    companion object {
        /** `EVENT_CREATURE_THINK_INTERVAL` (`src/creatures/creature.hpp:47`). */
        const val EVENT_CREATURE_THINK_INTERVAL = 1000

        /**
         * `ConditionDamage::generateDamageList` (`condition.cpp:2143-2160`).
         *
         * Produces the descending "bell" of damage rounds whose running sum
         * tracks the target average. Pure integer/float arithmetic so it is
         * byte-for-byte deterministic.
         */
        fun generateDamageList(amount: Int, start: Int): List<Int> {
            if (start <= 0) {
                return emptyList()
            }

            val list = mutableListOf<Int>()
            var sum = 0
            var i = start
            while (i > 0) {
                val n = start + 1 - i
                val med = (n * abs(amount)) / start

                var x1: Double
                var x2: Double
                do {
                    sum += i
                    list.add(i)

                    x1 = abs(1.0 - (sum + i).toDouble() / med)
                    x2 = abs(1.0 - sum.toDouble() / med)
                } while (x1 < x2)

                i--
            }
            return list
        }
    }
}
