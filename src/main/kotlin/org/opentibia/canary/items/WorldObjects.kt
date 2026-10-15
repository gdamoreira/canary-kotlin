package org.opentibia.canary.items

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

class MagicField(
    id: Int,
    damage: Int,
    var replaceable: Boolean = true,
    type: ItemType? = null,
) : Item(id, type = type) {

    private val damageValue: Int = damage

    /** `MagicField::getMagicField` (src/creatures/combat/combat.cpp:2787-2789). */
    override fun getMagicField(): MagicField = this

    /** `MagicField::isReplaceable` (src/creatures/combat/combat.cpp:2790-2792). */
    fun isReplaceable(): Boolean = replaceable

    /** `MagicField::getDamage` (src/creatures/combat/combat.cpp:2799-2805). */
    fun getDamage(): Int = damageValue

    /**
     * `g_game().getPlayerByGUID(ownerId)` / `g_game().getCreatureByID(ownerId)`
     * owner resolution (`src/creatures/combat/combat.cpp:2627`,
     * `condition.cpp:2000-2004`) as an injected seam (AGENTS.md 1.1): the
     * owner id stamped on the field ([getOwnerId]) to the attacking creature.
     * The composition root binds it over the game catalogs; an unbound field
     * (embedded/test roots) keeps the step-in unattributed.
     */
    var ownerCreatureProvider: (Long) -> Creature? = { null }

    /**
     * Mirrors Canary MagicField::onStepInField(creature)
     * src/creatures/combat/combat.hpp:369
     * src/creatures/combat/combat.cpp:2590-2641
     *
     * The step-in damage is attributed to the field owner: the owner id
     * ([getOwnerId]) resolves through [ownerCreatureProvider] and rides
     * `Creature::drainHealth(attacker, damage)` (creature.cpp:893-899), so the
     * drain is credited in the damage map like the condition engine's
     * owner-resolved drain (condition.cpp:2000-2004, 2030).
     *
     * PARITY GAPS: the safe-wall removal and `ExpertPvp` gates
     * (combat.cpp:2592-2605), the harmful-field `CONDITION_PARAM_OWNER` gate
     * (combat.cpp:2614-2636) and the `ItemType::conditionDamage` condition
     * application stay with their subsystems; this reduced field model applies
     * the field damage directly.
     */
    fun onStepInField(player: Player) {
        val ownerId = getOwnerId()
        val attacker = if (ownerId != 0L) ownerCreatureProvider(ownerId) else null
        player.drainHealth(attacker, damageValue)
    }
}

class TeleportItem(
    id: Int,
    val destination: Position
) : Item(id) {
    fun onStepIn(player: Player) {
        player.position = destination
    }
}

class DoorItem(
    val closedId: Int,
    val openId: Int,
    var isOpen: Boolean = false
) : Item(if (isOpen) openId else closedId) {

    fun toggle() {
        isOpen = !isOpen
    }

    override val weight: Double
        get() = 0.0

    // Dynamic ID reflecting current open/closed visual state
    val currentId: Int
        get() = if (isOpen) openId else closedId
}
