package org.opentibia.canary.network.protocol.map

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.network.protocol.CreatureAppearanceFrame
import org.opentibia.canary.network.protocol.GameWindowWriter

/**
 * Outbound map/tile description frames for the Canary
 * `ProtocolProfileId::Current` profile (`protocol_new` / 15.x: `oldProtocol ==
 * false`), ported from src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendMapDescription`  (0x64, protocolgame.cpp:8477-8483)
 *  - `ProtocolGame::GetMapDescription`   (protocolgame.cpp:2287-2309)
 *  - `ProtocolGame::GetFloorDescription` (protocolgame.cpp:2311-2332)
 *  - `ProtocolGame::GetTileDescription`  (protocolgame.cpp:2213-2285)
 *  - `ProtocolGame::MoveUpCreature`      (0xBE, protocolgame.cpp:10551-10593)
 *  - `ProtocolGame::MoveDownCreature`    (0xBF, protocolgame.cpp:10595-10635)
 *
 * Every multi-byte field is Little-Endian and the tile/floor skip runs are
 * `int8` counters terminated by `0xFF`. The `canSee(pos)` gates that guard the
 * C++ senders stay with the caller, matching
 * `GameWindowWriter.writeRemoveTileThing`.
 *
 * [appearanceFor] is the session seam for `ProtocolGame::checkCreatureAsKnown`
 * plus `AddCreature`; it must return the resolved creature frame for the tile
 * description and is invoked once per serialized creature.
 */
object MapDescriptionWriter {

    const val PACKET_MAP_DESCRIPTION: Int = 0x64
    const val PACKET_FLOOR_CHANGE_UP: Int = 0xBE
    const val PACKET_FLOOR_CHANGE_DOWN: Int = 0xBF

    /** `MAP_MAX_LAYERS` (src/map/map_const.hpp:17). */
    private const val MAP_MAX_LAYERS = 16

    private const val VIEW_PORT_WIDTH = (GameMap.MAP_MAX_CLIENT_VIEW_PORT_X + 1) * 2
    private const val VIEW_PORT_HEIGHT = (GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y + 1) * 2

    /** `sendMapDescription(pos)` (protocolgame.cpp:8477-8483). */
    fun writeMapDescription(
        out: ByteBuf,
        map: GameMap,
        player: Player,
        pos: Position,
        appearanceFor: (Creature) -> CreatureAppearanceFrame,
    ) {
        out.writeByte(PACKET_MAP_DESCRIPTION)
        out.writePosition(player.position)
        writeMapDescriptionRange(
            out,
            map,
            player,
            pos.x - GameMap.MAP_MAX_CLIENT_VIEW_PORT_X,
            pos.y - GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y,
            pos.z,
            VIEW_PORT_WIDTH,
            VIEW_PORT_HEIGHT,
            appearanceFor,
        )
    }

    /** `GetMapDescription(x, y, z, width, height, msg)` (protocolgame.cpp:2287-2309). */
    fun writeMapDescriptionRange(
        out: ByteBuf,
        map: GameMap,
        player: Player,
        x: Int,
        y: Int,
        z: Int,
        width: Int,
        height: Int,
        appearanceFor: (Creature) -> CreatureAppearanceFrame,
    ) {
        var skip = -1
        val startz: Int
        val endz: Int
        val zstep: Int

        if (z > GameMap.MAP_INIT_SURFACE_LAYER) {
            startz = z - GameMap.MAP_LAYER_VIEW_LIMIT
            endz = minOf(MAP_MAX_LAYERS - 1, z + GameMap.MAP_LAYER_VIEW_LIMIT)
            zstep = 1
        } else {
            startz = GameMap.MAP_INIT_SURFACE_LAYER
            endz = 0
            zstep = -1
        }

        var nz = startz
        while (nz != endz + zstep) {
            skip = writeFloorDescription(
                out,
                map,
                player,
                x,
                y,
                nz,
                width,
                height,
                z - nz,
                skip,
                appearanceFor,
            )
            nz += zstep
        }

        if (skip >= 0) {
            out.writeByte(skip)
            out.writeByte(0xFF)
        }
    }

    /**
     * `GetFloorDescription(msg, x, y, z, width, height, offset, skip)`
     * (protocolgame.cpp:2311-2332). The C++ `int32_t& skip` in/out parameter is
     * returned here so callers can carry the run across consecutive floors.
     */
    fun writeFloorDescription(
        out: ByteBuf,
        map: GameMap,
        player: Player,
        x: Int,
        y: Int,
        z: Int,
        width: Int,
        height: Int,
        offset: Int,
        skip: Int,
        appearanceFor: (Creature) -> CreatureAppearanceFrame,
    ): Int {
        var currentSkip = skip
        for (nx in 0 until width) {
            for (ny in 0 until height) {
                val tile = map.getTile(Position(x + nx + offset, y + ny + offset, z))
                if (tile != null) {
                    if (currentSkip >= 0) {
                        out.writeByte(currentSkip)
                        out.writeByte(0xFF)
                    }
                    currentSkip = 0
                    writeTileDescription(out, player, tile, appearanceFor)
                } else if (currentSkip == 0xFE) {
                    out.writeByte(0xFF)
                    out.writeByte(0xFF)
                    currentSkip = -1
                } else {
                    ++currentSkip
                }
            }
        }
        return currentSkip
    }

