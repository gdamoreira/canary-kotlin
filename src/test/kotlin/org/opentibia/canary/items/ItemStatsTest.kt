package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.creatures.WeaponType_t

/**
 * Transcript tests for the stat, forge and ownership members of `Item`
 * (src/items/item.hpp:339-482, item.cpp:545-629, 1541-1615, 3438-3491):
 * attack/defense/armor/hit-chance/shoot-range accessors with attribute
 * overrides, `getTier`/`setTier` (item.cpp:605-629), the tier chance formulas
 * (item.cpp:545-603), `getWorth`/`getForgeSlivers`/`getForgeCores`/
 * `getLightInfo`, ownership, `hasMarketAttributes` (item.cpp:3539-3559),
 * `clone`/`equals` (item.cpp:716-768), `canBeMoved` (item.cpp:1617-1628)
 * and `hasProperty` (item.cpp:1585-1615).
 */
class ItemStatsTest {

    @Test
    fun `stat accessors fall back to the item type`() {
        val sword = ItemType(
            id = 2400,
            name = "sword",
            attack = 10,
            defense = 12,
            extraDefense = 2,
            armor = 3,
            hitChance = -5,
            shootRange = 4,
            mantra = 7,
            weaponType = WeaponType_t.WEAPON_SWORD,
            ammoType = Ammo_t.AMMO_NONE.value,
            imbuementSlot = 2,
            slotPosition = SLOTP_HAND,
        )
        val item = Item(2400, type = sword)

        assertThat(item.getAttack()).isEqualTo(10)
        assertThat(item.getDefense()).isEqualTo(12)
        assertThat(item.getExtraDefense()).isEqualTo(2)
        assertThat(item.getArmor()).isEqualTo(3)
        assertThat(item.getHitChance()).isEqualTo(-5)
        assertThat(item.getShootRange()).isEqualTo(4)
        assertThat(item.getMantra()).isEqualTo(7)
        assertThat(item.getImbuementSlot()).isEqualTo(2)
        assertThat(item.getWeaponType()).isEqualTo(WeaponType_t.WEAPON_SWORD)
        assertThat(item.getAmmoType()).isEqualTo(Ammo_t.AMMO_NONE)
        assertThat(item.getSlotPosition()).isEqualTo(SLOTP_HAND)
    }

    @Test
    fun `stat accessors prefer item attributes over the item type`() {
        val sword = ItemType(id = 2400, name = "sword", attack = 10, defense = 12, armor = 3)
        val item = Item(2400, type = sword).apply {
            setAttribute(ItemAttribute.ATTACK, 42L)
            setAttribute(ItemAttribute.DEFENSE, 40L)
            setAttribute(ItemAttribute.EXTRADEFENSE, 4L)
            setAttribute(ItemAttribute.ARMOR, 9L)
            setAttribute(ItemAttribute.HITCHANCE, 200L)
            setAttribute(ItemAttribute.SHOOTRANGE, 6L)
            setAttribute(ItemAttribute.MANTRA, 8L)
            setAttribute(ItemAttribute.IMBUEMENT_SLOT, 3L)
        }

        assertThat(item.getAttack()).isEqualTo(42)
        assertThat(item.getDefense()).isEqualTo(40)
        assertThat(item.getExtraDefense()).isEqualTo(4)
        assertThat(item.getArmor()).isEqualTo(9)
        assertThat(item.getHitChance()).isEqualTo(-56)
        assertThat(item.getShootRange()).isEqualTo(6)
        assertThat(item.getMantra()).isEqualTo(8)
        assertThat(item.getImbuementSlot()).isEqualTo(3)
    }

    @Test
    fun `getHitChance truncates the attribute to int8`() {
        val item = Item(1).apply { setAttribute(ItemAttribute.HITCHANCE, 260L) }

        assertThat(item.getHitChance()).isEqualTo(4)
    }

