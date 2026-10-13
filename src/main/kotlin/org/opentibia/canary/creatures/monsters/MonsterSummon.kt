package org.opentibia.canary.creatures.monsters

import org.opentibia.canary.map.Position
import kotlin.math.max

/**
 * Port of the `Creature` master/summon state
 * (src/creatures/creature.hpp:460-481): `m_master`, `summoned` and the identity
 * of the owning creature (`Creature::getID`). The master's `m_summons` list is
 * owned by [SummonController] callbacks so no cross-object raw reference is held.
 */
class SummonMasterState(
    val creatureId: Long,
    var masterId: Long? = null,
    var summoned: Boolean = false,
)

/**
 * `Creature::checkSummonMove` (`creature.cpp:454-500`) summon facts. The
 * `getTile()` protection-zone/teleport checks of the *moving* creature (the
 * master) are projected onto each summon's facts, and [masterPosition] is
 * `summon->getMaster()->getPosition()`.
 */
class SummonMoveFacts(
    val id: Long,
    val position: Position,
    val masterId: Long? = null,
    val masterPosition: Position? = null,
    val isSummon: Boolean = false,
    val isFamiliar: Boolean = false,
    val masterTileHasProtectionZone: Boolean = false,
    val masterTileHasTeleport: Boolean = false,
)

/**
 * Port of the `Creature` summon-binding rules:
 * - `Creature::setMaster` (src/creatures/creature.cpp:1339-1373)
 * - `Creature::removeMaster` (src/creatures/creature.hpp:462-466)
 * - `Creature::isSummon`/`hasBeenSummoned` (src/creatures/creature.hpp:468-481)
 * - `Creature::checkSummonMove` (src/creatures/creature.cpp:454-500)
 *
 * The reload branch maps `setFollowCreature(nullptr)`, `setDropLoot(false)`,
 * `setSkillLoss(false)` and `g_game().reloadCreature(self)` onto
 * [setMaster]'s [onReloadCreature] seam; `m_summons` membership is owned by the
 * [addSummon]/[removeSummon] seams exactly like `newMaster->m_summons` and
 * `oldMaster->m_summons` in the C++.
 */
object SummonController {

    /**
     * `Creature::setMaster` (creature.cpp:1339-1373): the summoning latch is
     * persisted first; a null-to-null assignment is a no-op that still latches
     * `summoned`. A reload asks the caller to reset follow/loot/skill state and
     * re-register the creature; the new master receives the summon, the old
     * master loses it.
     */
    fun setMaster(
        state: SummonMasterState,
        newMasterId: Long?,
        reloadCreature: Boolean = false,
        addSummon: (Long, Long) -> Unit = { _, _ -> },
        removeSummon: (Long, Long) -> Unit = { _, _ -> },
        onReloadCreature: () -> Unit = {},
    ): Boolean {
        // Persists if this creature has ever been a summon
        state.summoned = true
        val oldMasterId = state.masterId

        if (newMasterId == null && oldMasterId == null) {
            return false
        }

        if (reloadCreature) {
            onReloadCreature()
        }

        if (newMasterId != null) {
            addSummon(newMasterId, state.creatureId)
        }

        state.masterId = newMasterId

        if (oldMasterId != null) {
            removeSummon(oldMasterId, state.creatureId)
        }

        return true
    }

    /** `Creature::removeMaster` (creature.hpp:462-466): drops only the pointer. */
    fun removeMaster(state: SummonMasterState) {
        state.masterId = null
    }

    /** `Creature::isSummon` (creature.hpp:468-470): `!m_master.expired()`. */
    fun isSummon(state: SummonMasterState): Boolean = state.masterId != null

    /** `Creature::hasBeenSummoned` (creature.hpp:475-478). */
    fun hasBeenSummoned(state: SummonMasterState): Boolean = state.summoned

    /**
     * `Creature::checkSummonMove` (creature.cpp:454-500): a familiar that moved
     * more than 15 tiles away or changed floor, or any summon when the teleport
     * config is on and the master's tile is no protection zone, is teleported to
     * the master (unless the master tile is a teleport, which only warns). A
     * non-familiar summon left more than 30 tiles or 2 floors behind is removed.
     */
    fun checkSummonMove(
        newPos: Position,
        teleportSummon: Boolean,
        summons: List<SummonMoveFacts>,
        onTeleport: (Long, Position) -> Unit = { _, _ -> },
        onRemove: (Long) -> Unit = {},
        onTeleportWarn: (Long) -> Unit = {},
    ) {
        val despawnMonsterList = mutableListOf<Long>()

        for (summon in summons) {
            val pos = summon.position

            // Check if any of our summons is out of range (+/- 0 floors or 15 tiles away)
            val checkSummonDist = pos.z != newPos.z ||
                max(kotlin.math.abs(newPos.x - pos.x), kotlin.math.abs(newPos.y - pos.y)) > 15
            // Check if any of our summons is out of range (+/- 2 floors or 30 tiles away)
            val checkRemoveDist = kotlin.math.abs(pos.z - newPos.z) > 2 ||
                max(kotlin.math.abs(newPos.x - pos.x), kotlin.math.abs(newPos.y - pos.y)) > 30

            if ((summon.isFamiliar && checkSummonDist) ||
                (teleportSummon && !summon.masterTileHasProtectionZone && checkSummonDist)
            ) {
                if (summon.masterId == null) {
                    continue
                }

                val masterPosition = summon.masterPosition
                if (masterPosition != null) {
                    if (summon.masterTileHasTeleport) {
                        onTeleportWarn(summon.id)
                    } else {
                        onTeleport(summon.id, masterPosition)
                        continue
                    }
                }
            }

            if (summon.isSummon && !summon.isFamiliar && !teleportSummon && checkRemoveDist) {
                despawnMonsterList.add(summon.id)
            }
        }

        for (despawnId in despawnMonsterList) {
            onRemove(despawnId)
        }
    }
}
