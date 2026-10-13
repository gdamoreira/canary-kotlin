package org.opentibia.canary.items.functions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.Stat
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.items.AugmentInfo
import org.opentibia.canary.items.Augment_t
import org.opentibia.canary.items.BedItemPart_t
import org.opentibia.canary.items.ItemGroup_t
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypes_t
import org.opentibia.canary.items.PLAYERSEX_FEMALE
import org.opentibia.canary.items.PLAYERSEX_MALE
import org.opentibia.canary.items.WeaponAction_t
import org.opentibia.canary.items.weapons.WeaponMelee
import org.opentibia.canary.items.weapons.WeaponWand
import org.opentibia.canary.items.weapons.Weapons
import org.opentibia.canary.systems.imbuement.ImbuementTypes_t
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Transcripts Canary's `ItemParse::initParse` XML attribute handling
 * (src/items/functions/item/item_parse.cpp) attribute by attribute.
 */
class ItemParseTest {

    @Test
    fun `parseType maps the item type and registers ladders and dummy rates`() {
        val registry = FakeRegistry()
        val parser = ItemParse(registry = registry)

        val container = ItemType(id = 1988, name = "bag")
        parse(parser, container, "type", "container")
        assertThat(container.type).isEqualTo(ItemTypes_t.ITEM_TYPE_CONTAINER)
        assertThat(container.group).isEqualTo(ItemGroup_t.ITEM_GROUP_CONTAINER)
        assertThat(container.isContainer).isTrue()

        val ladder = ItemType(id = 1948, name = "ladder")
        parse(parser, ladder, "type", "ladder")
        assertThat(ladder.type).isEqualTo(ItemTypes_t.ITEM_TYPE_LADDER)
        assertThat(registry.ladders).containsExactly(1948)

        val dummy = ItemType(id = 28559, name = "dummy")
        parse(parser, dummy, "type", "dummy", """<attribute key="rate" value="1500"/>""")
        assertThat(dummy.type).isEqualTo(ItemTypes_t.ITEM_TYPE_DUMMY)
        assertThat(registry.dummys).containsEntry(28559, 1500)

        val potion = ItemType(id = 35563, name = "magic shield potion")
        parse(parser, potion, "type", "magicshieldpotion")
        assertThat(potion.isMagicShieldPotion).isTrue()

        val unknown = ItemType(id = 1, name = "unknown")
        parse(parser, unknown, "type", "does-not-exist")
        assertThat(unknown.type).isEqualTo(ItemTypes_t.ITEM_TYPE_NONE)
    }

    @Test
    fun `parse scalar numbers match the C++ casts`() {
        val itemType = ItemType(id = 2400, name = "sword")
        val parser = ItemParse(registry = FakeRegistry())

        parse(parser, itemType, "weight", "100")
        parse(parser, itemType, "armor", "3")
        parse(parser, itemType, "defense", "12")
        parse(parser, itemType, "extradef", "1")
        parse(parser, itemType, "attack", "20")
        parse(parser, itemType, "mantra", "2")
        parse(parser, itemType, "rotateto", "5")
        parse(parser, itemType, "range", "6")
        parse(parser, itemType, "decayto", "2385")
        parse(parser, itemType, "leveldoor", "1000")
        parse(parser, itemType, "charges", "7")

        assertThat(itemType.weight).isEqualTo(100.0)
        assertThat(itemType.armor).isEqualTo(3)
        assertThat(itemType.defense).isEqualTo(12)
        assertThat(itemType.extraDefense).isEqualTo(1)
        assertThat(itemType.attack).isEqualTo(20)
        assertThat(itemType.mantra).isEqualTo(2)
        assertThat(itemType.rotateTo).isEqualTo(5)
        assertThat(itemType.shootRange).isEqualTo(6)
        assertThat(itemType.decayTo).isEqualTo(2385)
        assertThat(itemType.levelDoor).isEqualTo(1000L)
        assertThat(itemType.charges).isEqualTo(7)
    }

