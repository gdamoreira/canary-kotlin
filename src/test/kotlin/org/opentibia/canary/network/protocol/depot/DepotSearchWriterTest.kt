package org.opentibia.canary.network.protocol.depot

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.Item
import org.opentibia.canary.network.protocol.depot.DepotSearchWriter.DepotItemCount

class DepotSearchWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes depot items 0x94 with the count and tier-reduced rows`() {
        val out = Unpooled.buffer()

        DepotSearchWriter.writeDepotItems(
            out,
            entries = listOf(
                DepotItemCount(itemId = 2160, tier = 1, count = 100),
                DepotItemCount(itemId = 3031, tier = 0, count = 5),
            ),
            count = 2,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x94,
                0x02, 0x00, // item count
                0x70, 0x08, 0x00, 0x64, 0x00, // 2160, tier 1 -> 0, 100
                0xD7, 0x0B, 0x05, 0x00, // 3031, no tier byte, 5
            )
        )
    }

    @Test
    fun `writes close depot search 0x9A`() {
        val out = Unpooled.buffer()

        DepotSearchWriter.writeCloseDepotSearch(out)

        assertThat(out.toByteArray()).containsExactly(*bytes(0x9A))
    }

    @Test
    fun `writes depot search result detail 0x99 with depot inbox and stash blocks`() {
        val out = Unpooled.buffer()

        DepotSearchWriter.writeDepotSearchResultDetail(
            out,
            itemId = 2160,
            tier = 2,
            upgradeClassification = 0,
            depotCount = 3,
            depotItems = listOf(Item(2160, count = 5)),
            inboxCount = 0,
            inboxItems = emptyList(),
            stashCount = 7,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x99,
                0x70, 0x08, // item id 2160
                0x03, 0x00, 0x00, 0x00, // depot count
                0x01, // depot item list size
                0x70, 0x08, // AddItem 2160
                0x00, 0x00, 0x00, 0x00, // inbox count
                0x00, // inbox item list size
                0x01, // has stash
                0x70, 0x08, // stash item id
                0x07, 0x00, 0x00, 0x00, // stash count
            )
        )
    }

    @Test
    fun `writes depot search result detail 0x99 with the tier byte for a classified item`() {
        val out = Unpooled.buffer()

        DepotSearchWriter.writeDepotSearchResultDetail(
            out,
            itemId = 2160,
            tier = 2,
            upgradeClassification = 4,
            depotCount = 0,
            depotItems = emptyList(),
            inboxCount = 0,
            inboxItems = emptyList(),
            stashCount = 0,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x99,
                0x70, 0x08, // item id 2160
                0x02, // tier
                0x00, 0x00, 0x00, 0x00,
                0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00,
                0x00, // no stash block
            )
        )
    }
}
