package org.opentibia.canary.kv

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.protobuf.kv.ValueWrapper as ProtoValueWrapper

/**
 * Transcription tests for `ProtoSerializable` / `ProtoHelpers`
 * (`src/kv/value_wrapper_proto.cpp`): the typed KV [ValueWrapper] to
 * `Canary.protobuf.kv` conversion and back.
 *
 * The golden byte strings match `protoc --encode=Canary.protobuf.kv.ValueWrapper`
 * (libprotoc 33.4) for the same values.
 */
class KvValueWrapperProtoTest {

    private fun bytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun parse(proto: ByteArray): ProtoValueWrapper {
        val message = ProtoValueWrapper()
        assertThat(message.parseFromArray(proto)).isTrue()
        return message
    }

    @Test
    fun `toProto encodes a string as field one`() {
        assertThat(ProtoSerializable.toProto(ValueWrapper("hello")).serializeToByteArray())
            .isEqualTo(bytes("0a0568656c6c6f"))
    }

    @Test
    fun `toProto encodes an int as field two`() {
        assertThat(ProtoSerializable.toProto(ValueWrapper(42)).serializeToByteArray())
            .isEqualTo(bytes("102a"))
        assertThat(ProtoSerializable.toProto(ValueWrapper(-1)).serializeToByteArray())
            .isEqualTo(bytes("10ffffffffffffffffff01"))
    }

    @Test
    fun `toProto encodes a double as field three`() {
        assertThat(ProtoSerializable.toProto(ValueWrapper(3.14)).serializeToByteArray())
            .isEqualTo(bytes("191f85eb51b81e0940"))
    }

    @Test
    fun `toProto encodes a boolean as field six`() {
        assertThat(ProtoSerializable.toProto(ValueWrapper(true)).serializeToByteArray())
            .isEqualTo(bytes("3001"))
    }

    @Test
    fun `toProto encodes an array as field four`() {
        val value = ValueWrapper(
            listOf(ValueWrapper(1), ValueWrapper("two"), ValueWrapper(true)),
        )

        assertThat(ProtoSerializable.toProto(value).serializeToByteArray())
            .isEqualTo(bytes("220f0a0210010a050a0374776f0a023001"))
    }

    @Test
    fun `toProto encodes a map as field five in iteration order`() {
        val value = ValueWrapper(
            mapOf(
                "answer" to ValueWrapper(42),
                "nested" to ValueWrapper(mapOf("flag" to ValueWrapper(true))),
            ),
        )

        assertThat(ProtoSerializable.toProto(value).serializeToByteArray()).isEqualTo(
            bytes("2a280a0c0a06616e737765721202102a0a180a066e6573746564120e2a0c0a0a0a04666c616712023001"),
        )
    }

    @Test
    fun `toProto of a deleted wrapper encodes the default empty string member`() {
        assertThat(ProtoSerializable.toProto(ValueWrapper.deleted()).serializeToByteArray())
            .isEqualTo(bytes("0a00"))
    }

    @Test
    fun `fromProto preserves the timestamp on the value and every nested entry`() {
        val proto = parse(ProtoSerializable.toProto(ValueWrapper(listOf(ValueWrapper(1)))).serializeToByteArray())

        val value = ProtoSerializable.fromProto(proto, 1234L)

        assertThat(value.timestamp).isEqualTo(1234L)
        assertThat(value.getArray().single().timestamp).isEqualTo(1234L)
    }

    @Test
    fun `fromProto maps an unset oneof to the empty string like the C++ default variant`() {
        val value = ProtoSerializable.fromProto(ProtoValueWrapper(), 99L)

        assertThat(value.getVariant()).isEqualTo(ValueVariant.StringValue(""))
        assertThat(value.getString()).isEmpty()
        assertThat(value.timestamp).isEqualTo(99L)
    }

    @Test
    fun `every variant round-trips through protobuf bytes`() {
        val values = listOf(
            ValueWrapper("hello"),
            ValueWrapper(""),
            ValueWrapper(true),
            ValueWrapper(false),
            ValueWrapper(42),
            ValueWrapper(-42),
            ValueWrapper(3.5),
            ValueWrapper(listOf(ValueWrapper(1), ValueWrapper("two"), ValueWrapper(false))),
            ValueWrapper(mapOf("answer" to ValueWrapper(42), "nested" to ValueWrapper(mapOf("flag" to ValueWrapper(true))))),
            ValueWrapper(emptyList()),
            ValueWrapper(emptyMap()),
        )

        for (value in values) {
            val bytes = ProtoSerializable.toProto(value).serializeToByteArray()
            val restored = ProtoSerializable.fromProto(parse(bytes), value.timestamp)
            assertThat(restored.getVariant()).isEqualTo(value.getVariant())
        }
    }

    @Test
    fun `unicode strings round-trip`() {
        val value = ValueWrapper("coração-日本語-🎮")

        val restored = ProtoSerializable.fromProto(
            parse(ProtoSerializable.toProto(value).serializeToByteArray()),
            7L,
        )

        assertThat(restored.getString()).isEqualTo("coração-日本語-🎮")
    }
}