    @Test
    fun `parse booleans and strings mirror the XML keys`() {
        val itemType = ItemType(id = 2400, name = "sword")
        val parser = ItemParse(registry = FakeRegistry())

        parse(parser, itemType, "description", "a legendary blade")
        parse(parser, itemType, "runespellname", "adori")
        parse(parser, itemType, "showcount", "false")
        parse(parser, itemType, "movable", "true")
        parse(parser, itemType, "blockprojectile", "true")
        parse(parser, itemType, "allowpickupable", "false")
        parse(parser, itemType, "wrapcontainer", "true")
        parse(parser, itemType, "replaceable", "false")
        parse(parser, itemType, "walkstack", "false")
        parse(parser, itemType, "blocking", "true")

        assertThat(itemType.description).isEqualTo("a legendary blade")
        assertThat(itemType.runeSpellName).isEqualTo("adori")
        assertThat(itemType.showCount).isFalse()
        assertThat(itemType.isMovable).isTrue()
        assertThat(itemType.blockProjectile).isTrue()
        assertThat(itemType.isPickupable).isFalse()
        assertThat(itemType.wrapContainer).isTrue()
        assertThat(itemType.replaceable).isFalse()
        assertThat(itemType.walkStack).isFalse()
        assertThat(itemType.blockSolid).isTrue()
    }

    @Test
    fun `parse pickupable and writeables`() {
        val itemType = ItemType(id = 1987, name = "backpack")
        val parser = ItemParse(registry = FakeRegistry())

        parse(parser, itemType, "pickupable", "false")
        assertThat(itemType.isPickupable).isFalse()

        parse(parser, itemType, "readable", "true")
        assertThat(itemType.canReadText).isTrue()
        assertThat(itemType.canWriteText).isFalse()

        parse(parser, itemType, "writeable", "true")
        assertThat(itemType.canWriteText).isTrue()
        assertThat(itemType.canReadText).isTrue()

        parse(parser, itemType, "writeable", "false")
        assertThat(itemType.canWriteText).isFalse()
        assertThat(itemType.canReadText).isFalse()

        parse(parser, itemType, "maxtextlen", "300")
        parse(parser, itemType, "writeonceitemid", "1988")
        assertThat(itemType.maxTextLen).isEqualTo(300)
        assertThat(itemType.writeOnceItemId).isEqualTo(1988)
    }

    @Test
    fun `parse wrapable to sets the wrapable flag`() {
        val itemType = ItemType(id = 1, name = "wrap")
        parse(ItemParse(registry = FakeRegistry()), itemType, "wrapableto", "1990")

        assertThat(itemType.wrapableTo).isEqualTo(1990)
        assertThat(itemType.wrapable).isTrue()
    }

    @Test
    fun `parse floor change fluid source loot type and slot type`() {
        val itemType = ItemType(id = 1, name = "tile")
        val parser = ItemParse(registry = FakeRegistry())

        parse(parser, itemType, "floorchange", "north")
        assertThat(itemType.floorChange).isEqualTo(2)

        parse(parser, itemType, "fluidsource", "water")
        assertThat(itemType.fluidSource).isEqualTo(1)

        parse(parser, itemType, "loottype", "sword")
        assertThat(itemType.type).isEqualTo(ItemTypes_t.ITEM_TYPE_SWORD)

        parse(parser, itemType, "slottype", "head")
        assertThat(itemType.slotPosition).isEqualTo(48 or 1)

        parse(parser, itemType, "slottype", "right-hand")
        assertThat(itemType.slotPosition).isEqualTo(16)

        parse(parser, itemType, "slottype", "left-hand")
        assertThat(itemType.slotPosition).isEqualTo(32)

        parse(parser, itemType, "slottype", "two-handed")
        assertThat(itemType.slotPosition).isEqualTo(48 or 2048)

        parse(parser, itemType, "slottype", "ammo")
        assertThat(itemType.slotPosition).isEqualTo(48 or 512)
    }

    @Test
    fun `parse weapon ammo shoot effect and hitchance values`() {
        val itemType = ItemType(id = 2400, name = "sword")
        val parser = ItemParse(registry = FakeRegistry())

        parse(parser, itemType, "weapontype", "sword")
        assertThat(itemType.weaponType).isEqualTo(WeaponType_t.WEAPON_SWORD)

        val spellbook = ItemType(id = 2175, name = "spellbook")
        parse(parser, spellbook, "weapontype", "spellbook")
        assertThat(spellbook.weaponType).isEqualTo(WeaponType_t.WEAPON_SHIELD)

        parse(parser, itemType, "ammotype", "arrow")
        parse(parser, itemType, "shoottype", "arrow")
        parse(parser, itemType, "effect", "poison")
        parse(parser, itemType, "hitchance", "110")
        parse(parser, itemType, "maxhitchance", "150")

        assertThat(itemType.ammoType).isEqualTo(2)
        assertThat(itemType.shootType).isEqualTo(3)
        assertThat(itemType.magicEffect).isEqualTo(21)
        assertThat(itemType.hitChance).isEqualTo(100)
        assertThat(itemType.maxHitChance).isEqualTo(100)

        parse(parser, itemType, "hitchance", "-110")
        assertThat(itemType.hitChance).isEqualTo(-100)

        parse(parser, itemType, "hitchance", "150")
        assertThat(itemType.hitChance).isEqualTo(0)

        parse(parser, itemType, "hitchance", "50")
        assertThat(itemType.hitChance).isEqualTo(50)
    }

