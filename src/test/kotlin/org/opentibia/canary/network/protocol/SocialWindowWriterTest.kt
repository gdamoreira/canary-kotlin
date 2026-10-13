package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType

class SocialWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes bosstiary data 0x61 with the fixed kill and points thresholds`() {
        val out = Unpooled.buffer()

        SocialWindowWriter.writeBosstiaryData(out)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x61,
                0x19, 0x00, 0x64, 0x00, 0x2C, 0x01,
                0x05, 0x00, 0x14, 0x00, 0x3C, 0x00,
                0x01, 0x00, 0x03, 0x00, 0x05, 0x00,
                0x05, 0x00, 0x0F, 0x00, 0x1E, 0x00,
                0x0A, 0x00, 0x1E, 0x00, 0x3C, 0x00,
                0x0A, 0x00, 0x1E, 0x00, 0x3C, 0x00,
            )
        )
    }

    @Test
    fun `writes close container 0x6F and close trade 0x7F`() {
        val out = Unpooled.buffer()

        SocialWindowWriter.writeCloseContainer(out, cid = 3)
        SocialWindowWriter.writeCloseTrade(out)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6F, 0x03,
                0x7F,
            )
        )
    }

    @Test
    fun `writes a plain trade request 0x7D with a single item`() {
        val out = Unpooled.buffer()

        SocialWindowWriter.writeTradeItemRequest(
            out,
            traderName = "Trader",
            item = Item(id = 2160, count = 100, type = ItemType(id = 2160, name = "torch", isStackable = true)),
            ack = true,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x7D,
                0x06, 0x00, 0x54, 0x72, 0x61, 0x64, 0x65, 0x72,
                0x01,
                0x70, 0x08, 0x64,
            )
        )
    }

    @Test
    fun `writes a container trade request 0x7E breadth-first with the container first`() {
        val container = Container(
            id = 1987,
            capacity = 20,
            type = ItemType(id = 1987, name = "Backpack", isContainer = true),
        )
        val nested = Container(
            id = 1988,
            capacity = 10,
            type = ItemType(id = 1988, name = "Bag", isContainer = true),
        )
        nested.addItem(Item(id = 200))
        container.addItem(Item(id = 100))
        container.addItem(nested)

        val out = Unpooled.buffer()

        SocialWindowWriter.writeTradeItemRequest(out, traderName = "Trader", item = container, ack = false)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x7E,
                0x06, 0x00, 0x54, 0x72, 0x61, 0x64, 0x65, 0x72,
                0x04,
                0xC3, 0x07, 0x00,
                0x64, 0x00,
                0xC4, 0x07, 0x00,
                0xC8, 0x00,
            )
        )
    }

    @Test
    fun `writes channels dialog 0xAB with ids and names`() {
        val out = Unpooled.buffer()

        SocialWindowWriter.writeChannelsDialog(
            out,
            listOf(
                SocialWindowWriter.ChannelSummary(id = 1, name = "World"),
                SocialWindowWriter.ChannelSummary(id = 2, name = "Trade"),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xAB, 0x02,
                0x01, 0x00, 0x05, 0x00, 0x57, 0x6F, 0x72, 0x6C, 0x64,
                0x02, 0x00, 0x05, 0x00, 0x54, 0x72, 0x61, 0x64, 0x65,
            )
        )
    }

    @Test
    fun `writes channel 0xAC with user and invited name lists`() {
        val out = Unpooled.buffer()

        SocialWindowWriter.writeChannel(
            out,
            channelId = 7,
            channelName = "Help",
            users = listOf("A", "B"),
            invitedUsers = null,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xAC,
                0x07, 0x00,
                0x04, 0x00, 0x48, 0x65, 0x6C, 0x70,
                0x02, 0x00, 0x01, 0x00, 0x41, 0x01, 0x00, 0x42,
                0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes create private channel 0xB2 and close private 0xB3`() {
        val out = Unpooled.buffer()

        SocialWindowWriter.writeCreatePrivateChannel(out, channelId = 9, channelName = "Priv", playerName = "Bob")
        SocialWindowWriter.writeClosePrivate(out, channelId = 9)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xB2,
                0x09, 0x00,
                0x04, 0x00, 0x50, 0x72, 0x69, 0x76,
                0x01, 0x00,
                0x03, 0x00, 0x42, 0x6F, 0x62,
                0x00, 0x00,
                0xB3, 0x09, 0x00,
            )
        )
    }

    @Test
    fun `writes houses info 0xC6 with the owned house and the house client id list`() {
        val out = Unpooled.buffer()

        SocialWindowWriter.writeHousesInfo(
            out,
            accountHouseCount = 2,
            houseClientId = 123L,
            housesClientIds = listOf(123L, 124L, 125L),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC6,
                0x7B, 0x00, 0x00, 0x00,
                0x00,
                0x02,
                0x00,
                0x03,
                0x03,
                0x01,
                0x01,
                0x7B, 0x00, 0x00, 0x00,
                0x03, 0x00,
                0x7B, 0x00, 0x00, 0x00,
                0x7C, 0x00, 0x00, 0x00,
                0x7D, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes VIP 0xD2 with the icon cap status and group ids`() {
        val out = Unpooled.buffer()

        SocialWindowWriter.writeVip(
            out,
            SocialWindowWriter.VipEntry(
                guid = 1000L,
                name = "Alice",
                description = "friend",
                icon = 12L,
                notify = true,
                status = VipStatus.Online,
            ),
            groups = listOf(2, 5),
        )
        SocialWindowWriter.writeVip(
            out,
            SocialWindowWriter.VipEntry(guid = 1001L, name = "Eve", icon = 3L),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD2,
                0xE8, 0x03, 0x00, 0x00,
                0x05, 0x00, 0x41, 0x6C, 0x69, 0x63, 0x65,
                0x06, 0x00, 0x66, 0x72, 0x69, 0x65, 0x6E, 0x64,
                0x0A, 0x00, 0x00, 0x00,
                0x01,
                0x01,
                0x02, 0x02, 0x05,
                0xD2,
                0xE9, 0x03, 0x00, 0x00,
                0x03, 0x00, 0x45, 0x76, 0x65,
                0x00, 0x00,
                0x03, 0x00, 0x00, 0x00,
                0x00,
                0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes VIP groups 0xD4 with the remaining group slots`() {
        val out = Unpooled.buffer()

        SocialWindowWriter.writeVipGroups(
            out,
            listOf(
                SocialWindowWriter.VipGroup(id = 1, name = "Friends", customizable = true),
                SocialWindowWriter.VipGroup(id = 2, name = "Party"),
            ),
            maxGroupEntries = 8,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD4,
                0x02,
                0x01, 0x07, 0x00, 0x46, 0x72, 0x69, 0x65, 0x6E, 0x64, 0x73, 0x01,
                0x02, 0x05, 0x00, 0x50, 0x61, 0x72, 0x74, 0x79, 0x00,
                0x06,
            )
        )
    }

    @Test
    fun `writes modal window 0xFA with buttons choices defaults and priority`() {
        val out = Unpooled.buffer()

        SocialWindowWriter.writeModalWindow(
            out,
            SocialWindowWriter.ModalWindowFrame(
                id = 7L,
                title = "T",
                message = "M",
                buttons = listOf(
                    SocialWindowWriter.ModalWindowButton(id = 1, label = "Ok"),
                    SocialWindowWriter.ModalWindowButton(id = 2, label = "Cancel"),
                ),
                choices = listOf(SocialWindowWriter.ModalWindowButton(id = 3, label = "A")),
                defaultEscapeButton = 2,
                defaultEnterButton = 1,
                priority = true,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xFA,
                0x07, 0x00, 0x00, 0x00,
                0x01, 0x00, 0x54,
                0x01, 0x00, 0x4D,
                0x02,
                0x02, 0x00, 0x4F, 0x6B, 0x01,
                0x06, 0x00, 0x43, 0x61, 0x6E, 0x63, 0x65, 0x6C, 0x02,
                0x01,
                0x01, 0x00, 0x41, 0x03,
                0x02,
                0x01,
                0x01,
            )
        )
    }

    @Test
    fun `writes modal window 0xFA with no buttons or choices`() {
        val out = Unpooled.buffer()

        SocialWindowWriter.writeModalWindow(
            out,
            SocialWindowWriter.ModalWindowFrame(id = 1L, title = "", message = ""),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xFA,
                0x01, 0x00, 0x00, 0x00,
                0x00, 0x00,
                0x00, 0x00,
                0x00,
                0x00,
                0xFF,
                0xFF,
                0x00,
            )
        )
    }
}
