package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.NavCell
import org.opentibia.canary.map.NavCellFlag
import org.opentibia.canary.map.NavCellFlags
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import kotlin.math.abs
import kotlin.math.max

/**
 * Transcribes Canary `tests/unit/game/monster_pathfinding_test.cpp` plus the
 * no-path, radius and injected-walkability cases required by the port.
 */
class MonsterPathfinderTest {

    @Test
    fun `finds a valid bounded follow path`() {
        val start = Position(100, 100, 7)
        val target = Position(106, 100, 7)
        val request = makeRequest(start, target, radius = 12)

        val result = MonsterPathfinder.find(request)

        assertThat(result.found()).isTrue()
        assertThat(result.directions).isNotEmpty()
        assertThat(diagonalDistance(result.endpoint, target)).isEqualTo(1)

        val positions = expandPath(request, result)
        assertThat(positions).isNotEmpty()
        assertThat(positions.last()).isEqualTo(result.endpoint)
        for (position in positions) {
            val cell = request.getCell(position)
            assertThat(cell).isNotNull()
            assertThat(MonsterPathfinder.canEnter(cell!!, request.traits)).isTrue()
            assertThat(abs(start.x - position.x)).isLessThanOrEqualTo(12)
            assertThat(abs(start.y - position.y)).isLessThanOrEqualTo(12)
        }
    }

    @Test
    fun `routes around topology and occupancy blockers`() {
        val start = Position(100, 100, 7)
        val target = Position(106, 100, 7)
        val request = makeRequest(start, target, radius = 12) { position, cell ->
            if (position.x == 102 && position.y != 101) {
                cell.flags += NavCellFlag.BlockSolid
            }
            if (position.x == 101 && position.y == 100) {
                cell.blockingCreatures = 1
            }
        }

        val result = MonsterPathfinder.find(request)

        assertThat(result.found()).isTrue()
        val positions = expandPath(request, result)
        assertThat(positions).isNotEmpty()
        assertThat(positions).anyMatch { it.x == 102 && it.y == 101 }
        for (position in positions) {
            val cell = request.getCell(position)
            assertThat(cell).isNotNull()
            assertThat(cell!!.hasFlag(NavCellFlag.BlockSolid)).isFalse()
            assertThat(cell.blockingCreatures).isZero()
        }
    }

    @Test
    fun `applies monster specific tile contracts`() {
        val cell = NavCell()
        cell.flags = NavCellFlags.of(NavCellFlag.HasGround, NavCellFlag.ProtectionZone)
        val traits = MonsterPathTraits()
        assertThat(MonsterPathfinder.canEnter(cell, traits)).isFalse()

        traits.canEnterProtectionZone = true
        assertThat(MonsterPathfinder.canEnter(cell, traits)).isTrue()

        cell.flags = NavCellFlags.of(NavCellFlag.HasGround, NavCellFlag.WalkableSea)
        traits.isSummon = true
        assertThat(MonsterPathfinder.canEnter(cell, traits)).isFalse()

        traits.isSummon = false
        cell.flags = NavCellFlags.of(NavCellFlag.HasGround)
        cell.blockingCreatures = 1
        cell.pushableMonsters = 1
        traits.canPushCreatures = true
        assertThat(MonsterPathfinder.canEnter(cell, traits)).isTrue()
        cell.hasUnpushableCreature = true
        assertThat(MonsterPathfinder.canEnter(cell, traits)).isFalse()

        cell.flags = NavCellFlags.of(NavCellFlag.HasGround, NavCellFlag.HarmfulField)
        cell.blockingCreatures = 0
        cell.pushableMonsters = 0
        cell.hasUnpushableCreature = false
        cell.harmfulFieldCombatType = CombatType.FIREDAMAGE.value
        assertThat(MonsterPathfinder.canEnter(cell, traits)).isFalse()
        traits.fieldAllowed[CombatType.FIREDAMAGE.value] = true
        assertThat(MonsterPathfinder.canEnter(cell, traits)).isTrue()

        cell.flags = NavCellFlags.of(NavCellFlag.HasGround)
        cell.houseId = 42
        traits.isSummon = true
        assertThat(MonsterPathfinder.canEnter(cell, traits)).isFalse()
        traits.summonInvitedHouseIds.add(42)
        assertThat(MonsterPathfinder.canEnter(cell, traits)).isTrue()
    }

    @Test
    fun `uses navigation projectile topology for line of sight`() {
        val start = Position(100, 100, 7)
        val target = Position(106, 100, 7)
        val navigation = { position: Position ->
            groundCell {
                if (position.x == 103 && position.y == 100) {
                    it.flags += NavCellFlag.BlockProjectile
                }
            }
        }

        assertThat(MonsterPathfinder.isSightClear(navigation, start, target)).isFalse()
        assertThat(MonsterPathfinder.isSightClear(navigation, start, Position(101, 100, 7))).isTrue()
    }

    @Test
    fun `walks diagonal sight lines with the Canary accumulator`() {
        val start = Position(100, 100, 7)
        val navigation = { position: Position ->
            groundCell {
                if (position.x == 102 && position.y == 102) {
                    it.flags += NavCellFlag.BlockProjectile
                }
                if (position.x == 102 && position.y == 100) {
                    it.flags += NavCellFlag.BlockProjectile
                }
            }
        }

        assertThat(MonsterPathfinder.isSightClear(navigation, start, Position(103, 103, 7))).isFalse()
        assertThat(MonsterPathfinder.isSightClear(navigation, start, Position(102, 102, 7))).isTrue()
        assertThat(MonsterPathfinder.isSightClear(navigation, start, Position(103, 101, 7))).isFalse()
        assertThat(MonsterPathfinder.isSightClear(navigation, Position(103, 101, 7), start)).isFalse()
        assertThat(MonsterPathfinder.isSightClear(navigation, start, Position(104, 102, 7))).isTrue()
    }

