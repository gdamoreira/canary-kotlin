package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for the augment helpers of `src/items/items.cpp`:
 * `Items::getAugmentNameByType` (items.cpp:73-80), `Items::isAugmentWithoutValueDescription`
 * (items.hpp:458-466), `ItemType::parseAugmentDescription` (items.cpp:82-97),
 * `ItemType::getFormattedAugmentDescription` (items.cpp:99-116), `ItemType::addAugment`
 * (items.cpp:118-121), `ItemType::setImbuementType` (items.cpp:123-125),
 * `ItemType::triggerExhaustion` (items.cpp:127-129) and `ItemType::getPluralName`
 * (items.hpp:239-253). The string helpers mirror `toStartCaseWithSpace`
 * (tools.cpp:412-426) and `capitalizeWordsIgnoringString` (tools.cpp:1687-1700).
 */
class ItemTypeAugmentTest {

    @Test
    fun `augment type enum keeps the Canary values and gaps`() {
        assertThat(Augment_t.None.value).isEqualTo(0)
        assertThat(Augment_t.ManaCost.value).isEqualTo(1)
        assertThat(Augment_t.BaseDamage.value).isEqualTo(2)
        assertThat(Augment_t.BaseHealing.value).isEqualTo(3)
        assertThat(Augment_t.DurationIncreased.value).isEqualTo(4)
        assertThat(Augment_t.AdditionalTargets.value).isEqualTo(5)
        assertThat(Augment_t.Cooldown.value).isEqualTo(6)
        assertThat(Augment_t.SecondaryGroupCooldown.value).isEqualTo(7)
        assertThat(Augment_t.AffectedAreaEnlarged.value).isEqualTo(8)
        assertThat(Augment_t.IncreasedDamageReduction.value).isEqualTo(9)
        assertThat(Augment_t.EnhancedEffect.value).isEqualTo(12)
        assertThat(Augment_t.IncreasedSkill.value).isEqualTo(13)
        assertThat(Augment_t.LifeLeech.value).isEqualTo(14)
        assertThat(Augment_t.ManaLeech.value).isEqualTo(15)
        assertThat(Augment_t.CriticalExtraDamage.value).isEqualTo(16)
        assertThat(Augment_t.CriticalHitChance.value).isEqualTo(17)
        assertThat(Augment_t.PowerfulImpact.value).isEqualTo(100)
        assertThat(Augment_t.StrongImpact.value).isEqualTo(101)
        assertThat(Augment_t.IncreasedDamage.value).isEqualTo(102)
    }

    @Test
    fun `imbuement type enum keeps the Canary values`() {
        assertThat(ImbuementTypes_t.IMBUEMENT_NONE.value).isEqualTo(-1)
        assertThat(ImbuementTypes_t.IMBUEMENT_ELEMENTAL_DAMAGE.value).isEqualTo(0)
        assertThat(ImbuementTypes_t.IMBUEMENT_LIFE_LEECH.value).isEqualTo(1)
        assertThat(ImbuementTypes_t.IMBUEMENT_MANA_LEECH.value).isEqualTo(2)
        assertThat(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT.value).isEqualTo(3)
        assertThat(ImbuementTypes_t.IMBUEMENT_ELEMENTAL_PROTECTION_DEATH.value).isEqualTo(4)
        assertThat(ImbuementTypes_t.IMBUEMENT_ELEMENTAL_PROTECTION_EARTH.value).isEqualTo(5)
        assertThat(ImbuementTypes_t.IMBUEMENT_ELEMENTAL_PROTECTION_FIRE.value).isEqualTo(6)
        assertThat(ImbuementTypes_t.IMBUEMENT_ELEMENTAL_PROTECTION_ICE.value).isEqualTo(7)
        assertThat(ImbuementTypes_t.IMBUEMENT_ELEMENTAL_PROTECTION_ENERGY.value).isEqualTo(8)
        assertThat(ImbuementTypes_t.IMBUEMENT_ELEMENTAL_PROTECTION_HOLY.value).isEqualTo(9)
        assertThat(ImbuementTypes_t.IMBUEMENT_INCREASE_SPEED.value).isEqualTo(10)
        assertThat(ImbuementTypes_t.IMBUEMENT_SKILLBOOST_AXE.value).isEqualTo(11)
        assertThat(ImbuementTypes_t.IMBUEMENT_SKILLBOOST_SWORD.value).isEqualTo(12)
        assertThat(ImbuementTypes_t.IMBUEMENT_SKILLBOOST_CLUB.value).isEqualTo(13)
        assertThat(ImbuementTypes_t.IMBUEMENT_SKILLBOOST_SHIELDING.value).isEqualTo(14)
        assertThat(ImbuementTypes_t.IMBUEMENT_SKILLBOOST_DISTANCE.value).isEqualTo(15)
        assertThat(ImbuementTypes_t.IMBUEMENT_SKILLBOOST_MAGIC_LEVEL.value).isEqualTo(16)
        assertThat(ImbuementTypes_t.IMBUEMENT_INCREASE_CAPACITY.value).isEqualTo(17)
        assertThat(ImbuementTypes_t.IMBUEMENT_SKILLBOOST_FIST.value).isEqualTo(18)
        assertThat(ImbuementTypes_t.IMBUEMENT_PARALYSIS_DEFLECTION.value).isEqualTo(19)
    }