    @Test
    fun `parse transform attributes and the decay collision reset`() {
        val registry = FakeRegistry()
        val parser = ItemParse(registry = registry)

        val itemType = ItemType(id = 2400, name = "sword", type = ItemTypes_t.ITEM_TYPE_SWORD)
        parser.initParse(
            "transformequipto",
            xmlNode("""<attribute key="transformequipto" value="2401"/>"""),
            XmlAttribute("2401"),
            itemType,
        )
        assertThat(itemType.transformEquipTo).isEqualTo(2401)
        assertThat(registry.getItemType(2401).type).isEqualTo(ItemTypes_t.ITEM_TYPE_SWORD)

        val collision = ItemType(id = 2402, name = "collision", decayTo = 2403)
        parse(parser, collision, "transformequipto", "2403")
        assertThat(collision.decayTo).isEqualTo(0)

        val deEquip = ItemType(id = 2404, name = "de-equip", decayTo = 2405, transformDeEquipTo = 2405)
        parse(parser, deEquip, "transformdeequipto", "2406")
        assertThat(deEquip.transformDeEquipTo).isEqualTo(2406)
        assertThat(deEquip.decayTo).isEqualTo(0)

        parse(parser, itemType, "transformto", "2406")
        parse(parser, itemType, "destroyto", "2407")
        assertThat(itemType.transformToFree).isEqualTo(2406)
        assertThat(itemType.destroyTo).isEqualTo(2407)
    }

    @Test
    fun `parse duration charges and show flags`() {
        val itemType = ItemType(id = 2400, name = "sword")
        val parser = ItemParse(registry = FakeRegistry())

        parse(parser, itemType, "duration", "5000")
        parse(parser, itemType, "stopduration", "true")
        parse(parser, itemType, "showduration", "true")
        parse(parser, itemType, "showcharges", "true")
        parse(parser, itemType, "showattributes", "true")

        assertThat(itemType.decayTime).isEqualTo(5000L)
        assertThat(itemType.stopTime).isTrue()
        assertThat(itemType.showDuration).isTrue()
        assertThat(itemType.showCharges).isTrue()
        assertThat(itemType.showAttributes).isTrue()
    }

    @Test
    fun `parse stack size clamps to 255`() {
        val itemType = ItemType(id = 2148, name = "coin")
        val parser = ItemParse(registry = FakeRegistry())

        parse(parser, itemType, "stacksize", "300")
        assertThat(itemType.stackSize).isEqualTo(255)

        parse(parser, itemType, "stacksize", "50")
        assertThat(itemType.stackSize).isEqualTo(50)
    }

    @Test
    fun `parse beds transforms and bed parts`() {
        val registry = FakeRegistry()
        val parser = ItemParse(registry = registry)

        val femaleSleeper = ItemType(id = 7854, name = "female sleeper")
        registry.types[7854] = femaleSleeper
        val maleSleeper = ItemType(id = 7856, name = "male")
        registry.types[7856] = maleSleeper

        val itemType = ItemType(id = 7855, name = "bed")
        parse(parser, itemType, "partnerdirection", "east")
        assertThat(itemType.bedPartnerDir).isEqualTo(1)

        parse(parser, itemType, "maletransformto", "7856")
        assertThat(itemType.transformToOnUse[PLAYERSEX_MALE]).isEqualTo(7856)
        assertThat(itemType.transformToOnUse[PLAYERSEX_FEMALE]).isEqualTo(7856)
        assertThat(maleSleeper.transformToFree).isEqualTo(7855)

        val female = ItemType(id = 7854, name = "female sleeper 2")
        parse(parser, female, "femaletransformto", "7857")
        assertThat(female.transformToOnUse[PLAYERSEX_FEMALE]).isEqualTo(7857)
        assertThat(female.transformToOnUse[PLAYERSEX_MALE]).isEqualTo(7857)

        parse(parser, itemType, "bedpart", "pillow")
        parse(parser, itemType, "bedpartof", "7857")
        assertThat(itemType.bedPart).isEqualTo(BedItemPart_t.BED_PILLOW_PART)
        assertThat(itemType.bedPartOf).isEqualTo(7857)
    }

