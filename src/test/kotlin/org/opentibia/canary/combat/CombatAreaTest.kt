package org.opentibia.canary.combat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the combat-area handling.
 *
 * Sources:
 *  - `Combat::getCombatArea` (src/creatures/combat/combat.cpp:233-243)
 *  - `AreaCombat::getList` (src/creatures/combat/combat.cpp:2289-2325)
 *  - `getDirectionTo`/`getNextPosition` (src/utils/tools.cpp:581-690)
 *
 * The tile/map lookups that Canary performs through `g_game().map` are injected
 * through the [CombatMap] seam.
 */
class CombatAreaTest {

    @Test
    fun `getCombatArea guards the z layer and creates the bare target tile`() {
        // combat.cpp:234-242
        val map = FakeCombatMap()
        val list = mutableListOf<CombatTile>()

        Combat.getCombatArea(Position(100, 100, 16), Position(100, 100, 16), null, map, list)
        assertThat(list).isEmpty()

        Combat.getCombatArea(Position(100, 100, 7), Position(100, 100, 7), null, map, list)
        assertThat(list).hasSize(1)
        assertThat(map.created).containsExactly(Position(100, 100, 7))
    }

    @Test
    fun `getCombatArea selects the matrix by direction and walks it`() {
        // combat.cpp:239 `getDirectionTo(targetPos, centerPos)`; a target east
        // of the center picks the east matrix and the caster-adjacent cell.
        val map = FakeCombatMap()
        val area = AreaCombat()
        area.setupArea(listOf(3), 1)

        val list = mutableListOf<CombatTile>()
        Combat.getCombatArea(Position(100, 100, 7), Position(102, 100, 7), area, map, list)

        assertThat(list).hasSize(1)
        assertThat(map.created).containsExactly(Position(102, 100, 7))
        assertThat(map.sightChecks).containsExactly(Position(101, 100, 7) to Position(102, 100, 7))
    }

    @Test
    fun `AreaCombat getList skips floor-change tiles and advances past them`() {
        // combat.cpp:2310-2314: a floor-change tile advances x and `continue`s
        // before the sight check, so the cell is never added.
        val map = FakeCombatMap()
        val floorChange = FakeCombatTile(Position(100, 99, 7))
        floorChange.flags.add(TileFlags.TILESTATE_FLOORCHANGE)
        map.tiles[Position(100, 99, 7)] = floorChange

        // target north of the center selects the north matrix; 1x2 with both
        // cells marked and the center at (0, 0).
        val area = AreaCombat()
        area.setupArea(listOf(1, 1), 1)

        val list = mutableListOf<CombatTile>()
        area.getList(Position(100, 100, 7), Position(100, 99, 7), list, null, map)

        assertThat(list).hasSize(1)
        assertThat(map.created).containsExactly(Position(101, 99, 7))
    }

    @Test
    fun `AreaCombat getList requires a clear sight line`() {
        // combat.cpp:2316-2318 `g_game().isSightClear(...)`
        val map = FakeCombatMap()
        map.sightClear = false

        val area = AreaCombat()
        area.setupArea(listOf(3), 1)

        val list = mutableListOf<CombatTile>()
        area.getList(Position(100, 100, 7), Position(100, 100, 7), list, null, map)

        assertThat(list).isEmpty()
        assertThat(map.created).isEmpty()
    }
}
