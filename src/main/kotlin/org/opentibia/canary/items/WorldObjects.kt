package org.opentibia.canary.items

import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.ConditionParam
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.map.Position

/**
 * Faithful port of `MagicField` (`src/creatures/combat/combat.hpp:360-371`,
 * `combat.cpp:2590-2805`).
 *
 * The field carries no damage state of its own: like the C++ class, the
 * condition, combat type and replaceability come from the registered item type
 * (`Item::items[getID()]`, combat.cpp:2783-2805), which the production item
 * factory ([Item.createLike]) preserves by creating a `MagicField` for every
 * `ITEM_TYPE_MAGICFIELD` type (item.cpp:411-472).
 */
class MagicField(
    id: Int,
    type: ItemType? = null,
    /** `OTSYS_TIME()` clock of [createTime] and the freshness gate (combat.cpp:2638). */
    private val now: () -> Long = { System.currentTimeMillis() },
) : Item(id, type = type) {

    /** `MagicField::createTime` (`combat.hpp:364`, `OTSYS_TIME()` at construction). */
    val createTime: Long = now()

    /** `MagicField::getMagicField` (combat.cpp:2787-2789). */
    override fun getMagicField(): MagicField = this

    /** `MagicField::isReplaceable` (combat.cpp:2790-2792) — `items[getID()].replaceable`. */
    fun isReplaceable(): Boolean = type?.replaceable ?: true

    /** `MagicField::getCombatType` (combat.cpp:2794-2797) — `items[getID()].combatType`. */
    fun getCombatType(): CombatType = type?.combatType ?: CombatType.NONE

    /** `MagicField::getDamage` (combat.cpp:2799-2805) — the type condition's total damage. */
    fun getDamage(): Int = type?.conditionDamage?.getTotalDamage() ?: 0

    /**
     * `g_game().getWorldType() == WORLD_TYPE_NO_PVP` (`combat.cpp:2615`) world
     * gate of the harmful-field check. Bound by the world when the field is
     * placed; an unbound field keeps the PvP default.
     */
    var noPvpWorld: () -> Boolean = { false }

    /**
     * `g_game().getPlayerByGUID(ownerId)` owner resolution (`combat.cpp:2617`):
     * the no-PvP/PvP-zone harmful-field gate. The world binds it over its player
     * catalog when the field enters the map; an unbound field resolves none.
     */
    var ownerPlayerByGuidProvider: (Long) -> Player? = { null }

    /**
     * `g_game().getCreatureByID(ownerId)` owner resolution (`combat.cpp:2620`):
     * the summon/master check of the harmful-field gate.
     */
    var ownerCreatureProvider: (Long) -> Creature? = { null }

    /**
     * `g_game().getPlayerByID(ownerId)` owner resolution (`combat.cpp:2628`):
     * the `Combat::isProtected` check against a player target.
     */
    var ownerPlayerByIdProvider: (Long) -> Player? = { null }

    /**
     * `Combat::isProtected(attackerPlayer, targetPlayer)` (`combat.cpp:2630`).
     * The world binds the ported [org.opentibia.canary.combat.Combat.isProtected]
     * over the live players; an unbound field never protects.
     */
    var combatIsProtected: (Player, Player) -> Boolean = { _, _ -> false }

    /**
     * `MagicField::onStepInField(creature)` (`src/creatures/combat/combat.cpp:2590-2641`).
     *
     * Clones the item type's `conditionDamage` (`combat.cpp:2605-2606`) and
     * applies it through `Creature::addCondition` (combat.cpp:2638). The owner
     * id stamped on the field (`Item::getOwnerId`, `combat.cpp:2612`) resolves
     * through the bound world catalogs; the harmful-field gates
     * (`combat.cpp:2614-2633`) decide whether the clone carries
     * `CONDITION_PARAM_OWNER`, so the condition's owner-resolved drain
     * (`condition.cpp:2000-2033`) credits the field owner.
     *
     * The owner stamp applies when the field is not harmful, still fresh
     * (`OTSYS_TIME() - createTime <= 5000`) or the target has already been
     * attacked by the owner (combat.cpp:2638).
     *
     * PARITY GAPS: the safe-wall/wildgrowth removal and the `ExpertPvp` field
     * ownership gates (combat.cpp:2592-2605) stay with their subsystems, and
     * `applyExpertPvpFieldDamage` (combat.cpp:2607-2609) is not modelled.
     */
    fun onStepInField(creature: Creature) {
        val conditionDamage = type?.conditionDamage ?: return
        val conditionCopy = conditionDamage.clone()

        val ownerId = getOwnerId()
        if (ownerId != 0L) {
            var harmfulField = true
            val itemTile = getTile()
            if (noPvpWorld() || (itemTile != null && itemTile.hasFlag(TileFlags.TILESTATE_NOPVPZONE.value))) {
                if (ownerPlayerByGuidProvider(ownerId) != null) {
                    harmfulField = false
                }

                val ownerCreature = ownerCreatureProvider(ownerId)
                if (ownerCreature != null) {
                    val ownerMaster = ownerCreature.getMaster()
                    if (ownerCreature is Player || (ownerCreature.isSummon() && ownerMaster is Player)) {
                        harmfulField = false
                    }
                }
            }

            val targetPlayer = creature as? Player
            if (targetPlayer != null) {
                val attackerPlayer = ownerPlayerByIdProvider(ownerId)
                if (attackerPlayer != null && combatIsProtected(attackerPlayer, targetPlayer)) {
                    harmfulField = false
                }
            }

            if (!harmfulField || now() - createTime <= 5000L || creature.hasBeenAttacked(ownerId)) {
                conditionCopy.setParam(ConditionParam.CONDITION_PARAM_OWNER, ownerId.toInt())
            }
        }

        creature.addCondition(conditionCopy)
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
