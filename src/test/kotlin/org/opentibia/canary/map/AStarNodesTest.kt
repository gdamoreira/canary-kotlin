package org.opentibia.canary.map

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcribes the heap/scoring contract of Canary `AStarNodes`
 * (src/map/utils/astarnodes.cpp:16-297) and its unit coverage.
 */
class AStarNodesTest {

    @Test
    fun `initializes the start node as the only open node`() {
        val nodes = AStarNodes(x = 100, y = 100, extraCost = 7)

        val best = nodes.getBestNode()

        assertThat(best).isNotNull()
        assertThat(best!!.x).isEqualTo(100)
        assertThat(best.y).isEqualTo(100)
        assertThat(best.f).isZero()
        assertThat(best.g).isZero()
        assertThat(best.c).isEqualTo(7)
        assertThat(best.parent).isNull()
        assertThat(nodes.getClosedNodes()).isZero()
    }

    @Test
    fun `selects the cheapest open node by f plus g and keeps the earliest on ties`() {
        val nodes = AStarNodes(0, 0, 0)
        val start = nodes.getBestNode()!!
        nodes.createOpenNode(start, 1, 0, f = 10, heuristic = 0, extraCost = 0)
        nodes.createOpenNode(start, 0, 1, f = 5, heuristic = 5, extraCost = 0)
        nodes.closeNode(start)

        assertThat(nodes.getBestNode()!!.x).isEqualTo(1)

        nodes.createOpenNode(start, -1, 0, f = 9, heuristic = 0, extraCost = 0)

        assertThat(nodes.getBestNode()!!.x).isEqualTo(-1)
    }

    @Test
    fun `closing and reopening nodes tracks the closed count`() {
        val nodes = AStarNodes(0, 0, 0)
        val start = nodes.getBestNode()!!
        nodes.createOpenNode(start, 1, 0, f = 10, heuristic = 0, extraCost = 0)
        val child = nodes.getNodeByPosition(1, 0)!!

        nodes.closeNode(start)
        assertThat(nodes.getClosedNodes()).isEqualTo(1)
        assertThat(nodes.getBestNode()).isSameAs(child)

        nodes.closeNode(child)
        assertThat(nodes.getClosedNodes()).isEqualTo(2)
        assertThat(nodes.getBestNode()).isNull()

        nodes.openNode(child)
        assertThat(nodes.getClosedNodes()).isEqualTo(1)
        assertThat(nodes.getBestNode()).isSameAs(child)

        nodes.openNode(child)
        assertThat(nodes.getClosedNodes()).isEqualTo(1)
    }

    @Test
    fun `finds nodes by packed position and keeps the start node addressable`() {
        val nodes = AStarNodes(100, 100, 0)
        val start = nodes.getBestNode()!!
        nodes.createOpenNode(start, 101, 99, f = 10, heuristic = 2, extraCost = 3)

        assertThat(nodes.getNodeByPosition(100, 100)).isSameAs(start)
        val created = nodes.getNodeByPosition(101, 99)
        assertThat(created).isNotNull()
        assertThat(created!!.c).isEqualTo(3)
        assertThat(nodes.getNodeByPosition(101, 98)).isNull()
        assertThat(nodes.getNodeByPosition(100, 99)).isNull()
    }

    @Test
    fun `caps the node table at 512 entries`() {
        val nodes = AStarNodes(0, 0, 0)
        val start = nodes.getBestNode()!!

        var created = 0
        while (nodes.createOpenNode(start, created + 1, 0, f = 1, heuristic = 0, extraCost = 0)) {
            created++
        }

        assertThat(created).isEqualTo(511)
    }

    @Test
    fun `charges the Canary map walk cost for orthogonal and diagonal steps`() {
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