    @Test
    fun `honors cancellation before search`() {
        val start = Position(100, 100, 7)
        val target = Position(106, 100, 7)
        val request = makeRequest(start, target, radius = 12)

        val result = MonsterPathfinder.find(request) { true }

        assertThat(result.status).isEqualTo(MonsterPathStatus.Cancelled)
        assertThat(result.directions).isEmpty()
    }

    @Test
    fun `returns not found when the target is enclosed`() {
        val start = Position(100, 100, 7)
        val target = Position(106, 100, 7)
        val wall = (-1..1)
            .flatMap { dy -> (-1..1).map { dx -> Position(target.x + dx, target.y + dy, 7) } }
            .filter { it != target }
            .toSet()
        val request = makeRequest(start, target, radius = 12) { position, cell ->
            if (position in wall) {
                cell.flags += NavCellFlag.BlockSolid
            }
        }

        val result = MonsterPathfinder.find(request)

        assertThat(result.status).isEqualTo(MonsterPathStatus.NotFound)
        assertThat(result.endpoint).isEqualTo(start)
        assertThat(result.directions).isEmpty()
    }

    @Test
    fun `rejects a search distance beyond the navigation radius`() {
        val start = Position(100, 100, 7)
        val target = Position(106, 100, 7)
        val request = makeRequest(start, target, radius = 12)
        request.params.maxSearchDist = 16

        val result = MonsterPathfinder.find(request)

        assertThat(result.status).isEqualTo(MonsterPathStatus.NotFound)
        assertThat(result.endpoint).isEqualTo(start)
        assertThat(result.directions).isEmpty()
    }

    @Test
    fun `returns not found when the start cell is missing`() {
        val start = Position(100, 100, 7)
        val target = Position(106, 100, 7)
        val request = MonsterPathRequest(
            start = start,
            target = target,
            params = FindPathParams().apply {
                fullPathSearch = true
                clearSight = false
                maxSearchDist = 12
                minTargetDist = 1
                maxTargetDist = 1
            },
            traits = MonsterPathTraits(),
            radius = 12,
            getCell = { null },
        )

        val result = MonsterPathfinder.find(request)

        assertThat(result.status).isEqualTo(MonsterPathStatus.NotFound)
        assertThat(result.endpoint).isEqualTo(start)
        assertThat(result.directions).isEmpty()
    }

    @Test
    fun `follows an injected walkability predicate over GameMap tiles`() {
        val map = GameMap()
        val start = Position(200, 200, 7)
        val target = Position(204, 200, 7)
        for (x in 198..206) {
            for (y in 198..203) {
                map.getOrCreateTile(Position(x, y, 7))
            }
        }
        val blocked = setOf(
            Position(202, 199, 7),
            Position(202, 200, 7),
            Position(202, 201, 7),
        )
        val walkable = { position: Position, _: Tile? -> position !in blocked }
        val request = MonsterPathRequest(
            start = start,
            target = target,
            params = FindPathParams().apply {
                fullPathSearch = true
                clearSight = false
                maxSearchDist = 8
                minTargetDist = 1
                maxTargetDist = 1
            },
            traits = MonsterPathTraits(),
            radius = 8,
            getCell = { position ->
                map.getTile(position)
                    ?.takeIf { walkable(position, it) }
                    ?.let { groundCell() }
            },
        )

        val result = MonsterPathfinder.find(request)

        assertThat(result.found()).isTrue()
        val positions = expandPath(request, result)
        assertThat(positions).isNotEmpty()
        assertThat(positions).allMatch { it !in blocked }
        assertThat(positions).anyMatch { it.y > 201 || it.y < 199 }
    }

    private fun makeRequest(
        start: Position,
        target: Position,
        radius: Int,
        modifier: (Position, NavCell) -> Unit = { _, _ -> },
    ): MonsterPathRequest {
        val params = FindPathParams().apply {
            fullPathSearch = true
            clearSight = false
            maxSearchDist = radius
            minTargetDist = 1
            maxTargetDist = 1
        }
        return MonsterPathRequest(
            start = start,
            target = target,
            params = params,
            traits = MonsterPathTraits(),
            radius = radius,
            getCell = { position -> groundCell { modifier(position, it) } },
        )
    }

    private fun groundCell(modifier: (NavCell) -> Unit = {}): NavCell {
        val cell = NavCell()
        cell.flags = NavCellFlags.of(NavCellFlag.HasGround)
        modifier(cell)
        return cell
    }

    private fun expandPath(request: MonsterPathRequest, result: MonsterPathResult): List<Position> {
        val positions = mutableListOf<Position>()
        var position = request.start
        for (direction in result.directions.reversed()) {
            position = Position(position.x + direction.dx, position.y + direction.dy, position.z)
            positions.add(position)
        }
        return positions
    }

    private fun diagonalDistance(left: Position, right: Position): Int =
        max(abs(left.x - right.x), abs(left.y - right.y))
}
