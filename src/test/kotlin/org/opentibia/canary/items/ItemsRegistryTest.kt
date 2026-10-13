package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.opentibia.canary.creatures.WeaponType_t
import org.junit.jupiter.api.Test

/**
 * Transcript tests for the registry surface of `src/items/items.cpp`:
 * `Items::getLootType` (items.cpp:65-71) over the `lootTypeNames` table
 * (items.cpp:37-63), `protoItemCategoryToCpp` (items.cpp:142-151),
 * `Items::getItemIdByName` (items.cpp:412-420), `Items::hasItemType`
 * (items.cpp:422-427), `Items::getItemType` (items.cpp:398-410),
 * `Items::buildInventoryList` (items.cpp:319-328), `Items::addLadderId`/`addDummyId`
 * (items.hpp:442-447) and `Items::clear` (items.cpp:26-33).
 */
class ItemsRegistryTest {

    @Test
    fun `loot type table mirrors the Canary map`() {
        val expected = mapOf(
            "armor" to ItemTypes_t.ITEM_TYPE_ARMOR,
            "amulet" to ItemTypes_t.ITEM_TYPE_AMULET,
            "boots" to ItemTypes_t.ITEM_TYPE_BOOTS,
            "container" to ItemTypes_t.ITEM_TYPE_CONTAINER,
            "decoration" to ItemTypes_t.ITEM_TYPE_DECORATION,
            "food" to ItemTypes_t.ITEM_TYPE_FOOD,
            "helmet" to ItemTypes_t.ITEM_TYPE_HELMET,
            "legs" to ItemTypes_t.ITEM_TYPE_LEGS,
            "other" to ItemTypes_t.ITEM_TYPE_OTHER,
            "potion" to ItemTypes_t.ITEM_TYPE_POTION,
            "ring" to ItemTypes_t.ITEM_TYPE_RING,
            "rune" to ItemTypes_t.ITEM_TYPE_RUNE,
            "shield" to ItemTypes_t.ITEM_TYPE_SHIELD,
            "tools" to ItemTypes_t.ITEM_TYPE_TOOLS,
            "valuable" to ItemTypes_t.ITEM_TYPE_VALUABLE,
            "ammo" to ItemTypes_t.ITEM_TYPE_AMMO,
            "axe" to ItemTypes_t.ITEM_TYPE_AXE,
            "club" to ItemTypes_t.ITEM_TYPE_CLUB,
            "distance" to ItemTypes_t.ITEM_TYPE_DISTANCE,
            "sword" to ItemTypes_t.ITEM_TYPE_SWORD,
            "wand" to ItemTypes_t.ITEM_TYPE_WAND,
            "creatureproduct" to ItemTypes_t.ITEM_TYPE_CREATUREPRODUCT,
            "retrieve" to ItemTypes_t.ITEM_TYPE_RETRIEVE,
            "gold" to ItemTypes_t.ITEM_TYPE_GOLD,
            "unassigned" to ItemTypes_t.ITEM_TYPE_UNASSIGNED,
        )

        for ((name, type) in expected) {
            assertThat(Items.getLootType(name)).isEqualTo(type)
        }

        assertThat(Items.getLootType("ARMOR")).isEqualTo(ItemTypes_t.ITEM_TYPE_NONE)
        assertThat(Items.getLootType("unknown")).isEqualTo(ItemTypes_t.ITEM_TYPE_NONE)
        assertThat(Items.getLootType("")).isEqualTo(ItemTypes_t.ITEM_TYPE_NONE)
    }

    @Test
    fun `item type enum keeps the Canary ordered values`() {
        assertThat(ItemTypes_t.ITEM_TYPE_NONE.value).isEqualTo(0)
        assertThat(ItemTypes_t.ITEM_TYPE_ARMOR.value).isEqualTo(1)
        assertThat(ItemTypes_t.ITEM_TYPE_FIST.value).isEqualTo(27)
        assertThat(ItemTypes_t.ITEM_TYPE_DEPOT.value).isEqualTo(28)
        assertThat(ItemTypes_t.ITEM_TYPE_MAILBOX.value).isEqualTo(29)
        assertThat(ItemTypes_t.ITEM_TYPE_TRASHHOLDER.value).isEqualTo(30)
        assertThat(ItemTypes_t.ITEM_TYPE_DOOR.value).isEqualTo(31)
        assertThat(ItemTypes_t.ITEM_TYPE_MAGICFIELD.value).isEqualTo(32)
        assertThat(ItemTypes_t.ITEM_TYPE_TELEPORT.value).isEqualTo(33)
        assertThat(ItemTypes_t.ITEM_TYPE_BED.value).isEqualTo(34)
        assertThat(ItemTypes_t.ITEM_TYPE_KEY.value).isEqualTo(35)
        assertThat(ItemTypes_t.ITEM_TYPE_SUPPLY.value).isEqualTo(36)
        assertThat(ItemTypes_t.ITEM_TYPE_REWARDCHEST.value).isEqualTo(37)
        assertThat(ItemTypes_t.ITEM_TYPE_CARPET.value).isEqualTo(38)
        assertThat(ItemTypes_t.ITEM_TYPE_RETRIEVE.value).isEqualTo(39)
        assertThat(ItemTypes_t.ITEM_TYPE_GOLD.value).isEqualTo(40)
        assertThat(ItemTypes_t.ITEM_TYPE_UNASSIGNED.value).isEqualTo(41)
        assertThat(ItemTypes_t.ITEM_TYPE_LADDER.value).isEqualTo(42)
        assertThat(ItemTypes_t.ITEM_TYPE_DUMMY.value).isEqualTo(43)
        assertThat(ItemTypes_t.ITEM_TYPE_LAST.value).isEqualTo(44)
    }

