package org.opentibia.canary.network.protocol.player

import io.netty.buffer.ByteBuf
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.network.protocol.GameWindowWriter
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.network.protocol.TextMessageFrame

/**
 * Outbound player-window frames for the Canary `ProtocolProfileId::Current`
 * profile (`protocol_new` / 15.x: `oldProtocol == false`), ported from
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendTutorial`             (0xDC, protocolgame.cpp:4755-4760)
 *  - `ProtocolGame::sendCancelTarget`         (0xA3, protocolgame.cpp:8130-8135)
 *  - `ProtocolGame::sendCancelWalk`           (0xB5, protocolgame.cpp:8152-8159)
 *  - `ProtocolGame::sendFightModes`           (0xA7, protocolgame.cpp:8577-8593)
 *  - `ProtocolGame::sendSpellCooldown`        (0xA4, protocolgame.cpp:9427-9445)
 *  - `ProtocolGame::sendSpellGroupCooldown`   (0xA5, protocolgame.cpp:9447-9457)
 *  - `ProtocolGame::sendUseItemCooldown`      (0xA6, protocolgame.cpp:9459-9468)
 *  - `Player::sendCancelMessage`              (0xB4/21, player.cpp:2111-2119)
 *
 * Every multi-byte field is Little-Endian. The `shouldSuppressPreLoginPacket`,
 * `oldProtocol` (version < 870 / version < 1059) and absent-player gates stay
 * with the caller. The current profile always carries
 * `ProtocolFeature::TacticsWithoutFightMode` (protocol_profile.cpp:202), so
 * [writeFightModes] emits the chase/secure/dove triple without the fight mode
 * byte. `sendPlayerModes` is not a Canary method; `sendFightModes` is the real
 * sender.
 */
object PlayerWindowWriter {

    const val PACKET_TUTORIAL: Int = 0xDC
    const val PACKET_CANCEL_TARGET: Int = 0xA3
    const val PACKET_CANCEL_WALK: Int = 0xB5
    const val PACKET_FIGHT_MODES: Int = 0xA7
    const val PACKET_SPELL_COOLDOWN: Int = 0xA4
    const val PACKET_SPELL_GROUP_COOLDOWN: Int = 0xA5
    const val PACKET_USE_ITEM_COOLDOWN: Int = 0xA6

    /** `PVP_MODE_DOVE` (`src/creatures/creatures_definitions.hpp:819`). */
    private const val PVP_MODE_DOVE: Int = 0

    /** `sendTutorial` (protocolgame.cpp:4755-4760). */
    fun writeTutorial(out: ByteBuf, tutorialId: Int) {
        out.writeByte(PACKET_TUTORIAL)
        out.writeByte(tutorialId)
    }

    /** `sendCancelTarget` (protocolgame.cpp:8130-8135): a zero creature id. */
    fun writeCancelTarget(out: ByteBuf) {
        out.writeByte(PACKET_CANCEL_TARGET)
        out.writeIntLE(0)
    }

    /** `sendCancelWalk` (protocolgame.cpp:8152-8159): the player's `getDirection()` byte. */
    fun writeCancelWalk(out: ByteBuf, direction: Direction) {
        out.writeByte(PACKET_CANCEL_WALK)
        out.writeByte(direction.byteValue.toInt() and 0xFF)
    }

    /**
     * `sendFightModes` current-profile branch (protocolgame.cpp:8581-8585):
     * `player->chaseMode`, `player->secureMode` (both `bool` in player.hpp:1864-1865)
     * and the fixed `PVP_MODE_DOVE`.
     */
    fun writeFightModes(out: ByteBuf, chaseMode: Boolean, secureMode: Boolean) {
        out.writeByte(PACKET_FIGHT_MODES)
        out.writeByte(if (chaseMode) 0x01 else 0x00)
        out.writeByte(if (secureMode) 0x01 else 0x00)
        out.writeByte(PVP_MODE_DOVE)
    }

    /** `sendSpellCooldown` current-profile branch (protocolgame.cpp:9434-9444): `u16` spell id. */
    fun writeSpellCooldown(out: ByteBuf, spellId: Int, time: Int) {
        out.writeByte(PACKET_SPELL_COOLDOWN)
        out.writeShortLE(spellId)
        out.writeIntLE(time)
    }

    /** `sendSpellGroupCooldown` (protocolgame.cpp:9447-9457): `u8` group and `u32` time. */
    fun writeSpellGroupCooldown(out: ByteBuf, groupId: SpellGroup, time: Int) {
        out.writeByte(PACKET_SPELL_GROUP_COOLDOWN)
        out.writeByte(groupId.value)
        out.writeIntLE(time)
    }

    /** `sendUseItemCooldown` (protocolgame.cpp:9459-9468). */
    fun writeUseItemCooldown(out: ByteBuf, time: Int) {
        out.writeByte(PACKET_USE_ITEM_COOLDOWN)
        out.writeIntLE(time)
    }

    /**
     * `Player::sendCancelMessage` (player.cpp:2111-2115) via
     * `ProtocolGame::sendTextMessage(MESSAGE_FAILURE, ...)`: the caller resolves
     * the `ReturnValue` through `getReturnMessage` first (player.cpp:2117-2119).
     */
    fun writeCancelMessage(out: ByteBuf, message: String) {
        GameWindowWriter.writeTextMessage(out, TextMessageFrame(MessageClass.MESSAGE_FAILURE, message))
    }
}
