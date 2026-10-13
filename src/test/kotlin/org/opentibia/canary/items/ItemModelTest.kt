package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.systems.rewardchest.ReturnValue

class ItemModelTest {

    @Test
    fun `registers item type and instantiates items with attributes`() {
        val registry = ItemTypeRegistry()
        val swordType = ItemType(
            id = 2160,
            name = "Crystal Coin",
            isStackable = true,
            weight = 0.1
        )
        registry.register(swordType)

        assertThat(registry.get(2160)).isNotNull
        assertThat(registry.get(2160)?.isStackable).isTrue()

        val item = Item.create(2160, count = 50, registry = registry)
        assertThat(item.id).isEqualTo(2160)
        assertThat(item.count).isEqualTo(50)
        assertThat(item.weight).isEqualTo(5.0) // 50 * 0.1
    }

    @Test
    fun `item classification mirrors the item type upgrade classification`() {
        val registry = ItemTypeRegistry()
        registry.register(ItemType(id = 2400, name = "Magic Sword", upgradeClassification = 4))

        assertThat(Item.create(2400, registry = registry).getClassification()).isEqualTo(4)
        assertThat(Item.create(9999, registry = registry).getClassification()).isEqualTo(0)
    }

    @Test
    fun `container enforces its capacity through queryAdd and appends items`() {
        val registry = ItemTypeRegistry()
        val backpackType = ItemType(
            id = 1988,
            name = "Backpack",
            isContainer = true,
            capacity = 2,
            weight = 18.0
        )
        val coinType = ItemType(
            id = 2148,
            name = "Gold Coin",
            isStackable = true,
            weight = 0.1
        )
        registry.register(backpackType)
        registry.register(coinType)

        val backpack = Container.create(1988, registry = registry)
        assertThat(backpack.items).isEmpty()

        val item1 = Item.create(2148, count = 100, registry = registry)
        val item2 = Item.create(2148, count = 50, registry = registry)
        val item3 = Item.create(2148, count = 1, registry = registry)

        assertThat(backpack.queryAdd(INDEX_WHEREEVER, item1, 100, 0)).isEqualTo(ReturnValue.NOERROR)
        backpack.addItem(item1)
        assertThat(backpack.queryAdd(INDEX_WHEREEVER, item2, 50, 0)).isEqualTo(ReturnValue.NOERROR)
        backpack.addItem(item2)
        assertThat(backpack.queryAdd(INDEX_WHEREEVER, item3, 1, 0)).isEqualTo(ReturnValue.CONTAINERNOTENOUGHROOM)

        assertThat(backpack.items).hasSize(2)
        // Weight = container weight (18.0) + (100 * 0.1) + (50 * 0.1) = 33.0
        assertThat(backpack.weight).isEqualTo(33.0)
    }
}
