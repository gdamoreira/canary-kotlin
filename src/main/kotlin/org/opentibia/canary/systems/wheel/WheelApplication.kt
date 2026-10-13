package org.opentibia.canary.systems.wheel

import org.opentibia.canary.network.protocol.wheel.DestroyGemCommand
import org.opentibia.canary.network.protocol.wheel.ImproveGemGradeCommand
import org.opentibia.canary.network.protocol.wheel.RevealGemCommand
import org.opentibia.canary.network.protocol.wheel.SaveWheelCommand
import org.opentibia.canary.network.protocol.wheel.SwitchGemDomainCommand
import org.opentibia.canary.network.protocol.wheel.ToggleGemLockCommand
import org.opentibia.canary.network.protocol.wheel.WheelGemActionCommand
import org.opentibia.canary.network.protocol.wheel.WheelSlotPoints

/**
 * Minimal gem state mirroring the `PlayerWheelGem` struct fields
 * (src/creatures/players/components/wheel/player_wheel.hpp:44-69) that the
 * ported wheel model manipulates. Persistence, UI serialization and the
 * player item/gold economy are not modelled.
 */
data class PlayerWheelGem(
    var locked: Boolean = false,
    var affinity: WheelGemAffinity = WheelGemAffinity.Green,
    var quality: WheelGemQuality = WheelGemQuality.Lesser,
    var basicModifier1: WheelGemBasicModifier = WheelGemBasicModifier.General_PhysicalResistance,
    var basicModifier2: WheelGemBasicModifier? = null,
    var supremeModifier: WheelGemSupremeModifier? = null,
)

/**
 * Holds the wheel gem collections and modifier grades owned by a [PlayerWheel],
 * corresponding to `m_revealedGems`, `m_activeGems`, `m_basicGrades` and
 * `m_supremeGrades` (player_wheel.hpp:504-525).
 */
class WheelGemState {
    val revealedGems: MutableList<PlayerWheelGem> = mutableListOf()
    val activeGems: MutableList<PlayerWheelGem?> = MutableList(WheelGemAffinity.entries.size) { null }
    val basicGrades: IntArray = IntArray(49)
    val supremeGrades: IntArray = IntArray(95)
}

sealed interface WheelSaveResult {
    /**
     * The request was processed. [applied] are the slot points committed;
     * [unsatisfied] are submitted slots whose points exceeded the remaining
     * budget and were left at zero, mirroring Canary's retry loop.
     */
    data class Success(
        val applied: Map<WheelSlots, Int>,
        val unsatisfied: List<WheelSlots> = emptyList(),
    ) : WheelSaveResult

    /** The whole request was refused before any mutation. */
    data class Failure(
        val reason: WheelSaveRejection,
        val slot: WheelSlots? = null,
    ) : WheelSaveResult
}

enum class WheelSaveRejection {
    MAX_POINTS_PER_SLOT_EXCEEDED,
    PREREQUISITES_NOT_MET,
}

sealed interface WheelGemActionResult {
    val action: WheelGemAction

    data class Applied(
        override val action: WheelGemAction,
        val gemIndex: Int? = null,
    ) : WheelGemActionResult

    data class Rejected(
        override val action: WheelGemAction,
        val reason: WheelGemActionRejection,
    ) : WheelGemActionResult
}

enum class WheelGemActionRejection {
    NO_SUCH_GEM,
    GEM_LOCKED,
    MAX_GRADE_REACHED,
    INVALID_POSITION,

    /** `revealGem` could not resolve or find the required vocation gem item (player_wheel.cpp:1134-1141). */
    MISSING_GEM_ITEM,

    /** `removeMoney` could not pay the atelier/grade gold cost (player_wheel.cpp:1143,1298,1486). */
    NOT_ENOUGH_MONEY,

    /** `improveGemGrade` lacks the required fragments (player_wheel.cpp:1481-1484). */
    NOT_ENOUGH_FRAGMENTS,

