package org.opentibia.canary.map.pathfinding

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

/**
 * Transcript of the open/closed-set and scoring contract of Canary
 * `AStarNodes` (src/map/utils/astarnodes.hpp:16-53,
 * src/map/utils/astarnodes.cpp:16-277).
 *
 * Every expectation below mirrors the scalar code path
 * (astarnodes.cpp:16-67, 69-89, 190-227, 229-277) whose observable results are
 * identical to the SSE2/AVX2/AVX512 branches: the strict `<` comparisons keep
 * the earliest slot on ties and the `nodesTable`/`openNodes` bookkeeping is not
 * vectorized.
 */
class AStarNodesTest {

    @Test
    fun `initializes the start node as the only open node`() {
        val nodes = AStarNodes(x = 100, y = 100, extraCost = 7)

        val start = nodes.getBestNode()

        assertThat(start).isNotNull()
        assertThat(start!!.x).isEqualTo(100)
        assertThat(start.y).isEqualTo(100)
        assertThat(start.f).isZero()
        assertThat(start.g).isZero()
        assertThat(start.c).isEqualTo(7)
        assertThat(start.parent).isNull()
        assertThat(nodes.getClosedNodes()).isZero()
        assertThat(nodes.getNodeByPosition(100, 100)).isSameAs(start)
    }

    @Test
    fun `createOpenNode stores the parent coordinates f heuristic and extra cost`() {
        val nodes = AStarNodes(10, 20, 3)
        val start = nodes.getBestNode()!!

        assertThat(nodes.createOpenNode(start, 11, 21, f = 10, heuristic = 4, extraCost = 30)).isTrue()

        val child = nodes.getNodeByPosition(11, 21)
        assertThat(child).isNotNull()
        assertThat(child!!.parent).isSameAs(start)
        assertThat(child.x).isEqualTo(11)
        assertThat(child.y).isEqualTo(21)
        assertThat(child.f).isEqualTo(10)
        assertThat(child.g).isEqualTo(4)
        assertThat(child.c).isEqualTo(30)
    }

    @Test
    fun `getBestNode selects the lowest f plus g and keeps the earliest slot on ties`() {
        val nodes = AStarNodes(0, 0, 0)
        val start = nodes.getBestNode()!!
        nodes.createOpenNode(start, 1, 0, f = 10, heuristic = 0, extraCost = 0)
        nodes.createOpenNode(start, 0, 1, f = 5, heuristic = 5, extraCost = 0)
        nodes.createOpenNode(start, 2, 0, f = 9, heuristic = 0, extraCost = 0)
        nodes.closeNode(start)

        val cheapest = nodes.getNodeByPosition(2, 0)!!
        val earliestTie = nodes.getNodeByPosition(1, 0)!!
        val laterTie = nodes.getNodeByPosition(0, 1)!!

        assertThat(nodes.getBestNode()).isSameAs(cheapest)

        nodes.closeNode(cheapest)
        assertThat(nodes.getBestNode()).isSameAs(earliestTie)

        nodes.closeNode(earliestTie)
        assertThat(nodes.getBestNode()).isSameAs(laterTie)
    }

    @Test
    fun `getBestNode ignores closed nodes and is null once every node is closed`() {
        val nodes = AStarNodes(0, 0, 0)
        val start = nodes.getBestNode()!!
        nodes.createOpenNode(start, 1, 0, f = 1, heuristic = 0, extraCost = 0)
        val child = nodes.getNodeByPosition(1, 0)!!

        nodes.closeNode(start)
        nodes.closeNode(child)

        assertThat(nodes.getBestNode()).isNull()
    }

    @Test
    fun `closeNode counts every close and openNode decrements only for a closed node`() {
        val nodes = AStarNodes(0, 0, 0)
        val start = nodes.getBestNode()!!

        nodes.closeNode(start)
        nodes.closeNode(start)
        assertThat(nodes.getClosedNodes()).isEqualTo(2)

        nodes.openNode(start)
        assertThat(nodes.getClosedNodes()).isEqualTo(1)
        assertThat(nodes.getBestNode()).isSameAs(start)

        nodes.openNode(start)
        assertThat(nodes.getClosedNodes()).isEqualTo(1)
    }

