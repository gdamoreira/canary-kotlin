package org.opentibia.canary.network.protocol.misc

import io.netty.buffer.ByteBuf
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.network.protocol.SourceEffect

/**
 * Outbound marker/effect/sound miscellaneous frames for the Canary
 * `ProtocolProfileId::Current` profile (`protocol_new` / 15.x:
 * `oldProtocol == false`), ported from
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendAddMarker`           (0xDD, protocolgame.cpp:4762-4775)
 *  - `ProtocolGame::sendSingleSoundEffect`   (0x83, protocolgame.cpp:11566-11579)
 *  - `ProtocolGame::sendDoubleSoundEffect`   (0x83, protocolgame.cpp:11581-11609)
 *  - `ProtocolGame::sendAmbientSoundEffect`  (0x85, protocolgame.cpp:11611-11621)
 *  - `ProtocolGame::sendMusicSoundEffect`    (0x85, protocolgame.cpp:11623-11633)
 *  - `ProtocolGame::sendAttachedEffect`      (0x34, protocolgame.cpp:11809-11819)
 *  - `ProtocolGame::sendDetachEffect`        (0x35, protocolgame.cpp:11821-11831)
 *  - `ProtocolGame::sendShader`              (0x36, protocolgame.cpp:11833-11843)
 *  - `ProtocolGame::sendMapShader`           (0x37, protocolgame.cpp:11845-11855)
 *
 * The `oldProtocol` and `isOTCR` gates stay with the caller. The current
 * profile always writes the `CyclopediaMapData_t::MinimapMarker` byte of
 * `sendAddMarker` (protocolgame.cpp:4767-4769). Every multi-byte field is
 * Little-Endian.
 */
object MiscEffectWriter {

    const val PACKET_ADD_MARKER: Int = 0xDD
    const val PACKET_MAGIC_EFFECTS: Int = 0x83
    const val PACKET_SOUND_EFFECT: Int = 0x85
    const val PACKET_ATTACHED_EFFECT: Int = 0x34
    const val PACKET_DETACHED_EFFECT: Int = 0x35
    const val PACKET_SHADER: Int = 0x36
    const val PACKET_MAP_SHADER: Int = 0x37

    /** `sendAddMarker` `CyclopediaMapData_t::MinimapMarker` byte (player_cyclopedia.hpp:51). */
    private const val CYCLOPEDIA_MAP_MINIMAP_MARKER: Int = 0x00

    /** `MAGIC_EFFECTS_CREATE_SOUND` type (protocolgame.cpp:11572). */
    private const val SOUND_EFFECT_TYPE: Int = 0x06

    /** `MAGIC_EFFECTS_CREATE_SOUND` in a multiple-effects loop (protocolgame.cpp:11595). */
    private const val SOUND_MULTIPLE_EFFECT_TYPE: Int = 0x07

    /** `MAGIC_EFFECTS_END_LOOP` (protocolgame.cpp:11578). */
    private const val MAGIC_EFFECTS_END_LOOP: Int = 0x00

    /** `sendDoubleSoundEffect`'s "useless enum" byte (protocolgame.cpp:11596). */
    private const val SOUND_MULTIPLE_UNKNOWN: Int = 0x01

    /** `sendAmbientSoundEffect` sub-opcode (protocolgame.cpp:11617). */
    private const val AMBIENT_SOUND_TYPE: Int = 0x00

    /** `sendMusicSoundEffect` sub-opcode (protocolgame.cpp:11629). */
    private const val MUSIC_SOUND_TYPE: Int = 0x01

    /**
     * `sendAddMarker` (protocolgame.cpp:4762-4775). The current profile writes
     * the `MinimapMarker` byte before the position.
     */
    fun writeAddMarker(out: ByteBuf, pos: Position, markType: Int, desc: String) {
        out.writeByte(PACKET_ADD_MARKER)
        out.writeByte(CYCLOPEDIA_MAP_MINIMAP_MARKER)
        out.writeShortLE(pos.x)
        out.writeShortLE(pos.y)
        out.writeByte(pos.z)
        out.writeByte(markType)
        out.writeTibiaString(desc)
    }

