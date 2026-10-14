package org.opentibia.canary.network.protocol.creature

import io.netty.buffer.ByteBuf
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.network.protocol.CreatureAppearanceFrame
import org.opentibia.canary.network.protocol.GameWindowWriter
import org.opentibia.canary.network.protocol.map.MapDescriptionWriter
import kotlin.math.abs

/**
 * Outbound creature motion/speech frames for the Canary
 * `ProtocolProfileId::Current` profile (`protocol_new` / 15.x: `oldProtocol ==
 * false`), ported from src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendCreatureTurn`     (0x6B, protocolgame.cpp:7988-8004)
 *  - `ProtocolGame::sendCreatureSay`      (0xAA, protocolgame.cpp:8006-8046)
 *  - `ProtocolGame::sendCreatureHelpers`  (0x94, protocolgame.cpp:10928-10938)
 *  - `Player::sendCreatureMove`           (player.cpp:8803-8807) forwarding to
 *    `ProtocolGame::sendMoveCreature`     (protocolgame.cpp:8814-8874)
 *
 * The state gates the C++ senders own (`canSee(creature)`/`canSee(pos)`,
 * `oldProtocol`, the process-wide `static uint32_t statementId` counter) stay
 * with the caller. The already-ported creature state senders live in
 * `CreatureWindowWriter` (health 0x8C, outfit 0x8E, change speed 0x8F, skull
 * 0x90, shield 0x91, square 0x93, type 0x95, icon 0x8B, icons 0xA2) and are not
 * duplicated here.
 *
 * [writeMoveCreature]'s `appearanceFor` is the session seam for
 * `ProtocolGame::checkCreatureAsKnown` plus `AddCreature`; it resolves the
 * moving creature and every creature serialized by the refreshed map strips.
 */
object CreaturePacketWriter {

    const val PACKET_CREATURE_TURN: Int = 0x6B
    const val PACKET_CREATURE_SAY: Int = 0xAA
    const val PACKET_CREATURE_HELPERS: Int = 0x94
    const val PACKET_MOVE_CREATURE: Int = 0x6D

    private const val CREATURE_UPDATE_MARKER: Int = 0x63
    private const val MAX_STACKPOS = 10
    private const val SHOW_TRADED = 0x00

    /** `sendCreatureTurn(creature, stackPos)` (protocolgame.cpp:7988-8004). */
    fun writeCreatureTurn(
        out: ByteBuf,
        pos: Position,
        stackPos: Int,
        id: Long,
        direction: Direction,
        walkthrough: Boolean,
    ) {
        out.writeByte(PACKET_CREATURE_TURN)
        out.writePosition(pos)
        out.writeByte(stackPos)
        out.writeShortLE(CREATURE_UPDATE_MARKER)
        out.writeIntLE(id.toInt())
        out.writeByte(direction.byteValue.toInt() and 0xFF)
        out.writeByte(if (walkthrough) 0x00 else 0x01)
    }

    /**
     * `sendCreatureSay(creature, type, text, pos)` current-profile layout
     * (protocolgame.cpp:8006-8046). [speakerLevel] is `Player::getLevel()` for a
     * player speaker and `0` otherwise; [statementId] mirrors the
     * `++statementId` process-wide counter the C++ static owns.
     */
    fun writeCreatureSay(
        out: ByteBuf,
        statementId: Int,
        speakerName: String,
        speakerLevel: Int,
        type: SpeakClasses,
        pos: Position,
        text: String,
    ) {
        out.writeByte(PACKET_CREATURE_SAY)
        out.writeIntLE(statementId)
        out.writeTibiaString(speakerName)
        out.writeByte(SHOW_TRADED)
        out.writeShortLE(speakerLevel)
        out.writeByte(type.value)
        out.writePosition(pos)
        out.writeTibiaString(text)
    }

    /**
     * `sendCreatureHelpers(creatureId, helpers)` (protocolgame.cpp:10928-10938).
     * Canary emits this frame only for old-protocol sessions; the `oldProtocol`
     * gate stays with the caller like the other profile gates.
     */
    fun writeCreatureHelpers(out: ByteBuf, creatureId: Long, helpers: Int) {
        out.writeByte(PACKET_CREATURE_HELPERS)
        out.writeIntLE(creatureId.toInt())
        out.writeShortLE(helpers)
    }

