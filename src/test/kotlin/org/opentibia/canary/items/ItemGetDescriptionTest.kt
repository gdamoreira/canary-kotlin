package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.Stat
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.spells.RuneSpell
import org.opentibia.canary.spells.Spells
import org.opentibia.canary.systems.imbuement.Imbuements

/**
 * Transcript tests for the composite `Item::getDescription` renderer
 * (src/items/item.cpp:2554-3353 static, :3358-3360 instance).
 *
 * Every expected literal is read from the C++ source: the article/plural name,
 * the weight line, the charges/duration/fluid/subtype text, the distance gates
 * and the attribute/ability/imbuement/augment/classification tails.
 */
class ItemGetDescriptionTest {

    private fun swordType(): ItemType = ItemType(id = 2400, name = "sword", article = "a", weight = 42.0)

    // --- article/pluralization and the look-distance tail ---------------------

    @Test
    fun `single item at close range renders name and weight`() {
        val sword = swordType()
        val item = Item(2400, count = 1, type = sword)

        assertThat(Item.getDescription(sword, 1, item)).isEqualTo("a sword.\nIt weighs 42.00 oz.")
        assertThat(item.getDescription(1)).isEqualTo("a sword.\nIt weighs 42.00 oz.")
    }

    @Test
    fun `single item at look distance 2 omits the weight`() {
        val sword = swordType()
        val item = Item(2400, count = 1, type = sword)

        assertThat(Item.getDescription(sword, 2, item)).isEqualTo("a sword.")
        assertThat(item.getDescription(2)).isEqualTo("a sword.")
    }

    @Test
    fun `stackable item shows the count the plural name and the plural weight`() {
        val coins = ItemType(id = 2148, name = "gold coin", isStackable = true, weight = 1.0, pluralName = "gold coins")
        val item = Item(2148, count = 5, type = coins)

        assertThat(item.getDescription(1)).isEqualTo("5 gold coins.\nThey weigh 5.00 oz.")
        assertThat(item.getDescription(3)).isEqualTo("5 gold coins.")
    }

    @Test
    fun `no-article path skips the item article`() {
        val sword = swordType()
        val item = Item(2400, count = 1, type = sword)

        assertThat(Item.getDescription(sword, 1, item, addArticle = false))
            .isEqualTo("sword.\nIt weighs 42.00 oz.")
    }

    @Test
    fun `type description is appended only within look distance 1`() {
        val sign = ItemType(id = 100, name = "sign", article = "a", description = "You can read it.", isPickupable = false)
        val item = Item(100, type = sign)

        assertThat(item.getDescription(1)).isEqualTo("a sign.\nYou can read it.")
        assertThat(item.getDescription(5)).isEqualTo("a sign.")
    }

    @Test
    fun `special description attribute is appended at any distance`() {
        val sign = ItemType(id = 100, name = "sign", article = "a", description = "You can read it.", isPickupable = false)
        val item = Item(100, type = sign).apply { setAttribute(ItemAttribute.DESCRIPTION, "Fragile This Way Up") }

        assertThat(item.getDescription(5)).isEqualTo("a sign.\nFragile This Way Up")
    }

    // --- fluid containers and splashes ----------------------------------------

    private val fluidRegistry = Items().apply {
        register(ItemType(id = 7, name = "blood"))
    }

    @Test
    fun `fluid container shows the fluid name and the close-range weight`() {
        val vial = ItemType(id = 2006, name = "vial", article = "a", isFluidContainer = true, weight = 1.8)
        val item = Item(2006, count = 7, type = vial)

        assertThat(Item.getDescription(vial, 1, item, items = fluidRegistry))
            .isEqualTo("a vial of blood.\nIt weighs 1.80 oz.")
    }

    @Test
    fun `empty fluid container says it is empty`() {
        val vial = ItemType(id = 2006, name = "vial", article = "a", isFluidContainer = true, weight = 1.8)
        val item = Item(2006, count = 0, type = vial)

        assertThat(Item.getDescription(vial, 1, item, items = fluidRegistry))
            .isEqualTo("a vial. It is empty.\nIt weighs 1.80 oz.")
    }

