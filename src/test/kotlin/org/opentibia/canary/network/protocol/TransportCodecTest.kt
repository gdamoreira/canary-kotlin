package org.opentibia.canary.network.protocol

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.connection.XTEA_MULTIPLE

/**
 * Transcript of Canary `tests/unit/server/network/protocol/multiprotocol_test.cpp`
 * (`ConnectionTransportTest`) plus the framing boundary cases enforced by
 * `TransportCodec::decodeBodySize`.
 *
 * C++ sources: src/server/network/protocol/transport_codec.hpp/.cpp,
 * src/server/network/protocol/protocol_profile.cpp
 */
class TransportCodecTest {

    @Test
    fun `modern framing decodes block counts plus the checksum length`() {
        assertThat(TransportCodecs.currentModern().decodeBodySize(1))
            .isEqualTo(XTEA_MULTIPLE + CHECKSUM_LENGTH)
    }

    @Test
    fun `raw and legacy framing decode the raw body length`() {
        assertThat(TransportCodecs.rawClientFirst().decodeBodySize(1)).isEqualTo(1)
        assertThat(TransportCodecs.legacyClassic().decodeBodySize(1)).isEqualTo(1)
        assertThat(TransportCodecs.legacyRawWithLoginHeader().decodeBodySize(1)).isEqualTo(1)
    }

    @Test
    fun `server first packet header bytes match canary`() {
        assertThat(TransportCodecs.currentModern().getProfile().serverFirstPacketHeaderBytes)
            .isEqualTo(CHECKSUM_LENGTH + 2)
        assertThat(TransportCodecs.legacyClassic().getProfile().serverFirstPacketHeaderBytes)
            .isEqualTo(CHECKSUM_LENGTH + 1)
        assertThat(TransportCodecs.rawClientFirst().getProfile().serverFirstPacketHeaderBytes).isEqualTo(0)
    }

    @Test
    fun `zero raw length decodes to no body size while modern counts blocks`() {
        assertThat(TransportCodecs.rawClientFirst().decodeBodySize(0)).isNull()
        assertThat(TransportCodecs.currentModern().decodeBodySize(0)).isEqualTo(CHECKSUM_LENGTH)
        assertThat(TransportCodecs.currentModern().decodeBodySize(0xFFFF)).isNull()
        assertThat(TransportCodecs.rawClientFirst().decodeBodySize(0xFFFF)).isEqualTo(0xFFFF)
    }

    @Test
    fun `get resolves every transport profile id`() {
        assertThat(TransportCodecs.get(TransportProfileId.RawClientFirst).getProfile().id)
            .isEqualTo(TransportProfileId.RawClientFirst)
        assertThat(TransportCodecs.get(TransportProfileId.CurrentModern).getProfile().id)
            .isEqualTo(TransportProfileId.CurrentModern)
        assertThat(TransportCodecs.get(TransportProfileId.LegacyRawWithLoginHeader).getProfile().id)
            .isEqualTo(TransportProfileId.LegacyRawWithLoginHeader)
        assertThat(TransportCodecs.get(TransportProfileId.LegacyClassic).getProfile().id)
            .isEqualTo(TransportProfileId.LegacyClassic)
    }
}
