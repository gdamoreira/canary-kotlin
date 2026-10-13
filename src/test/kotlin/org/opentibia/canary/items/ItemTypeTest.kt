package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.systems.imbuement.ImbuementTypes_t

/**
 * Transcript tests for the `ItemType` predicates in
 * src/items/items.hpp:96-253 and its derived state:
 * `isWeapon`/`isWand`/`isShield`/`isSpellBook`, the slot predicates
 * (`isArmor`/`isHelmet`/`isLegs`/`isBoots`), `isRanged`/`isMissile`, the
 * `ItemTypes_t` predicates, `hasSubType`, `isActiveEquipment`,
 * `getPluralName` and `hasImbuementType`.
 */
class ItemTypeTest {

    @Test
    fun `isWeapon excludes shields and ammunition`() {
        assertThat(typeWith(WeaponType_t.WEAPON_SWORD).isWeapon()).isTrue()
        assertThat(typeWith(WeaponType_t.WEAPON_CLUB).isWeapon()).isTrue()
        assertThat(typeWith(WeaponType_t.WEAPON_AXE).isWeapon()).isTrue()
        assertThat(typeWith(WeaponType_t.WEAPON_FIST).isWeapon()).isTrue()
        assertThat(typeWith(WeaponType_t.WEAPON_WAND).isWeapon()).isTrue()
        assertThat(typeWith(WeaponType_t.WEAPON_DISTANCE).isWeapon()).isTrue()
        assertThat(typeWith(WeaponType_t.WEAPON_MISSILE).isWeapon()).isTrue()
        assertThat(typeWith(WeaponType_t.WEAPON_SHIELD).isWeapon()).isFalse()
        assertThat(typeWith(WeaponType_t.WEAPON_AMMO).isWeapon()).isFalse()
        assertThat(ItemType(1, "nothing").isWeapon()).isFalse()
    }

    @Test
    fun `weapon type predicates follow WeaponType_t`() {
        assertThat(typeWith(WeaponType_t.WEAPON_WAND).isWand()).isTrue()
        assertThat(typeWith(WeaponType_t.WEAPON_DISTANCE).isRanged()).isTrue()
        assertThat(typeWith(WeaponType_t.WEAPON_MISSILE).isMissile()).isTrue()
        assertThat(typeWith(WeaponType_t.WEAPON_WAND).isRanged()).isFalse()
        assertThat(typeWith(WeaponType_t.WEAPON_DISTANCE).isMissile()).isFalse()
    }

    @Test
    fun `slot predicates follow the SLOTP bits of slotPosition`() {
        assertThat(ItemType(1, "helmet", slotPosition = SLOTP_HEAD).isHelmet()).isTrue()
        assertThat(ItemType(2, "armor", slotPosition = SLOTP_ARMOR).isArmor()).isTrue()
        assertThat(ItemType(3, "legs", slotPosition = SLOTP_LEGS).isLegs()).isTrue()
        assertThat(ItemType(4, "boots", slotPosition = SLOTP_FEET).isBoots()).isTrue()
        assertThat(ItemType(5, "helmet", slotPosition = SLOTP_HEAD).isArmor()).isFalse()
        assertThat(ItemType(6, "hand", slotPosition = SLOTP_HAND).isHelmet()).isFalse()
    }

    @Test
    fun `type predicates follow ItemTypes_t`() {
        assertThat(ItemType(1, "key", type = ItemTypes_t.ITEM_TYPE_KEY).isKey()).isTrue()
        assertThat(ItemType(2, "rune", type = ItemTypes_t.ITEM_TYPE_RUNE).isRune()).isTrue()
        assertThat(ItemType(3, "quiver", type = ItemTypes_t.ITEM_TYPE_QUIVER).isQuiver()).isTrue()
        assertThat(ItemType(4, "depot", type = ItemTypes_t.ITEM_TYPE_DEPOT).isDepot()).isTrue()
        assertThat(ItemType(5, "mailbox", type = ItemTypes_t.ITEM_TYPE_MAILBOX).isMailbox()).isTrue()
        assertThat(ItemType(6, "field", type = ItemTypes_t.ITEM_TYPE_MAGICFIELD).isMagicField()).isTrue()
        assertThat(ItemType(7, "teleport", type = ItemTypes_t.ITEM_TYPE_TELEPORT).isTeleport()).isTrue()
        assertThat(ItemType(8, "ring", type = ItemTypes_t.ITEM_TYPE_RING).isRing()).isTrue()
        assertThat(ItemType(9, "amulet", type = ItemTypes_t.ITEM_TYPE_AMULET).isAmulet()).isTrue()
        assertThat(ItemType(10, "ammo", type = ItemTypes_t.ITEM_TYPE_AMMO).isAmmo()).isTrue()
        assertThat(ItemType(11, "ladder", type = ItemTypes_t.ITEM_TYPE_LADDER).isLadder()).isTrue()
        assertThat(ItemType(12, "dummy", type = ItemTypes_t.ITEM_TYPE_DUMMY).isDummy()).isTrue()
        assertThat(ItemType(13, "chest", type = ItemTypes_t.ITEM_TYPE_REWARDCHEST).isRewardChest()).isTrue()
        assertThat(ItemType(14, "sword", type = ItemTypes_t.ITEM_TYPE_SWORD).isKey()).isFalse()
    }

