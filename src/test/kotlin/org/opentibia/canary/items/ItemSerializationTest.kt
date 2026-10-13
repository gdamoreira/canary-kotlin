package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream

/**
 * Transcript tests for `Item::serializeAttr` / `Item::unserializeAttr` /
 * `Item::getSubType` / `Item::isSavedToHouses`
 * (src/items/item.cpp:867-910, 912-1539, 2332-2335),
 * `Container::readAttr` (src/items/containers/container.cpp:171-181) and
 * `BedItem::serializeAttr` (src/items/bed.cpp:69-81). Wire codes come from
 * `AttrTypes_t` (src/items/items_definitions.hpp:202-252).
 */
class ItemSerializationTest {

    private fun serialize(item: Item): ByteArray {
        val stream = PropWriteStream()
        item.serializeAttr(stream)
        return stream.getStream()
    }

    @Test
    fun `stackable subtype writes ATTR_COUNT with the item count`() {
        val type = ItemType(id = 2148, name = "Gold Coin", isStackable = true)
        val item = Item(id = 2148, count = 100, type = type)

        assertThat(item.getSubType()).isEqualTo(100)
        assertThat(serialize(item)).containsExactly(15, 100)
    }

    @Test
    fun `action id is only serialized for movable item types`() {
        val movable = ItemType(id = 2160, name = "Crystal Coin", isMovable = true)
        val fixed = ItemType(id = 1210, name = "Door", isMovable = false)

        val movableItem = Item(id = 2160, type = movable).apply { setActionId(1234) }
        val fixedItem = Item(id = 1210, type = fixed).apply { setActionId(1234) }

        assertThat(serialize(movableItem)).containsExactly(4, 0xD2.toByte(), 0x04)
        assertThat(serialize(fixedItem)).isEmpty()
    }

    @Test
    fun `attributes are serialized in Canary order with their exact wire codes`() {
        val type = ItemType(id = 2400, name = "Sword", isMovable = true)
        val item = Item(id = 2400, type = type).apply {
            setAttribute(ItemAttribute.STORE, 1L)
            setAttribute(ItemAttribute.TEXT, "Hi")
            setAttribute(ItemAttribute.DATE, 0x0102030405060708L)
            setAttribute(ItemAttribute.DURATION, 0L)
            setDecaying(ItemDecayState.DECAYING_TRUE)
            setAttribute(ItemAttribute.WEIGHT, 100L)
            setAttribute(ItemAttribute.TIER, 3L)
            setAttribute(ItemAttribute.AMOUNT, 7L)
        }

        assertThat(serialize(item)).containsExactly(
            1, 1, 0, 0, 0, 0, 0, 0, 0,
            6, 2, 0, 'H'.code.toByte(), 'i'.code.toByte(),
            18, 8, 7, 6, 5, 4, 3, 2, 1,
            16, 0, 0, 0, 0,
            17, 1,
            27, 100, 0, 0, 0,
            40, 3,
            39, 7, 0,
        )
    }

    @Test
    fun `serialized attributes round-trip through unserializeAttr`() {
        val type = ItemType(id = 2400, name = "Sword", isMovable = true)
        val item = Item(id = 2400, type = type).apply {
            setActionId(77)
            setAttribute(ItemAttribute.TEXT, "Hello")
            setAttribute(ItemAttribute.WRITER, "Bob")
            setAttribute(ItemAttribute.DESCRIPTION, "A fine sword")
            setAttribute(ItemAttribute.DURATION, 5L)
            setAttribute(ItemAttribute.WEIGHT, 100L)
        }

        val restored = Item(id = 2400, type = type)
        assertThat(restored.unserializeAttr(PropStream(serialize(item)))).isTrue()

        assertThat(restored.getActionId()).isEqualTo(77)
        assertThat(restored.getAttribute(ItemAttribute.TEXT)).isEqualTo("Hello")
        assertThat(restored.getAttribute(ItemAttribute.WRITER)).isEqualTo("Bob")
        assertThat(restored.getAttribute(ItemAttribute.DESCRIPTION)).isEqualTo("A fine sword")
        assertThat(restored.getAttribute(ItemAttribute.DURATION)).isEqualTo(5L)
        assertThat(restored.getAttribute(ItemAttribute.WEIGHT)).isEqualTo(100L)
    }

    @Test
    fun `container treats ATTR_CONTAINER_ITEMS as the attribute list terminator`() {
        val type = ItemType(id = 1987, name = "Bag", isContainer = true, capacity = 8, isMovable = true)
        val container = Container(id = 1987, capacity = 8, type = type)
        val stream = PropWriteStream()
        stream.writeU8(23)
        stream.writeU32(4)
        stream.writeU8(0xAB)

        assertThat(container.unserializeAttr(PropStream(stream.getStream()))).isTrue()
        assertThat(container.serializationCount).isEqualTo(4)
    }

    @Test
    fun `bed item serializes the sleeper guid and sleep start`() {
        val type = ItemType(id = 1750, name = "Bed", isBed = true)
        val bed = BedItem(id = 1750, type = type).apply {
            sleeperGUID = 42
            sleepStart = 99
        }

        assertThat(serialize(bed)).containsExactly(
            20, 42, 0, 0, 0,
            21, 99, 0, 0, 0,
        )

        bed.forceClearSleeper()
        assertThat(bed.sleeperGUID).isEqualTo(0L)
        assertThat(bed.sleepStart).isEqualTo(0L)
        assertThat(bed.getAttribute(ItemAttribute.DESCRIPTION)).isEqualTo("Nobody is sleeping there.")
    }

    @Test
    fun `tier above the configured maximum serializes as zero`() {
        val type = ItemType(id = 2400, name = "Sword", isMovable = true, upgradeClassification = 3)
        val item = Item(id = 2400, type = type).apply {
            setAttribute(ItemAttribute.TIER, 99L)
        }

        assertThat(item.getTier()).isZero()
        assertThat(serialize(item)).containsExactly(40, 0)
    }

    @Test
    fun `isSavedToHouses follows the Canary item-type predicate`() {
        val movable = Item(id = 1, type = ItemType(1, "movable", isMovable = true))
        val fixed = Item(id = 2, type = ItemType(2, "fixed"))
        val carpet = Item(id = 3, type = ItemType(3, "carpet", isCarpet = true))
        val transformable = Item(id = 4, type = ItemType(4, "transformable", transformOnUse = 5))
        val emptyContainer = Container(5, capacity = 8, type = ItemType(5, "bag", isContainer = true))
        val filledContainer = Container(5, capacity = 8, type = ItemType(5, "bag", isContainer = true)).apply {
            addItem(Item(6))
        }

        assertThat(movable.isSavedToHouses()).isTrue()
        assertThat(fixed.isSavedToHouses()).isFalse()
        assertThat(carpet.isSavedToHouses()).isTrue()
        assertThat(transformable.isSavedToHouses()).isTrue()
        assertThat(emptyContainer.isSavedToHouses()).isFalse()
        assertThat(filledContainer.isSavedToHouses()).isTrue()
    }
}
