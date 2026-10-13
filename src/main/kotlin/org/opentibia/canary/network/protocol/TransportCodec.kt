package org.opentibia.canary.network.protocol

import org.opentibia.canary.network.connection.XTEA_MULTIPLE

/**
 * Kotlin port of Canary's `TransportCodec` framing table
 * (src/server/network/protocol/transport_codec.hpp/.cpp).
 *
 * PARITY GAPS: `encodeOutbound`/`prepareInbound` depend on the `Protocol` runtime
 * state (checksum/compression/XTEA) and are owned by the Netty codec pipeline in
 * this project; only the profile-bound size decoding used by
 * `Connection::parseHeader` is ported.
 */
class TransportCodec(
    private val profile: TransportProfile,
) {
    fun getProfile(): TransportProfile = profile

    /** `TransportCodec::decodeBodySize` (transport_codec.cpp:21-32). */
    fun decodeBodySize(rawLengthHeader: Int): Int? {
        var size = rawLengthHeader
        if (profile.outerLength == OuterLengthEncoding.ModernBlockCount) {
            size = (size * XTEA_MULTIPLE) + profile.modernLengthExtraBytes
        }

        if (size == 0 || size > 0xFFFF) {
            return null
        }

        return size
    }
}

/** `TransportCodecs` (transport_codec.hpp:50-55): the registered framing codecs. */
object TransportCodecs {

    private val rawClientFirst = TransportCodec(
        ProtocolProfileRegistry.getTransportProfile(TransportProfileId.RawClientFirst),
    )

    private val currentModern = TransportCodec(
        ProtocolProfileRegistry.getTransportProfile(TransportProfileId.CurrentModern),
    )

    private val legacyRawWithLoginHeader = TransportCodec(
        ProtocolProfileRegistry.getTransportProfile(TransportProfileId.LegacyRawWithLoginHeader),
    )

    private val legacyClassic = TransportCodec(
        ProtocolProfileRegistry.getTransportProfile(TransportProfileId.LegacyClassic),
    )

    fun get(id: TransportProfileId): TransportCodec = when (id) {
        TransportProfileId.RawClientFirst -> rawClientFirst
        TransportProfileId.CurrentModern -> currentModern
        TransportProfileId.LegacyRawWithLoginHeader -> legacyRawWithLoginHeader
        TransportProfileId.LegacyClassic -> legacyClassic
    }

    fun rawClientFirst(): TransportCodec = rawClientFirst

    fun currentModern(): TransportCodec = currentModern

    fun legacyRawWithLoginHeader(): TransportCodec = legacyRawWithLoginHeader

    fun legacyClassic(): TransportCodec = legacyClassic
}