    /**
     * `GetTileDescription(tile, msg)` current-profile layout
     * (protocolgame.cpp:2213-2285). The `oldProtocol && version >= 910` env
     * effects word and the `isCipsoft860Profile` branch are not emitted for the
     * current profile.
     */
    fun writeTileDescription(
        out: ByteBuf,
        player: Player,
        tile: Tile,
        appearanceFor: (Creature) -> CreatureAppearanceFrame,
    ) {
        var count: Int
        val ground = tile.ground
        if (ground != null) {
            GameWindowWriter.writeItem(out, ground)
            count = 1
        } else {
            count = 0
        }

        val items = tile.items
        val downItemCount = tile.getDownItemCount()

        var topIndex = downItemCount
        while (topIndex < items.size) {
            GameWindowWriter.writeItem(out, items[topIndex])
            ++count
            if (count == 9 && tile.position == player.position) {
                break
            } else if (count == 10) {
                return
            }
            ++topIndex
        }

        val creatures = tile.getCreatures()
        var playerAdded = false
        for (creatureIndex in creatures.indices.reversed()) {
            var creature = creatures[creatureIndex]
            if (creature.isRemoved() || !creature.isAlive()) {
                continue
            }
            if (!player.canSeeCreature(creature)) {
                continue
            }

            if (tile.position == player.position && count == 9 && !playerAdded) {
                creature = player
            }

            if (creature.id == player.id) {
                playerAdded = true
            }

            GameWindowWriter.writeCreature(out, appearanceFor(creature))

            if (++count == 10) {
                return
            }
        }

        for (downIndex in 0 until downItemCount) {
            GameWindowWriter.writeItem(out, items[downIndex])
            if (++count == 10) {
                return
            }
        }
    }

    /** `MoveUpCreature(msg, creature, newPos, oldPos)` (protocolgame.cpp:10551-10593). */
    fun writeMoveUpCreature(
        out: ByteBuf,
        map: GameMap,
        player: Player,
        creature: Creature,
        newPos: Position,
        oldPos: Position,
        appearanceFor: (Creature) -> CreatureAppearanceFrame,
    ) {
        if (creature !== player) {
            return
        }

        out.writeByte(PACKET_FLOOR_CHANGE_UP)

        if (newPos.z == GameMap.MAP_INIT_SURFACE_LAYER) {
            var skip = -1
            skip = writeFloorDescription(
                out, map, player,
                oldPos.x - GameMap.MAP_MAX_CLIENT_VIEW_PORT_X,
                oldPos.y - GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y,
                5, VIEW_PORT_WIDTH, VIEW_PORT_HEIGHT, 3, skip, appearanceFor,
            )
            skip = writeFloorDescription(
                out, map, player,
                oldPos.x - GameMap.MAP_MAX_CLIENT_VIEW_PORT_X,
                oldPos.y - GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y,
                4, VIEW_PORT_WIDTH, VIEW_PORT_HEIGHT, 4, skip, appearanceFor,
            )
            skip = writeFloorDescription(
                out, map, player,
                oldPos.x - GameMap.MAP_MAX_CLIENT_VIEW_PORT_X,
                oldPos.y - GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y,
                3, VIEW_PORT_WIDTH, VIEW_PORT_HEIGHT, 5, skip, appearanceFor,
            )
            skip = writeFloorDescription(
                out, map, player,
                oldPos.x - GameMap.MAP_MAX_CLIENT_VIEW_PORT_X,
                oldPos.y - GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y,
                2, VIEW_PORT_WIDTH, VIEW_PORT_HEIGHT, 6, skip, appearanceFor,
            )
            skip = writeFloorDescription(
                out, map, player,
                oldPos.x - GameMap.MAP_MAX_CLIENT_VIEW_PORT_X,
                oldPos.y - GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y,
                1, VIEW_PORT_WIDTH, VIEW_PORT_HEIGHT, 7, skip, appearanceFor,
            )
            skip = writeFloorDescription(
                out, map, player,
                oldPos.x - GameMap.MAP_MAX_CLIENT_VIEW_PORT_X,
                oldPos.y - GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y,
                0, VIEW_PORT_WIDTH, VIEW_PORT_HEIGHT, 8, skip, appearanceFor,
            )

            if (skip >= 0) {
                out.writeByte(skip)
                out.writeByte(0xFF)
            }
        } else if (newPos.z > GameMap.MAP_INIT_SURFACE_LAYER) {
            var skip = -1
            skip = writeFloorDescription(
                out, map, player,
                oldPos.x - GameMap.MAP_MAX_CLIENT_VIEW_PORT_X,
                oldPos.y - GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y,
                oldPos.z - 3, VIEW_PORT_WIDTH, VIEW_PORT_HEIGHT, 3, skip, appearanceFor,
            )

            if (skip >= 0) {
                out.writeByte(skip)
                out.writeByte(0xFF)
            }
        }

        out.writeByte(0x68)
        writeMapDescriptionRange(
            out,
            map,
            player,
            oldPos.x - GameMap.MAP_MAX_CLIENT_VIEW_PORT_X,
            oldPos.y - (GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y - 1),
            newPos.z,
            1,
            VIEW_PORT_HEIGHT,
            appearanceFor,
        )

        out.writeByte(0x65)
        writeMapDescriptionRange(
            out,
            map,
            player,
            oldPos.x - GameMap.MAP_MAX_CLIENT_VIEW_PORT_X,
            oldPos.y - GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y,
            newPos.z,
            VIEW_PORT_WIDTH,
            1,
            appearanceFor,
        )
    }