    @Test
    fun `parse abilities skills and regeneration`() {
        val itemType = ItemType(id = 2400, name = "sword")
        val parser = ItemParse(registry = FakeRegistry())

        parse(parser, itemType, "skillsword", "10")
        parse(parser, itemType, "criticalhitchance", "5")
        parse(parser, itemType, "lifeleechamount", "7")
        parse(parser, itemType, "manaleechchance", "3")
        parse(parser, itemType, "healthgain", "15")
        parse(parser, itemType, "manaticks", "12000")
        parse(parser, itemType, "manashield", "true")
        parse(parser, itemType, "invisible", "true")
        parse(parser, itemType, "speed", "20")

        val abilities = itemType.getAbilities()
        assertThat(abilities.skills[skills_t.SKILL_SWORD.value]).isEqualTo(10)
        assertThat(abilities.skills[skills_t.SKILL_CRITICAL_HIT_CHANCE.value]).isEqualTo(5)
        assertThat(abilities.skills[skills_t.SKILL_LIFE_LEECH_AMOUNT.value]).isEqualTo(7)
        assertThat(abilities.skills[skills_t.SKILL_MANA_LEECH_CHANCE.value]).isEqualTo(3)
        assertThat(abilities.healthGain).isEqualTo(15)
        assertThat(abilities.manaTicks).isEqualTo(12000)
        assertThat(abilities.regeneration).isTrue()
        assertThat(abilities.manaShield).isTrue()
        assertThat(abilities.invisible).isTrue()
        assertThat(abilities.speed).isEqualTo(20)
    }

    @Test
    fun `parse stats and magic level points`() {
        val itemType = ItemType(id = 2400, name = "sword")
        val parser = ItemParse(registry = FakeRegistry())

        parse(parser, itemType, "maxhitpoints", "50")
        parse(parser, itemType, "maxhitpointspercent", "10")
        parse(parser, itemType, "maxmanapoints", "30")
        parse(parser, itemType, "maxmanapointspercent", "5")
        parse(parser, itemType, "magiclevelpoints", "4")
        parse(parser, itemType, "magiclevelpointspercent", "2")

        val abilities = itemType.getAbilities()
        assertThat(abilities.stats[Stat.STAT_MAXHITPOINTS.value]).isEqualTo(50)
        assertThat(abilities.statsPercent[Stat.STAT_MAXHITPOINTS.value]).isEqualTo(10)
        assertThat(abilities.stats[Stat.STAT_MAXMANAPOINTS.value]).isEqualTo(30)
        assertThat(abilities.statsPercent[Stat.STAT_MAXMANAPOINTS.value]).isEqualTo(5)
        assertThat(abilities.stats[Stat.STAT_MAGICPOINTS.value]).isEqualTo(4)
        assertThat(abilities.statsPercent[Stat.STAT_MAGICPOINTS.value]).isEqualTo(2)
    }

    @Test
    fun `parse absorb percentages accumulate`() {
        val itemType = ItemType(id = 2400, name = "sword")
        val parser = ItemParse(registry = FakeRegistry())

        parse(parser, itemType, "absorbpercentenergy", "10")
        parse(parser, itemType, "absorbpercentfire", "5")
        parse(parser, itemType, "absorbpercentpoison", "3")
        parse(parser, itemType, "absorbpercentearth", "2")
        parse(parser, itemType, "absorbpercentice", "4")
        parse(parser, itemType, "absorbpercentholy", "6")
        parse(parser, itemType, "absorbpercentdeath", "7")
        parse(parser, itemType, "absorbpercentlifedrain", "8")
        parse(parser, itemType, "absorbpercentmanadrain", "9")
        parse(parser, itemType, "absorbpercentdrown", "1")
        parse(parser, itemType, "absorbpercentphysical", "11")
        parse(parser, itemType, "absorbpercenthealing", "12")

        val abilities = itemType.getAbilities()
        assertThat(abilities.absorbPercent[CombatType.ENERGYDAMAGE.value].toInt()).isEqualTo(10)
        assertThat(abilities.absorbPercent[CombatType.FIREDAMAGE.value].toInt()).isEqualTo(5)
        assertThat(abilities.absorbPercent[CombatType.EARTHDAMAGE.value].toInt()).isEqualTo(3 + 2)
        assertThat(abilities.absorbPercent[CombatType.ICEDAMAGE.value].toInt()).isEqualTo(4)
        assertThat(abilities.absorbPercent[CombatType.HOLYDAMAGE.value].toInt()).isEqualTo(6)
        assertThat(abilities.absorbPercent[CombatType.DEATHDAMAGE.value].toInt()).isEqualTo(7)
        assertThat(abilities.absorbPercent[CombatType.LIFEDRAIN.value].toInt()).isEqualTo(8)
        assertThat(abilities.absorbPercent[CombatType.MANADRAIN.value].toInt()).isEqualTo(9)
        assertThat(abilities.absorbPercent[CombatType.DROWNDAMAGE.value].toInt()).isEqualTo(1)
        assertThat(abilities.absorbPercent[CombatType.PHYSICALDAMAGE.value].toInt()).isEqualTo(11)
        assertThat(abilities.absorbPercent[CombatType.HEALING.value].toInt()).isEqualTo(12)
    }