    /**
     * A seam mutation failed after pre-validation (`removeItemCountById` /
     * `internalPlayerAddItem`), mirroring Canary's abort branches
     * (player_wheel.cpp:1147,1240-1256). As in Canary, mutations performed
     * before the failure are not rolled back.
     */
    INTERNAL_ERROR,
}

/**
 * Applies parsed wheel commands to a [PlayerWheel]. This is a thin application
 * service over the C++ state mutations; it owns no state itself.
 *
 * Ported from Canary:
 *  - `PlayerWheel::saveSlotPointsOnPressSaveButton` / `checkSavePointsBySlotType`
 *    (src/creatures/players/components/wheel/player_wheel.cpp:1611-1724)
 *  - `Game::playerWheelGemAction` dispatch (src/game/game.cpp:11554) and the
 *    `PlayerWheel` gem handlers (player_wheel.cpp:1212-1506)
 */
class WheelApplication(
    private val random: WheelRandom = WheelRandom { minNumber, _ -> minNumber },
    private val normalRandom: WheelNormalRandom = WheelNormalRandom { minNumber, _ -> minNumber },
) {
    private val ioWheel = IOWheel()

    /**
     * Validates the submitted slot points exactly like Canary: a point count
     * above the slot maximum rejects the whole request (client manipulation),
     * otherwise slots are committed highest-priority-first while they fit the
     * unused-point budget and their prerequisites are met. Slots that do not fit
     * are retried (the C++ loop runs six passes) and reported as
     * [WheelSaveResult.Success.unsatisfied]; a slot left unsatisfied because its
     * prerequisite set is still not full rejects the whole request with
     * [WheelSaveRejection.PREREQUISITES_NOT_MET] before any slot is committed.
     */
    fun applySave(wheel: PlayerWheel, command: SaveWheelCommand): WheelSaveResult {
        for (slotPoint in command.slotPoints) {
            val maxPointsPerSlot = wheel.getMaxPointsPerSlot(slotPoint.slot)
            if (slotPoint.points > maxPointsPerSlot) {
                return WheelSaveResult.Failure(WheelSaveRejection.MAX_POINTS_PER_SLOT_EXCEEDED, slotPoint.slot)
            }
        }

        val prioritized = command.slotPoints
            .mapIndexedNotNull { index, slotPoint ->
                val order = ioWheel.getSlotPrioritaryOrder(slotPoint.slot)
                if (order == -1) null else Triple(order, index, slotPoint)
            }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.third }

        val snapshot = wheel.pointsBySlot.toMap()
        val applied = LinkedHashMap<WheelSlots, Int>()
        var retry = prioritized.toMutableList()
        repeat(SAVE_RETRY_PASSES) {
            val pending = ArrayList<WheelSlotPoints>()
            for (slotPoint in retry) {
                if (checkSavePointsBySlotType(wheel, slotPoint.slot, slotPoint.points)) {
                    applied[slotPoint.slot] = slotPoint.points
                } else {
                    pending += slotPoint
                }
            }
            retry = pending
        }

        val unmetPrerequisite = retry.firstOrNull {
            it.points > 0 && !canPlayerSelectPointOnSlot(wheel, it.slot, false)
        }
        if (unmetPrerequisite != null) {
            wheel.pointsBySlot.clear()
            wheel.pointsBySlot.putAll(snapshot)
            return WheelSaveResult.Failure(WheelSaveRejection.PREREQUISITES_NOT_MET, unmetPrerequisite.slot)
        }

        applyGemVessels(wheel, command)

        return WheelSaveResult.Success(applied, retry.map { it.slot })
    }

    /**
     * Mirror of `PlayerWheel::checkSavePointsBySlotType` (player_wheel.cpp:1611):
     * a positive point count must first satisfy the
     * [canPlayerSelectPointOnSlot] prerequisite graph, then the slot is cleared
     * and committed only while it fits the unused-point budget.
     */
    private fun checkSavePointsBySlotType(wheel: PlayerWheel, slot: WheelSlots, points: Int): Boolean {
        if (points > 0 && !canPlayerSelectPointOnSlot(wheel, slot, false)) {
            return false
        }
        wheel.setPointsBySlotType(slot, 0)
        val unusedPoints = wheel.getUnusedPoints()
        if (points > unusedPoints) {
            return false
        }
        wheel.setPointsBySlotType(slot, points)
        return true
    }

    /**
     * Mirror of `PlayerWheel::canSelectSlotFullOrPartial` (player_wheel.cpp:2086):
     * a prerequisite slot counts as satisfied only when it is filled to its
     * maximum.
     */
    private fun canSelectSlotFullOrPartial(wheel: PlayerWheel, slot: WheelSlots): Boolean =
        wheel.getPointsBySlotType(slot) == wheel.getMaxPointsPerSlot(slot)

    /**
     * Mirror of `PlayerWheel::canPlayerSelectPointOnSlot` (player_wheel.cpp:381):
     * per-quadrant prerequisite graph. Each gated slot requires the player to own
     * a minimum number of points and at least one listed neighbour to be full.
     * The four 50-point slots are the tree roots and are always selectable.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun canPlayerSelectPointOnSlot(wheel: PlayerWheel, slot: WheelSlots, recursive: Boolean): Boolean {
        val playerPoints = wheel.getWheelPoints()

        // Green quadrant
        if (slot == WheelSlots.SLOT_GREEN_200) {
            if (playerPoints < 375) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_TOP_150)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_BOTTOM_150)) return true
        } else if (slot == WheelSlots.SLOT_GREEN_TOP_150) {
            if (playerPoints < 225) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_TOP_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_BOTTOM_150)) return true
        } else if (slot == WheelSlots.SLOT_GREEN_BOTTOM_150) {
            if (playerPoints < 225) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_BOTTOM_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_TOP_150)) return true
        } else if (slot == WheelSlots.SLOT_GREEN_TOP_100) {
            if (playerPoints < 125) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_TOP_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_TOP_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_MIDDLE_100)) return true
        } else if (slot == WheelSlots.SLOT_GREEN_MIDDLE_100) {
            if (playerPoints < 125) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_TOP_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_TOP_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_BOTTOM_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_BOTTOM_100)) return true
        } else if (slot == WheelSlots.SLOT_GREEN_BOTTOM_100) {
            if (playerPoints < 125) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_BOTTOM_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_TOP_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_BOTTOM_150)) return true
        } else if (slot == WheelSlots.SLOT_GREEN_TOP_75) {
            if (playerPoints < 50) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_50)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_TOP_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_BOTTOM_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_TOP_100)) return true
        } else if (slot == WheelSlots.SLOT_GREEN_BOTTOM_75) {
            if (playerPoints < 50) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_50)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_TOP_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_BOTTOM_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_TOP_75)) return true
        } else if (slot == WheelSlots.SLOT_GREEN_50) {
            return (recursive && wheel.getPointsBySlotType(slot) == wheel.getMaxPointsPerSlot(slot)) || true
        }

        // Red quadrant
        if (slot == WheelSlots.SLOT_RED_200) {
            if (playerPoints < 375) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_TOP_150)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_BOTTOM_150)) return true
        } else if (slot == WheelSlots.SLOT_RED_TOP_150) {
            if (playerPoints < 225) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_TOP_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_BOTTOM_150)) return true
        } else if (slot == WheelSlots.SLOT_RED_BOTTOM_150) {
            if (playerPoints < 225) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_BOTTOM_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_TOP_150)) return true
        } else if (slot == WheelSlots.SLOT_RED_TOP_100) {
            if (playerPoints < 125) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_TOP_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_TOP_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_TOP_150)) return true
        } else if (slot == WheelSlots.SLOT_RED_MIDDLE_100) {
            if (playerPoints < 125) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_TOP_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_BOTTOM_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_BOTTOM_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_TOP_100)) return true
        } else if (slot == WheelSlots.SLOT_RED_BOTTOM_100) {
            if (playerPoints < 125) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_BOTTOM_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_TOP_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_BOTTOM_150)) return true
        } else if (slot == WheelSlots.SLOT_RED_TOP_75) {
            if (playerPoints < 50) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_50)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_TOP_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_TOP_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_BOTTOM_75)) return true
        } else if (slot == WheelSlots.SLOT_RED_BOTTOM_75) {
            if (playerPoints < 50) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_50)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_TOP_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_BOTTOM_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_TOP_75)) return true
        } else if (slot == WheelSlots.SLOT_RED_50) {
            return (recursive && wheel.getPointsBySlotType(slot) == wheel.getMaxPointsPerSlot(slot)) || true
        }

        // Purple quadrant
        if (slot == WheelSlots.SLOT_PURPLE_200) {
            if (playerPoints < 375) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_TOP_150)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_BOTTOM_150)) return true
        } else if (slot == WheelSlots.SLOT_PURPLE_TOP_150) {
            if (playerPoints < 225) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_TOP_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_BOTTOM_150)) return true
        } else if (slot == WheelSlots.SLOT_PURPLE_BOTTOM_150) {
            if (playerPoints < 225) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_BOTTOM_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_TOP_150)) return true
        } else if (slot == WheelSlots.SLOT_PURPLE_TOP_100) {
            if (playerPoints < 125) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_TOP_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_BOTTOM_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_TOP_150)) return true
        } else if (slot == WheelSlots.SLOT_PURPLE_MIDDLE_100) {
            if (playerPoints < 125) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_TOP_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_BOTTOM_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_BOTTOM_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_TOP_100)) return true
        } else if (slot == WheelSlots.SLOT_PURPLE_BOTTOM_100) {
            if (playerPoints < 125) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_BOTTOM_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_BOTTOM_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_BOTTOM_150)) return true
        } else if (slot == WheelSlots.SLOT_PURPLE_TOP_75) {
            if (playerPoints < 50) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_50)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_RED_BOTTOM_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_BOTTOM_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_TOP_100)) return true
        } else if (slot == WheelSlots.SLOT_PURPLE_BOTTOM_75) {
            if (playerPoints < 50) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_50)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_BOTTOM_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_TOP_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_BOTTOM_100)) return true
        } else if (slot == WheelSlots.SLOT_PURPLE_50) {
            return (recursive && wheel.getPointsBySlotType(slot) == wheel.getMaxPointsPerSlot(slot)) || true
        }

        // Blue quadrant
        if (slot == WheelSlots.SLOT_BLUE_200) {
            if (playerPoints < 375) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_TOP_150)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_BOTTOM_150)) return true
        } else if (slot == WheelSlots.SLOT_BLUE_TOP_150) {
            if (playerPoints < 225) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_TOP_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_BOTTOM_150)) return true
        } else if (slot == WheelSlots.SLOT_BLUE_BOTTOM_150) {
            if (playerPoints < 225) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_BOTTOM_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_TOP_150)) return true
        } else if (slot == WheelSlots.SLOT_BLUE_TOP_100) {
            if (playerPoints < 125) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_TOP_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_BOTTOM_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_TOP_150)) return true
        } else if (slot == WheelSlots.SLOT_BLUE_MIDDLE_100) {
            if (playerPoints < 125) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_TOP_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_BOTTOM_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_BOTTOM_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_TOP_100)) return true
        } else if (slot == WheelSlots.SLOT_BLUE_BOTTOM_100) {
            if (playerPoints < 125) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_BOTTOM_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_BOTTOM_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_BOTTOM_150)) return true
        } else if (slot == WheelSlots.SLOT_BLUE_TOP_75) {
            if (playerPoints < 50) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_50)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_GREEN_BOTTOM_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_BOTTOM_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_TOP_100)) return true
        } else if (slot == WheelSlots.SLOT_BLUE_BOTTOM_75) {
            if (playerPoints < 50) return false
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_50)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_PURPLE_BOTTOM_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_TOP_75)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_MIDDLE_100)) return true
            if (canSelectSlotFullOrPartial(wheel, WheelSlots.SLOT_BLUE_BOTTOM_100)) return true
        } else if (slot == WheelSlots.SLOT_BLUE_50) {
            return (recursive && wheel.getPointsBySlotType(slot) == wheel.getMaxPointsPerSlot(slot)) || true
        }

        return false
    }

    private fun applyGemVessels(wheel: PlayerWheel, command: SaveWheelCommand) {
        for (vessel in command.gemVessels) {
            val gemIndex = vessel.gemIndex
            if (gemIndex == null) {
                wheel.removeActiveGem(vessel.affinity)
            } else {
                wheel.setActiveGem(vessel.affinity, gemIndex)
            }
        }
    }

    fun applyGemAction(
        wheel: PlayerWheel,
        command: WheelGemActionCommand,
        player: WheelPlayer = WheelPlayer.UNWIRED,
    ): WheelGemActionResult =
        when (command) {
            is DestroyGemCommand -> destroyGem(wheel, command, player)
            is RevealGemCommand -> revealGem(wheel, command, player)
            is SwitchGemDomainCommand -> switchGemDomain(wheel, command, player)
            is ToggleGemLockCommand -> toggleGemLock(wheel, command)
            is ImproveGemGradeCommand -> improveGemGrade(wheel, command, player)
        }

    /**
     * Mirror of `PlayerWheel::revealGem` (player_wheel.cpp:1132-1183): the
     * player must own the quality's vocation gem item, then pay the configured
     * atelier gold cost; only then is the gem item consumed and the gem
     * revealed.
     */
    private fun revealGem(wheel: PlayerWheel, command: RevealGemCommand, player: WheelPlayer): WheelGemActionResult {
        val gemItemId = player.getWheelGemId(command.quality)
        if (gemItemId == 0 || !player.hasItemCountById(gemItemId, 1, false)) {
            return WheelGemActionResult.Rejected(command.action, WheelGemActionRejection.MISSING_GEM_ITEM)
        }
        if (!player.removeMoney(wheel.getGemRevealCost(command.quality))) {
            return WheelGemActionResult.Rejected(command.action, WheelGemActionRejection.NOT_ENOUGH_MONEY)
        }
        if (!player.removeItemCountById(gemItemId, 1, false)) {
            return WheelGemActionResult.Rejected(command.action, WheelGemActionRejection.INTERNAL_ERROR)
        }
        val gem = wheel.revealGem(command.quality, random)
        return WheelGemActionResult.Applied(command.action, wheel.gems.revealedGems.indexOf(gem))
    }

    /**
     * Mirror of `PlayerWheel::destroyGem` (player_wheel.cpp:1212-1285): locked
     * gems are refused, then fragments are refunded by quality before the gem
     * is unset from its vessel and removed. A failed refund delivery leaves the
     * gem intact.
     */
    private fun destroyGem(
        wheel: PlayerWheel,
        command: DestroyGemCommand,
        player: WheelPlayer,
    ): WheelGemActionResult {
        val gem = wheel.getGem(command.gemIndex)
            ?: return WheelGemActionResult.Rejected(command.action, WheelGemActionRejection.NO_SUCH_GEM)
        if (gem.locked) {
            return WheelGemActionResult.Rejected(command.action, WheelGemActionRejection.GEM_LOCKED)
        }

        val (fragmentItemId, fragmentCount) = when (gem.quality) {
            WheelGemQuality.Lesser -> WheelItemIds.ITEM_LESSER_FRAGMENT to normalRandom.normalRandom(1, 5)
            WheelGemQuality.Regular -> WheelItemIds.ITEM_LESSER_FRAGMENT to normalRandom.normalRandom(2, 10)
            WheelGemQuality.Greater -> WheelItemIds.ITEM_GREATER_FRAGMENT to normalRandom.normalRandom(1, 5)
        }
        if (fragmentCount > 0 && !player.internalPlayerAddItem(fragmentItemId, fragmentCount)) {
            return WheelGemActionResult.Rejected(command.action, WheelGemActionRejection.INTERNAL_ERROR)
        }

        wheel.destroyGem(command.gemIndex)
        return WheelGemActionResult.Applied(command.action, command.gemIndex)
    }

    /**
     * Mirror of `PlayerWheel::switchGemDomain` (player_wheel.cpp:1287-1306):
     * locked gems are refused, then the quality's rotate cost is paid before
     * the affinity rotates.
     */
    private fun switchGemDomain(
        wheel: PlayerWheel,
        command: SwitchGemDomainCommand,
        player: WheelPlayer,
    ): WheelGemActionResult {
        val gem = wheel.getGem(command.gemIndex)
            ?: return WheelGemActionResult.Rejected(command.action, WheelGemActionRejection.NO_SUCH_GEM)
        if (gem.locked) {
            return WheelGemActionResult.Rejected(command.action, WheelGemActionRejection.GEM_LOCKED)
        }
        if (!player.removeMoney(wheel.getGemRotateCost(gem.quality))) {
            return WheelGemActionResult.Rejected(command.action, WheelGemActionRejection.NOT_ENOUGH_MONEY)
        }
        wheel.switchGemDomain(command.gemIndex)
        return WheelGemActionResult.Applied(command.action, command.gemIndex)
    }

    private fun toggleGemLock(wheel: PlayerWheel, command: ToggleGemLockCommand): WheelGemActionResult {
        if (wheel.getGem(command.gemIndex) == null) {
            return WheelGemActionResult.Rejected(command.action, WheelGemActionRejection.NO_SUCH_GEM)
        }
        wheel.toggleGemLock(command.gemIndex)
        return WheelGemActionResult.Applied(command.action, command.gemIndex)
    }

    /**
     * Mirror of `PlayerWheel::improveGemGrade` (player_wheel.cpp:1448-1506):
     * the next grade's fragment quantity must be held (stash included) and the
     * grade's gold cost paid before the fragments are consumed and the grade
     * raised.
     */
    private fun improveGemGrade(
        wheel: PlayerWheel,
        command: ImproveGemGradeCommand,
        player: WheelPlayer,
    ): WheelGemActionResult {
        val grades = when (command.fragmentType) {
            WheelFragmentType.Lesser -> wheel.gems.basicGrades
            WheelFragmentType.Greater -> wheel.gems.supremeGrades
        }
        if (command.position !in grades.indices) {
            return WheelGemActionResult.Rejected(command.action, WheelGemActionRejection.INVALID_POSITION)
        }
        if (grades[command.position] >= PlayerWheel.MAX_GEM_GRADE) {
            return WheelGemActionResult.Rejected(command.action, WheelGemActionRejection.MAX_GRADE_REACHED)
        }

        val nextGrade = grades[command.position] + 1
        val fragmentItemId = when (command.fragmentType) {
            WheelFragmentType.Lesser -> WheelItemIds.ITEM_LESSER_FRAGMENT
            WheelFragmentType.Greater -> WheelItemIds.ITEM_GREATER_FRAGMENT
        }
        val (goldCost, fragmentCount) = when (command.fragmentType) {
            WheelFragmentType.Lesser -> wheel.getLesserGradeCost(nextGrade)
            WheelFragmentType.Greater -> wheel.getGreaterGradeCost(nextGrade)
        }

        if (!player.hasItemCountById(fragmentItemId, fragmentCount, true)) {
            return WheelGemActionResult.Rejected(command.action, WheelGemActionRejection.NOT_ENOUGH_FRAGMENTS)
        }
        if (!player.removeMoney(goldCost)) {
            return WheelGemActionResult.Rejected(command.action, WheelGemActionRejection.NOT_ENOUGH_MONEY)
        }
        if (!player.removeItemCountById(fragmentItemId, fragmentCount, true)) {
            return WheelGemActionResult.Rejected(command.action, WheelGemActionRejection.INTERNAL_ERROR)
        }

        wheel.improveGemGrade(command.fragmentType, command.position)
        return WheelGemActionResult.Applied(command.action, command.position)
    }

    companion object {
        /** `while (maxLoop <= 5) { maxLoop++; ... }` in Canary (player_wheel.cpp:1695). */
        const val SAVE_RETRY_PASSES = 6
    }
}