    @Test
    fun `getBaseWeight prefers the weight attribute and converts type weight to hundredths`() {
        val item = Item(1, type = ItemType(1, "x", weight = 1.5))
        assertThat(item.getBaseWeight()).isEqualTo(150)

        item.setAttribute(ItemAttribute.WEIGHT, 250L)
        assertThat(item.getBaseWeight()).isEqualTo(250)
    }

    @Test
    fun `getStackSize is one unless the type is stackable`() {
        assertThat(Item(1, type = ItemType(1, "sword")).getStackSize()).isEqualTo(1)
        assertThat(Item(1, type = ItemType(1, "coin", isStackable = true, stackSize = 25)).getStackSize()).isEqualTo(25)
        assertThat(Item(1, type = ItemType(1, "coin", isStackable = true)).getStackSize()).isEqualTo(100)
    }

    @Test
    fun `getTier reads the tier attribute and rejects values above the configured maximum`() {
        val item = Item(2400, type = ItemType(2400, "sword", upgradeClassification = 3))

        assertThat(item.getTier()).isZero()
        item.setTier(4)
        assertThat(item.getTier()).isEqualTo(4)
        assertThat(item.getAttribute(ItemAttribute.TIER)).isEqualTo(4L)

        item.setTier(11)
        assertThat(item.getTier()).isEqualTo(4)

        item.setAttribute(ItemAttribute.TIER, 99L)
        assertThat(item.getTier()).isZero()

        item.setTier(5, maxItemTier = 6)
        assertThat(item.getTier(6)).isEqualTo(5)
        item.setTier(7, maxItemTier = 6)
        assertThat(item.getTier(6)).isEqualTo(5)
    }

    @Test
    fun `setTier is a no-op for item types without upgrade classification`() {
        val item = Item(2400, type = ItemType(2400, "sword"))

        item.setTier(3)

        assertThat(item.hasAttribute(ItemAttribute.TIER)).isFalse()
    }

    @Test
    fun `tier chance formulas are zero at tier zero and quadratic above`() {
        val item = Item(2400, type = ItemType(2400, "sword", upgradeClassification = 3))

        assertThat(item.getDodgeChance(1.0, 2.0, 3.0)).isZero()
        assertThat(item.getFatalChance(1.0, 2.0, 3.0)).isZero()
        assertThat(item.getMomentumChance(1.0, 2.0, 3.0)).isZero()
        assertThat(item.getTranscendenceChance(1.0, 2.0, 3.0)).isZero()
        assertThat(item.getAmplificationChance(1.0, 2.0, 3.0)).isZero()

        item.setTier(3)
        assertThat(item.getDodgeChance(1.0, 2.0, 3.0)).isEqualTo(18.0)
        assertThat(item.getFatalChance(1.0, 2.0, 3.0)).isEqualTo(18.0)
        assertThat(item.getMomentumChance(1.0, 2.0, 3.0)).isEqualTo(18.0)
        assertThat(item.getTranscendenceChance(1.0, 2.0, 3.0)).isEqualTo(18.0)
        assertThat(item.getAmplificationChance(1.0, 2.0, 3.0)).isEqualTo(18.0)
    }

    @Test
    fun `tier chance formulas use the Canary configuration defaults`() {
        val item = Item(2400, type = ItemType(2400, "sword", upgradeClassification = 3))
        item.setTier(10)

        assertThat(item.getFatalChance()).isCloseTo(9.05, within(1e-9))
        assertThat(item.getDodgeChance()).isCloseTo(7.50873, within(1e-6))
    }

    @Test
    fun `getWorth converts coins to gold`() {
        assertThat(Item(ITEM_GOLD_COIN, count = 5).getWorth()).isEqualTo(5L)
        assertThat(Item(ITEM_PLATINUM_COIN, count = 5).getWorth()).isEqualTo(500L)
        assertThat(Item(ITEM_CRYSTAL_COIN, count = 5).getWorth()).isEqualTo(50_000L)
        assertThat(Item(2400).getWorth()).isZero()
    }

