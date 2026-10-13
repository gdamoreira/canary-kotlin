package org.opentibia.canary.combat.condition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position

/**
 * `ConditionFeared` (`src/creatures/combat/condition.cpp:2163-2455`,
 * `condition.hpp:344-380`) in the main condition hierarchy.
 *
 * The live-map walk scheduling of `executeCondition` (`getFleePath`,
 * `forcePlayerAutoWalk`, `condition.cpp:2299-2427`) is delegated to the map and
 * dispatcher subsystems and is not reproduced here; the region state machine,
 * stacking, icon and the end-of-fear immunity are.
 */
class ConditionFearedTest {

    private var clock = 0L
    private val now: () -> Long = { clock }

    private fun feared(
        caster: Position,
        walkable: (Position) -> Boolean = { false },
    ): ConditionFeared {
        val fear = ConditionFeared(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_FEARED,
            ticks = 6000,
            now = now,
            walkable = walkable,
        )
        fear.setPositionParam(ConditionParam.CONDITION_PARAM_CASTER_POSITION, caster)
        return fear
    }

    @Test
    fun `setPositionParam stores only the caster position`() {
        // ConditionFeared::setPositionParam (condition.cpp:2385-2391).
        val fear = feared(Position(1, 1, 7))
        val caster = Position(123, 456, 7)

        assertThat(fear.setPositionParam(ConditionParam.CONDITION_PARAM_CASTER_POSITION, caster)).isTrue()
        assertThat(fear.fleeingFromPos).isEqualTo(caster)
        assertThat(fear.setPositionParam(ConditionParam.CONDITION_PARAM_TICKS, caster)).isFalse()
    }

    @Test
    fun `startCondition selects the flee index for the caster quadrant`() {
        // ConditionFeared::getFleeDirection (condition.cpp:2223-2297).
        val caster = Position(100, 100, 7)

        assertThat(fleeIndex(caster, Position(105, 100, 7))).isEqualTo(2) // SW, offy == 0 -> East
        assertThat(fleeIndex(caster, Position(105, 98, 7))).isEqualTo(0)  // SW, offy != 0 -> North
        assertThat(fleeIndex(caster, Position(100, 105, 7))).isEqualTo(4) // NW, offx == 0 -> South
        assertThat(fleeIndex(caster, Position(102, 105, 7))).isEqualTo(2) // NW, offx != 0 -> East
        assertThat(fleeIndex(caster, Position(98, 100, 7))).isEqualTo(6)  // NE, offy == 0 -> West
        assertThat(fleeIndex(caster, Position(98, 105, 7))).isEqualTo(4)  // NE, offy != 0 -> South
        assertThat(fleeIndex(caster, Position(100, 98, 7))).isEqualTo(0)  // SE, offx == 0 -> North
        assertThat(fleeIndex(caster, Position(98, 98, 7))).isEqualTo(6)   // SE, offx != 0 -> West
    }

    @Test
    fun `same square falls back to a random walkable direction`() {
        // Same SQM -> getRandomDirection; only East is walkable.
        val caster = Position(100, 100, 7)
        val fear = feared(caster) { pos -> pos == Position(101, 100, 7) }

        assertThat(fear.getFleeDirection(caster)).isTrue()
        assertThat(fear.fleeIndx).isEqualTo(Direction.East.ordinal)
    }

    @Test
    fun `no walkable direction leaves the creature stuck`() {
        val caster = Position(100, 100, 7)
        val fear = feared(caster) { false }

        assertThat(fear.isStuck(caster)).isTrue()
        assertThat(fear.getFleeDirection(caster)).isFalse()
        assertThat(fear.fleeIndx).isEqualTo(99)
    }

    @Test
    fun `random direction stores the Canary direction value`() {
        // condition.cpp:2192 stores `static_cast<uint8_t>(*it)` (the direction
        // value, not the shuffled vector index); NorthEast is 7.
        val caster = Position(100, 100, 7)
        val fear = feared(caster) { pos -> pos == Position(101, 99, 7) }

        assertThat(fear.getRandomDirection(caster)).isTrue()
        assertThat(fear.fleeIndx).isEqualTo(Direction.NorthEast.ordinal)
    }

    @Test
    fun `feared icon is part of the icon set`() {
        val fear = feared(Position(1, 1, 7))

        assertThat(fear.getIcons()).containsExactly(PlayerIcon.Feared)
    }

    @Test
    fun `stacking refreshes the duration`() {
        val fear = feared(Position(1, 1, 7))
        fear.startCondition(null)

        val incoming = ConditionFeared(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_FEARED,
            ticks = 12000,
            now = now,
        )
        fear.addCondition(null, incoming)

        assertThat(fear.ticks).isEqualTo(12000)
    }

    @Test
    fun `ending the fear grants the player fear immunity`() {
        // ConditionFeared::endCondition (condition.cpp:2429-2438).
        val target = FakeConditionTarget(positionValue = Position(100, 100, 7))
        val fear = feared(Position(105, 100, 7))

        assertThat(target.immuneFearTime).isNull()
        fear.endCondition(target)

        assertThat(target.immuneFearTime).isEqualTo(10000L)
    }

    @Test
    fun `executeCondition ticks the base duration`() {
        val target = FakeConditionTarget(positionValue = Position(100, 100, 7))
        val fear = feared(Position(105, 100, 7))
        fear.startCondition(target)

        assertThat(fear.executeCondition(target, 1000)).isTrue()
        assertThat(fear.ticks).isEqualTo(5000)
    }

    private fun fleeIndex(caster: Position, creaturePos: Position): Int {
        val fear = feared(caster)
        fear.startCondition(FakeConditionTarget(positionValue = creaturePos))
        return fear.fleeIndx
    }
}