    @Test
    fun `parse absorb all and grouped percentages`() {
        val all = ItemType(id = 1, name = "all")
        parse(ItemParse(registry = FakeRegistry()), all, "absorbpercentall", "5")
        assertThat(all.getAbilities().absorbPercent.all { it.toInt() == 5 }).isTrue()

        val elements = ItemType(id = 2, name = "elements")
        parse(ItemParse(registry = FakeRegistry()), elements, "absorbpercentelements", "3")
        val abilities = elements.getAbilities()
        assertThat(abilities.absorbPercent[CombatType.ENERGYDAMAGE.value].toInt()).isEqualTo(3)
        assertThat(abilities.absorbPercent[CombatType.FIREDAMAGE.value].toInt()).isEqualTo(3)
        assertThat(abilities.absorbPercent[CombatType.EARTHDAMAGE.value].toInt()).isEqualTo(3)
        assertThat(abilities.absorbPercent[CombatType.ICEDAMAGE.value].toInt()).isEqualTo(3)
        assertThat(abilities.absorbPercent[CombatType.HOLYDAMAGE.value].toInt()).isEqualTo(0)
    }

    @Test
    fun `parse field absorb element bond and elemental damage`() {
        val itemType = ItemType(id = 2400, name = "sword")
        val parser = ItemParse(registry = FakeRegistry())

        parse(parser, itemType, "fieldabsorbpercentfire", "15")
        parse(parser, itemType, "elementalbond", "earth")
        parse(parser, itemType, "elementfire", "12")

        val abilities = itemType.getAbilities()
        assertThat(abilities.fieldAbsorbPercent[CombatType.FIREDAMAGE.value].toInt()).isEqualTo(15)
        assertThat(itemType.elementalBond).isEqualTo(CombatType.EARTHDAMAGE)
        assertThat(abilities.elementDamage).isEqualTo(12)
        assertThat(abilities.elementType).isEqualTo(CombatType.FIREDAMAGE)
    }

    @Test
    fun `parse specialized magic level magic shield perfect shot and cleave`() {
        val itemType = ItemType(id = 2400, name = "sword")
        val parser = ItemParse(registry = FakeRegistry())

        parse(parser, itemType, "firemagiclevelpoints", "3")
        parse(parser, itemType, "magicshieldcapacitypercent", "10")
        parse(parser, itemType, "magicshieldcapacityflat", "40")
        parse(parser, itemType, "perfectshotdamage", "15")
        parse(parser, itemType, "perfectshotrange", "3")
        parse(parser, itemType, "cleavepercent", "4")
        parse(parser, itemType, "reflectdamage", "5")
        parse(parser, itemType, "reflectpercentall", "2")

        val abilities = itemType.getAbilities()
        assertThat(abilities.specializedMagicLevel[CombatType.FIREDAMAGE.value]).isEqualTo(3)
        assertThat(abilities.elementType).isEqualTo(CombatType.FIREDAMAGE)
        assertThat(abilities.magicShieldCapacityPercent).isEqualTo(10)
        assertThat(abilities.magicShieldCapacityFlat).isEqualTo(40)
        assertThat(abilities.perfectShotDamage).isEqualTo(15)
        assertThat(abilities.perfectShotRange).isEqualTo(3)
        assertThat(abilities.cleavePercent).isEqualTo(4)
        assertThat(abilities.reflectFlat[CombatType.PHYSICALDAMAGE.value]).isEqualTo(5)
        assertThat(abilities.reflectPercent.all { it.toInt() == 2 }).isTrue()
    }