    @Test
    fun `fluid container with an unregistered subtype says unknown`() {
        val vial = ItemType(id = 2006, name = "vial", article = "a", isFluidContainer = true, isPickupable = false)
        val item = Item(2006, count = 9, type = vial)

        assertThat(Item.getDescription(vial, 1, item, items = fluidRegistry)).isEqualTo("a vial of unknown.")
    }

    @Test
    fun `splash shows the fluid name`() {
        val pool = ItemType(id = 2016, name = "pool", article = "a", isSplash = true, isPickupable = false)
        val item = Item(2016, count = 7, type = pool)

        assertThat(Item.getDescription(pool, 1, item, items = fluidRegistry)).isEqualTo("a pool of blood.")
    }

    // --- charges and duration --------------------------------------------------

    @Test
    fun `showCharges renders the subtype charge count`() {
        val machine = ItemType(id = 2400, name = "machine", article = "a", showCharges = true, charges = 10)
        val item = Item(2400, count = 2, type = machine)

        assertThat(item.getDescription(1)).isEqualTo("a machine that has 2 charges left.")
    }

    @Test
    fun `showCharges uses the singular for one charge`() {
        val machine = ItemType(id = 2400, name = "machine", article = "a", showCharges = true, charges = 10)
        val item = Item(2400, count = 1, type = machine)

        assertThat(item.getDescription(1)).isEqualTo("a machine that has 1 charge left.")
    }

    @Test
    fun `showCharges with a zero subtype prints the type charges with the C++ plural quirk`() {
        val machine = ItemType(id = 2400, name = "machine", article = "a", showCharges = true, charges = 1)

        assertThat(Item.getDescription(machine, 1, null, 0)).isEqualTo("a machine that has 1 charges left.")
    }

    @Test
    fun `showDuration renders the remaining time`() {
        val torch = ItemType(id = 2050, name = "torch", article = "a", showDuration = true)
        val item = Item(2050, type = torch).apply { setDuration(60_000) }

        assertThat(item.getDescription(1)).isEqualTo("a torch that will expire in 1 minute.")
    }

    @Test
    fun `showDuration renders brand-new without a duration attribute`() {
        val torch = ItemType(id = 2050, name = "torch", article = "a", showDuration = true)
        val item = Item(2050, type = torch)

        assertThat(item.getDescription(1)).isEqualTo("a torch that is brand-new.")
    }

    // --- containers, keys, rings and level doors --------------------------------

    @Test
    fun `container renders the volume`() {
        val backpack = ItemType(id = 1988, name = "backpack", article = "a", isContainer = true, maxItems = 20, isPickupable = false)
        val item = Item(1988, type = backpack)

        assertThat(item.getDescription(1)).isEqualTo("a backpack (Vol:20).")
    }

    @Test
    fun `quiver renders the volume without the closing parenthesis`() {
        val quiver = ItemType(
            id = 25700, name = "quiver", article = "a", isContainer = true, maxItems = 20,
            isPickupable = false, type = ItemTypes_t.ITEM_TYPE_QUIVER,
        )
        val item = Item(25700, type = quiver)

        assertThat(item.getDescription(1)).isEqualTo("a quiver (Vol:20.")
    }

    @Test
    fun `key renders the zero padded action id`() {
        val key = ItemType(id = 2086, name = "key", article = "a", isPickupable = false, type = ItemTypes_t.ITEM_TYPE_KEY)
        val item = Item(2086, type = key).apply { setActionId(123) }

        assertThat(item.getDescription(1)).isEqualTo("a key (Key:0123).")
    }

    @Test
    fun `level door renders the level above the door id`() {
        val door = ItemType(id = 6898, name = "gate of expertise", article = "a", isPickupable = false, levelDoor = 1000)
        val item = Item(6898, type = door).apply { setActionId(1050) }

        assertThat(item.getDescription(1)).isEqualTo("a gate of expertise for level 50.")
    }