    @Test
    fun `item group enum keeps the Canary values`() {
        assertThat(ItemGroup_t.ITEM_GROUP_NONE.value).isEqualTo(0)
        assertThat(ItemGroup_t.ITEM_GROUP_GROUND.value).isEqualTo(1)
        assertThat(ItemGroup_t.ITEM_GROUP_CONTAINER.value).isEqualTo(2)
        assertThat(ItemGroup_t.ITEM_GROUP_SPLASH.value).isEqualTo(3)
        assertThat(ItemGroup_t.ITEM_GROUP_FLUID.value).isEqualTo(4)
        assertThat(ItemGroup_t.ITEM_GROUP_LAST.value).isEqualTo(5)
    }

    @Test
    fun `proto item category maps 1 to 27 directly and everything else to other`() {
        assertThat(Items.protoItemCategoryToCpp(1)).isEqualTo(ItemTypes_t.ITEM_TYPE_ARMOR)
        assertThat(Items.protoItemCategoryToCpp(4)).isEqualTo(ItemTypes_t.ITEM_TYPE_CONTAINER)
        assertThat(Items.protoItemCategoryToCpp(21)).isEqualTo(ItemTypes_t.ITEM_TYPE_WAND)
        assertThat(Items.protoItemCategoryToCpp(27)).isEqualTo(ItemTypes_t.ITEM_TYPE_FIST)
        assertThat(Items.protoItemCategoryToCpp(0)).isEqualTo(ItemTypes_t.ITEM_TYPE_OTHER)
        assertThat(Items.protoItemCategoryToCpp(28)).isEqualTo(ItemTypes_t.ITEM_TYPE_OTHER)
        assertThat(Items.protoItemCategoryToCpp(29)).isEqualTo(ItemTypes_t.ITEM_TYPE_OTHER)
        assertThat(Items.protoItemCategoryToCpp(30)).isEqualTo(ItemTypes_t.ITEM_TYPE_OTHER)
        assertThat(Items.protoItemCategoryToCpp(31)).isEqualTo(ItemTypes_t.ITEM_TYPE_OTHER)
        assertThat(Items.protoItemCategoryToCpp(100)).isEqualTo(ItemTypes_t.ITEM_TYPE_OTHER)
    }

    @Test
    fun `item type lookup falls back to the front item and has item type is vector bound`() {
        val registry = ItemTypeRegistry()
        registry.register(ItemType(id = 2160, name = "Crystal Coin"))

        assertThat(registry.getItemType(2160).name).isEqualTo("Crystal Coin")
        assertThat(registry.get(2160)).isNotNull
        assertThat(registry.get(1)).isNull()
        assertThat(registry.hasItemType(2160)).isTrue()
        assertThat(registry.hasItemType(2161)).isFalse()
        assertThat(registry.size()).isEqualTo(2161)

        assertThat(registry.getItemType(99999).id).isEqualTo(0)
        assertThat(registry.getItemType(99999).name).isEmpty()
    }

    @Test
    fun `item id by name is case insensitive and returns zero when unknown`() {
        val registry = Items()
        registry.register(ItemType(id = 2160, name = "Crystal Coin"))

        assertThat(registry.getItemIdByName("Crystal Coin")).isEqualTo(2160)
        assertThat(registry.getItemIdByName("crystal coin")).isEqualTo(2160)
        assertThat(registry.getItemIdByName("CRYSTAL COIN")).isEqualTo(2160)
        assertThat(registry.getItemIdByName("missing")).isEqualTo(0)
    }

    @Test
    fun `register replaces the same id`() {
        val registry = Items()
        registry.register(ItemType(id = 100, name = "first"))
        registry.register(ItemType(id = 100, name = "second"))

        assertThat(registry.getItemType(100).name).isEqualTo("second")
        assertThat(registry.size()).isEqualTo(101)
    }

    @Test
    fun `build inventory list keeps weapons and equipment sorted by id`() {
        val registry = Items()
        registry.register(ItemType(id = 4, name = "bow", weaponType = WeaponType_t.WEAPON_DISTANCE))
        registry.register(ItemType(id = 3, name = "plain"))
        registry.register(ItemType(id = 2, name = "ring", slotPosition = 256))
        registry.register(ItemType(id = 1, name = "sword", attack = 10))
        registry.register(ItemType(id = 5, name = "armor", armor = 12))

        registry.buildInventoryList()

        assertThat(registry.getInventory()).containsExactly(1, 2, 4, 5)
    }

    @Test
    fun `ladders and dummys mirror the Canary containers`() {
        val registry = Items()
        registry.addLadderId(1386)
        registry.addDummyId(1, 50)
        registry.addDummyId(2, 25)

        assertThat(registry.getLadders()).containsExactly(1386)
        assertThat(registry.getDummys()).containsEntry(1, 50).containsEntry(2, 25)
    }

    @Test
    fun `clear drops items names ladders dummys and inventory`() {
        val registry = Items()
        registry.register(ItemType(id = 2160, name = "Crystal Coin"))
        registry.addLadderId(1386)
        registry.addDummyId(1, 50)
        registry.buildInventoryList()

        registry.clear()

        assertThat(registry.get(2160)).isNull()
        assertThat(registry.getItemIdByName("crystal coin")).isEqualTo(0)
        assertThat(registry.getLadders()).isEmpty()
        assertThat(registry.getDummys()).isEmpty()
        assertThat(registry.getInventory()).isEmpty()
        assertThat(registry.size()).isEqualTo(0)
    }
}