    /**
     * `ProtocolGame::sendMoveCreature(creature, newPos, newStackPos, oldPos,
     * oldStackPos, teleport)` (protocolgame.cpp:8814-8874), reached through
     * `Player::sendCreatureMove` (player.cpp:8803-8807). The caller owns the
     * `ProtocolGame::canSee(pos)`-equivalent state only where the C++ sender
     * does not check it; the two `canSee` branches are evaluated here because
     * they select between the 0x6C/0x6A/0x6D frames.
     */
    fun writeMoveCreature(
        out: ByteBuf,
        map: GameMap,
        player: Player,
        creature: Creature,
        newPos: Position,
        newStackPos: Int,
        oldPos: Position,
        oldStackPos: Int,
        teleport: Boolean,
        appearanceFor: (Creature) -> CreatureAppearanceFrame,
    ) {
        if (creature === player) {
            if (oldStackPos >= MAX_STACKPOS) {
                MapDescriptionWriter.writeMapDescription(out, map, player, newPos, appearanceFor)
            } else if (teleport) {
                GameWindowWriter.writeRemoveTileThing(out, oldPos, oldStackPos)
                MapDescriptionWriter.writeMapDescription(out, map, player, newPos, appearanceFor)
            } else {
                if (
                    oldPos.z == GameMap.MAP_INIT_SURFACE_LAYER &&
                    newPos.z >= GameMap.MAP_INIT_SURFACE_LAYER + 1
                ) {
                    GameWindowWriter.writeRemoveTileThing(out, oldPos, oldStackPos)
                } else {
                    out.writeByte(PACKET_MOVE_CREATURE)
                    out.writePosition(oldPos)
                    out.writeByte(oldStackPos)
                    out.writePosition(newPos)
                }

                if (newPos.z > oldPos.z) {
                    MapDescriptionWriter.writeMoveDownCreature(
                        out, map, player, creature, newPos, oldPos, appearanceFor,
                    )
                } else if (newPos.z < oldPos.z) {
                    MapDescriptionWriter.writeMoveUpCreature(
                        out, map, player, creature, newPos, oldPos, appearanceFor,
                    )
                }

                if (oldPos.y > newPos.y) {
                    out.writeByte(0x65)
                    MapDescriptionWriter.writeMapDescriptionRange(
                        out, map, player,
                        oldPos.x - GameMap.MAP_MAX_CLIENT_VIEW_PORT_X,
                        newPos.y - GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y,
                        newPos.z,
                        (GameMap.MAP_MAX_CLIENT_VIEW_PORT_X + 1) * 2,
                        1,
                        appearanceFor,
                    )
                } else if (oldPos.y < newPos.y) {
                    out.writeByte(0x67)
                    MapDescriptionWriter.writeMapDescriptionRange(
                        out, map, player,
                        oldPos.x - GameMap.MAP_MAX_CLIENT_VIEW_PORT_X,
                        newPos.y + (GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y + 1),
                        newPos.z,
                        (GameMap.MAP_MAX_CLIENT_VIEW_PORT_X + 1) * 2,
                        1,
                        appearanceFor,
                    )
                }

                if (oldPos.x < newPos.x) {
                    out.writeByte(0x66)
                    MapDescriptionWriter.writeMapDescriptionRange(
                        out, map, player,
                        newPos.x + (GameMap.MAP_MAX_CLIENT_VIEW_PORT_X + 1),
                        newPos.y - GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y,
                        newPos.z,
                        1,
                        (GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y + 1) * 2,
                        appearanceFor,
                    )
                } else if (oldPos.x > newPos.x) {
                    out.writeByte(0x68)
                    MapDescriptionWriter.writeMapDescriptionRange(
                        out, map, player,
                        newPos.x - GameMap.MAP_MAX_CLIENT_VIEW_PORT_X,
                        newPos.y - GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y,
                        newPos.z,
                        1,
                        (GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y + 1) * 2,
                        appearanceFor,
                    )
                }
            }
        } else if (canSee(player, oldPos) && canSee(player, newPos)) {
            if (
                teleport ||
                (oldPos.z == GameMap.MAP_INIT_SURFACE_LAYER && newPos.z >= GameMap.MAP_INIT_SURFACE_LAYER + 1) ||
                oldStackPos >= MAX_STACKPOS
            ) {
                GameWindowWriter.writeRemoveTileThing(out, oldPos, oldStackPos)
                GameWindowWriter.writeAddCreature(out, newPos, newStackPos, appearanceFor(creature))
            } else {
                out.writeByte(PACKET_MOVE_CREATURE)
                out.writePosition(oldPos)
                out.writeByte(oldStackPos)
                out.writePosition(newPos)
            }
        } else if (canSee(player, oldPos)) {
            GameWindowWriter.writeRemoveTileThing(out, oldPos, oldStackPos)
        } else if (canSee(player, newPos)) {
            GameWindowWriter.writeAddCreature(out, newPos, newStackPos, appearanceFor(creature))
        }
    }

    /**
     * `ProtocolGame::canSee(x, y, z)` (protocolgame.cpp:2392-2410), the client
     * viewport check behind every `canSee(pos)` gate. `ProtocolGame::canSee(creature)`
     * (protocolgame.cpp:2376-2386) composes it with the removed flag and
     * `player.canSeeCreature`; callers gate on that composition themselves.
     */
    fun canSee(player: Player, pos: Position): Boolean {
        val myPos = player.position
        if (myPos.z <= GameMap.MAP_INIT_SURFACE_LAYER) {
            if (pos.z > GameMap.MAP_INIT_SURFACE_LAYER) {
                return false
            }
        } else if (myPos.z >= GameMap.MAP_INIT_SURFACE_LAYER + 1) {
            if (abs(myPos.z - pos.z) > GameMap.MAP_LAYER_VIEW_LIMIT) {
                return false
            }
        }

        val offsetz = myPos.z - pos.z
        return pos.x >= myPos.x - GameMap.MAP_MAX_CLIENT_VIEW_PORT_X + offsetz &&
            pos.x <= myPos.x + (GameMap.MAP_MAX_CLIENT_VIEW_PORT_X + 1) + offsetz &&
            pos.y >= myPos.y - GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y + offsetz &&
            pos.y <= myPos.y + (GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y + 1) + offsetz
    }

    private fun ByteBuf.writePosition(pos: Position) {
        writeShortLE(pos.x)
        writeShortLE(pos.y)
        writeByte(pos.z)
    }
}
