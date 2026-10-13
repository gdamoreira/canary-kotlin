package org.opentibia.canary.io.appearances

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Wire-format unit tests for [ProtobufWireReader]. Payloads are encoded by hand following
 * the protobuf encoding spec used by `src/protobuf/appearances.proto`.
 */
class ProtobufWireReaderTest {

    @Test
    fun `reads single and multi byte varints`() {
        val reader = ProtobufWireReader(byteArrayOf(0x00, 0x01, 0xAC.toByte(), 0x02, 0x7F))
        assertThat(reader.readVarint()).isEqualTo(0L)
        assertThat(reader.readVarint()).isEqualTo(1L)
        assertThat(reader.readVarint()).isEqualTo(300L)
        assertThat(reader.readVarint()).isEqualTo(127L)
        assertThat(reader.hasRemaining()).isFalse()
    }

    @Test
    fun `decodes negative enum values encoded as ten byte varints`() {
        val minusOne = byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x01
        )
        assertThat(ProtobufWireReader(minusOne).readVarint().toInt()).isEqualTo(-1)
    }

    @Test
    fun `reads length delimited payloads as sub readers`() {
        val bytes = byteArrayOf(0x03, 0x01, 0x02, 0x03, 0x02, 0x04, 0x05)
        val reader = ProtobufWireReader(bytes)

        val sub = reader.readLengthDelimitedReader()
        assertThat(sub.readVarint()).isEqualTo(1L)
        assertThat(sub.readVarint()).isEqualTo(2L)
        assertThat(sub.readVarint()).isEqualTo(3L)
        assertThat(sub.hasRemaining()).isFalse()

        val packed = reader.readLengthDelimitedReader()
        assertThat(packed.readVarint()).isEqualTo(4L)
        assertThat(packed.readVarint()).isEqualTo(5L)
    }

    @Test
    fun `reads length prefixed strings`() {
        val bytes = byteArrayOf(0x03, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())
        assertThat(ProtobufWireReader(bytes).readString()).isEqualTo("abc")
    }

    @Test
    fun `reads little endian fixed width values`() {
        val reader = ProtobufWireReader(
            byteArrayOf(0x78, 0x56, 0x34, 0x12, 0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01)
        )
        assertThat(reader.readFixed32()).isEqualTo(0x12345678)
        assertThat(reader.readFixed64()).isEqualTo(0x0102030405060708L)
    }

    @Test
    fun `decodes field tags into number and wire type`() {
        val reader = ProtobufWireReader(byteArrayOf(0x0A, 0x08))
        val tag = reader.readTag()
        assertThat(tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT).isEqualTo(1)
        assertThat(tag and ProtobufWireReader.WIRE_TYPE_MASK).isEqualTo(ProtobufWireReader.WIRE_TYPE_LENGTH_DELIMITED)
    }

    @Test
    fun `rejects truncated input`() {
        assertThatThrownBy { ProtobufWireReader(byteArrayOf(0x80.toByte())).readVarint() }
            .isInstanceOf(WireFormatException::class.java)

        assertThatThrownBy { ProtobufWireReader(byteArrayOf(0x05, 0x01)).readBytes() }
            .isInstanceOf(WireFormatException::class.java)

        assertThatThrownBy { ProtobufWireReader(byteArrayOf(0x01, 0x02)).readFixed32() }
            .isInstanceOf(WireFormatException::class.java)
    }

    @Test
    fun `rejects unsupported group wire types`() {
        assertThatThrownBy { ProtobufWireReader(byteArrayOf(0x0B)).skip(3) }
            .isInstanceOf(WireFormatException::class.java)
    }
}