    /** `MoveDownCreature(msg, creature, newPos, oldPos)` (protocolgame.cpp:10595-10635). */
    fun writeMoveDownCreature(
        out: ByteBuf,
        map: GameMap,
        player: Player,
        creature: Creature,
        newPos: Position,
        oldPos: Position,
        appearanceFor: (Creature) -> CreatureAppearanceFrame,
    ) {
        if (creature !== player) {
            return
        }

        out.writeByte(PACKET_FLOOR_CHANGE_DOWN)

        if (newPos.z == GameMap.MAP_INIT_SURFACE_LAYER + 1) {
            var skip = -1
            skip = writeFloorDescription(
                out, map, player,
                oldPos.x - GameMap.MAP_MAX_CLIENT_VIEW_PORT_X,
                oldPos.y - GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y,
                newPos.z, VIEW_PORT_WIDTH, VIEW_PORT_HEIGHT, -1, skip, appearanceFor,
            )
            skip = writeFloorDescription(
                out, map, player,
                oldPos.x - GameMap.MAP_MAX_CLIENT_VIEW_PORT_X,
                oldPos.y - GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y,
                newPos.z + 1, VIEW_PORT_WIDTH, VIEW_PORT_HEIGHT, -2, skip, appearanceFor,
            )
            skip = writeFloorDescription(
                out, map, player,
                oldPos.x - GameMap.MAP_MAX_CLIENT_VIEW_PORT_X,
                oldPos.y - GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y,
                newPos.z + 2, VIEW_PORT_WIDTH, VIEW_PORT_HEIGHT, -3, skip, appearanceFor,
            )

            if (skip >= 0) {
                out.writeByte(skip)
                out.writeByte(0xFF)
            }
        } else if (
            newPos.z > oldPos.z &&
            newPos.z > GameMap.MAP_INIT_SURFACE_LAYER + 1 &&
            newPos.z < MAP_MAX_LAYERS - GameMap.MAP_LAYER_VIEW_LIMIT
        ) {
            var skip = -1
            skip = writeFloorDescription(
                out, map, player,
                oldPos.x - GameMap.MAP_MAX_CLIENT_VIEW_PORT_X,
                oldPos.y - GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y,
                newPos.z + GameMap.MAP_LAYER_VIEW_LIMIT, VIEW_PORT_WIDTH, VIEW_PORT_HEIGHT, -3, skip, appearanceFor,
            )

            if (skip >= 0) {
                out.writeByte(skip)
                out.writeByte(0xFF)
            }
        }

        out.writeByte(0x66)
        writeMapDescriptionRange(
            out,
            map,
            player,
            oldPos.x + GameMap.MAP_MAX_CLIENT_VIEW_PORT_X + 1,
            oldPos.y - (GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y + 1),
            newPos.z,
            1,
            VIEW_PORT_HEIGHT,
            appearanceFor,
        )

        out.writeByte(0x67)
        writeMapDescriptionRange(
            out,
            map,
            player,
            oldPos.x - GameMap.MAP_MAX_CLIENT_VIEW_PORT_X,
            oldPos.y + (GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y + 1),
            newPos.z,
            VIEW_PORT_WIDTH,
            1,
            appearanceFor,
        )
    }

    private fun ByteBuf.writePosition(pos: Position) {
        writeShortLE(pos.x)
        writeShortLE(pos.y)
        writeByte(pos.z)
    }
}
