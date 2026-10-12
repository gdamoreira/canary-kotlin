package org.opentibia.canary.systems.familiar

/**
 * Canary parity: `struct Familiar` (src/creatures/players/grouping/familiars.hpp).
 * Field order and names mirror the C++ struct: name, lookType, premium, unlocked, type.
 */
data class Familiar(
    val name: String,
    val lookType: Int,
    val premium: Boolean,
    val unlocked: Boolean,
    val type: String
)

/**
 * Canary parity: `struct FamiliarEntry` (src/creatures/players/grouping/familiars.hpp).
 * Player-owned familiar reference, keyed only by lookType.
 */
data class FamiliarEntry(
    val lookType: Int
)