    @Test
    fun `parse suppress conditions`() {
        val itemType = ItemType(id = 2400, name = "sword")
        val parser = ItemParse(registry = FakeRegistry())

        parse(parser, itemType, "suppressdrunk", "true")
        parse(parser, itemType, "suppressfire", "true")
        parse(parser, itemType, "suppresscurse", "true")
        parse(parser, itemType, "suppresspoison", "false")

        val abilities = itemType.getAbilities()
        assertThat(abilities.conditionSuppressions[ConditionType.CONDITION_DRUNK.value]).isEqualTo(ConditionType.CONDITION_DRUNK.value)
        assertThat(abilities.conditionSuppressions[ConditionType.CONDITION_FIRE.value]).isEqualTo(ConditionType.CONDITION_FIRE.value)
        assertThat(abilities.conditionSuppressions[ConditionType.CONDITION_CURSED.value]).isEqualTo(ConditionType.CONDITION_CURSED.value)
        assertThat(abilities.conditionSuppressions[ConditionType.CONDITION_POISON.value]).isEqualTo(0)
    }

    @Test
    fun `parse field builds the condition damage`() {
        val itemType = ItemType(id = 1487, name = "fire field")
        val parser = ItemParse(registry = FakeRegistry())

        parse(
            parser,
            itemType,
            "field",
            "fire",
            """<attribute key="ticks" value="5000"/>""" +
                """<attribute key="count" value="3"/>""" +
                """<attribute key="start" value="100"/>""" +
                """<attribute key="damage" value="20"/>""",
        )

        assertThat(itemType.combatType).isEqualTo(CombatType.FIREDAMAGE)
        val condition = itemType.conditionDamage
        assertThat(condition).isNotNull
        assertThat(condition!!.type).isEqualTo(ConditionType.CONDITION_FIRE)
        assertThat(condition.getTotalDamage()).isGreaterThan(0)
    }

    @Test
    fun `parse imbuement slots map the child keys`() {
        val itemType = ItemType(id = 2400, name = "sword")
        val parser = ItemParse(registry = FakeRegistry())

        parse(
            parser,
            itemType,
            "imbuementslot",
            "1",
            """<attribute key="elemental damage" value="2"/>""" +
                """<attribute key="life leech" value="5"/>""",
        )

        assertThat(itemType.imbuementSlot).isEqualTo(1)
        assertThat(itemType.imbuementTypes[ImbuementTypes_t.IMBUEMENT_ELEMENTAL_DAMAGE]).isEqualTo(2)
        assertThat(itemType.imbuementTypes[ImbuementTypes_t.IMBUEMENT_LIFE_LEECH]).isEqualTo(3)
    }

    @Test
    fun `parse augments reads the spell name type and value`() {
        val itemType = ItemType(id = 2400, name = "sword")
        val parser = ItemParse(registry = FakeRegistry(), augmentDefaultValue = { 42 })

        parse(
            parser,
            itemType,
            "augments",
            "1",
            """<attribute key="fierce berserk" value="base damage">""" +
                """<attribute key="value" value="4"/>""" +
                """</attribute>""" +
                """<attribute key="fireball" value="increased damage"/>""",
        )

        assertThat(itemType.augments).containsExactly(
            AugmentInfo("fierce berserk", Augment_t.BaseDamage, 4),
            AugmentInfo("fireball", Augment_t.IncreasedDamage, 42),
        )

        val disabled = ItemType(id = 2400, name = "sword")
        parse(parser, disabled, "augments", "0", """<attribute key="fireball" value="increased damage"/>""")
        assertThat(disabled.augments).isEmpty()
    }

    @Test
    fun `parse proficiency validates through the injected table`() {
        val registry = FakeRegistry()
        val parser = ItemParse(registry = registry, proficiencyIds = setOf(11, 12))

        val itemType = ItemType(id = 2400, name = "sword")
        parse(parser, itemType, "proficiency", "11")
        assertThat(itemType.proficiencyId).isEqualTo(11)

        val unknown = ItemType(id = 2401, name = "sword")
        parse(parser, unknown, "proficiency", "99")
        assertThat(unknown.proficiencyId).isEqualTo(0)

        val zero = ItemType(id = 2402, name = "sword")
        parse(parser, zero, "proficiency", "0")
        assertThat(zero.proficiencyId).isEqualTo(0)
    }

    @Test
    fun `parse description applies the gold pouch quickloot config`() {
        val parser = ItemParse(registry = FakeRegistry(), goldPouchQuickLootOnly = true, lootPouchMaxLimit = 100)
        val itemType = ItemType(id = 23721, name = "gold pouch")

        parse(parser, itemType, "description", "original")

        assertThat(itemType.description).isEqualTo("A bag with 100 slots where you can hold your loots.")
        assertThat(itemType.name).isEqualTo("loot pouch")
    }

