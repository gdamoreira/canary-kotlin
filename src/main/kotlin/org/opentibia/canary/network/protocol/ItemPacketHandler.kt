package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.luaj.vm2.LuaValue
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

object ItemPacketHandler {

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

        val action = luaEnvironment.actions.get(item.id) ?: return false

        // Always post to WorldCommit so the Lua handler is confined even if this is
        // ever invoked off the game lane; the world mutation contract (AGENTS.md 1.2)
        // must not depend on the caller's thread.
        dispatcher.dispatch(DispatcherLane.WorldCommit) {
            action.onUse.invoke(
                LuaValue.varargsOf(
                    arrayOf(
                        luaEnvironment.userdata.wrap("Player", player),
                        luaEnvironment.userdata.wrap("Item", item),
                        luaEnvironment.userdata.wrap("Position", itemPos),
                        LuaValue.NIL,
                        LuaValue.NIL,
                        LuaValue.FALSE
                    )
                )
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
}