    @Test
    fun `ring speed renders the attribute and the duration tail`() {
        val ring = ItemType(id = 2200, name = "ring", article = "a", isPickupable = false, slotPosition = SLOTP_RING)
        ring.getAbilities().speed = 20
        val item = Item(2200, type = ring)

        assertThat(item.getDescription(1)).isEqualTo("a ring (speed +20) that is brand-new.")
    }

    @Test
    fun `ring suppression and boost flags render their fixed text`() {
        val suppression = ItemType(id = 2200, name = "ring", article = "a", isPickupable = false, slotPosition = SLOTP_RING)
        suppression.getAbilities().conditionSuppressions[org.opentibia.canary.combat.condition.ConditionType.CONDITION_DRUNK.value] = 1
        assertThat(Item(suppression.id, type = suppression).getDescription(1)).isEqualTo("a ring (hard drinking).")

        val invisible = ItemType(id = 2200, name = "ring", article = "a", isPickupable = false, slotPosition = SLOTP_RING)
        invisible.getAbilities().invisible = true
        assertThat(Item(invisible.id, type = invisible).getDescription(1)).isEqualTo("a ring (invisibility).")

        val regeneration = ItemType(id = 2200, name = "ring", article = "a", isPickupable = false, slotPosition = SLOTP_RING)
        regeneration.getAbilities().regeneration = true
        assertThat(Item(regeneration.id, type = regeneration).getDescription(1)).isEqualTo("a ring (faster regeneration).")

        val manaShield = ItemType(id = 2200, name = "ring", article = "a", isPickupable = false, slotPosition = SLOTP_RING)
        manaShield.getAbilities().manaShield = true
        assertThat(Item(manaShield.id, type = manaShield).getDescription(1)).isEqualTo("a ring (mana shield).")
    }

    // --- weapons and abilities --------------------------------------------------

    @Test
    fun `distance weapon renders range attack and hit chance`() {
        val bow = ItemType(
            id = 2456, name = "bow", article = "a", isPickupable = false,
            weaponType = WeaponType_t.WEAPON_DISTANCE, ammoType = Ammo_t.AMMO_ARROW.value,
            shootRange = 5, attack = 3, hitChance = -5,
        )
        val item = Item(2456, type = bow)

        assertThat(item.getDescription(1)).isEqualTo("a bow (Range: 5, Atk +3, Hit% -5).")
    }

    @Test
    fun `melee weapon renders attack defense extra defense and the element`() {
        val sword = ItemType(
            id = 2400, name = "sword", article = "a", isPickupable = false,
            weaponType = WeaponType_t.WEAPON_SWORD, attack = 5, defense = 3, extraDefense = 1,
        )
        sword.getAbilities().elementType = CombatType.FIREDAMAGE
        sword.getAbilities().elementDamage = 10
        val item = Item(2400, type = sword)

        assertThat(item.getDescription(1)).isEqualTo("a sword (Atk:5 physical + 10 fire, Def:3 +1).")
    }

    @Test
    fun `show attributes renders skills magic level and protection`() {
        val ring = ItemType(id = 2200, name = "ring", article = "a", isPickupable = false, showAttributes = true)
        val abilities = ring.getAbilities()
        abilities.skills[skills_t.SKILL_SWORD.value] = 5
        abilities.stats[Stat.STAT_MAGICPOINTS.value] = 10
        abilities.absorbPercent[CombatType.PHYSICALDAMAGE.value] = 2
        abilities.absorbPercent[CombatType.FIREDAMAGE.value] = 2
        val item = Item(2200, type = ring)

        assertThat(item.getDescription(1))
            .isEqualTo("a ring (sword fighting +5, magic level +10, protection physical +2%, fire +2%).")
    }

    @Test
    fun `show attributes collapses an equal absorb into protection all`() {
        val ring = ItemType(id = 2200, name = "ring", article = "a", isPickupable = false, showAttributes = true)
        ring.getAbilities().absorbPercent.fill(3)
        val item = Item(2200, type = ring)

        assertThat(item.getDescription(1)).isEqualTo("a ring (protection all +3%).")
    }

    // --- writable text ----------------------------------------------------------

