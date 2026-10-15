package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.systems.imbuement.Imbuements
import org.opentibia.canary.utils.Tools

/**
 * Transcript tests for the plural inspection renderer
 * `Item::getDescriptions(const ItemType &, const std::shared_ptr<Item> &)`
 * (src/items/item.cpp:1658-2298), the key/value pair list
 * `ProtocolGame::sendItemInspection` serializes (protocolgame.cpp:2966-2971).
 *
 * Every expected literal is read from the C++ source, including the duplicated
 * `Charges`/`Effect`/`Skill Boost` entries and the reversed `Tradeable`
 * fields of the item and type-only branches.
 */
class ItemGetDescriptionsTest {

    @Test
    fun `sword inspection list carries attack defence weight and trade fields`() {
        val sword = ItemType(
            id = 2400, name = "sword", article = "a", weight = 42.0,
            weaponType = WeaponType_t.WEAPON_SWORD, attack = 5, defense = 3,
        )
        val item = Item(2400, type = sword)

        assertThat(Item.getDescriptions(sword, item)).containsExactly(
            "Attack" to "5",
            "Defence" to "3",
            "Weight" to "42.00 oz",
            "Tradeable" to "yes",
            "Weapon Type" to "sword",
            "Body Position" to "weapon hand",
        )
    }

    @Test
    fun `armor inspection list renders the armor value and body position`() {
        val armor = ItemType(
            id = 2464, name = "plate armor", article = "a", weight = 100.0,
            armor = 10, slotPosition = SLOTP_ARMOR,
        )
        val item = Item(2464, type = armor)

        assertThat(Item.getDescriptions(armor, item)).containsExactly(
            "Armor" to "10",
            "Weight" to "100.00 oz",
            "Tradeable" to "yes",
            "Body Position" to "body",
        )
    }

    @Test
    fun `show charges renders both the plain and the actual-over-max entries`() {
        val machine = ItemType(id = 2400, name = "machine", article = "a", showCharges = true, charges = 10)
        val item = Item(2400, count = 2, type = machine)

        assertThat(Item.getDescriptions(machine, item)).containsExactly(
            "Charges" to "2",
            "Charges" to "2/2",
            "Tradeable" to "yes",
            "Body Position" to "weapon hand",
        )
    }

    @Test
    fun `ability effects render protections skill boosts and the field protection`() {
        val ring = ItemType(id = 2200, name = "ring", article = "a", isPickupable = false, slotPosition = SLOTP_RING)
        val abilities = ring.getAbilities()
        abilities.absorbPercent[CombatType.FIREDAMAGE.value] = 10
        abilities.fieldAbsorbPercent[CombatType.FIREDAMAGE.value] = 10
        abilities.speed = 20
        abilities.skills[skills_t.SKILL_SWORD.value] = 5
        abilities.regeneration = true
        abilities.magicShieldCapacityFlat = 50
        abilities.magicShieldCapacityPercent = 10
        abilities.perfectShotDamage = 15
        abilities.perfectShotRange = 3
        abilities.reflectFlat[Tools.combatTypeToIndex(CombatType.PHYSICALDAMAGE)] = 5
        abilities.cleavePercent = 5
        abilities.manaShield = true
        abilities.invisible = true
        abilities.conditionSuppressions[ConditionType.CONDITION_DRUNK.value] = 1
        val item = Item(2200, type = ring)

        assertThat(Item.getDescriptions(ring, item)).containsExactly(
            "Protection" to "fire +10%",
            "Skill Boost" to "speed +10, sword fighting +5, faster regeneration",
            "Magic Shield Capacity" to "+50 and 10%",
            "Perfect Shot" to "+15 at range 3",
            "Damage Reflection" to "5",
            "Cleave" to "+5%",
            "Effect" to "Hard Drinking",
            "Effect" to "Invisibility",
            "Effect" to "Faster Regeneration",
            "Effect" to "Mana Shield",
            "Field Protection" to "fire +10%",
            "Skill Boost" to "Hard Drinking",
            "Skill Boost" to "Invisibility",
            "Skill Boost" to "Faster Regeneration",
            "Skill Boost" to "Mana Shield",
            "Tradeable" to "yes",
            "Body Position" to "finger",
        )
    }

    @Test
    fun `tier and classification render through the object category seam`() {
        val helmet = ItemType(
            id = 2457, name = "helmet", article = "a", isPickupable = false,
            upgradeClassification = 4, slotPosition = SLOTP_HEAD,
        )
        val item = Item(2457, type = helmet).apply { setTier(4) }

        assertThat(Item.getDescriptions(helmet, item, objectCategory = { ObjectCategory_t.OBJECTCATEGORY_HELMETS }))
            .containsExactly(
                "Tier" to "4 (8.45% Momentum)",
                "Tradeable" to "yes",
                "Body Position" to "head",
                "Classification" to "4",
            )
    }

    @Test
    fun `type-only rune list carries the spell name and the market trade field`() {
        val rune = ItemType(
            id = 3155, name = "adori rune", article = "an", isPickupable = false,
            type = ItemTypes_t.ITEM_TYPE_RUNE, runeSpellName = "adori",
        )

        assertThat(Item.getDescriptions(rune)).containsExactly(
            "Rune Spell Name" to "adori",
            "Tradeable In Market" to "yes",
            "Body Position" to "weapon hand",
        )
    }

    private val imbuements = Imbuements().apply { assertThat(loadFromXml(TEST_IMBUEMENT_XML)).isTrue() }
    private val knownImbuementId: Int = imbuements.imbuementMap.keys.single()

    @Test
    fun `filled and empty imbuement slots render their own entries and drop tradeable`() {
        val sword = ItemType(id = 2400, name = "sword", article = "a", isPickupable = false, imbuementSlot = 2)
        val item = Item(2400, type = sword)
        item.imbuementRegistry = imbuements
        item.setImbuement(0, knownImbuementId, 18_000)

        assertThat(Item.getDescriptions(sword, item)).containsExactly(
            "Imbuement Slot 1" to "Basic Strike (), lasts 05:00h while fighting.",
            "Imbuement Slot 2" to "empty",
            "Body Position" to "weapon hand",
        )
    }

    private companion object {
        const val TEST_IMBUEMENT_XML = """
<imbuements>
    <base id="1" name="Basic" price="5000" protectionPrice="10000" percent="90" removecost="15000" duration="72000"/>
    <category id="3" name="Critical Hit" agressive="1"/>
    <imbuement name="Strike" base="1" category="3" iconid="1">
        <attribute key="effect" type="skill" value="critical" bonus="1500" chance="1000"/>
    </imbuement>
</imbuements>
"""
    }
}