    @Test
    fun `openNode restores a previously closed node with its stored scores`() {
        val nodes = AStarNodes(0, 0, 0)
        val start = nodes.getBestNode()!!
        nodes.createOpenNode(start, 1, 0, f = 42, heuristic = 8, extraCost = 5)
        val child = nodes.getNodeByPosition(1, 0)!!

        nodes.closeNode(start)
        nodes.closeNode(child)
        assertThat(nodes.getBestNode()).isNull()

        child.f = 3
        child.parent = start
        nodes.openNode(child)

        assertThat(nodes.getBestNode()).isSameAs(child)
        assertThat(child.f).isEqualTo(3)
        assertThat(child.g).isEqualTo(8)
        assertThat(child.c).isEqualTo(5)
        assertThat(child.parent).isSameAs(start)
    }

    @Test
    fun `getNodeByPosition packs x into the high 16 bits and finds the start node`() {
        val nodes = AStarNodes(1, 0, 0)
        val start = nodes.getBestNode()!!
        nodes.createOpenNode(start, 0, 1, f = 1, heuristic = 0, extraCost = 0)

        assertThat(nodes.getNodeByPosition(1, 0)).isSameAs(start)
        assertThat(nodes.getNodeByPosition(0, 1)).isNotNull()
        assertThat(nodes.getNodeByPosition(0, 1)).isNotSameAs(start)
        assertThat(nodes.getNodeByPosition(1, 1)).isNull()
        assertThat(nodes.getNodeByPosition(0, 0)).isNull()
    }

    @Test
    fun `caps the node table at 512 slots`() {
        val nodes = AStarNodes(0, 0, 0)
        val start = nodes.getBestNode()!!

        var created = 0
        while (nodes.createOpenNode(start, created + 1, 0, f = 1, heuristic = 0, extraCost = 0)) {
            created++
        }

        assertThat(created).isEqualTo(511)
        assertThat(nodes.createOpenNode(start, 513, 0, f = 1, heuristic = 0, extraCost = 0)).isFalse()
        assertThat(nodes.getNodeByPosition(513, 0)).isNull()
    }

    @Test
    fun `truncates coordinates into the uint16 node fields while packing the full key`() {
        val nodes = AStarNodes(70000, 70000, 0)

        val start = nodes.getBestNode()

        assertThat(start!!.x).isEqualTo(70000 and 0xFFFF)
        assertThat(start.y).isEqualTo(70000 and 0xFFFF)
        assertThat(nodes.getNodeByPosition(70000, 70000)).isSameAs(start)

        nodes.createOpenNode(start, 65541, 65542, f = 1, heuristic = 0, extraCost = 0)

        val child = nodes.getNodeByPosition(65541, 65542)!!
        assertThat(child.x).isEqualTo(65541 and 0xFFFF)
        assertThat(child.y).isEqualTo(65542 and 0xFFFF)
    }

    @Test
    fun `charges the Canary map walk cost for orthogonal diagonal and longer steps`() {
        val nodes = AStarNodes(10, 10, 0)
        val node = nodes.getBestNode()!!

        assertThat(AStarNodes.getMapWalkCost(node, Position(11, 10, 7))).isEqualTo(10)
        assertThat(AStarNodes.getMapWalkCost(node, Position(9, 10, 7))).isEqualTo(10)
        assertThat(AStarNodes.getMapWalkCost(node, Position(10, 11, 7))).isEqualTo(10)
        assertThat(AStarNodes.getMapWalkCost(node, Position(11, 11, 7))).isEqualTo(35)
        assertThat(AStarNodes.getMapWalkCost(node, Position(9, 9, 7))).isEqualTo(35)
        assertThat(AStarNodes.getMapWalkCost(node, Position(12, 11, 7))).isEqualTo(60)
    }
}