    @Test
    fun `isShield excludes spellbooks`() {
        assertThat(ItemType(1, "shield", type = ItemTypes_t.ITEM_TYPE_SHIELD).isShield()).isTrue()
        assertThat(ItemType(2, "spellbook", type = ItemTypes_t.ITEM_TYPE_SHIELD, spellbook = true).isShield()).isFalse()
        assertThat(ItemType(3, "spellbook", spellbook = true).isSpellBook()).isTrue()
    }

    @Test
    fun `hasSubType covers fluid, splash, stackable and charged items`() {
        assertThat(ItemType(1, "vial", isFluidContainer = true).hasSubType()).isTrue()
        assertThat(ItemType(2, "splash", isSplash = true).hasSubType()).isTrue()
        assertThat(ItemType(3, "coin", isStackable = true).hasSubType()).isTrue()
        assertThat(ItemType(4, "wand", charges = 3).hasSubType()).isTrue()
        assertThat(ItemType(5, "sword").hasSubType()).isFalse()
    }

    @Test
    fun `isMultiUse mirrors the multiUse flag`() {
        assertThat(ItemType(1, "lever", multiUse = true).isMultiUse()).isTrue()
        assertThat(ItemType(2, "sword").isMultiUse()).isFalse()
    }

    @Test
    fun `isActiveEquipment requires a de-equip target plus event or decay`() {
        val scripted = ItemType(
            id = 1,
            name = "belt",
            transformDeEquipTo = 2,
            hasDeEquipEvent = true,
        )
        val timed = ItemType(
            id = 1,
            name = "belt",
            transformDeEquipTo = 2,
            decayTime = 10,
            decayTo = 2,
        )
        val bare = ItemType(id = 1, name = "belt", transformDeEquipTo = 2)
        val broken = ItemType(id = 1, name = "belt", decayTime = 10, decayTo = 2)

        assertThat(scripted.isActiveEquipment()).isTrue()
        assertThat(timed.isActiveEquipment()).isTrue()
        assertThat(bare.isActiveEquipment()).isFalse()
        assertThat(broken.isActiveEquipment()).isFalse()
    }

    @Test
    fun `getPluralName falls back to name plus s unless showCount is off`() {
        assertThat(ItemType(1, "gold coin").getPluralName()).isEqualTo("gold coins")
        assertThat(ItemType(2, "gold coin", pluralName = "gold pieces").getPluralName()).isEqualTo("gold pieces")
        assertThat(ItemType(3, "gold coin", showCount = false).getPluralName()).isEqualTo("gold coin")
    }

    @Test
    fun `hasImbuementType compares the stored tier`() {
        val itemType = ItemType(
            id = 1,
            name = "sword",
            imbuementTypes = mutableMapOf(ImbuementTypes_t.IMBUEMENT_SKILLBOOST_SWORD to 3),
        )

        assertThat(itemType.hasImbuementType(ImbuementTypes_t.IMBUEMENT_SKILLBOOST_SWORD, 3)).isTrue()
        assertThat(itemType.hasImbuementType(ImbuementTypes_t.IMBUEMENT_SKILLBOOST_SWORD, 4)).isFalse()
        assertThat(itemType.hasImbuementType(ImbuementTypes_t.IMBUEMENT_SKILLBOOST_AXE, 1)).isFalse()
    }

    private fun typeWith(weaponType: WeaponType_t): ItemType =
        ItemType(id = 1, name = "weapon", weaponType = weaponType)
}