    @Test
    fun `getForgeSlivers and getForgeCores only read their own item ids`() {
        assertThat(Item(ITEM_FORGE_SLIVER, count = 12).getForgeSlivers()).isEqualTo(12L)
        assertThat(Item(ITEM_FORGE_CORE, count = 4).getForgeCores()).isEqualTo(4L)
        assertThat(Item(ITEM_FORGE_SLIVER, count = 12).getForgeCores()).isZero()
        assertThat(Item(2400).getForgeSlivers()).isZero()
    }

    @Test
    fun `getLightInfo mirrors the item type light`() {
        val item = Item(1, type = ItemType(1, "torch", lightLevel = 6, lightColor = 215))

        assertThat(item.getLightInfo()).isEqualTo(
            org.opentibia.canary.combat.condition.LightInfo(level = 6, color = 215)
        )
    }

    @Test
    fun `ownership follows the owner attribute`() {
        val item = Item(1)

        assertThat(item.getOwnerId()).isZero()
        assertThat(item.hasOwner()).isFalse()
        assertThat(item.canBeMovedToStore()).isFalse()

        item.setOwner(42)
        assertThat(item.getOwnerId()).isEqualTo(42L)
        assertThat(item.hasOwner()).isTrue()
        assertThat(item.isOwner(42)).isTrue()
        assertThat(item.isOwner(43)).isFalse()
        assertThat(item.canBeMovedToStore()).isTrue()
    }

    @Test
    fun `hasMarketAttributes rejects modified charges, duration and tier`() {
        val type = ItemType(
            id = 2400,
            name = "sword",
            isMovable = true,
            charges = 5,
            decayTime = 100,
            upgradeClassification = 1,
        )
        val pristine = Item(2400, type = type)
        assertThat(pristine.hasMarketAttributes()).isTrue()

        val charged = Item(2400, type = type).apply { setAttribute(ItemAttribute.CHARGES, 7L) }
        assertThat(charged.hasMarketAttributes()).isFalse()

        val timed = Item(2400, type = type).apply { setAttribute(ItemAttribute.DURATION, 1L) }
        assertThat(timed.hasMarketAttributes()).isFalse()

        val tiered = Item(2400, type = type).apply { setAttribute(ItemAttribute.TIER, 99L) }
        assertThat(tiered.hasMarketAttributes()).isFalse()

        val owned = Item(2400, type = type).apply { setOwner(7) }
        assertThat(owned.hasMarketAttributes()).isFalse()
    }

    @Test
    fun `isStowable requires a warehouse id, no tier and pristine attributes`() {
        val stowable = ItemType(id = 2160, name = "coin", isStackable = true, wareId = 2160)
        assertThat(Item(2160, count = 10, type = stowable).isStowable()).isTrue()

        val noWare = ItemType(id = 2160, name = "coin", isStackable = true)
        assertThat(Item(2160, count = 10, type = noWare).isStowable()).isFalse()

        val container = ItemType(id = 1987, name = "bag", isStackable = true, wareId = 1987, isContainer = true)
        assertThat(Item(1987, count = 1, type = container).isStowable()).isFalse()
    }

    @Test
    fun `isItemStorable rejects store items, owned items and decaying items`() {
        val stackType = ItemType(id = 2160, name = "coin", isStackable = true, wareId = 2160)
        assertThat(Item(2160, count = 10, type = stackType).isItemStorable()).isTrue()

        val storeItem = Item(2160, count = 10, type = stackType).apply { setAttribute(ItemAttribute.STORE, 123L) }
        assertThat(storeItem.isItemStorable()).isFalse()

        val owned = Item(2160, count = 10, type = stackType).apply { setOwner(7) }
        assertThat(owned.isItemStorable()).isFalse()

        val bagType = ItemType(id = 1987, name = "bag", isContainer = true, capacity = 2)
        val bag = Container(1987, capacity = 2, type = bagType)
        assertThat(bag.isItemStorable()).isFalse()
        bag.addItem(Item(2160, count = 10, type = stackType))
        assertThat(bag.isItemStorable()).isTrue()
    }

