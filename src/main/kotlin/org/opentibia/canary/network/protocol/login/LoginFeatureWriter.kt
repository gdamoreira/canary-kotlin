package org.opentibia.canary.network.protocol.login

import io.netty.buffer.ByteBuf

/**
 * Outbound login feature serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendFeatures`            (0x43, protocolgame.cpp:10682-10703)
 *  - `ProtocolGame::sendOTCRFeatures`        (0x43, protocolgame.cpp:10706-10725)
 *  - `ProtocolGame::sendClientLoginPreamble` (0x32, protocolgame.cpp:12361-12378)
 *
 * [writeFeatures] emits the OTCv8 feature map, which only carries
 * `GameFeature_t::ExtendedOpcode` (80); Canary suppresses the frame entirely
 * when `otclientV8 == 0`. [writeOTCRFeatures] writes the enabled list followed
 * by the disabled list under a single `uint16_t` total. The preamble composes
 * them with the `0x32 0x00 uint16_t 0x00` opcode frame exactly like the C++
 * method: features when `otclientV8 > 0`, OTCR features only when the client is
 * an OTClient and `otclientV8 == 0`, and the trailing opcode frame for every
 * OTClient. The `isOTC` flag the C++ method sets and the layout selection stay
 * with the caller. Every multi-byte field is Little-Endian.
 */
object LoginFeatureWriter {

    const val PACKET_FEATURES: Int = 0x43
    const val PACKET_LOGIN_OPCODE: Int = 0x32

    /** `GameFeature_t::ExtendedOpcode` (`src/creatures/creatures_definitions.hpp:681`). */
    const val GAME_FEATURE_EXTENDED_OPCODE: Int = 80

    /** `CLIENTOS_OTCLIENT_LINUX` (`src/creatures/creatures_definitions.hpp:747`). */
    const val CLIENTOS_OTCLIENT_LINUX: Int = 10

    /** `sendFeatures` (protocolgame.cpp:10682-10703). */
    fun writeFeatures(out: ByteBuf, otclientV8: Int) {
        if (otclientV8 == 0) {
            return
        }

        out.writeByte(PACKET_FEATURES)
        out.writeShortLE(1)
        out.writeByte(GAME_FEATURE_EXTENDED_OPCODE)
        out.writeByte(0x01)
    }

    /** `sendOTCRFeatures` (protocolgame.cpp:10706-10725). */
    fun writeOTCRFeatures(out: ByteBuf, enabledFeatures: List<Int>, disabledFeatures: List<Int>) {
        out.writeByte(PACKET_FEATURES)
        out.writeShortLE(enabledFeatures.size + disabledFeatures.size)
        for (feature in enabledFeatures) {
            out.writeByte(feature)
            out.writeByte(0x01)
        }
        for (feature in disabledFeatures) {
            out.writeByte(feature)
            out.writeByte(0x00)
        }
    }

    /** `sendClientLoginPreamble` (protocolgame.cpp:12361-12378). */
    fun writeClientLoginPreamble(
        out: ByteBuf,
        operatingSystem: Int,
        otclientV8: Int,
        enabledFeatures: List<Int>,
        disabledFeatures: List<Int>,
    ) {
        if (otclientV8 > 0) {
            writeFeatures(out, otclientV8)
        }

        if (operatingSystem >= CLIENTOS_OTCLIENT_LINUX) {
            if (otclientV8 == 0) {
                writeOTCRFeatures(out, enabledFeatures, disabledFeatures)
            }

            out.writeByte(PACKET_LOGIN_OPCODE)
            out.writeByte(0x00)
            out.writeShortLE(0x00)
        }
    }
}
