package org.opentibia.canary.io.otb

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.io.otbm.OtbmConstants

class OtbLoaderTest {

    @Test
    fun `parses binary OTB item nodes and registers item types`() {
        val buf = Unpooled.buffer()
        buf.writeInt(0) // format identifier

        // Root Node (0xFE)
        buf.writeByte(OtbmConstants.NODE_START.toInt())
        buf.writeByte(0) // ROOT
        buf.writeIntLE(1) // version

        // ITEM Node: Server ID 2160, Client ID 3043 (Crystal Coin), Stackable flag
        buf.writeByte(OtbmConstants.NODE_START.toInt())
        buf.writeByte(1) // ITEM
        buf.writeIntLE(0x00000008) // Flag: STACKABLE (1 << 3)

        // Attribute: Server ID (0x10)
        buf.writeByte(0x10) // ATTR_SERVER_ID
        buf.writeShortLE(2) // length
        buf.writeShortLE(2160) // Server Item ID

        // Attribute: Client ID (0x11)
        buf.writeByte(0x11) // ATTR_CLIENT_ID
        buf.writeShortLE(2)
        buf.writeShortLE(3043) // Client Sprite ID

        buf.writeByte(OtbmConstants.NODE_END.toInt()) // Close ITEM
        buf.writeByte(OtbmConstants.NODE_END.toInt()) // Close ROOT

        val registry = OtbItemsLoader.load(buf)
        val crystalCoin = registry.get(2160)

        assertThat(crystalCoin).isNotNull
        assertThat(crystalCoin?.id).isEqualTo(2160)
        assertThat(crystalCoin?.isStackable).isTrue()
    }
}