    @Test
    fun `book within distance 4 renders the writer and text`() {
        val book = ItemType(id = 1949, name = "book", article = "a", isPickupable = false, allowDistRead = true)
        val item = Item(1949, type = book).apply {
            setAttribute(ItemAttribute.WRITER, "Kilmar")
            setAttribute(ItemAttribute.TEXT, "Hello")
        }

        assertThat(item.getDescription(1)).isEqualTo("a book.\nKilmar wrote: Hello")
    }

    @Test
    fun `book beyond distance 4 says it is too far away`() {
        val book = ItemType(id = 1949, name = "book", article = "a", isPickupable = false, allowDistRead = true)
        val item = Item(1949, type = book).apply { setAttribute(ItemAttribute.TEXT, "Hello") }

        // The written text is known from the attribute, so Canary adds no period.
        assertThat(item.getDescription(5)).isEqualTo("a book.\nYou are too far away to read it")
    }

    @Test
    fun `book without text beyond distance 4 closes with a period`() {
        val book = ItemType(id = 1949, name = "book", article = "a", isPickupable = false, allowDistRead = true)
        val item = Item(1949, type = book)

        assertThat(item.getDescription(5)).isEqualTo("a book.\nYou are too far away to read it.")
    }

    @Test
    fun `book with no text says nothing is written`() {
        val book = ItemType(id = 1949, name = "book", article = "a", isPickupable = false, allowDistRead = true)
        val item = Item(1949, type = book)

        assertThat(item.getDescription(1)).isEqualTo("a book.\nNothing is written on it.")
    }

    @Test
    fun `writeable text id 7371 appends the text at any distance`() {
        val text = ItemType(id = 7371, name = "text", article = "a", isPickupable = false, allowDistRead = true)
        val item = Item(7371, type = text).apply { setAttribute(ItemAttribute.TEXT, "Hello") }

        assertThat(item.getDescription(5)).isEqualTo("a text.\nHello")
    }

    // --- wield info, augments, imbuements, classification ------------------------

    @Test
    fun `wield info renders premium vocation level and magic level`() {
        val sword = ItemType(
            id = 2400, name = "sword", article = "a", isPickupable = false,
            wieldInfo = WIELDINFO_PREMIUM.toLong() or WIELDINFO_LEVEL.toLong() or WIELDINFO_MAGLV.toLong(),
            vocationString = "sorcerers and druids", minReqLevel = 8, minReqMagicLevel = 3,
        )
        val item = Item(2400, type = sword)

        assertThat(item.getDescription(1)).isEqualTo(
            "a sword.\nIt can only be wielded properly by premium sorcerers and druids of level 8 or higher and magic level 3 or higher."
        )
    }

    @Test
    fun `wield info without vocations falls back to players`() {
        val sword = ItemType(
            id = 2400, name = "sword", article = "a", isPickupable = false,
            wieldInfo = WIELDINFO_MAGLV.toLong(), minReqMagicLevel = 3,
        )
        val item = Item(2400, type = sword)

        assertThat(item.getDescription(1))
            .isEqualTo("a sword.\nIt can only be wielded properly by players of magic level 3 or higher.")
    }

    @Test
    fun `augments render between the period and the weight`() {
        val sword = swordType()
        sword.addAugment("Wound Cleansing", Augment_t.BaseDamage, 10)
        val item = Item(2400, type = sword)

        assertThat(item.getDescription(1))
            .isEqualTo("a sword.\nAugments: (Wound Cleansing -> +10% base damage).\nIt weighs 42.00 oz.")
    }

    private val imbuements = Imbuements().apply { assertThat(loadFromXml(TEST_IMBUEMENT_XML)).isTrue() }
    private val knownImbuementId: Int = imbuements.imbuementMap.keys.single()

    @Test
    fun `imbuements render the base and imbuement names with the remaining time`() {
        val sword = ItemType(id = 2400, name = "sword", article = "a", isPickupable = false, imbuementSlot = 1)
        val item = Item(2400, type = sword)
        item.imbuementRegistry = imbuements
        item.setImbuement(0, knownImbuementId, 18_000)

        assertThat(item.getDescription(1)).isEqualTo("a sword.\nImbuements: (Basic Strike 05:00h).")
    }

