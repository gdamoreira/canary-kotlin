package org.opentibia.canary.creatures

/**
 * Faithful port of Canary `struct FindPathParams`
 * (src/creatures/creatures_definitions.hpp:1630-1638).
 *
 * `allowDiagonal` is part of the C++ struct even though `MonsterPathfinder::find`
 * (src/creatures/monsters/monster_pathfinding.cpp:260-396) never reads it.
 */
class FindPathParams {
    var fullPathSearch: Boolean = true
    var clearSight: Boolean = true
    var allowDiagonal: Boolean = true
    var keepDistance: Boolean = false
    var maxSearchDist: Int = 0
    var minTargetDist: Int = -1
    var maxTargetDist: Int = -1
}
