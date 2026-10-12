package org.opentibia.canary.network.message

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NetworkMessageTranscriptTest {

    @Test
    fun `addByte and getByte matches Canary NetworkMessageTest`() {
        val buf = Unpooled.buffer()
        val byteToAdd: Int = 100
        buf.writeByte(byteToAdd)

        val retrieved = buf.readUnsignedByte().toInt()
        assertThat(retrieved).isEqualTo(byteToAdd)
    }

    @Test
    fun `addString and getString matches Canary NetworkMessageTest`() {
        val buf = Unpooled.buffer()
        val testStr = "TestString"
        buf.writeTibiaString(testStr)

        val retrievedStr = buf.readTibiaString()
        assertThat(retrievedStr).isEqualTo(testStr)
    }

    @Test
    fun `addString handles empty string correctly`() {
        val buf = Unpooled.buffer()
        buf.writeTibiaString("")

        val retrievedStr = buf.readTibiaString()
        assertThat(retrievedStr).isEqualTo("")
    }

    @Test
    fun `reads and writes primitive Little-Endian types matching network message specifications`() {
        val buf = Unpooled.buffer()
        buf.writeShortLE(12345)
        buf.writeIntLE(0x12345678)
        buf.writeLongLE(0x0102030405060708L)

        assertThat(buf.readUnsignedShortLE()).isEqualTo(12345)
        assertThat(buf.readIntLE()).isEqualTo(0x12345678)
        assertThat(buf.readLongLE()).isEqualTo(0x0102030405060708L)
    }
}