    @Test
    fun `clone copies the attributes and stays in the derived type`() {
        val type = ItemType(id = 1987, name = "bag", isContainer = true, capacity = 8, isMovable = true)
        val bag = Container(1987, capacity = 8, type = type).apply {
            setActionId(77)
            setAttribute(ItemAttribute.TEXT, "hello")
        }

        val copy = bag.clone()

        assertThat(copy).isInstanceOf(Container::class.java)
        assertThat(copy.getActionId()).isEqualTo(77)
        assertThat(copy.getAttribute(ItemAttribute.TEXT)).isEqualTo("hello")
    }

    @Test
    fun `equals compares id, store state, owner and matching attributes`() {
        val type = ItemType(2400, "sword", isMovable = true)
        val item = Item(2400, type = type).apply { setAttribute(ItemAttribute.TEXT, "hi") }
        val same = Item(2400, type = type).apply { setAttribute(ItemAttribute.TEXT, "hi") }
        val different = Item(2400, type = type).apply { setAttribute(ItemAttribute.TEXT, "bye") }
        val otherId = Item(2401, type = type)

        assertThat(item.equals(same)).isTrue()
        assertThat(item.equals(different)).isFalse()
        assertThat(item.equals(otherId)).isFalse()
        assertThat(item.equals(null)).isFalse()
    }

    @Test
    fun `canBeMoved requires movability and rejects unique or immovable action ids`() {
        val movable = Item(1, type = ItemType(1, "crate", isMovable = true))
        assertThat(movable.canBeMoved()).isTrue()

        movable.setUniqueId(5)
        assertThat(movable.canBeMoved()).isFalse()

        movable.removeAttribute(ItemAttribute.UNIQUEID)
        movable.setActionId(IMMOVABLE_ACTION_ID)
        assertThat(movable.canBeMoved()).isFalse()

        val fixed = Item(2, type = ItemType(2, "wall"))
        assertThat(fixed.canBeMoved()).isFalse()
    }

    @Test
    fun `hasProperty maps ItemProperty to the item type flags`() {
        val blocking = ItemType(
            id = 1,
            name = "wall",
            blockSolid = true,
            blockProjectile = true,
            blockPathFind = true,
            hasHeight = true,
            isMovable = false,
        )
        val item = Item(1, type = blocking)

        assertThat(item.hasProperty(ItemProperty.CONST_PROP_BLOCKSOLID)).isTrue()
        assertThat(item.hasProperty(ItemProperty.CONST_PROP_BLOCKPROJECTILE)).isTrue()
        assertThat(item.hasProperty(ItemProperty.CONST_PROP_BLOCKPATH)).isTrue()
        assertThat(item.hasProperty(ItemProperty.CONST_PROP_HASHEIGHT)).isTrue()
        assertThat(item.hasProperty(ItemProperty.CONST_PROP_IMMOVABLEBLOCKSOLID)).isTrue()
        assertThat(item.hasProperty(ItemProperty.CONST_PROP_MOVABLE)).isFalse()
        assertThat(item.hasProperty(ItemProperty.CONST_PROP_ISVERTICAL)).isFalse()
    }

    @Test
    fun `getWeaponType and getAmmoType default to the struct sentinels`() {
        val item = Item(1)

        assertThat(item.getWeaponType()).isEqualTo(WeaponType_t.WEAPON_NONE)
        assertThat(item.getAmmoType()).isEqualTo(Ammo_t.AMMO_NONE)
        assertThat(ItemType(1, "x").elementalBond).isEqualTo(CombatType.NONE)
        assertThat(ItemType(1, "x", elementalBond = CombatType.FIREDAMAGE).elementalBond)
            .isEqualTo(CombatType.FIREDAMAGE)
    }
}
