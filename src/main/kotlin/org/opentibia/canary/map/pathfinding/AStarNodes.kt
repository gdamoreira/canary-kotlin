package org.opentibia.canary.map.pathfinding

import org.opentibia.canary.map.Position
import kotlin.math.abs

/**
 * Faithful port of Canary `struct AStarNode` (src/map/utils/astarnodes.hpp:16-20).
 *
 * [index] is the array slot the C++ code derives from pointer arithmetic
 * (`node - nodes`, astarnodes.cpp:206 and 216); Kotlin has no pointer arithmetic, so
 * the slot is carried explicitly and stays internal.
 *
 * Canary stores [x]/[y] as `uint16_t`; [AStarNodes] truncates the assigned
 * coordinates with `and 0xFFFF` to reproduce that narrowing conversion.
 */
class AStarNode internal constructor(internal val index: Int) {
    var parent: AStarNode? = null
    var f: Int = 0
    var g: Int = 0
    var c: Int = 0
    var x: Int = 0
    var y: Int = 0
}

/**
 * Faithful port of Canary `class AStarNodes` (src/map/utils/astarnodes.hpp:22-53,
 * src/map/utils/astarnodes.cpp:16-277) using the portable scalar code paths.
 *
 * The C++ SIMD `calculatedNodes` cache is a hot-path implementation detail; [getBestNode]
 * reproduces the scalar branch (astarnodes.cpp:190-201), whose strict `<` comparison
 * selects the lowest `f + g` and keeps the earliest slot on ties exactly like every SIMD
 * variant. [getNodeByPosition] scans slots in creation order; the scalar C++ build checks
 * slot 0 last (astarnodes.cpp:265-271), but node positions are unique by construction,
 * so the scan order has no observable effect.
 *
 * Canary narrows the `uint32_t` coordinates to the `uint16_t` node fields and packs
 * `(x << 16) | y` into `nodesTable` with `uint32_t` wrap-around arithmetic
 * (astarnodes.cpp:63-64, 84); both are reproduced with `and 0xFFFF` and Kotlin `Int`
 * wrap semantics. This is stricter than the legacy `org.opentibia.canary.map.AStarNodes`
 * port, which keeps coordinates untruncated; migration to this class is a follow-up
 * owned by `MonsterPathfinder.kt` and the legacy file.
 *
 * `AStarNodes::getTileWalkCost` (astarnodes.cpp:279-297) is not ported here: it reads
 * `Tile::getTopVisibleCreature`, `MagicField::getCombatType`, `Creature::isImmune`,
 * `Creature::getMonsterRaw` and `Monster::canWalkOnFieldType`, none of which the Kotlin
 * item/creature model exposes yet (PARITY GAP). The navigation-snapshot walk cost is
 * provided by `org.opentibia.canary.creatures.MonsterPathfinder.getTileWalkCost`.
 */
class AStarNodes(x: Int, y: Int, extraCost: Int) {
    private val nodes = Array(MAX_NODES) { AStarNode(it) }
    private val nodesTable = IntArray(MAX_NODES)
    private val openNodes = BooleanArray(MAX_NODES)
    private var closedNodes = 0
    private var curNode = 0

    init {
        curNode = 1
        closedNodes = 0
        openNodes[0] = true

        val startNode = nodes[0]
        startNode.parent = null
        startNode.x = x and 0xFFFF
        startNode.y = y and 0xFFFF
        startNode.f = 0
        startNode.g = 0
        startNode.c = extraCost
        nodesTable[0] = (x shl 16) or y
    }

    /** `AStarNodes::createOpenNode` (astarnodes.cpp:69-89). */
    fun createOpenNode(parent: AStarNode, x: Int, y: Int, f: Int, heuristic: Int, extraCost: Int): Boolean {
        if (curNode >= MAX_NODES) {
            return false
        }

        val retNode = curNode++
        openNodes[retNode] = true

        val node = nodes[retNode]
        node.parent = parent
        node.x = x and 0xFFFF
        node.y = y and 0xFFFF
        node.f = f
        node.g = heuristic
        node.c = extraCost
        nodesTable[retNode] = (x shl 16) or y
        return true
    }

    /** `AStarNodes::getBestNode` scalar branch (astarnodes.cpp:190-201). */
    fun getBestNode(): AStarNode? {
        var bestNodeF = Int.MAX_VALUE
        var bestNode = -1
        for (pos in 0 until curNode) {
            if (!openNodes[pos]) {
                continue
            }

            val totalCost = nodes[pos].f + nodes[pos].g
            if (totalCost < bestNodeF) {
                bestNode = pos
                bestNodeF = totalCost
            }
        }
        return if (bestNode != -1) nodes[bestNode] else null
    }

    /** `AStarNodes::closeNode` (astarnodes.cpp:205-213). */
    fun closeNode(node: AStarNode) {
        assert(node.index < MAX_NODES)
        openNodes[node.index] = false
        ++closedNodes
    }

    /** `AStarNodes::openNode` (astarnodes.cpp:215-223). */
    fun openNode(node: AStarNode) {
        assert(node.index < MAX_NODES)
        closedNodes -= if (openNodes[node.index]) 0 else 1
        openNodes[node.index] = true
    }

    /** `AStarNodes::getClosedNodes` (astarnodes.cpp:225-227). */
    fun getClosedNodes(): Int = closedNodes

    /** `AStarNodes::getNodeByPosition` (astarnodes.cpp:229-272). */
    fun getNodeByPosition(x: Int, y: Int): AStarNode? {
        val xy = (x shl 16) or y
        for (i in 0 until curNode) {
            if (nodesTable[i] == xy) {
                return nodes[i]
            }
        }
        return null
    }

    companion object {
        private const val MAX_NODES = 512

        /**
         * `MAP_NORMALWALKCOST` (astarnodes.hpp:38). `MAP_PREFERDIAGONALWALKCOST` (14,
         * astarnodes.hpp:39) is declared but never read in Canary either.
         */
        private const val MAP_NORMALWALKCOST = 10
        private const val MAP_DIAGONALWALKCOST = 25

        /** `AStarNodes::getMapWalkCost` (astarnodes.cpp:274-277). */
        fun getMapWalkCost(node: AStarNode, neighborPosition: Position): Int {
            return ((abs(node.x - neighborPosition.x) + abs(node.y - neighborPosition.y)) - 1) * MAP_DIAGONALWALKCOST + MAP_NORMALWALKCOST
        }
    }
}
