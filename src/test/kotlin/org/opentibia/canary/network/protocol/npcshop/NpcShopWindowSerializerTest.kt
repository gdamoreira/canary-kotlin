package org.opentibia.canary.network.protocol.npcshop

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.message.readTibiaString
import org.opentibia.canary.network.message.writeTibiaString

class NpcShopWindowSerializerTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun expectedBytes(block: ByteBuf.() -> Unit): ByteArray {
        val buffer = Unpooled.buffer()
        buffer.block()
        return buffer.toByteArray()
    }

    @Test
    fun `serializes a modern shop window 0x7A with currency and item fields`() {
        val out = Unpooled.buffer()

        NpcShopWindowSerializer.writeShop(
            out,
            npcName = "Rashid",
            shopBlocks = listOf(
                ShopBlock(itemId = 2160, itemBuyPrice = 100, itemSellPrice = 200),
            ),
            currency = 3031,
            itemTypeName = { "Crystal Coin" },
            itemTypeWeight = { 1200 },
        )

        val expected = expectedBytes {
            writeByte(0x7A)
            writeTibiaString("Rashid")
            writeShortLE(3031)
            writeTibiaString("")
            writeShortLE(1)
            writeShortLE(2160)
            writeByte(0x00)
            writeTibiaString("Crystal Coin")
            writeIntLE(1200)
            writeIntLE(100)
            writeIntLE(200)
        }

        assertThat(out.toByteArray()).containsExactly(*expected)
    }

    @Test
    fun `serializes an old protocol shop window 0x7A without currency fields`() {
        val out = Unpooled.buffer()

        NpcShopWindowSerializer.writeShop(
            out,
            npcName = "Rashid",
            shopBlocks = listOf(
                ShopBlock(itemId = 2160, itemBuyPrice = 100, itemSellPrice = 200),
            ),
            oldProtocol = true,
            itemTypeName = { "Crystal Coin" },
            itemTypeWeight = { 1200 },
        )

        val expected = expectedBytes {
            writeByte(0x7A)
            writeTibiaString("Rashid")
            writeShortLE(1)
            writeShortLE(2160)
            writeByte(0x00)
            writeTibiaString("Crystal Coin")
            writeIntLE(1200)
            writeIntLE(100)
            writeIntLE(200)
        }

        assertThat(out.toByteArray()).containsExactly(*expected)
    }

    @Test
    fun `serializes a cipsoft 860 shop window with a byte item count`() {
        val out = Unpooled.buffer()

        NpcShopWindowSerializer.writeShop(
            out,
            npcName = "Rashid",
            shopBlocks = listOf(
                ShopBlock(itemId = 2160, itemName = "crystal coin", itemBuyPrice = 100),
            ),
            oldProtocol = true,
            cipsoft860ShopLayout = true,
        )

        assertThat(out.toByteArray()).containsExactly(
            *expectedBytes {
                writeByte(0x7A)
                writeTibiaString("Rashid")
                writeByte(1)
                writeShortLE(2160)
                writeByte(0x00)
                writeTibiaString("crystal coin")
                writeIntLE(0)
                writeIntLE(100)
                writeIntLE(0)
            }
        )
    }

    @Test
    fun `hides a shop item whose storage gate is not met`() {
        val out = Unpooled.buffer()

        NpcShopWindowSerializer.writeShop(
            out,
            npcName = "Rashid",
            shopBlocks = listOf(
                ShopBlock(itemId = 2160, itemBuyPrice = 100, itemStorageKey = 1000, itemStorageValue = 2),
            ),
            oldProtocol = true,
            storageValue = { 1 },
        )

        assertThat(out.toByteArray()).containsExactly(
            *expectedBytes {
                writeByte(0x7A)
                writeTibiaString("Rashid")
                writeShortLE(1)
                writeShortLE(0)
                writeByte(0x00)
                writeTibiaString("")
                writeIntLE(0)
                writeIntLE(0)
                writeIntLE(0)
            }
        )
    }

    @Test
    fun `hides sell only shop items that are not in the inventory when the flag is set`() {
        val hidden = Unpooled.buffer()

        NpcShopWindowSerializer.writeShop(
            hidden,
            npcName = "Rashid",
            shopBlocks = listOf(ShopBlock(itemId = 2160, itemSellPrice = 200)),
            oldProtocol = true,
            hideSellItemsNotInInventory = true,
        )

        assertThat(hidden.toByteArray()).containsExactly(
            *expectedBytes {
                writeByte(0x7A)
                writeTibiaString("Rashid")
                writeShortLE(1)
                writeShortLE(0)
                writeByte(0x00)
                writeTibiaString("")
                writeIntLE(0)
                writeIntLE(0)
                writeIntLE(0)
            }
        )

        val visible = Unpooled.buffer()

        NpcShopWindowSerializer.writeShop(
            visible,
            npcName = "Rashid",
            shopBlocks = listOf(ShopBlock(itemId = 2160, itemSellPrice = 200)),
            oldProtocol = true,
            hideSellItemsNotInInventory = true,
            inventoryMap = mapOf(2160 to 1),
        )

        assertThat(visible.toByteArray()).containsExactly(
            *expectedBytes {
                writeByte(0x7A)
                writeTibiaString("Rashid")
                writeShortLE(1)
                writeShortLE(2160)
                writeByte(0x00)
                writeTibiaString("")
                writeIntLE(0)
                writeIntLE(0)
                writeIntLE(200)
            }
        )
    }

    @Test
    fun `keeps sell only shop items without the inventory when the hidden flag is off`() {
        val out = Unpooled.buffer()

        NpcShopWindowSerializer.writeShop(
            out,
            npcName = "Rashid",
            shopBlocks = listOf(ShopBlock(itemId = 2160, itemSellPrice = 200)),
            oldProtocol = true,
        )

        assertThat(out.toByteArray()).containsExactly(
            *expectedBytes {
                writeByte(0x7A)
                writeTibiaString("Rashid")
                writeShortLE(1)
                writeShortLE(2160)
                writeByte(0x00)
                writeTibiaString("")
                writeIntLE(0)
                writeIntLE(0)
                writeIntLE(200)
            }
        )
    }

    @Test
    fun `writes zero for the hidden uint32 max price sentinels`() {
        val out = Unpooled.buffer()

        NpcShopWindowSerializer.writeShop(
            out,
            npcName = "R",
            shopBlocks = listOf(
                ShopBlock(itemId = 2160, itemBuyPrice = 0xFFFFFFFFL, itemSellPrice = 0xFFFFFFFFL),
            ),
            oldProtocol = true,
        )

        assertThat(out.toByteArray()).containsExactly(
            *expectedBytes {
                writeByte(0x7A)
                writeTibiaString("R")
                writeShortLE(1)
                writeShortLE(2160)
                writeByte(0x00)
                writeTibiaString("")
                writeIntLE(0)
                writeIntLE(0)
                writeIntLE(0)
            }
        )
    }

    @Test
    fun `writes the item subtype byte only for splash and fluid containers`() {
        val splash = Unpooled.buffer()

        NpcShopWindowSerializer.writeShop(
            splash,
            npcName = "R",
            shopBlocks = listOf(ShopBlock(itemId = 2160, itemSubType = 7)),
            oldProtocol = true,
            itemTypeIsSplashOrFluid = { true },
        )

        assertThat(splash.toByteArray()).containsExactly(
            *expectedBytes {
                writeByte(0x7A)
                writeTibiaString("R")
                writeShortLE(1)
                writeShortLE(2160)
                writeByte(7)
                writeTibiaString("")
                writeIntLE(0)
                writeIntLE(0)
                writeIntLE(0)
            }
        )

        val solid = Unpooled.buffer()

        NpcShopWindowSerializer.writeShop(
            solid,
            npcName = "R",
            shopBlocks = listOf(ShopBlock(itemId = 2160, itemSubType = 7)),
            oldProtocol = true,
            itemTypeIsSplashOrFluid = { false },
        )

        assertThat(solid.toByteArray()).containsExactly(
            *expectedBytes {
                writeByte(0x7A)
                writeTibiaString("R")
                writeShortLE(1)
                writeShortLE(2160)
                writeByte(0)
                writeTibiaString("")
                writeIntLE(0)
                writeIntLE(0)
                writeIntLE(0)
            }
        )
    }

    @Test
    fun `caps a cipsoft 860 shop window at 255 item blocks`() {
        val out = Unpooled.buffer()

        NpcShopWindowSerializer.writeShop(
            out,
            npcName = "N",
            shopBlocks = List(256) { ShopBlock(itemId = 2160) },
            oldProtocol = true,
            cipsoft860ShopLayout = true,
        )

        // header: opcode (1) + name (2 + 1) + byte count (1); 255 empty item blocks of 17 bytes
        assertThat(out.writerIndex()).isEqualTo(5 + 255 * 17)
        assertThat(out.getUnsignedByte(4)).isEqualTo(255)
    }

    @Test
    fun `caps a modern shop window at 65535 item blocks`() {
        val out = Unpooled.buffer()

        NpcShopWindowSerializer.writeShop(
            out,
            npcName = "N",
            shopBlocks = List(65536) { ShopBlock(itemId = 2160) },
        )

        // header: opcode (1) + name (2 + 1) + currency (2) + currency name (2) + count (2)
        assertThat(out.writerIndex()).isEqualTo(10 + 65535 * 17)
        assertThat(out.getUnsignedShortLE(8)).isEqualTo(65535)
    }

    @Test
    fun `serializes a modern sale item list 0x7B with only sellable inventory entries`() {
        val out = Unpooled.buffer()

        NpcShopWindowSerializer.writeSaleItemList(
            out,
            shopBlocks = listOf(
                ShopBlock(itemId = 2160, itemSellPrice = 100),
                ShopBlock(itemId = 2152, itemSellPrice = 0),
                ShopBlock(itemId = 2148, itemSellPrice = 50),
            ),
            inventoryMap = mapOf(2160 to 5),
        )

        assertThat(out.toByteArray()).containsExactly(
            *expectedBytes {
                writeByte(0x7B)
                writeShortLE(1)
                writeShortLE(2160)
                writeShortLE(5)
            }
        )
    }

    @Test
    fun `serializes an old protocol sale item list with money and a byte entry count`() {
        val out = Unpooled.buffer()

        NpcShopWindowSerializer.writeSaleItemList(
            out,
            shopBlocks = listOf(ShopBlock(itemId = 2160, itemSellPrice = 100)),
            inventoryMap = mapOf(2160 to 5),
            oldProtocol = true,
            money = 100,
            bankBalance = 250,
        )

        assertThat(out.toByteArray()).containsExactly(
            *expectedBytes {
                writeByte(0x7B)
                writeLongLE(350)
                writeByte(1)
                writeShortLE(2160)
                writeByte(5)
            }
        )
    }

    @Test
    fun `clamps an old protocol sale item count to the byte maximum`() {
        val out = Unpooled.buffer()

        NpcShopWindowSerializer.writeSaleItemList(
            out,
            shopBlocks = listOf(ShopBlock(itemId = 2160, itemSellPrice = 100)),
            inventoryMap = mapOf(2160 to 70000),
            oldProtocol = true,
        )

        assertThat(out.toByteArray()).containsExactly(
            *expectedBytes {
                writeByte(0x7B)
                writeLongLE(0)
                writeByte(1)
                writeShortLE(2160)
                writeByte(0xFF)
            }
        )
    }

    @Test
    fun `caps an old protocol sale item list at 255 entries`() {
        val out = Unpooled.buffer()

        NpcShopWindowSerializer.writeSaleItemList(
            out,
            shopBlocks = List(300) { ShopBlock(itemId = 1000, itemSellPrice = 1) },
            inventoryMap = mapOf(1000 to 1),
            oldProtocol = true,
        )

        // header: opcode (1) + money (8) + byte count (1); 255 entries of 3 bytes (short id + byte count)
        assertThat(out.writerIndex()).isEqualTo(10 + 255 * 3)
        assertThat(out.getUnsignedByte(9)).isEqualTo(255)
    }

    @Test
    fun `serializes the close shop frame 0x7C`() {
        val out = Unpooled.buffer()

        NpcShopWindowSerializer.writeCloseShop(out)

        assertThat(out.toByteArray()).containsExactly(0x7C.toByte())
    }

    @Test
    fun `writes the shop window through an embedded channel`() {
        val channel = EmbeddedChannel()
        try {
            val out = Unpooled.buffer()
            NpcShopWindowSerializer.writeShop(
                out,
                npcName = "Rashid",
                shopBlocks = listOf(
                    ShopBlock(itemId = 2160, itemBuyPrice = 100, itemSellPrice = 200),
                ),
                currency = 3031,
                itemTypeName = { "Crystal Coin" },
                itemTypeWeight = { 1200 },
            )

            assertThat(channel.writeOutbound(out)).isTrue()
            val frame = channel.readOutbound<ByteBuf>()
            assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0x7A)
            assertThat(frame.readTibiaString()).isEqualTo("Rashid")
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