    @Test
    fun `parse remaining scalar attributes`() {
        val itemType = ItemType(id = 2400, name = "sword")
        val parser = ItemParse(registry = FakeRegistry())

        parse(parser, itemType, "transformonuse", "123")
        parse(parser, itemType, "primarytype", "Sword")
        parse(parser, itemType, "usedbyhouseguests", "true")
        parse(parser, itemType, "allowdistread", "yes")

        assertThat(itemType.transformOnUse).isEqualTo(123)
        assertThat(itemType.primaryType).isEqualTo("sword")
        assertThat(itemType.canBeUsedByGuests).isTrue()
        assertThat(itemType.allowDistRead).isTrue()

        parse(parser, itemType, "allowdistread", "false")
        assertThat(itemType.allowDistRead).isFalse()
        parse(parser, itemType, "allowdistread", "0")
        assertThat(itemType.allowDistRead).isFalse()
    }

    @Test
    fun `parseUnscriptedItems weapon builds and registers a melee weapon`() {
        val registry = FakeRegistry()
        registry.types[2400] = ItemType(id = 2400, name = "magic sword")
        val weapons = Weapons()
        val parser = ItemParse(
            registry = registry,
            weapons = weapons,
            weaponVocationIdProvider = { name -> if (name == "knight") 4 else -1 },
        )

        val itemType = registry.getItemType(2400)
        parse(
            parser,
            itemType,
            "script",
            "weapon",
            """<attribute key="weapontype" value="sword"/>""" +
                """<attribute key="slot" value="two-handed"/>""" +
                """<attribute key="level" value="30"/>""" +
                """<attribute key="vocation" value="knight;true"/>""" +
                """<attribute key="action" value="removecharge"/>""" +
                """<attribute key="breakchance" value="5"/>""" +
                """<attribute key="mana" value="20"/>""" +
                """<attribute key="unproperly" value="true"/>""",
        )

        val weapon = weapons.getWeapon(FakeWeaponItem(id = 2400))
        assertThat(weapon).isInstanceOf(WeaponMelee::class.java)
        assertThat(weapon!!.weaponType).isEqualTo(WeaponType_t.WEAPON_SWORD)
        assertThat(weapon.action).isEqualTo(WeaponAction_t.WEAPONACTION_REMOVECHARGE)
        assertThat(weapon.getBreakChance()).isEqualTo(5)
        assertThat(weapon.getMana()).isEqualTo(20)
        assertThat(weapon.isWieldedUnproperly()).isTrue()
        assertThat(weapon.isFromXML()).isTrue()

        assertThat(registry.getItemType(2400).slotPosition).isEqualTo(2048)
        assertThat(itemType.wieldInfo).isEqualTo(1L)
        assertThat(itemType.minReqLevel).isEqualTo(30L)
        assertThat(itemType.vocationString).isEqualTo("knights")
    }

    @Test
    fun `parseUnscriptedItems weapon builds a wand with damage range`() {
        val registry = FakeRegistry()
        registry.types[8924] = ItemType(id = 8924, name = "wand of vortex")
        val weapons = Weapons()
        val parser = ItemParse(registry = registry, weapons = weapons)

        val itemType = registry.getItemType(8924)
        parse(
            parser,
            itemType,
            "script",
            "weapon",
            """<attribute key="weapontype" value="wand"/>""" +
                """<attribute key="fromdamage" value="10"/>""" +
                """<attribute key="todamage" value="30"/>""" +
                """<attribute key="wandtype" value="death"/>""" +
                """<attribute key="chain" value="1.5"/>""",
        )

        val weapon = weapons.getWeapon(FakeWeaponItem(id = 8924))
        assertThat(weapon).isInstanceOf(WeaponWand::class.java)
        assertThat(weapon!!.getChainSkillValue()).isEqualTo(1.5)
        assertThat(itemType.combatType).isEqualTo(CombatType.DEATHDAMAGE)
        assertThat(itemType.maxHitChance).isEqualTo(20)
        assertThat(weapon.getWeaponDamage(FakeWeaponPlayer(), FakeWeaponTarget(), FakeWeaponItem(id = 8924), maxDamage = true)).isEqualTo(-30)
    }

    @Test
    fun `parseUnscriptedItems moveevent registers through the seam`() {
        val seam = FakeMoveEventSeam()
        val registry = FakeRegistry()
        registry.types[2400] = ItemType(id = 2400, name = "magic sword")
        val parser = ItemParse(registry = registry, moveEvent = seam)

        val itemType = registry.getItemType(2400)
        parse(parser, itemType, "script", "moveevent", """<attribute key="eventtype" value="stepin"/>""")
        assertThat(seam.registrations).hasSize(1)
        assertThat(seam.registrations.first().second).isEqualTo(MoveEvent_t.MOVE_EVENT_STEP_IN)

        val equip = ItemType(id = 2401, name = "armor")
        parse(parser, equip, "script", "moveevent")
        assertThat(seam.registrations.drop(1).map { it.second })
            .containsExactly(MoveEvent_t.MOVE_EVENT_EQUIP, MoveEvent_t.MOVE_EVENT_DEEQUIP)
    }

