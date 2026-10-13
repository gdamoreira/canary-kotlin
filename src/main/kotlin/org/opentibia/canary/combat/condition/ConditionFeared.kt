package org.opentibia.canary.combat.condition

import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position
import kotlin.random.Random

/**
 * `ConditionFeared` (`src/creatures/combat/condition.cpp:2163-2455`,
 * `condition.hpp:344-380`).
 *
 * Ports the region-selection state machine, the stacking [addCondition], the
 * [getIcons] feared icon and the [endCondition] fear immunity.
 *
 * The live-map pieces (`canWalkTo`'s tile query, `getFleePath`,
 * `forcePlayerAutoWalk`, `condition.cpp:2204-2427`) depend on the map and
 * dispatcher subsystems; `canWalkTo` is folded into the injected [walkable]
 * seam and [executeCondition] only runs the base duration tick, so the walk
 * scheduling is a PARITY GAP shared with the soul-war port of this class.
 */
class ConditionFeared(
    id: ConditionId,
    type: ConditionType,
    ticks: Int,
    buff: Boolean = false,
    subId: Int = 0,
    now: () -> Long = { System.currentTimeMillis() },
    private val walkable: (Position) -> Boolean = { false },
    private val random: Random = Random.Default,
) : Condition(id, type, ticks, buff, subId, false, now) {

    /** `m_directionsVector` (`condition.hpp:367-376`). */
    private val directionsVector = listOf(
        Direction.North,
        Direction.NorthEast,
        Direction.East,
        Direction.SouthEast,
        Direction.South,
        Direction.SouthWest,
        Direction.West,
        Direction.NorthWest,
    )

    /** `ConditionFeared::fleeingFromPos` (`condition.hpp:378`) — caster SQM. */
    var fleeingFromPos: Position = Position(0, 0, 0)
        internal set

    /** `ConditionFeared::fleeIndx` (`condition.hpp:379`), 99 until resolved. */
    var fleeIndx: Int = 99
        internal set

    /** `ConditionFeared::setPositionParam` (`condition.cpp:2385-2391`). */
    override fun setPositionParam(param: ConditionParam, pos: Position): Boolean {
        if (param == ConditionParam.CONDITION_PARAM_CASTER_POSITION) {
            fleeingFromPos = pos
            return true
        }
        return false
    }

    /** `ConditionFeared::startCondition` (`condition.cpp:2396-2401`). */
    override fun startCondition(target: ConditionTarget?): Boolean {
        if (target != null) {
            getFleeDirection(target.getPosition())
        }
        return super.startCondition(target)
    }

    /**
     * `ConditionFeared::executeCondition` (`condition.cpp:2403-2427`) — the
     * `getFleePath` / `forcePlayerAutoWalk` scheduling is omitted (see class
     * docs); only the base duration tick runs.
     */
    override fun executeCondition(target: ConditionTarget?, interval: Int): Boolean =
        super.executeCondition(target, interval)

    /** `ConditionFeared::endCondition` (`condition.cpp:2429-2438`). */
    override fun endCondition(target: ConditionTarget?) {
        // `creature->stopEventWalk()` has no walk state to release in this port.
        target?.player?.setImmuneFear()
    }

    /** `ConditionFeared::addCondition` (`condition.cpp:2440-2444`). */
    override fun addCondition(target: ConditionTarget?, condition: Condition) {
        if (updateCondition(condition)) {
            setTicks(condition.ticks)
        }
    }

    /** `ConditionFeared::getIcons` (`condition.cpp:2446-2451`). */
    override fun getIcons(): Set<PlayerIcon> {
        val icons = super.getIcons().toMutableSet()
        icons.add(PlayerIcon.Feared)
        return icons
    }

    /** `ConditionFeared::clone` (`condition.cpp:2453-2455`). */
    override fun clone(): Condition {
        val copy = ConditionFeared(id, type, ticks, isBuff, subId, now, walkable, random)
        copy.apply {
            endTime = this@ConditionFeared.endTime
            owner = this@ConditionFeared.owner
            drainBodyStage = this@ConditionFeared.drainBodyStage
            fleeingFromPos = this@ConditionFeared.fleeingFromPos
            fleeIndx = this@ConditionFeared.fleeIndx
        }
        return copy
    }

    /**
     * `ConditionFeared::getFleeDirection` (`condition.cpp:2223-2297`).
     *
     * Selects the starting index into [directionsVector] from the caster
     * offset. Returns false only when the creature shares the caster's SQM and
     * no random walkable direction can be found.
     */
    internal fun getFleeDirection(creaturePos: Position): Boolean {
        val offx = creaturePos.x - fleeingFromPos.x
        val offy = creaturePos.y - fleeingFromPos.y

        return when {
            // Same SQM: flee anywhere (condition.cpp:2230-2236).
            offx == 0 && offy == 0 -> getRandomDirection(creaturePos)

            // Monster on SW region: flee N, NE or E (condition.cpp:2237-2250).
            offx >= 1 && offy <= 0 -> {
                fleeIndx = if (offy == 0) 2 else 0
                true
            }

            // Monster on NW region: flee E, SE or S (condition.cpp:2251-2264).
            offx >= 0 && offy >= 1 -> {
                fleeIndx = if (offx == 0) 4 else 2
                true
            }

            // Monster on NE region: flee S, SW or W (condition.cpp:2265-2278).
            offx <= -1 && offy >= 0 -> {
                fleeIndx = if (offy == 0) 6 else 4
                true
            }

            // Monster on SE region: flee W, NW or N (condition.cpp:2279-2293).
            offx <= 0 && offy <= -1 -> {
                fleeIndx = if (offx == 0) 0 else 6
                true
            }

            else -> false
        }
    }

    /**
     * `ConditionFeared::getRandomDirection` (`condition.cpp:2172-2197`).
     *
     * Canary stores the *direction value* (not the shuffled index) into
     * `fleeIndx` (`this->fleeIndx = static_cast<uint8_t>(*it)`); the quirk is
     * reproduced faithfully with [Direction.ordinal], which matches the C++
     * direction enum values.
     */
    internal fun getRandomDirection(pos: Position): Boolean {
        val directions = directionsVector.shuffled(random)
        val found = directions.firstOrNull { canWalkTo(pos, it) }
        if (found != null) {
            fleeIndx = found.ordinal
            return true
        }
        return false
    }

    /**
     * `ConditionFeared::canWalkTo` (`condition.cpp:2204-2221`). The live tile
     * `queryAdd`/field-damage check is folded into the injected [walkable] seam.
     */
    internal fun canWalkTo(pos: Position, moveDirection: Direction): Boolean =
        walkable(getNextPosition(moveDirection, pos))

    /** `ConditionFeared::isStuck` (`condition.cpp:2165-2170`). */
    internal fun isStuck(pos: Position): Boolean =
        directionsVector.all { !canWalkTo(pos, it) }

    /** `getNextPosition` (`src/utils/tools.cpp:581-628`). */
    private fun getNextPosition(direction: Direction, pos: Position): Position = direction.offset(pos)
}
