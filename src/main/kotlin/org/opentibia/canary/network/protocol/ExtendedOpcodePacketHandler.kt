package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.ScriptingDispatch
import org.opentibia.canary.network.message.readTibiaString

/**
 * OTClient extended opcode request (client opcode `0x32`), mirroring
 * `ProtocolGame::parseExtendedOpcode` (protocolgame.cpp:10673-10679): one
 * opcode byte followed by a length-prefixed string buffer.
 */
data class ExtendedOpcodeCommand(
    val opcode: Int,
    val buffer: String,
)

/**
 * Decoder and dispatcher for `ProtocolGame::parseExtendedOpcode`.
 *
 * Canary hands the decoded pair to `Game::parsePlayerExtendedOpcode`
 * (game.cpp:11114-11123), which fires every `CREATURE_EVENT_EXTENDED_OPCODE`
 * creature event attached to the player. This port has no per-player
 * attachment registry yet, so [handle] fires every registered creature event
 * whose type is `extendedopcode` (the same substitution
 * [ScriptingDispatch.creatureOnThink]/[ScriptingDispatch.creatureOnDeath]
 * document as PARITY GAP), confined to the WorldCommit lane through
 * [ScriptingDispatch.creatureEventOnExtendedOpcode].
 */
object ExtendedOpcodePacketHandler {

    /** Client opcode for `ProtocolGame::parseExtendedOpcode`. */
    const val OPCODE_EXTENDED_OPCODE: Int = 0x32

    /** `CreatureEvent` type registered by `onExtendedOpcode` (CreatureEventBindings.kt:35). */
    const val EXTENDED_OPCODE_EVENT_TYPE: String = "extendedopcode"

    /**
     * 0x32: reads the opcode byte and the `NetworkMessage::getString()`
     * length-prefixed buffer.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parse(payload: ByteBuf): ExtendedOpcodeCommand? {
        if (payload.readableBytes() < 1) {
            return null
        }
        val opcode = payload.readUnsignedByte().toInt()
        if (payload.readableBytes() < 2) {
            return null
        }
        val length = payload.getUnsignedShortLE(payload.readerIndex())
        if (payload.readableBytes() < 2 + length) {
            return null
        }
        return ExtendedOpcodeCommand(opcode = opcode, buffer = payload.readTibiaString())
    }

    /**
     * Decodes the packet and submits the registered `onExtendedOpcode`
     * callbacks to the WorldCommit lane. Returns `true` whenever the packet
     * decodes (Canary consumes the packet even when no event is registered).
     */
    fun handle(
        player: Player,
        payload: ByteBuf,
        dispatcher: GameDispatcher,
        luaEnvironment: LuaEnvironment,
    ): Boolean {
        val command = parse(payload) ?: return false

        val scriptingDispatch = ScriptingDispatch(luaEnvironment, dispatcher)
        for (name in luaEnvironment.creatureEvents.names()) {
            if (luaEnvironment.creatureEvents.get(name)?.eventType != EXTENDED_OPCODE_EVENT_TYPE) {
                continue
            }
            scriptingDispatch.creatureEventOnExtendedOpcode(name, player, command.opcode, command.buffer)
        }
        return true
    }
}