    /** `sendSingleSoundEffect` (protocolgame.cpp:11566-11579). */
    fun writeSingleSoundEffect(out: ByteBuf, pos: Position, id: Int, source: SourceEffect) {
        out.writeByte(PACKET_MAGIC_EFFECTS)
        writePosition(out, pos)
        out.writeByte(SOUND_EFFECT_TYPE)
        out.writeByte(source.value)
        out.writeShortLE(id)
        out.writeByte(MAGIC_EFFECTS_END_LOOP)
    }

    /** `sendDoubleSoundEffect` (protocolgame.cpp:11581-11609). */
    fun writeDoubleSoundEffect(
        out: ByteBuf,
        pos: Position,
        mainSoundId: Int,
        mainSource: SourceEffect,
        secondarySoundId: Int,
        secondarySource: SourceEffect,
    ) {
        out.writeByte(PACKET_MAGIC_EFFECTS)
        writePosition(out, pos)

        out.writeByte(SOUND_EFFECT_TYPE)
        out.writeByte(mainSource.value)
        out.writeShortLE(mainSoundId)

        out.writeByte(SOUND_MULTIPLE_EFFECT_TYPE)
        out.writeByte(SOUND_MULTIPLE_UNKNOWN)
        out.writeByte(secondarySource.value)
        out.writeShortLE(secondarySoundId)

        out.writeByte(MAGIC_EFFECTS_END_LOOP)
    }

    /** `sendAmbientSoundEffect` (protocolgame.cpp:11611-11621). */
    fun writeAmbientSoundEffect(out: ByteBuf, id: Int) {
        out.writeByte(PACKET_SOUND_EFFECT)
        out.writeByte(AMBIENT_SOUND_TYPE)
        out.writeShortLE(id)
    }

    /**
     * `sendMusicSoundEffect` (protocolgame.cpp:11623-11633). The frame shares
     * opcode `0x85` with `sendDisableLoginMusic`.
     */
    fun writeMusicSoundEffect(out: ByteBuf, id: Int) {
        out.writeByte(PACKET_SOUND_EFFECT)
        out.writeByte(MUSIC_SOUND_TYPE)
        out.writeShortLE(id)
    }

    /** `sendAttachedEffect` (protocolgame.cpp:11809-11819). */
    fun writeAttachedEffect(out: ByteBuf, creatureId: Long, effectId: Int) {
        out.writeByte(PACKET_ATTACHED_EFFECT)
        out.writeIntLE(creatureId.toInt())
        out.writeShortLE(effectId)
    }

    /** `sendDetachEffect` (protocolgame.cpp:11821-11831). */
    fun writeDetachEffect(out: ByteBuf, creatureId: Long, effectId: Int) {
        out.writeByte(PACKET_DETACHED_EFFECT)
        out.writeIntLE(creatureId.toInt())
        out.writeShortLE(effectId)
    }

    /** `sendShader` (protocolgame.cpp:11833-11843). */
    fun writeShader(out: ByteBuf, creatureId: Long, shaderName: String) {
        out.writeByte(PACKET_SHADER)
        out.writeIntLE(creatureId.toInt())
        out.writeTibiaString(shaderName)
    }

    /** `sendMapShader` (protocolgame.cpp:11845-11855). */
    fun writeMapShader(out: ByteBuf, shaderName: String) {
        out.writeByte(PACKET_MAP_SHADER)
        out.writeTibiaString(shaderName)
    }

    private fun writePosition(out: ByteBuf, pos: Position) {
        out.writeShortLE(pos.x)
        out.writeShortLE(pos.y)
        out.writeByte(pos.z)
    }
}
