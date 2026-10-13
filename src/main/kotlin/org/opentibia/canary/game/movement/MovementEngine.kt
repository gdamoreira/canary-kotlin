package org.opentibia.canary.game.movement

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.ScriptingDispatch
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.game.movement.getDirectionTo as positionGetDirectionTo

enum class Direction(val byteValue: Byte, val dx: Int, val dy: Int) {
    North(0, 0, -1),
    East(1, 1, 0),
    South(2, 0, 1),
    West(3, -1, 0),
    SouthWest(4, -1, 1),
    SouthEast(5, 1, 1),
    NorthWest(6, -1, -1),
    NorthEast(7, 1, -1);

    /** `getNextPosition` (src/utils/tools.cpp:581) for this direction. */
    fun offset(pos: Position): Position = getNextPosition(this, pos)
}

object MovementEngine {

    /**
     * Delegates to the canonical `getDirectionTo` (src/utils/tools.cpp:626-690) in
     * Position.kt so every call site shares the exact Canary algorithm, including
     * the exact-diagonal bias for non-equal offsets. `DIRECTION_NONE` maps to `null`.
     */
    fun getDirectionTo(from: Position, to: Position, exactDiagonal: Boolean = true): Direction? =
        positionGetDirectionTo(from, to, exactDiagonal)

    fun calculateStepDuration(creatureSpeed: Int, groundSpeed: Int = 100): Long {
        if (creatureSpeed <= 0) return Long.MAX_VALUE
        return (groundSpeed.toLong() * 1000L) / creatureSpeed.toLong()
    }

    /**
     * Moves [creature] one tile towards [direction] when the destination tile exists.
     *
     * When [scripting] is supplied every step event of the origin tile fires first
     * (`onStepOut`), then every step event of the destination tile fires (`onStepIn`),
     * mirroring `Map::moveCreature`'s `postRemoveNotification` then `postAddNotification`
     * order (src/map/map.cpp:793-796) and `MoveEvents::onCreatureMove`
     * (src/lua/creature/movement.cpp:290-322). `fromPosition` is the pre-move position, as
     * in `MoveEvent::executeStep` (movement.cpp:756). The dispatch itself is no-op-safe:
     * [ScriptingDispatch.moveEventOnStepIn]/[ScriptingDispatch.moveEventOnStepOut] return
     * false and enqueue nothing when no move event is registered for the item id.
     */
    fun move(
        creature: Creature,
        direction: Direction,
        map: GameMap,
        scripting: ScriptingDispatch? = null,
    ): Boolean {
        val fromPosition = creature.position
        val targetPos = direction.offset(fromPosition)
        val targetTile = map.getTile(targetPos) ?: return false

        creature.position = targetPos

        if (scripting != null) {
            map.getTile(fromPosition)?.let { fromTile ->
                stepItems(fromTile).forEach { item ->
                    scripting.moveEventOnStepOut(item.id, creature, item, fromPosition, fromPosition)
                }
            }
            stepItems(targetTile).forEach { item ->
                scripting.moveEventOnStepIn(item.id, creature, item, targetPos, fromPosition)
            }
        }

        return true
    }

    /**
     * Mirrors the `tile->getFirstIndex()..tile->getLastIndex()` scan of
     * `MoveEvents::onCreatureMove` (src/lua/creature/movement.cpp:300-320): the ground and
     * every stacked item can carry a registered step event.
     */
    private fun stepItems(tile: Tile): List<Item> = listOfNotNull(tile.ground) + tile.items
}
