package org.opentibia.canary.game

/**
 * Faithful transcription of Canary's `enum CylinderFlags_t`
 * (src/items/items_definitions.hpp:487).
 *
 * The constants keep the exact C++ member names and bit values so facade callers
 * can pass the same flag masks used by `Game`/`Cylinder` code.
 */
object CylinderFlags {
    const val FLAG_NOLIMIT = 1 shl 0
    const val FLAG_IGNOREBLOCKITEM = 1 shl 1
    const val FLAG_IGNOREBLOCKCREATURE = 1 shl 2
    const val FLAG_CHILDISOWNER = 1 shl 3
    const val FLAG_PATHFINDING = 1 shl 4
    const val FLAG_IGNOREFIELDDAMAGE = 1 shl 5
    const val FLAG_IGNORENOTMOVABLE = 1 shl 6
    const val FLAG_IGNOREAUTOSTACK = 1 shl 7
    const val FLAG_DROPONMAP = 1 shl 8
    const val FLAG_LOOTPOUCH = 1 shl 9
}
