package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.LuaEventDispatcher
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

/**
 * Use-item-ex request (client opcode `0x83`), mirroring
 * `ProtocolGame::parseUseItemEx` (protocolgame.cpp:2566-2574): the source
 * `Position` + `uint16_t` item id + stack position byte followed by the same
 * triple for the target.
 */
data class UseItemExCommand(
    val fromPosition: Position,
    val fromItemId: Int,
    val fromStackPosition: Int,
    val toPosition: Position,
    val toItemId: Int,
    val toStackPosition: Int,
)

object ItemPacketHandler {

    const val OPCODE_USE_ITEM_EX: Int = 0x83

    /**
     * 0x83: reads the source `Position`, the source `uint16_t` item id and
     * stack position byte, then the same triple for the target, in Canary's
     * exact read order (`ProtocolGame::parseUseItemEx`,
     * protocolgame.cpp:2566-2574).
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseUseItemEx(payload: ByteBuf): UseItemExCommand? {
        val fromPosition = readPosition(payload) ?: return null
        val fromItemId = readShort(payload) ?: return null
        val fromStackPosition = readByte(payload) ?: return null
        val toPosition = readPosition(payload) ?: return null
        val toItemId = readShort(payload) ?: return null
        val toStackPosition = readByte(payload) ?: return null
        return UseItemExCommand(
            fromPosition = fromPosition,
            fromItemId = fromItemId,
            fromStackPosition = fromStackPosition,
            toPosition = toPosition,
            toItemId = toItemId,
            toStackPosition = toStackPosition,
        )
    }

    /**
     * Decodes 0x83 and marks the WorldCommit confinement boundary. The
     * multi-use engine behind `Game::playerUseItemEx` (game.cpp:4576) is not
     * ported, so no world mutation is applied yet.
     */
    fun handleUseItemEx(payload: ByteBuf, dispatcher: GameDispatcher): Boolean {
        parseUseItemEx(payload) ?: return false
        dispatcher.dispatch(DispatcherLane.WorldCommit) {
            // Game::playerUseItemEx resolves both things and applies the use; the
            // decoded command only marks the confinement boundary.
        }
        return true
    }

    fun handleUse(
        player: Player,
        payload: ByteBuf,
        map: GameMap,
        dispatcher: GameDispatcher,
        luaEnvironment: LuaEnvironment
    ): Boolean {
        if (payload.readableBytes() < 9) return false

        val x = payload.readUnsignedShortLE()
        val y = payload.readUnsignedShortLE()
        val z = payload.readUnsignedByte().toInt()
        val itemId = payload.readUnsignedShortLE()
        val stackpos = payload.readUnsignedByte().toInt()
        val index = payload.readUnsignedByte().toInt()

        val itemPos = Position(x, y, z)
        val tile = map.getTile(itemPos) ?: return false
        val item = tile.items.find { it.id == itemId } ?: (if (tile.ground?.id == itemId) tile.ground else null) ?: Item(itemId)

        luaEnvironment.actions.get(item.id) ?: return false

        // Always post to WorldCommit so the Lua handler is confined even if this is
        // ever invoked off the game lane; the world mutation contract (AGENTS.md 1.2)
        // must not depend on the caller's thread. Argument order and userdata wrapping
        // live in LuaEventDispatcher so they match Canary's Action::executeUse exactly.
        dispatcher.dispatch(DispatcherLane.WorldCommit) {
            LuaEventDispatcher.actionOnUse(
                luaEnvironment,
                item.id,
                player,
                item,
                itemPos,
                null,
                itemPos,
                false
            )
        }
        return true
    }

    fun handleThrow(player: Player, payload: ByteBuf, map: GameMap): Boolean {
        if (payload.readableBytes() < 14) return false

        val fromX = payload.readUnsignedShortLE()
        val fromY = payload.readUnsignedShortLE()
        val fromZ = payload.readUnsignedByte().toInt()
        val itemId = payload.readUnsignedShortLE()
        val fromStackpos = payload.readUnsignedByte().toInt()

        val toX = payload.readUnsignedShortLE()
        val toY = payload.readUnsignedShortLE()
        val toZ = payload.readUnsignedByte().toInt()
        val count = payload.readUnsignedByte().toInt()

        val fromPos = Position(fromX, fromY, fromZ)
        val toPos = Position(toX, toY, toZ)

        val fromTile = map.getTile(fromPos) ?: return false
        val toTile = map.getTile(toPos) ?: return false

        val itemToMove = fromTile.items.find { it.id == itemId } ?: return false

        fromTile.removeItem(itemToMove)
        toTile.addItem(itemToMove)
        return true
    }

    private fun readByte(payload: ByteBuf): Int? =
        if (payload.readableBytes() < 1) null else payload.readUnsignedByte().toInt()

    private fun readShort(payload: ByteBuf): Int? =
        if (payload.readableBytes() < 2) null else payload.readUnsignedShortLE()

    private fun readPosition(payload: ByteBuf): Position? {
        if (payload.readableBytes() < 5) return null
        val x = payload.readUnsignedShortLE()
        val y = payload.readUnsignedShortLE()
        val z = payload.readUnsignedByte().toInt()
        return Position(x, y, z)
    }
}