    @Test
    fun `empty imbuement slots render as empty slot`() {
        val sword = ItemType(id = 2400, name = "sword", article = "a", isPickupable = false, imbuementSlot = 2)
        val item = Item(2400, type = sword)
        item.imbuementRegistry = imbuements
        item.setImbuement(0, knownImbuementId, 18_000)

        assertThat(item.getDescription(1))
            .isEqualTo("a sword.\nImbuements: (Basic Strike 05:00h, Empty Slot).")
    }

    @Test
    fun `classification renders the tier and the category effect`() {
        val helmet = ItemType(
            id = 2457, name = "helmet", article = "a", isPickupable = false,
            upgradeClassification = 4, slotPosition = SLOTP_HEAD,
        )
        val item = Item(2457, type = helmet).apply { setTier(4) }

        assertThat(Item.getDescription(helmet, 1, item, objectCategory = { ObjectCategory_t.OBJECTCATEGORY_HELMETS }))
            .isEqualTo("a helmet.\nClassification: 4 Tier: 4 (8.45% Momentum).")
    }

    @Test
    fun `classification of a weapon renders the onslaught chance`() {
        val sword = ItemType(
            id = 2400, name = "sword", article = "a", isPickupable = false,
            upgradeClassification = 4, weaponType = WeaponType_t.WEAPON_SWORD,
        )
        val item = Item(2400, type = sword).apply { setTier(1) }

        assertThat(item.getDescription(1)).isEqualTo("a sword.\nClassification: 4 Tier: 1 (0.50% Onslaught).")
    }

    @Test
    fun `classification without a tier renders tier zero`() {
        val helmet = ItemType(
            id = 2457, name = "helmet", article = "a", isPickupable = false,
            upgradeClassification = 1, slotPosition = SLOTP_HEAD,
        )
        val item = Item(2457, type = helmet)

        assertThat(Item.getDescription(helmet, 1, item, objectCategory = { ObjectCategory_t.OBJECTCATEGORY_HELMETS }))
            .isEqualTo("a helmet.\nClassification: 1 Tier: 0.")
    }

    // --- runes and elemental bond ------------------------------------------------

    @Test
    fun `rune renders the spell name and the vocation list`() {
        val runeType = ItemType(
            id = 3155, name = "adori rune", article = "an", isPickupable = false, type = ItemTypes_t.ITEM_TYPE_RUNE,
            runeSpellName = "adori", runeLevel = 3, runeMagLevel = 2,
        )
        val rune = RuneSpell().apply {
            runeId = 3155
            vocSpellMap[3] = false
            vocSpellMap[2] = true
            vocSpellMap[1] = true
        }
        val spells = Spells().apply { registerRuneLuaEvent(rune) }
        val vocations = Vocations().apply {
            loadFromXml(
                """
                <vocations>
                    <vocation id="1" name="Sorcerer"/>
                    <vocation id="2" name="Druid"/>
                    <vocation id="3" name="Paladin"/>
                </vocations>
                """.trimIndent()
            )
        }

        assertThat(Item.getDescription(runeType, 1, null, spells = spells, vocations = vocations)).isEqualTo(
            "an adori rune (\"adori\"). It can only be used by sorcerers and druids with level 3 and magic level 2 or higher."
        )
    }

    @Test
    fun `rune without a registered spell falls back to the plain name`() {
        val runeType = ItemType(
            id = 3155, name = "adori rune", article = "an", isPickupable = false, type = ItemTypes_t.ITEM_TYPE_RUNE,
            runeSpellName = "adori", runeLevel = 3,
        )

        assertThat(Item.getDescription(runeType, 1, null)).isEqualTo("an adori rune.")
    }

    @Test
    fun `elemental bond renders the pascal cased combat name`() {
        val sword = swordType()
        sword.elementalBond = CombatType.FIREDAMAGE
        val item = Item(2400, type = sword)

        assertThat(item.getDescription(5)).isEqualTo("a sword.\nElemental Bond: Fire.")
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
