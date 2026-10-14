package org.opentibia.canary.network.protocol.loot

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ObjectCategory_t

class LootWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes loot containers 0xC0 with the fallback flag and category rows`() {
        val out = Unpooled.buffer()

        LootWindowWriter.writeLootContainers(
            out,
            fallbackToMainContainer = true,
            entries = listOf(
                LootWindowWriter.LootContainerEntry(ObjectCategory_t.OBJECTCATEGORY_NONE, lootContainerId = 300, obtainContainerId = 400),
                LootWindowWriter.LootContainerEntry(ObjectCategory_t.OBJECTCATEGORY_ARMORS, lootContainerId = 100, obtainContainerId = 200),
                LootWindowWriter.LootContainerEntry(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, lootContainerId = 0, obtainContainerId = 0),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC0,
                0x01, // fallback
                0x03, // category count
                0x00, 0x2C, 0x01, 0x90, 0x01,
                0x01, 0x64, 0x00, 0xC8, 0x00,
                0x1F, 0x00, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes loot stats 0xCF with the cloned count and item name`() {
        val out = Unpooled.buffer()
        val goldType = ItemType(id = 2160, name = "gold coin", isStackable = true)
        val item = Item(2160, count = 10, type = goldType)

        LootWindowWriter.writeLootStats(out, item = item, count = 7)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xCF,
                0x70, 0x08, // item id 2160
                0x07, // cloned count
                0x09, 0x00, 0x67, 0x6F, 0x6C, 0x64, 0x20, 0x63, 0x6F, 0x69, 0x6E,
            )
        )
        assertThat(item.count).isEqualTo(10)
    }

    @Test
    fun `writes kill tracker update 0xD1 with the corpse outfit and items`() {
        val out = Unpooled.buffer()
        val corpse = Container(id = 3058, capacity = 8)
        corpse.addItem(Item(2148))
        corpse.addItem(Item(2152))

        LootWindowWriter.writeKillTrackerUpdate(
            out,
            name = "Demon",
            creatureOutfit = Outfit(
                lookType = 35,
                lookHead = 1,
                lookBody = 2,
                lookLegs = 3,
                lookFeet = 4,
                lookAddons = 5,
            ),
            corpse = corpse,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD1,
                0x05, 0x00, 0x44, 0x65, 0x6D, 0x6F, 0x6E,
                0x23, 0x00, // look type 35
                0x01, 0x02, 0x03, 0x04, 0x05,
                0x02, // corpse size
                0x64, 0x08, // 2148
                0x68, 0x08, // 2152
            )
        )
    }

    @Test
    fun `writes kill tracker update 0xD1 with the fallback outfit for an empty corpse`() {
        val out = Unpooled.buffer()

        LootWindowWriter.writeKillTrackerUpdate(
            out,
            name = "Rat",
            creatureOutfit = Outfit(),
            corpse = Container(id = 3058, capacity = 8),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD1,
                0x03, 0x00, 0x52, 0x61, 0x74,
                0x15, 0x00, // look type 21
                0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, // empty corpse
            )
        )
    }
}