    private fun parse(parser: ItemParse, itemType: ItemType, key: String, value: String, children: String = "") {
        val node = xmlNode("<attribute key=\"$key\" value=\"$value\">$children</attribute>")
        parser.initParse(key.lowercase(), node, XmlAttribute(value), itemType)
    }

    private class FakeRegistry : ItemParseRegistry {
        val ladders = mutableListOf<Int>()
        val dummys = mutableMapOf<Int, Int>()
        val types = mutableMapOf<Int, ItemType>()

        override fun getItemType(id: Int): ItemType = types.getOrPut(id) { ItemType(id = id, name = "item $id") }

        override fun addLadderId(newId: Int) {
            ladders.add(newId)
        }

        override fun addDummyId(newId: Int, rate: Int) {
            dummys[newId] = rate
        }
    }

    private class FakeMoveEventSeam : ItemParseMoveEventSeam {
        val registrations = mutableListOf<Pair<ItemType, MoveEvent_t>>()

        override fun registerLuaItemEvent(itemType: ItemType, spec: MoveEventSpec): Boolean {
            registrations.add(itemType to spec.eventType)
            return true
        }
    }

    private class FakeWeaponItem(private val id: Int) : org.opentibia.canary.items.weapons.WeaponItem {
        override fun getID(): Int = id

        override fun getAttack(): Int = 0

        override fun getShootRange(): Int = 1

        override fun getWeaponType(): WeaponType_t = WeaponType_t.WEAPON_NONE

        override fun getItemCount(): Int = 1

        override fun getCharges(): Int = 0

        override fun getName(): String = "item $id"

        override fun getHitChance(): Int = 0

        override fun getPerfectShotRange(): Int = 0

        override fun getPerfectShotDamage(): Int = 0
    }

    private class FakeWeaponPlayer : org.opentibia.canary.items.weapons.WeaponPlayer {
        private val proficiency = org.opentibia.canary.systems.proficiency.WeaponProficiency(
            object : org.opentibia.canary.systems.proficiency.WeaponProficiencyPlayer {
                override fun getSkillLevel(skill: Int): Int = 0

                override fun getWeapon(ignoreWeaponProficiency: Boolean): Boolean = false
            },
        )

        override fun weaponProficiency(): org.opentibia.canary.systems.proficiency.WeaponProficiency = proficiency

        override fun getPosition(): org.opentibia.canary.map.Position = org.opentibia.canary.map.Position(100, 100, 7)

        override fun hasFlag(flag: org.opentibia.canary.creatures.PlayerFlags_t): Boolean = false

        override fun getMana(): Int = 0

        override fun getMaxMana(): Int = 0

        override fun getHealth(): Int = 100

        override fun getMaxHealth(): Int = 100

        override fun getSoul(): Int = 0

        override fun isPremium(): Boolean = false

        override fun getVocationId(): Int = 0

        override fun getLevel(): Int = 1

        override fun getMagicLevel(): Int = 0

        override fun getWeaponSkill(item: org.opentibia.canary.items.weapons.WeaponItem?): Int = 0

        override fun getAttackFactor(): Float = 1.0f

        override fun getVocation(): org.opentibia.canary.creatures.vocations.Vocation =
            org.opentibia.canary.creatures.vocations.Vocation(0)

        override fun getSkillLevel(skill: skills_t): Int = 0

        override fun getAddAttackSkill(): Boolean = false

        override fun getLastAttackBlockType(): org.opentibia.canary.combat.BlockType =
            org.opentibia.canary.combat.BlockType.NONE

        override fun getWeapon(ignoreAmmo: Boolean): org.opentibia.canary.items.weapons.WeaponItem? = null
    }

    private class FakeWeaponTarget : org.opentibia.canary.items.weapons.WeaponTarget {
        override fun getPosition(): org.opentibia.canary.map.Position =
            org.opentibia.canary.map.Position(101, 100, 7)

        override fun getPlayer(): Any? = null
    }

    companion object {
        private fun xmlNode(xml: String): XmlNode {
            val document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(ByteArrayInputStream(xml.toByteArray()))
            return XmlNode(document.documentElement as Element)
        }
    }
}