    @Test
    fun `augment names mirror the enum_name start case and lowercase rule`() {
        val expected = mapOf(
            Augment_t.None to "none",
            Augment_t.ManaCost to "mana cost",
            Augment_t.BaseDamage to "base damage",
            Augment_t.BaseHealing to "base healing",
            Augment_t.DurationIncreased to "duration increased",
            Augment_t.AdditionalTargets to "additional targets",
            Augment_t.Cooldown to "cooldown",
            Augment_t.SecondaryGroupCooldown to "secondary group cooldown",
            Augment_t.AffectedAreaEnlarged to "affected area enlarged",
            Augment_t.IncreasedDamageReduction to "increased damage reduction",
            Augment_t.EnhancedEffect to "enhanced effect",
            Augment_t.IncreasedSkill to "increased skill",
            Augment_t.LifeLeech to "life leech",
            Augment_t.ManaLeech to "mana leech",
            Augment_t.CriticalExtraDamage to "critical extra damage",
            Augment_t.CriticalHitChance to "critical hit chance",
            Augment_t.PowerfulImpact to "Powerful Impact",
            Augment_t.StrongImpact to "Strong Impact",
            Augment_t.IncreasedDamage to "Increased Damage",
        )

        for ((augment, name) in expected) {
            assertThat(Items.getAugmentNameByType(augment)).isEqualTo(name)
        }
    }

    @Test
    fun `augments without value description are the Canary three`() {
        assertThat(Items.isAugmentWithoutValueDescription(Augment_t.IncreasedDamage)).isTrue()
        assertThat(Items.isAugmentWithoutValueDescription(Augment_t.PowerfulImpact)).isTrue()
        assertThat(Items.isAugmentWithoutValueDescription(Augment_t.StrongImpact)).isTrue()
        assertThat(Items.isAugmentWithoutValueDescription(Augment_t.BaseDamage)).isFalse()
        assertThat(Items.isAugmentWithoutValueDescription(Augment_t.Cooldown)).isFalse()
        assertThat(Items.isAugmentWithoutValueDescription(Augment_t.None)).isFalse()
    }

    @Test
    fun `formatted augment description mirrors the Canary format branches`() {
        val itemType = ItemType(id = 1, name = "test")

        assertThat(itemType.getFormattedAugmentDescription(AugmentInfo("Wound Cleansing", Augment_t.BaseDamage, 15)))
            .isEqualTo("Wound Cleansing -> +15% base damage")
        assertThat(itemType.getFormattedAugmentDescription(AugmentInfo("Wound Cleansing", Augment_t.BaseDamage, -15)))
            .isEqualTo("Wound Cleansing -> -15% base damage")
        assertThat(itemType.getFormattedAugmentDescription(AugmentInfo("Wound Cleansing", Augment_t.BaseHealing, 5)))
            .isEqualTo("Wound Cleansing -> +5% base healing")
        assertThat(itemType.getFormattedAugmentDescription(AugmentInfo("Haste", Augment_t.Cooldown, 2000)))
            .isEqualTo("Haste -> -2s cooldown")
        assertThat(itemType.getFormattedAugmentDescription(AugmentInfo("Haste", Augment_t.Cooldown, -500)))
            .isEqualTo("Haste -> +0s cooldown")
        assertThat(itemType.getFormattedAugmentDescription(AugmentInfo("Wound Cleansing", Augment_t.LifeLeech, 3)))
            .isEqualTo("Wound Cleansing -> +3% life leech")
        assertThat(itemType.getFormattedAugmentDescription(AugmentInfo("Wound Cleansing", Augment_t.CriticalHitChance, -2)))
            .isEqualTo("Wound Cleansing -> -2% critical hit chance")
        assertThat(itemType.getFormattedAugmentDescription(AugmentInfo("Wound Cleansing", Augment_t.IncreasedDamage, 0)))
            .isEqualTo("Wound Cleansing -> Increased Damage")
        assertThat(itemType.getFormattedAugmentDescription(AugmentInfo("Wound Cleansing", Augment_t.PowerfulImpact, 0)))
            .isEqualTo("Wound Cleansing -> Powerful Impact")
        assertThat(itemType.getFormattedAugmentDescription(AugmentInfo("death of doom", Augment_t.BaseDamage, 10)))
            .isEqualTo("Death of Doom -> +10% base damage")
        assertThat(itemType.getFormattedAugmentDescription(AugmentInfo("WOUND CLEANSING", Augment_t.BaseDamage, 10)))
            .isEqualTo("Wound Cleansing -> +10% base damage")
    }

