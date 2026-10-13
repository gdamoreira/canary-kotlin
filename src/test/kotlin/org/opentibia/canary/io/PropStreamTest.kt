package org.opentibia.canary.io

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for `PropStream` / `PropWriteStream`
 * (src/io/fileloader.hpp:57-163): little-endian native words and
 * uint16-length-prefixed raw string bytes.
 */
class PropStreamTest {

    @Test
    fun `writes little-endian words and reads them back`() {
        val stream = PropWriteStream()
        stream.writeU8(0x12)
        stream.writeU16(0x3456)
        stream.writeU32(0x789ABCDEL)
        stream.writeU64(0x0102030405060708uL.toLong())
        stream.writeI32(-2)
        stream.writeI64(-3)

        assertThat(stream.getStream()).containsExactly(
            0x12,
            0x56, 0x34,
            0xDE.toByte(), 0xBC.toByte(), 0x9A.toByte(), 0x78,
            0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01,
            0xFE.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFD.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
        )

        val reader = PropStream(stream.getStream())
        assertThat(reader.readU8()).isEqualTo(0x12)
        assertThat(reader.readU16()).isEqualTo(0x3456)
        assertThat(reader.readU32()).isEqualTo(0x789ABCDEL)
        assertThat(reader.readU64()).isEqualTo(0x0102030405060708L)
        assertThat(reader.readI32()).isEqualTo(-2)
        assertThat(reader.readI64()).isEqualTo(-3L)
        assertThat(reader.hasRemaining()).isFalse()
    }

    @Test
    fun `writes uint16-length-prefixed UTF-8 strings`() {
        val stream = PropWriteStream()
        stream.writeString("Hi")

        assertThat(stream.getStream()).containsExactly(2, 0, 'H'.code.toByte(), 'i'.code.toByte())

        val reader = PropStream(stream.getStream())
        assertThat(reader.readString()).isEqualTo("Hi")
    }

    @Test
    fun `writes a zero length prefix for overlong strings`() {
        val stream = PropWriteStream()
        stream.writeString("x".repeat(0x10000))

        assertThat(stream.getStream()).containsExactly(0, 0)
    }

    @Test
    fun `truncated reads fail without consuming the stream`() {
        val reader = PropStream(byteArrayOf(1, 2))

        assertThat(reader.readU32()).isNull()
        assertThat(reader.readU16()).isEqualTo(0x0201)
        assertThat(reader.readU8()).isNull()
        assertThat(reader.hasRemaining()).isFalse()
    }

    @Test
    fun `truncated strings fail after consuming the length prefix`() {
        val reader = PropStream(byteArrayOf(5, 0, 'a'.code.toByte()))

        // PropStream::readString consumes the uint16 length before it discovers
        // the payload is short, exactly like the C++ cursor.
        assertThat(reader.readString()).isNull()
        assertThat(reader.readU16()).isNull()
    }

    @Test
    fun `skip advances and clear resets the writer`() {
        val reader = PropStream(byteArrayOf(1, 2, 3, 4))
        assertThat(reader.skip(2)).isTrue()
        assertThat(reader.readU8()).isEqualTo(3)
        assertThat(reader.skip(9)).isFalse()

        val writer = PropWriteStream()
        writer.writeU8(1)
        writer.clear()
        assertThat(writer.getStream()).isEmpty()
    }
}