    @Test
    fun `add augment appends the info in insertion order`() {
        val itemType = ItemType(id = 1, name = "test")
        assertThat(itemType.augments).isEmpty()

        itemType.addAugment("Wound Cleansing", Augment_t.BaseDamage, 10)
        itemType.addAugment("Haste", Augment_t.Cooldown, 2000)

        assertThat(itemType.augments).containsExactly(
            AugmentInfo("Wound Cleansing", Augment_t.BaseDamage, 10),
            AugmentInfo("Haste", Augment_t.Cooldown, 2000),
        )
    }

    @Test
    fun `parse augment description joins every augment with the inspect variants`() {
        val itemType = ItemType(id = 1, name = "test")
        assertThat(itemType.parseAugmentDescription()).isEmpty()
        assertThat(itemType.parseAugmentDescription(inspect = true)).isEmpty()

        itemType.addAugment("Wound Cleansing", Augment_t.BaseDamage, 10)
        itemType.addAugment("Haste", Augment_t.Cooldown, 2000)

        assertThat(itemType.parseAugmentDescription())
            .isEqualTo("\nAugments: (Wound Cleansing -> +10% base damage, Haste -> -2s cooldown).")
        assertThat(itemType.parseAugmentDescription(inspect = true))
            .isEqualTo("Wound Cleansing -> +10% base damage, Haste -> -2s cooldown.")
    }

    @Test
    fun `set imbuement type clamps the slot tier to the Canary maximum`() {
        val itemType = ItemType(id = 1, name = "test")
        assertThat(itemType.imbuementTypes).isEmpty()

        itemType.setImbuementType(ImbuementTypes_t.IMBUEMENT_LIFE_LEECH, 5)
        itemType.setImbuementType(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT, 2)
        itemType.setImbuementType(ImbuementTypes_t.IMBUEMENT_MANA_LEECH, 0)

        assertThat(itemType.imbuementTypes)
            .containsEntry(ImbuementTypes_t.IMBUEMENT_LIFE_LEECH, 3)
            .containsEntry(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT, 2)
            .containsEntry(ImbuementTypes_t.IMBUEMENT_MANA_LEECH, 0)
    }

    @Test
    fun `trigger exhaustion mirrors the rune and non magic shield potion rule`() {
        assertThat(ItemType(id = 1, name = "rune", type = ItemTypes_t.ITEM_TYPE_RUNE).triggerExhaustion()).isTrue()
        assertThat(ItemType(id = 2, name = "potion", type = ItemTypes_t.ITEM_TYPE_POTION).triggerExhaustion()).isTrue()
        assertThat(
            ItemType(id = 3, name = "shield potion", type = ItemTypes_t.ITEM_TYPE_POTION, isMagicShieldPotion = true)
                .triggerExhaustion()
        ).isFalse()
        assertThat(ItemType(id = 4, name = "sword", type = ItemTypes_t.ITEM_TYPE_SWORD).triggerExhaustion()).isFalse()
        assertThat(ItemType(id = 5, name = "none").triggerExhaustion()).isFalse()
    }

    @Test
    fun `plural name uses the stored plural then the show count fallback`() {
        assertThat(ItemType(id = 1, name = "talon", pluralName = "talons").getPluralName()).isEqualTo("talons")
        assertThat(ItemType(id = 2, name = "gold coin").getPluralName()).isEqualTo("gold coins")
        assertThat(ItemType(id = 3, name = "gold coin", showCount = false).getPluralName()).isEqualTo("gold coin")
    }
}
