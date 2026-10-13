package org.opentibia.canary.items.functions

import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.ConditionDamage
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionParam
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.Stat
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.items.Augment_t
import org.opentibia.canary.items.BedItemPart_t
import org.opentibia.canary.items.Fluids_t
import org.opentibia.canary.items.ItemGroup_t
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypes_t
import org.opentibia.canary.items.PLAYERSEX_FEMALE
import org.opentibia.canary.items.PLAYERSEX_MALE
import org.opentibia.canary.items.WeaponAction_t
import org.opentibia.canary.items.weapons.Weapon
import org.opentibia.canary.items.weapons.WeaponDistance
import org.opentibia.canary.items.weapons.WeaponMelee
import org.opentibia.canary.items.weapons.WeaponWand
import org.opentibia.canary.items.weapons.Weapons
import org.opentibia.canary.systems.imbuement.ImbuementTypes_t
import org.w3c.dom.Element
import java.util.Random

/** `MoveEvent_t` (`src/lua/lua_definitions.hpp:77-88`). */
enum class MoveEvent_t(val value: Int) {
    MOVE_EVENT_STEP_IN(0),
    MOVE_EVENT_STEP_OUT(1),
    MOVE_EVENT_EQUIP(2),
    MOVE_EVENT_DEEQUIP(3),
    MOVE_EVENT_ADD_ITEM(4),
    MOVE_EVENT_REMOVE_ITEM(5),
    MOVE_EVENT_ADD_ITEM_ITEMTILE(6),
    MOVE_EVENT_REMOVE_ITEM_ITEMTILE(7),
    MOVE_EVENT_LAST(8),
    MOVE_EVENT_NONE(9),
}

/** `getMoveEventType` (`src/utils/tools.cpp:1179-1197`). */
fun getMoveEventType(name: String): MoveEvent_t = when (name) {
    "stepin" -> MoveEvent_t.MOVE_EVENT_STEP_IN
    "stepout" -> MoveEvent_t.MOVE_EVENT_STEP_OUT
    "equip" -> MoveEvent_t.MOVE_EVENT_EQUIP
    "deequip" -> MoveEvent_t.MOVE_EVENT_DEEQUIP
    "additem" -> MoveEvent_t.MOVE_EVENT_ADD_ITEM
    "removeitem" -> MoveEvent_t.MOVE_EVENT_REMOVE_ITEM
    "additemitemtile" -> MoveEvent_t.MOVE_EVENT_ADD_ITEM_ITEMTILE
    "removeitemitemtile" -> MoveEvent_t.MOVE_EVENT_REMOVE_ITEM_ITEMTILE
    else -> MoveEvent_t.MOVE_EVENT_NONE
}

/**
 * Minimal pugixml `xml_attribute` adapter: only the accessors `item_parse.cpp`
 * uses (`as_string`, `as_bool`, `value`).
 */
class XmlAttribute(private val rawValue: String) {
    fun asString(): String = rawValue

    /**
     * `pugixml::xml_attribute::as_bool`: `"1"`/`"true"` are true, `"0"` is
     * false, every other spelling falls back to the default `false`.
     */
    fun asBool(): Boolean = rawValue == "1" || rawValue == "true"

    fun value(): String = rawValue
}

/**
 * Minimal pugixml `xml_node` adapter: element children and named attributes.
 */
class XmlNode(private val element: Element) {
    /** `pugi::xml_node::children()`, element nodes only. */
    fun children(): List<XmlNode> {
        val nodes = element.childNodes
        val result = mutableListOf<XmlNode>()
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node is Element) {
                result.add(XmlNode(node))
            }
        }
        return result
    }

    /** `pugi::xml_node::attribute(name)`, null when absent. */
    fun attribute(name: String): XmlAttribute? {
        if (!element.hasAttribute(name)) {
            return null
        }
        return XmlAttribute(element.getAttribute(name))
    }
}

/*
 * `pugi::cast<T>` (src/utils/pugicast.hpp:15-79): full-string `from_chars`,
 * `0` for a missing, malformed, trailing-garbage or out-of-range value.
 */
internal fun pugiCastInt8(value: String): Int = pugiCastInt(value, -128, 127).toInt()
internal fun pugiCastInt16(value: String): Int = pugiCastInt(value, -32_768, 32_767).toInt()
internal fun pugiCastInt32(value: String): Int = pugiCastInt(value, Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
internal fun pugiCastUInt8(value: String): Int = pugiCastInt(value, 0, 255).toInt()
internal fun pugiCastUInt16(value: String): Int = pugiCastInt(value, 0, 65_535).toInt()
internal fun pugiCastUInt32(value: String): Long = pugiCastInt(value, 0, 4_294_967_295L)

private fun pugiCastInt(value: String, minimum: Long, maximum: Long): Long {
    if (!value.matches(Regex("-?\\d+"))) {
        return 0
    }
    val parsed = value.toLongOrNull() ?: return 0
    if (parsed < minimum || parsed > maximum) {
        return 0
    }
    return parsed
}

/** `pugi::cast<double>` (pugicast.hpp:67-78): `std::stod` prefix parsing, 0 on failure. */
internal fun pugiCastDouble(value: String): Double {
    val match = Regex("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?").find(value) ?: return 0.0
    if (match.range.first != 0) {
        return 0.0
    }
    return match.value.toDoubleOrNull() ?: 0.0
}

/** `booleanString` (`src/utils/tools.cpp:1125-1133`). */
internal fun booleanString(value: String): Boolean {
    if (value.isEmpty()) {
        return false
    }

    val ch = value.first().lowercaseChar()
    return ch != 'f' && ch != 'n' && ch != '0'
}

/**
 * The `Items` registry methods `item_parse.cpp` calls. The real registry is
 * ported with `src/items/items.cpp`; this seam keeps the parse testable and
 * injectable (AGENTS.md 1.1).
 *
 * `getLootType` ships the exact `Items::getLootType` table
 * (src/items/items.cpp:37-71) as the interface default.
 */
interface ItemParseRegistry {
    /** `Items::getItemType(id)` (items.cpp:392-402). */
    fun getItemType(id: Int): ItemType

    /** `Items::addLadderId` (items.hpp:499-501). */
    fun addLadderId(newId: Int)

    /** `Items::addDummyId` (items.hpp:502-504). */
    fun addDummyId(newId: Int, rate: Int)

    /** `Items::getLootType` (items.cpp:65-71). */
    fun getLootType(strValue: String): ItemTypes_t = lootTypeNames[strValue] ?: ItemTypes_t.ITEM_TYPE_NONE

    companion object {
        private val lootTypeNames: Map<String, ItemTypes_t> = mapOf(
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
    }
}

internal object UnwiredItemParseRegistry : ItemParseRegistry {
    override fun getItemType(id: Int): ItemType = throw NotImplementedError("ItemParseRegistry.getItemType seam is not wired")

    override fun addLadderId(newId: Int) = throw NotImplementedError("ItemParseRegistry.addLadderId seam is not wired")

    override fun addDummyId(newId: Int, rate: Int) = throw NotImplementedError("ItemParseRegistry.addDummyId seam is not wired")
}

/**
 * The `MoveEvent` state `ItemParse::createAndRegisterScript` writes
 * (item_parse.cpp:1076-1177). The Lua/game `MoveEvent` itself stays a seam.
 */
class MoveEventSpec(
    var eventType: MoveEvent_t = MoveEvent_t.MOVE_EVENT_NONE,
    var itemId: Int = 0,
    var slot: Int = 0,
    var requiredLevel: Int = 0,
    var wieldInfo: Int = 0,
    val vocEquipMap: MutableList<String> = mutableListOf(),
    var vocationString: String = "",
)

/**
 * `g_moveEvents().registerLuaItemEvent(moveevent)` (item_parse.cpp:1254).
 * Building and running the Lua move event is scripting-layer behavior and is
 * injected instead (AGENTS.md 1.4).
 */
interface ItemParseMoveEventSeam {
    fun registerLuaItemEvent(itemType: ItemType, spec: MoveEventSpec): Boolean
}

internal object NoopItemParseMoveEventSeam : ItemParseMoveEventSeam {
    override fun registerLuaItemEvent(itemType: ItemType, spec: MoveEventSpec): Boolean = true
}

/**
 * `class ItemParse` (src/items/functions/item/item_parse.hpp:267-353) and its
 * mappings (item_parse.hpp:19-265).
 *
 * `initParse` receives the lowercased attribute key exactly like Canary's
 * `Items::parseItemNode` dispatch (items.cpp:383-389). The item registry,
 * weapon registry, config toggles, augment defaults and proficiency table are
 * constructor-injected (AGENTS.md 1.1); Lua move events and the Lua weapon
 * event stay seams (AGENTS.md 1.4).
 */
class ItemParse(
    private val registry: ItemParseRegistry = UnwiredItemParseRegistry,
    private val weapons: Weapons = Weapons(),
    private val weaponVocationIdProvider: (String) -> Int = { -1 },
    private val weaponRandom: Random = Random(),
    private val goldPouchQuickLootOnly: Boolean = false,
    private val lootPouchMaxLimit: Int = 0,
    private val augmentDefaultValue: (Augment_t) -> Int = { 0 },
    private val proficiencyIds: Set<Int> = emptySet(),
    private val moveEvent: ItemParseMoveEventSeam = NoopItemParseMoveEventSeam,
) {
    private val itemTypeProvider: (Int) -> ItemType? = { registry.getItemType(it) }

    /** `ItemParse::initParse` (item_parse.cpp:21-88). */
    fun initParse(stringValue: String, attributeNode: XmlNode, valueAttribute: XmlAttribute, itemType: ItemType) {
        parseType(stringValue, attributeNode, valueAttribute, itemType)
        parseDescription(stringValue, valueAttribute, itemType)
        parseRuneSpellName(stringValue, valueAttribute, itemType)
        parseWeight(stringValue, valueAttribute, itemType)
        parseShowCount(stringValue, valueAttribute, itemType)
        parseArmor(stringValue, valueAttribute, itemType)
        parseDefense(stringValue, valueAttribute, itemType)
        parseExtraDefense(stringValue, valueAttribute, itemType)
        parseAttack(stringValue, valueAttribute, itemType)
        parseMantra(stringValue, valueAttribute, itemType)
        parseRotateTo(stringValue, valueAttribute, itemType)
        parseWrapContainer(stringValue, valueAttribute, itemType)
        parseWrapableTo(stringValue, valueAttribute, itemType)
        parseMovable(stringValue, valueAttribute, itemType)
        parseBlockProjectTile(stringValue, valueAttribute, itemType)
        parsePickupable(stringValue, valueAttribute, itemType)
        parseFloorChange(stringValue, valueAttribute, itemType)
        parseContainerSize(stringValue, valueAttribute, itemType)
        parseFluidSource(stringValue, valueAttribute, itemType)
        parseWriteables(stringValue, valueAttribute, itemType)
        parseWeaponType(stringValue, valueAttribute, itemType)
        parseSlotType(stringValue, valueAttribute, itemType)
        parseAmmoType(stringValue, valueAttribute, itemType)
        parseShootType(stringValue, valueAttribute, itemType)
        parseMagicEffect(stringValue, valueAttribute, itemType)
        parseLootType(stringValue, valueAttribute, itemType)
        parseRange(stringValue, valueAttribute, itemType)
        parseDecayTo(stringValue, valueAttribute, itemType)
        parseDuration(stringValue, valueAttribute, itemType)
        parseTransform(stringValue, valueAttribute, itemType)
        parseCharges(stringValue, valueAttribute, itemType)
        parseShowAttributes(stringValue, valueAttribute, itemType)
        parseHitChance(stringValue, valueAttribute, itemType)
        parseInvisible(stringValue, valueAttribute, itemType)
        parseSpeed(stringValue, valueAttribute, itemType)
        parseHealthAndMana(stringValue, valueAttribute, itemType)
        parseSkills(stringValue, valueAttribute, itemType)
        parseCriticalHit(stringValue, valueAttribute, itemType)
        parseLifeAndManaLeech(stringValue, valueAttribute, itemType)
        parseMaxHitAndManaPoints(stringValue, valueAttribute, itemType)
        parseMagicLevelPoint(stringValue, valueAttribute, itemType)
        parseFieldAbsorbPercent(stringValue, valueAttribute, itemType)
        parseAbsorbPercent(stringValue, valueAttribute, itemType)
        parseSupressDrunk(stringValue, valueAttribute, itemType)
        parseField(stringValue, attributeNode, valueAttribute, itemType)
        parseReplaceable(stringValue, valueAttribute, itemType)
        parseLevelDoor(stringValue, valueAttribute, itemType)
        parseBeds(stringValue, valueAttribute, itemType)
        parseElement(stringValue, valueAttribute, itemType)
        parseElementalBond(stringValue, valueAttribute, itemType)
        parseWalk(stringValue, valueAttribute, itemType)
        parseAllowDistanceRead(stringValue, valueAttribute, itemType)
        parseImbuement(stringValue, attributeNode, valueAttribute, itemType)
        parseAugment(stringValue, attributeNode, valueAttribute, itemType)
        parseStackSize(stringValue, valueAttribute, itemType)
        parseSpecializedMagicLevelPoint(stringValue, valueAttribute, itemType)
        parseMagicShieldCapacity(stringValue, valueAttribute, itemType)
        parsePerfecShot(stringValue, valueAttribute, itemType)
        parseCleavePercent(stringValue, valueAttribute, itemType)
        parseReflectDamage(stringValue, valueAttribute, itemType)
        parseTransformOnUse(stringValue, valueAttribute, itemType)
        parsePrimaryType(stringValue, valueAttribute, itemType)
        parseProficiency(stringValue, valueAttribute, itemType)
        parseHouseRelated(stringValue, valueAttribute, itemType)
        parseUnscriptedItems(stringValue, attributeNode, valueAttribute, itemType)
    }

    /** `ItemParse::parseDummyRate` (item_parse.cpp:90-108). */
    internal fun parseDummyRate(attributeNode: XmlNode, itemType: ItemType) {
        for (subAttributeNode in attributeNode.children()) {
            val subKeyAttribute = subAttributeNode.attribute("key") ?: continue
            val subValueAttribute = subAttributeNode.attribute("value") ?: continue

            val stringValue = subKeyAttribute.asString().lowercase()
            if (stringValue == "rate") {
                registry.addDummyId(itemType.id, pugiCastUInt16(subValueAttribute.value()))
            }
        }
    }

    /** `ItemParse::parseType` (item_parse.cpp:110-132). */
    internal fun parseType(stringValue: String, attributeNode: XmlNode, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "type") {
            val lowerValue = valueAttribute.asString().lowercase()
            val itemMap = itemTypesMap[lowerValue]
            if (itemMap != null) {
                itemType.type = itemMap
                if (itemType.type == ItemTypes_t.ITEM_TYPE_CONTAINER) {
                    itemType.group = ItemGroup_t.ITEM_GROUP_CONTAINER
                    itemType.isContainer = true
                }
                if (itemType.type == ItemTypes_t.ITEM_TYPE_LADDER) {
                    registry.addLadderId(itemType.id)
                }
                if (itemType.type == ItemTypes_t.ITEM_TYPE_DUMMY) {
                    parseDummyRate(attributeNode, itemType)
                }
            } else if (lowerValue == "magicshieldpotion") {
                itemType.isMagicShieldPotion = true
            }
        }
    }

    /** `ItemParse::parseDescription` (item_parse.cpp:134-143). */
    internal fun parseDescription(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "description") {
            itemType.description = valueAttribute.asString()
            if (goldPouchQuickLootOnly && itemType.id == ITEM_GOLD_POUCH) {
                itemType.description = "A bag with $lootPouchMaxLimit slots where you can hold your loots."
                itemType.name = "loot pouch"
            }
        }
    }

    /** `ItemParse::parseRuneSpellName` (item_parse.cpp:145-149). */
    internal fun parseRuneSpellName(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "runespellname") {
            itemType.runeSpellName = valueAttribute.asString()
        }
    }

    /** `ItemParse::parseWeight` (item_parse.cpp:151-155). */
    internal fun parseWeight(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "weight") {
            itemType.weight = pugiCastInt32(valueAttribute.value()).toDouble()
        }
    }

    /** `ItemParse::parseShowCount` (item_parse.cpp:157-161). */
    internal fun parseShowCount(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "showcount") {
            itemType.showCount = valueAttribute.asBool()
        }
    }

    /** `ItemParse::parseArmor` (item_parse.cpp:163-167). */
    internal fun parseArmor(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "armor") {
            itemType.armor = pugiCastInt32(valueAttribute.value())
        }
    }

    /** `ItemParse::parseDefense` (item_parse.cpp:169-173). */
    internal fun parseDefense(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "defense") {
            itemType.defense = pugiCastInt32(valueAttribute.value())
        }
    }

    /** `ItemParse::parseExtraDefense` (item_parse.cpp:175-179). */
    internal fun parseExtraDefense(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "extradef") {
            itemType.extraDefense = pugiCastInt32(valueAttribute.value())
        }
    }

    /** `ItemParse::parseAttack` (item_parse.cpp:181-185). */
    internal fun parseAttack(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "attack") {
            itemType.attack = pugiCastInt32(valueAttribute.value())
        }
    }

    /** `ItemParse::parseMantra` (item_parse.cpp:187-191). */
    internal fun parseMantra(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "mantra") {
            itemType.mantra = pugiCastInt32(valueAttribute.value())
        }
    }

    /** `ItemParse::parseRotateTo` (item_parse.cpp:193-197). */
    internal fun parseRotateTo(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "rotateto") {
            itemType.rotateTo = pugiCastInt32(valueAttribute.value())
        }
    }

    /** `ItemParse::parseWrapContainer` (item_parse.cpp:199-203). */
    internal fun parseWrapContainer(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "wrapcontainer") {
            itemType.wrapContainer = valueAttribute.asBool()
        }
    }

    /** `ItemParse::parseWrapableTo` (item_parse.cpp:205-210). */
    internal fun parseWrapableTo(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "wrapableto") {
            itemType.wrapableTo = pugiCastInt32(valueAttribute.value())
            itemType.wrapable = true
        }
    }

    /** `ItemParse::parseMovable` (item_parse.cpp:212-216). */
    internal fun parseMovable(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "movable") {
            itemType.isMovable = valueAttribute.asBool()
        }
    }

    /** `ItemParse::parseBlockProjectTile` (item_parse.cpp:218-222). */
    internal fun parseBlockProjectTile(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "blockprojectile") {
            itemType.blockProjectile = valueAttribute.asBool()
        }
    }

    /** `ItemParse::parsePickupable` (item_parse.cpp:224-228). */
    internal fun parsePickupable(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "allowpickupable" || stringValue == "pickupable") {
            itemType.isPickupable = valueAttribute.asBool()
        }
    }

    /** `ItemParse::parseFloorChange` (item_parse.cpp:230-240). */
    internal fun parseFloorChange(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "floorchange") {
            val lowerValue = valueAttribute.asString().lowercase()
            val itemMap = tileStatesMap[lowerValue]
            if (itemMap != null) {
                itemType.floorChange = itemMap.value
            }
        }
    }

    /** `ItemParse::parseContainerSize` (item_parse.cpp:242-246). */
    internal fun parseContainerSize(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "containersize") {
            itemType.maxItems = pugiCastUInt16(valueAttribute.value())
        }
    }

    /** `ItemParse::parseFluidSource` (item_parse.cpp:248-258). */
    internal fun parseFluidSource(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "fluidsource") {
            val lowerValue = valueAttribute.asString().lowercase()
            val itemMap = fluidTypesMap[lowerValue]
            if (itemMap != null) {
                itemType.fluidSource = itemMap.value
            }
        }
    }

    /** `ItemParse::parseWriteables` (item_parse.cpp:260-271). */
    internal fun parseWriteables(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "readable") {
            itemType.canReadText = valueAttribute.asBool()
        } else if (stringValue == "writeable") {
            itemType.canWriteText = valueAttribute.asBool()
            itemType.canReadText = itemType.canWriteText
        } else if (stringValue == "maxtextlen") {
            itemType.maxTextLen = pugiCastUInt16(valueAttribute.value())
        } else if (stringValue == "writeonceitemid") {
            itemType.writeOnceItemId = pugiCastUInt16(valueAttribute.value())
        }
    }

    /** `ItemParse::parseWeaponType` (item_parse.cpp:273-286). */
    internal fun parseWeaponType(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "weapontype") {
            val lowerValue = valueAttribute.asString().lowercase()
            val itemMap = weaponTypesMap[lowerValue]
            if (itemMap != null) {
                if (stringValue == "spellbook") {
                    itemType.spellbook = true
                }
                itemType.weaponType = itemMap
            }
        }
    }

    /** `ItemParse::parseSlotType` (item_parse.cpp:288-320). */
    internal fun parseSlotType(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "slottype") {
            itemType.slotPosition = SLOTP_HAND
            val subStringValue = valueAttribute.asString().lowercase()
            when (subStringValue) {
                "head" -> itemType.slotPosition = itemType.slotPosition or SLOTP_HEAD
                "body" -> itemType.slotPosition = itemType.slotPosition or SLOTP_ARMOR
                "legs" -> itemType.slotPosition = itemType.slotPosition or SLOTP_LEGS
                "feet" -> itemType.slotPosition = itemType.slotPosition or SLOTP_FEET
                "backpack" -> itemType.slotPosition = itemType.slotPosition or SLOTP_BACKPACK
                "two-handed", "dualwielding" -> itemType.slotPosition = itemType.slotPosition or SLOTP_TWO_HAND
                "right-hand" -> itemType.slotPosition = itemType.slotPosition and SLOTP_LEFT.inv()
                "left-hand" -> itemType.slotPosition = itemType.slotPosition and SLOTP_RIGHT.inv()
                "necklace" -> itemType.slotPosition = itemType.slotPosition or SLOTP_NECKLACE
                "ring" -> itemType.slotPosition = itemType.slotPosition or SLOTP_RING
                "ammo" -> itemType.slotPosition = itemType.slotPosition or SLOTP_AMMO
                "hand" -> itemType.slotPosition = itemType.slotPosition or SLOTP_HAND
            }
        }
    }

    /** `ItemParse::parseAmmoType` (item_parse.cpp:322-329). */
    internal fun parseAmmoType(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "ammotype") {
            itemType.ammoType = getAmmoType(valueAttribute.asString().lowercase())
        }
    }

    /** `ItemParse::parseShootType` (item_parse.cpp:331-340). */
    internal fun parseShootType(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "shoottype") {
            val shoot = getShootType(valueAttribute.asString().lowercase())
            if (shoot != CONST_ANI_NONE) {
                itemType.shootType = shoot
            }
        }
    }

    /** `ItemParse::parseMagicEffect` (item_parse.cpp:342-351). */
    internal fun parseMagicEffect(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "effect") {
            val effect = getMagicEffect(valueAttribute.asString().lowercase())
            if (effect != CONST_ME_NONE) {
                itemType.magicEffect = effect
            }
        }
    }

    /** `ItemParse::parseLootType` (item_parse.cpp:353-357). */
    internal fun parseLootType(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "loottype") {
            itemType.type = registry.getLootType(valueAttribute.asString())
        }
    }

    /** `ItemParse::parseRange` (item_parse.cpp:359-363). */
    internal fun parseRange(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "range") {
            itemType.shootRange = pugiCastUInt8(valueAttribute.value())
        }
    }

    /** `ItemParse::parseDecayTo` (item_parse.cpp:365-369). */
    internal fun parseDecayTo(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "decayto") {
            itemType.decayTo = pugiCastUInt16(valueAttribute.value())
        }
    }

    /** `ItemParse::parseDuration` (item_parse.cpp:371-379). */
    internal fun parseDuration(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "duration") {
            itemType.decayTime = pugiCastUInt32(valueAttribute.value())
        } else if (stringValue == "stopduration") {
            itemType.stopTime = valueAttribute.asBool()
        } else if (stringValue == "showduration") {
            itemType.showDuration = valueAttribute.asBool()
        }
    }

    /** `ItemParse::parseTransform` (item_parse.cpp:381-404). */
    internal fun parseTransform(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "transformequipto") {
            itemType.transformEquipTo = pugiCastUInt16(valueAttribute.value())
            if (itemType.transformEquipTo == itemType.decayTo) {
                itemType.decayTo = 0
            }
            val transform = registry.getItemType(itemType.transformEquipTo)
            if (transform.type == ItemTypes_t.ITEM_TYPE_NONE) {
                transform.type = itemType.type
            }
        } else if (stringValue == "transformdeequipto") {
            if (itemType.transformDeEquipTo == itemType.decayTo) {
                itemType.decayTo = 0
            }

            itemType.transformDeEquipTo = pugiCastUInt16(valueAttribute.value())
        } else if (stringValue == "transformto") {
            itemType.transformToFree = pugiCastUInt16(valueAttribute.value())
        } else if (stringValue == "destroyto") {
            itemType.destroyTo = pugiCastUInt16(valueAttribute.value())
        }
    }

    /** `ItemParse::parseCharges` (item_parse.cpp:406-412). */
    internal fun parseCharges(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "charges") {
            itemType.charges = pugiCastUInt32(valueAttribute.value()).toInt()
        } else if (stringValue == "showcharges") {
            itemType.showCharges = valueAttribute.asBool()
        }
    }

    /** `ItemParse::parseShowAttributes` (item_parse.cpp:414-418). */
    internal fun parseShowAttributes(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "showattributes") {
            itemType.showAttributes = valueAttribute.asBool()
        }
    }

    /** `ItemParse::parseHitChance` (item_parse.cpp:420-426). */
    internal fun parseHitChance(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "hitchance") {
            itemType.hitChance = minOf(100, maxOf(-100, pugiCastInt8(valueAttribute.value())))
        } else if (stringValue == "maxhitchance") {
            itemType.maxHitChance = minOf(100, pugiCastUInt32(valueAttribute.value()).toInt())
        }
    }

    /** `ItemParse::parseInvisible` (item_parse.cpp:428-432). */
    internal fun parseInvisible(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "invisible") {
            itemType.getAbilities().invisible = valueAttribute.asBool()
        }
    }

    /** `ItemParse::parseSpeed` (item_parse.cpp:434-438). */
    internal fun parseSpeed(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "speed") {
            itemType.getAbilities().speed = pugiCastInt32(valueAttribute.value())
        }
    }

    /** `ItemParse::parseHealthAndMana` (item_parse.cpp:440-460). */
    internal fun parseHealthAndMana(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        val abilities = itemType.getAbilities()
        when (stringValue) {
            "healthgain" -> {
                abilities.regeneration = true
                abilities.healthGain = pugiCastUInt32(valueAttribute.value()).toInt()
            }

            "healthticks" -> {
                abilities.regeneration = true
                abilities.healthTicks = pugiCastUInt32(valueAttribute.value()).toInt()
            }

            "managain" -> {
                abilities.regeneration = true
                abilities.manaGain = pugiCastUInt32(valueAttribute.value()).toInt()
            }

            "manaticks" -> {
                abilities.regeneration = true
                abilities.manaTicks = pugiCastUInt32(valueAttribute.value()).toInt()
            }

            "manashield" -> abilities.manaShield = valueAttribute.asBool()
        }
    }

    /** `ItemParse::parseSkills` (item_parse.cpp:462-478). */
    internal fun parseSkills(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        val abilities = itemType.getAbilities()
        val skill = when (stringValue) {
            "skillsword" -> skills_t.SKILL_SWORD
            "skillaxe" -> skills_t.SKILL_AXE
            "skillclub" -> skills_t.SKILL_CLUB
            "skilldist" -> skills_t.SKILL_DISTANCE
            "skillfish" -> skills_t.SKILL_FISHING
            "skillshield" -> skills_t.SKILL_SHIELD
            "skillfist" -> skills_t.SKILL_FIST
            else -> null
        }

        if (skill != null) {
            abilities.skills[skill.value] = pugiCastInt32(valueAttribute.value())
        }
    }

    /** `ItemParse::parseCriticalHit` (item_parse.cpp:480-486). */
    internal fun parseCriticalHit(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        val abilities = itemType.getAbilities()
        if (stringValue == "criticalhitchance") {
            abilities.skills[skills_t.SKILL_CRITICAL_HIT_CHANCE.value] = pugiCastInt32(valueAttribute.value())
        } else if (stringValue == "criticalhitdamage") {
            abilities.skills[skills_t.SKILL_CRITICAL_HIT_DAMAGE.value] = pugiCastInt32(valueAttribute.value())
        }
    }

    /** `ItemParse::parseLifeAndManaLeech` (item_parse.cpp:488-498). */
    internal fun parseLifeAndManaLeech(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        val abilities = itemType.getAbilities()
        val skill = when (stringValue) {
            "lifeleechchance" -> skills_t.SKILL_LIFE_LEECH_CHANCE
            "lifeleechamount" -> skills_t.SKILL_LIFE_LEECH_AMOUNT
            "manaleechchance" -> skills_t.SKILL_MANA_LEECH_CHANCE
            "manaleechamount" -> skills_t.SKILL_MANA_LEECH_AMOUNT
            else -> null
        }

        if (skill != null) {
            abilities.skills[skill.value] = pugiCastInt32(valueAttribute.value())
        }
    }

    /** `ItemParse::parseMaxHitAndManaPoints` (item_parse.cpp:500-510). */
    internal fun parseMaxHitAndManaPoints(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        val abilities = itemType.getAbilities()
        when (stringValue) {
            "maxhitpoints" -> abilities.stats[Stat.STAT_MAXHITPOINTS.value] = pugiCastInt32(valueAttribute.value())
            "maxhitpointspercent" -> abilities.statsPercent[Stat.STAT_MAXHITPOINTS.value] = pugiCastInt32(valueAttribute.value())
            "maxmanapoints" -> abilities.stats[Stat.STAT_MAXMANAPOINTS.value] = pugiCastInt32(valueAttribute.value())
            "maxmanapointspercent" -> abilities.statsPercent[Stat.STAT_MAXMANAPOINTS.value] = pugiCastInt32(valueAttribute.value())
        }
    }

    /** `ItemParse::parseMagicLevelPoint` (item_parse.cpp:512-518). */
    internal fun parseMagicLevelPoint(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        val abilities = itemType.getAbilities()
        if (stringValue == "magiclevelpoints" || stringValue == "magicpoints") {
            abilities.stats[Stat.STAT_MAGICPOINTS.value] = pugiCastInt32(valueAttribute.value())
        } else if (stringValue == "magiclevelpointspercent") {
            abilities.statsPercent[Stat.STAT_MAGICPOINTS.value] = pugiCastInt32(valueAttribute.value())
        }
    }

    /** `ItemParse::parseFieldAbsorbPercent` (item_parse.cpp:520-528). */
    internal fun parseFieldAbsorbPercent(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        val abilities = itemType.getAbilities()
        val combatType = when (stringValue) {
            "fieldabsorbpercentenergy" -> CombatType.ENERGYDAMAGE
            "fieldabsorbpercentfire" -> CombatType.FIREDAMAGE
            "fieldabsorbpercentpoison", "fieldabsorbpercentearth" -> CombatType.EARTHDAMAGE
            else -> null
        }

        if (combatType != null) {
            val index = combatType.value
            abilities.fieldAbsorbPercent[index] =
                (abilities.fieldAbsorbPercent[index] + pugiCastInt16(valueAttribute.value())).toShort()
        }
    }

    /** `ItemParse::parseAbsorbPercent` (item_parse.cpp:530-576). */
    internal fun parseAbsorbPercent(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        val abilities = itemType.getAbilities()
        when (stringValue) {
            "absorbpercentall" -> {
                val value = pugiCastInt16(valueAttribute.value()).toShort()
                for (index in abilities.absorbPercent.indices) {
                    abilities.absorbPercent[index] = (abilities.absorbPercent[index] + value).toShort()
                }
            }

            "absorbpercentelements" -> {
                val value = pugiCastInt16(valueAttribute.value()).toShort()
                addAbsorb(abilities, CombatType.ENERGYDAMAGE, value)
                addAbsorb(abilities, CombatType.FIREDAMAGE, value)
                addAbsorb(abilities, CombatType.EARTHDAMAGE, value)
                addAbsorb(abilities, CombatType.ICEDAMAGE, value)
            }

            "absorbpercentmagic" -> {
                val value = pugiCastInt16(valueAttribute.value()).toShort()
                addAbsorb(abilities, CombatType.ENERGYDAMAGE, value)
                addAbsorb(abilities, CombatType.FIREDAMAGE, value)
                addAbsorb(abilities, CombatType.EARTHDAMAGE, value)
                addAbsorb(abilities, CombatType.ICEDAMAGE, value)
                addAbsorb(abilities, CombatType.HOLYDAMAGE, value)
                addAbsorb(abilities, CombatType.DEATHDAMAGE, value)
            }

            "absorbpercentenergy" -> addAbsorb(abilities, CombatType.ENERGYDAMAGE, pugiCastInt16(valueAttribute.value()).toShort())
            "absorbpercentfire" -> addAbsorb(abilities, CombatType.FIREDAMAGE, pugiCastInt16(valueAttribute.value()).toShort())
            "absorbpercentpoison", "absorbpercentearth" -> addAbsorb(abilities, CombatType.EARTHDAMAGE, pugiCastInt16(valueAttribute.value()).toShort())
            "absorbpercentice" -> addAbsorb(abilities, CombatType.ICEDAMAGE, pugiCastInt16(valueAttribute.value()).toShort())
            "absorbpercentholy" -> addAbsorb(abilities, CombatType.HOLYDAMAGE, pugiCastInt16(valueAttribute.value()).toShort())
            "absorbpercentdeath" -> addAbsorb(abilities, CombatType.DEATHDAMAGE, pugiCastInt16(valueAttribute.value()).toShort())
            "absorbpercentlifedrain" -> addAbsorb(abilities, CombatType.LIFEDRAIN, pugiCastInt16(valueAttribute.value()).toShort())
            "absorbpercentmanadrain" -> addAbsorb(abilities, CombatType.MANADRAIN, pugiCastInt16(valueAttribute.value()).toShort())
            "absorbpercentdrown" -> addAbsorb(abilities, CombatType.DROWNDAMAGE, pugiCastInt16(valueAttribute.value()).toShort())
            "absorbpercentphysical" -> addAbsorb(abilities, CombatType.PHYSICALDAMAGE, pugiCastInt16(valueAttribute.value()).toShort())
            "absorbpercenthealing" -> addAbsorb(abilities, CombatType.HEALING, pugiCastInt16(valueAttribute.value()).toShort())
        }
    }

    private fun addAbsorb(abilities: org.opentibia.canary.items.Abilities, combatType: CombatType, value: Short) {
        val index = combatType.value
        abilities.absorbPercent[index] = (abilities.absorbPercent[index] + value).toShort()
    }

    /** `ItemParse::parseSupressDrunk` (item_parse.cpp:578-603). */
    internal fun parseSupressDrunk(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (valueAttribute.asBool()) {
            val conditionType = when (stringValue) {
                "suppressdrunk" -> ConditionType.CONDITION_DRUNK
                "suppressenergy" -> ConditionType.CONDITION_ENERGY
                "suppressfire" -> ConditionType.CONDITION_FIRE
                "suppresspoison" -> ConditionType.CONDITION_POISON
                "suppressdrown" -> ConditionType.CONDITION_DROWN
                "suppressphysical" -> ConditionType.CONDITION_BLEEDING
                "suppressfreeze" -> ConditionType.CONDITION_FREEZING
                "suppressdazzle" -> ConditionType.CONDITION_DAZZLED
                "suppresscurse" -> ConditionType.CONDITION_CURSED
                else -> ConditionType.CONDITION_NONE
            }

            itemType.getAbilities().conditionSuppressions[conditionType.value] = conditionType.value
        }
    }

    /** `ItemParse::parseFieldConditions` (item_parse.cpp:605-628). */
    internal fun parseFieldConditions(valueAttribute: XmlAttribute): Pair<ConditionId, ConditionType> {
        val lowerStringValue = valueAttribute.asString().lowercase()
        val conditionId = ConditionId.CONDITIONID_COMBAT
        return when (lowerStringValue) {
            "fire" -> conditionId to ConditionType.CONDITION_FIRE
            "energy" -> conditionId to ConditionType.CONDITION_ENERGY
            "poison" -> conditionId to ConditionType.CONDITION_POISON
            "drown" -> conditionId to ConditionType.CONDITION_DROWN
            "physical" -> conditionId to ConditionType.CONDITION_BLEEDING
            else -> ConditionId.CONDITIONID_DEFAULT to ConditionType.CONDITION_NONE
        }
    }

    /** `ItemParse::parseFieldCombatType` (item_parse.cpp:630-648). */
    internal fun parseFieldCombatType(valueAttribute: XmlAttribute): CombatType =
        when (valueAttribute.asString().lowercase()) {
            "fire" -> CombatType.FIREDAMAGE
            "energy" -> CombatType.ENERGYDAMAGE
            "poison" -> CombatType.EARTHDAMAGE
            "drown" -> CombatType.DROWNDAMAGE
            "physical" -> CombatType.PHYSICALDAMAGE
            "agony" -> CombatType.AGONYDAMAGE
            else -> CombatType.NONE
        }

    /** `ItemParse::parseFieldCombatDamage` (item_parse.cpp:650-689). */
    internal fun parseFieldCombatDamage(conditionDamage: ConditionDamage, attributeNode: XmlNode) {
        var combatTicks = 0
        var combatDamage = 0
        var combatStart = 0
        var combatCount = 1

        for (subAttributeNode in attributeNode.children()) {
            val subKeyAttribute = subAttributeNode.attribute("key") ?: continue
            val subValueAttribute = subAttributeNode.attribute("value") ?: continue

            when (subKeyAttribute.asString().lowercase()) {
                "ticks" -> combatTicks = pugiCastUInt32(subValueAttribute.value()).toInt()
                "count" -> combatCount = maxOf(1, pugiCastInt32(subValueAttribute.value()))
                "start" -> combatStart = maxOf(0, pugiCastInt32(subValueAttribute.value()))
                "damage" -> {
                    combatDamage = -pugiCastInt32(subValueAttribute.value())
                    if (combatStart == 0) {
                        conditionDamage.addDamage(combatCount, combatTicks, combatDamage)
                    }

                    for (damageValue in ConditionDamage.generateDamageList(combatDamage, combatStart)) {
                        conditionDamage.addDamage(1, combatTicks, -damageValue)
                    }

                    combatStart = 0
                }
            }
        }
    }

    /** `ItemParse::parseField` (item_parse.cpp:691-712). */
    internal fun parseField(stringValue: String, attributeNode: XmlNode, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "field") {
            val combatType = parseFieldCombatType(valueAttribute)
            val (conditionId, conditionType) = parseFieldConditions(valueAttribute)
            if (combatType != CombatType.NONE) {
                val conditionDamage = ConditionDamage(conditionId, conditionType)

                itemType.combatType = combatType
                itemType.conditionDamage = conditionDamage

                parseFieldCombatDamage(conditionDamage, attributeNode)

                conditionDamage.setParam(ConditionParam.CONDITION_PARAM_FIELD, 1)

                if (conditionDamage.getTotalDamage() > 0) {
                    conditionDamage.setParam(ConditionParam.CONDITION_PARAM_FORCEUPDATE, 1)
                }
            }
        }
    }

    /** `ItemParse::parseReplaceable` (item_parse.cpp:714-718). */
    internal fun parseReplaceable(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "replaceable") {
            itemType.replaceable = valueAttribute.asBool()
        }
    }

    /** `ItemParse::parseLevelDoor` (item_parse.cpp:720-724). */
    internal fun parseLevelDoor(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "leveldoor") {
            itemType.levelDoor = pugiCastUInt32(valueAttribute.value())
        }
    }

    /** `ItemParse::parseBeds` (item_parse.cpp:726-762). */
    internal fun parseBeds(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "partnerdirection") {
            itemType.bedPartnerDir = getDirection(valueAttribute.asString()).byteValue.toInt()
        }

        if (stringValue == "maletransformto") {
            val valueMale = pugiCastUInt16(valueAttribute.value())
            val other = registry.getItemType(valueMale)
            itemType.transformToOnUse[PLAYERSEX_MALE] = valueMale
            if (other.transformToFree == 0) {
                other.transformToFree = itemType.id
            }

            if (itemType.transformToOnUse[PLAYERSEX_FEMALE] == 0) {
                itemType.transformToOnUse[PLAYERSEX_FEMALE] = valueMale
            }
        } else if (stringValue == "femaletransformto") {
            val valueFemale = pugiCastUInt16(valueAttribute.value())
            val other = registry.getItemType(valueFemale)

            itemType.transformToOnUse[PLAYERSEX_FEMALE] = valueFemale

            if (other.transformToFree == 0) {
                other.transformToFree = itemType.id
            }

            if (itemType.transformToOnUse[PLAYERSEX_MALE] == 0) {
                itemType.transformToOnUse[PLAYERSEX_MALE] = valueFemale
            }
        }

        if (stringValue == "bedpart") {
            itemType.bedPart = getBedPart(valueAttribute.asString())
        } else if (stringValue == "bedpartof") {
            itemType.bedPartOf = pugiCastUInt16(valueAttribute.value())
        }
    }

    /** `ItemParse::parseElementalBond` (item_parse.cpp:764-778). */
    internal fun parseElementalBond(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "elementalbond") {
            when (valueAttribute.asString()) {
                "energy" -> itemType.elementalBond = CombatType.ENERGYDAMAGE
                "earth" -> itemType.elementalBond = CombatType.EARTHDAMAGE
                "physical" -> itemType.elementalBond = CombatType.PHYSICALDAMAGE
            }
        }
    }

    /** `ItemParse::parseElement` (item_parse.cpp:780-806). */
    internal fun parseElement(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        val abilities = itemType.getAbilities()
        val elementType = when (stringValue) {
            "elementice" -> CombatType.ICEDAMAGE
            "elementearth" -> CombatType.EARTHDAMAGE
            "elementfire" -> CombatType.FIREDAMAGE
            "elementenergy" -> CombatType.ENERGYDAMAGE
            "elementdeath" -> CombatType.DEATHDAMAGE
            "elementholy" -> CombatType.HOLYDAMAGE
            else -> null
        }

        if (elementType != null) {
            abilities.elementDamage = pugiCastUInt16(valueAttribute.value())
            abilities.elementType = elementType
        }
    }

    /** `ItemParse::parseWalk` (item_parse.cpp:808-814). */
    internal fun parseWalk(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "walkstack") {
            itemType.walkStack = valueAttribute.asBool()
        } else if (stringValue == "blocking") {
            itemType.blockSolid = valueAttribute.asBool()
        }
    }

    /** `ItemParse::parseAllowDistanceRead` (item_parse.cpp:816-820). */
    internal fun parseAllowDistanceRead(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "allowdistread") {
            itemType.allowDistRead = booleanString(valueAttribute.asString())
        }
    }

    /** `ItemParse::parseImbuement` (item_parse.cpp:822-850). */
    internal fun parseImbuement(stringValue: String, attributeNode: XmlNode, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue != "imbuementslot") {
            return
        }
        itemType.imbuementSlot = pugiCastUInt8(valueAttribute.value())

        for (subAttributeNode in attributeNode.children()) {
            val subKeyAttribute = subAttributeNode.attribute("key") ?: continue
            val subValueAttribute = subAttributeNode.attribute("value") ?: continue

            val imbuementType = imbuementsTypeMap[subKeyAttribute.asString().lowercase()] ?: continue
            if (imbuementType != ImbuementTypes_t.IMBUEMENT_NONE) {
                itemType.setImbuementType(imbuementType, pugiCastUInt16(subValueAttribute.value()))
            }
        }
    }

    /** `ItemParse::parseAugment` (item_parse.cpp:852-902). */
    internal fun parseAugment(stringValue: String, attributeNode: XmlNode, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue != "augments") {
            return
        }

        if (!valueAttribute.asBool()) {
            return
        }

        for (subAttributeNode in attributeNode.children()) {
            val subKeyAttribute = subAttributeNode.attribute("key") ?: continue
            val subValueAttribute = subAttributeNode.attribute("value") ?: continue

            val augmentType = Augment_t.entries.firstOrNull { it.name == toPascalCase(subValueAttribute.asString()) } ?: continue
            var augmentValue = 0
            val hasValueDescription = augmentType in augmentWithoutValueDescription

            if (hasValueDescription) {
                augmentValue = augmentDefaultValue(augmentType)
            }

            val augmentName = subKeyAttribute.asString().lowercase()
            val augmentValueAttributeNode = subAttributeNode.children()
            if (augmentValueAttributeNode.isNotEmpty()) {
                val augmentValueAttribute = augmentValueAttributeNode.first().attribute("value")
                if (augmentValueAttribute != null) {
                    augmentValue = pugiCastInt32(augmentValueAttribute.value())
                }
            }

            if (augmentType != Augment_t.None) {
                itemType.addAugment(augmentName, augmentType, augmentValue)
            }
        }
    }

    /** `ItemParse::parseStackSize` (item_parse.cpp:904-913). */
    internal fun parseStackSize(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "stacksize") {
            var stackSize = pugiCastUInt16(valueAttribute.value())
            if (stackSize > 255) {
                stackSize = 255
            }
            itemType.stackSize = stackSize
        }
    }

    /** `ItemParse::parseSpecializedMagicLevelPoint` (item_parse.cpp:915-942). */
    internal fun parseSpecializedMagicLevelPoint(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        val abilities = itemType.getAbilities()
        val combatType = when (stringValue) {
            "deathmagiclevelpoints" -> CombatType.DEATHDAMAGE
            "energymagiclevelpoints" -> CombatType.ENERGYDAMAGE
            "earthmagiclevelpoints" -> CombatType.EARTHDAMAGE
            "firemagiclevelpoints" -> CombatType.FIREDAMAGE
            "healingmagiclevelpoints" -> CombatType.HEALING
            "holymagiclevelpoints" -> CombatType.HOLYDAMAGE
            "icemagiclevelpoints" -> CombatType.ICEDAMAGE
            "physicalmagiclevelpoints" -> CombatType.PHYSICALDAMAGE
            else -> null
        }

        if (combatType != null) {
            abilities.specializedMagicLevel[combatType.value] += pugiCastInt32(valueAttribute.value())
            abilities.elementType = combatType
        }
    }

    /** `ItemParse::parseMagicShieldCapacity` (item_parse.cpp:944-951). */
    internal fun parseMagicShieldCapacity(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        val abilities = itemType.getAbilities()
        if (stringValue == "magicshieldcapacitypercent") {
            abilities.magicShieldCapacityPercent += pugiCastInt32(valueAttribute.value())
        } else if (stringValue == "magicshieldcapacityflat") {
            abilities.magicShieldCapacityFlat += pugiCastInt32(valueAttribute.value())
        }
    }

    /** `ItemParse::parsePerfecShot` (item_parse.cpp:953-960). */
    internal fun parsePerfecShot(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        val abilities = itemType.getAbilities()
        if (stringValue == "perfectshotdamage") {
            abilities.perfectShotDamage = pugiCastInt32(valueAttribute.value())
        } else if (stringValue == "perfectshotrange") {
            abilities.perfectShotRange = pugiCastInt16(valueAttribute.value())
        }
    }

    /** `ItemParse::parseCleavePercent` (item_parse.cpp:962-967). */
    internal fun parseCleavePercent(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "cleavepercent") {
            itemType.getAbilities().cleavePercent += pugiCastInt32(valueAttribute.value())
        }
    }

    /** `ItemParse::parseReflectDamage` (item_parse.cpp:969-979). */
    internal fun parseReflectDamage(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        val abilities = itemType.getAbilities()
        if (stringValue == "reflectdamage") {
            val index = CombatType.PHYSICALDAMAGE.value
            abilities.reflectFlat[index] += pugiCastInt32(valueAttribute.value())
        } else if (stringValue == "reflectpercentall") {
            val value = pugiCastInt32(valueAttribute.value()).toShort()
            for (index in abilities.reflectPercent.indices) {
                abilities.reflectPercent[index] = (abilities.reflectPercent[index] + value).toShort()
            }
        }
    }

    /** `ItemParse::parseTransformOnUse` (item_parse.cpp:981-985). */
    internal fun parseTransformOnUse(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "transformonuse") {
            itemType.transformOnUse = pugiCastUInt16(valueAttribute.value())
        }
    }

    /** `ItemParse::parsePrimaryType` (item_parse.cpp:987-991). */
    internal fun parsePrimaryType(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "primarytype") {
            itemType.primaryType = valueAttribute.asString().lowercase()
        }
    }

    /** `ItemParse::parseProficiency` (item_parse.cpp:993-1011). */
    internal fun parseProficiency(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue != "proficiency") {
            return
        }

        val proficiencyId = pugiCastUInt16(valueAttribute.value())
        if (proficiencyId == 0) {
            return
        }

        if (proficiencyId !in proficiencyIds) {
            return
        }

        itemType.proficiencyId = proficiencyId
    }

    /** `ItemParse::parseHouseRelated` (item_parse.cpp:1013-1018). */
    internal fun parseHouseRelated(stringValue: String, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue == "usedbyhouseguests") {
            itemType.canBeUsedByGuests = valueAttribute.asBool()
        }
    }

    /** `ItemParse::createAndRegisterScript` (item_parse.cpp:1020-1257). */
    internal fun createAndRegisterScript(
        itemType: ItemType,
        attributeNode: XmlNode,
        eventType: MoveEvent_t = MoveEvent_t.MOVE_EVENT_NONE,
        weaponType: WeaponType_t = WeaponType_t.WEAPON_NONE,
    ) {
        var moveEventSpec: MoveEventSpec? = null
        if (eventType != MoveEvent_t.MOVE_EVENT_NONE) {
            moveEventSpec = MoveEventSpec(eventType = eventType, itemId = itemType.id)
            if (eventType == MoveEvent_t.MOVE_EVENT_DEEQUIP) {
                itemType.hasDeEquipEvent = true
            }
        }

        var weapon: Weapon? = null
        if (weaponType != WeaponType_t.WEAPON_NONE) {
            weapon = when (weaponType) {
                WeaponType_t.WEAPON_DISTANCE, WeaponType_t.WEAPON_AMMO, WeaponType_t.WEAPON_MISSILE ->
                    WeaponDistance(weaponVocationIdProvider, weaponRandom, itemTypeProvider)

                WeaponType_t.WEAPON_WAND -> WeaponWand(weaponVocationIdProvider, weaponRandom, itemTypeProvider)
                else -> WeaponMelee(weaponVocationIdProvider, weaponRandom, itemTypeProvider)
            }

            weapon.weaponType = weaponType
            itemType.weaponType = weapon.weaponType
            weapon.configureWeapon(itemType)
        }

        var fromDamage = 0
        var toDamage = 0
        for (subAttributeNode in attributeNode.children()) {
            val subKeyAttribute = subAttributeNode.attribute("key") ?: continue
            val subValueAttribute = subAttributeNode.attribute("value") ?: continue

            val stringKey = subKeyAttribute.asString().lowercase()
            when (stringKey) {
                "slot" -> {
                    val slotName = subValueAttribute.asString().lowercase()
                    if (moveEventSpec != null && (moveEventSpec.eventType == MoveEvent_t.MOVE_EVENT_EQUIP || moveEventSpec.eventType == MoveEvent_t.MOVE_EVENT_DEEQUIP)) {
                        moveEventSpec.slot = moveEventSlot(slotName) ?: moveEventSpec.slot
                    } else if (weapon != null) {
                        val it = registry.getItemType(weapon.getID())
                        if (slotName == "two-handed") {
                            it.slotPosition = SLOTP_TWO_HAND
                        } else {
                            it.slotPosition = SLOTP_HAND
                        }
                    }
                }

                "level" -> {
                    val numberValue = pugiCastUInt32(subValueAttribute.value()).toInt()
                    if (moveEventSpec != null) {
                        moveEventSpec.requiredLevel = numberValue
                        moveEventSpec.wieldInfo = moveEventSpec.wieldInfo or WIELDINFO_LEVEL
                    } else if (weapon != null) {
                        weapon.setRequiredLevel(numberValue)
                        weapon.setWieldInfo(WIELDINFO_LEVEL)
                    }
                }

                "vocation" -> {
                    var tmp = ""
                    for (token in subValueAttribute.asString().split(",")) {
                        val trimmed = token.trim()
                        val v1 = trimmed.substringBefore(';').trim()
                        val showInDescription = trimmed.substringAfter(';', "").trim() == "true"

                        if (moveEventSpec != null) {
                            moveEventSpec.vocEquipMap.add(v1)
                            moveEventSpec.wieldInfo = moveEventSpec.wieldInfo or WIELDINFO_VOCREQ
                        }

                        if (showInDescription) {
                            if (moveEventSpec != null && moveEventSpec.vocationString.isEmpty()) {
                                tmp = v1.lowercase() + "s"
                                moveEventSpec.vocationString = tmp
                            } else if (weapon != null && weapon.getVocationString().isEmpty()) {
                                tmp = v1.lowercase() + "s"
                                weapon.setVocationString(tmp)
                            } else {
                                tmp += ", "
                                tmp += v1.lowercase()
                                tmp += "s"
                            }
                        }
                    }

                    val lastComma = tmp.lastIndexOf(',')
                    if (lastComma != -1) {
                        tmp = tmp.substring(0, lastComma) + " and" + tmp.substring(lastComma + 1)
                        if (moveEventSpec != null) {
                            moveEventSpec.vocationString = tmp
                        } else if (weapon != null) {
                            weapon.setVocationString(tmp)
                        }
                    }
                }

                "action" -> if (weapon != null) {
                    when (subValueAttribute.asString().lowercase()) {
                        "removecharge" -> weapon.action = WeaponAction_t.WEAPONACTION_REMOVECHARGE
                        "removecount" -> weapon.action = WeaponAction_t.WEAPONACTION_REMOVECOUNT
                        "move" -> weapon.action = WeaponAction_t.WEAPONACTION_MOVE
                    }
                }

                "breakchance" -> if (weapon != null) {
                    weapon.setBreakChance(pugiCastUInt8(subValueAttribute.value()))
                }

                "mana" -> if (weapon != null) {
                    weapon.setMana(pugiCastUInt32(subValueAttribute.value()).toInt())
                }

                "unproperly" -> if (weapon != null) {
                    weapon.setWieldUnproperly(subValueAttribute.asBool())
                }

                "fromdamage" -> if (weapon != null) {
                    fromDamage = pugiCastUInt32(subValueAttribute.value()).toInt()
                }

                "todamage" -> if (weapon != null) {
                    toDamage = pugiCastUInt32(subValueAttribute.value()).toInt()
                }

                "wandtype" -> if (weapon != null) {
                    val elementName = subValueAttribute.asString().lowercase()
                    when (elementName) {
                        "earth" -> weapon.params.combatType = CombatType.EARTHDAMAGE
                        "ice" -> weapon.params.combatType = CombatType.ICEDAMAGE
                        "energy" -> weapon.params.combatType = CombatType.ENERGYDAMAGE
                        "fire" -> weapon.params.combatType = CombatType.FIREDAMAGE
                        "death" -> weapon.params.combatType = CombatType.DEATHDAMAGE
                        "holy" -> weapon.params.combatType = CombatType.HOLYDAMAGE
                    }
                }

                "chain" -> if (weapon != null) {
                    val doubleValue = pugiCastDouble(subValueAttribute.value())
                    if (doubleValue > 0) {
                        weapon.setChainSkillValue(doubleValue)
                    }
                    if (doubleValue < 0.1 && !subValueAttribute.asBool()) {
                        weapon.setDisabledChain()
                    }
                }
            }
        }

        if (weapon != null) {
            if (weapon is WeaponWand) {
                weapon.setMinChange(fromDamage)
                weapon.setMaxChange(toDamage)
                weapon.configureWeapon(itemType)
            }

            // `weapon->getCombat()->setupChain(weapon)` has no Kotlin combat
            // instance yet (PARITY GAP: the chain system is a combat seam).

            if (weapon.getWieldInfo() != 0) {
                itemType.wieldInfo = weapon.getWieldInfo().toLong()
                itemType.vocationString = weapon.getVocationString()
                itemType.minReqLevel = weapon.getReqLevel().toLong()
                itemType.minReqMagicLevel = weapon.getReqMagLv().toLong()
            }

            weapons.registerLuaEvent(weapon, true)
        }

        if (moveEventSpec != null) {
            moveEvent.registerLuaItemEvent(itemType, moveEventSpec)
        }
    }

    /** `ItemParse::parseUnscriptedItems` (item_parse.cpp:1259-1319). */
    internal fun parseUnscriptedItems(stringValue: String, attributeNode: XmlNode, valueAttribute: XmlAttribute, itemType: ItemType) {
        if (stringValue != "script") {
            return
        }

        for (token in valueAttribute.asString().split(';')) {
            if (token == "moveevent") {
                var eventType = MoveEvent_t.MOVE_EVENT_NONE
                for (subAttributeNode in attributeNode.children()) {
                    val subKeyAttribute = subAttributeNode.attribute("key") ?: continue
                    val subValueAttribute = subAttributeNode.attribute("value") ?: continue

                    if (subKeyAttribute.asString().lowercase() == "eventtype") {
                        eventType = getMoveEventType(subValueAttribute.asString().lowercase())
                        break
                    }
                }

                if (eventType == MoveEvent_t.MOVE_EVENT_NONE) {
                    createAndRegisterScript(itemType, attributeNode, MoveEvent_t.MOVE_EVENT_EQUIP)
                    createAndRegisterScript(itemType, attributeNode, MoveEvent_t.MOVE_EVENT_DEEQUIP)
                } else {
                    createAndRegisterScript(itemType, attributeNode, eventType)
                }
            } else if (token == "weapon") {
                var weaponType = WeaponType_t.WEAPON_NONE
                for (subAttributeNode in attributeNode.children()) {
                    val subKeyAttribute = subAttributeNode.attribute("key") ?: continue
                    val subValueAttribute = subAttributeNode.attribute("value") ?: continue

                    if (subKeyAttribute.asString().lowercase() == "weapontype") {
                        weaponType = getWeaponType(subValueAttribute.asString())
                        break
                    }
                }

                createAndRegisterScript(itemType, attributeNode, MoveEvent_t.MOVE_EVENT_NONE, weaponType)
            }
        }
    }

    private fun moveEventSlot(slotName: String): Int? = when (slotName) {
        "head" -> SLOTP_HEAD
        "necklace" -> SLOTP_NECKLACE
        "backpack" -> SLOTP_BACKPACK
        "armor", "body" -> SLOTP_ARMOR
        "right-hand" -> SLOTP_RIGHT
        "left-hand" -> SLOTP_LEFT
        "hand", "shield" -> SLOTP_RIGHT or SLOTP_LEFT
        "legs" -> SLOTP_LEGS
        "feet" -> SLOTP_FEET
        "ring" -> SLOTP_RING
        "ammo" -> SLOTP_AMMO
        "two-handed", "dualwielding" -> SLOTP_TWO_HAND
        else -> null
    }

    companion object {
        /** `ITEM_GOLD_POUCH` (`src/utils/utils_definitions.hpp:557`). */
        private const val ITEM_GOLD_POUCH = 23721

        private const val CONST_ME_NONE = 0
        private const val CONST_ANI_NONE = 0

        private const val SLOTP_HEAD = 1 shl 0
        private const val SLOTP_NECKLACE = 1 shl 1
        private const val SLOTP_BACKPACK = 1 shl 2
        private const val SLOTP_ARMOR = 1 shl 3
        private const val SLOTP_RIGHT = 1 shl 4
        private const val SLOTP_LEFT = 1 shl 5
        private const val SLOTP_LEGS = 1 shl 6
        private const val SLOTP_FEET = 1 shl 7
        private const val SLOTP_RING = 1 shl 8
        private const val SLOTP_AMMO = 1 shl 9
        private const val SLOTP_TWO_HAND = 1 shl 11
        private const val SLOTP_HAND = SLOTP_LEFT or SLOTP_RIGHT

        private const val WIELDINFO_LEVEL = 1 shl 0
        private const val WIELDINFO_VOCREQ = 1 shl 2

        private val augmentWithoutValueDescription = setOf(
            Augment_t.IncreasedDamage,
            Augment_t.PowerfulImpact,
            Augment_t.StrongImpact,
        )

        /** `ItemTypesMap` (item_parse.hpp:167-188). */
        private val itemTypesMap: Map<String, ItemTypes_t> = mapOf(
            "key" to ItemTypes_t.ITEM_TYPE_KEY,
            "magicfield" to ItemTypes_t.ITEM_TYPE_MAGICFIELD,
            "container" to ItemTypes_t.ITEM_TYPE_CONTAINER,
            "depot" to ItemTypes_t.ITEM_TYPE_DEPOT,
            "rewardchest" to ItemTypes_t.ITEM_TYPE_REWARDCHEST,
            "carpet" to ItemTypes_t.ITEM_TYPE_CARPET,
            "mailbox" to ItemTypes_t.ITEM_TYPE_MAILBOX,
            "trashholder" to ItemTypes_t.ITEM_TYPE_TRASHHOLDER,
            "teleport" to ItemTypes_t.ITEM_TYPE_TELEPORT,
            "door" to ItemTypes_t.ITEM_TYPE_DOOR,
            "bed" to ItemTypes_t.ITEM_TYPE_BED,
            "rune" to ItemTypes_t.ITEM_TYPE_RUNE,
            "supply" to ItemTypes_t.ITEM_TYPE_SUPPLY,
            "creatureproduct" to ItemTypes_t.ITEM_TYPE_CREATUREPRODUCT,
            "food" to ItemTypes_t.ITEM_TYPE_FOOD,
            "valuable" to ItemTypes_t.ITEM_TYPE_VALUABLE,
            "potion" to ItemTypes_t.ITEM_TYPE_POTION,
            "soulcore" to ItemTypes_t.ITEM_TYPE_SOULCORES,
            "ladder" to ItemTypes_t.ITEM_TYPE_LADDER,
            "dummy" to ItemTypes_t.ITEM_TYPE_DUMMY,
        )

        /** `TileStatesMap` (item_parse.hpp:190-198). */
        private val tileStatesMap: Map<String, TileFlags> = mapOf(
            "down" to TileFlags.TILESTATE_FLOORCHANGE_DOWN,
            "north" to TileFlags.TILESTATE_FLOORCHANGE_NORTH,
            "south" to TileFlags.TILESTATE_FLOORCHANGE_SOUTH,
            "southalt" to TileFlags.TILESTATE_FLOORCHANGE_SOUTH_ALT,
            "west" to TileFlags.TILESTATE_FLOORCHANGE_WEST,
            "east" to TileFlags.TILESTATE_FLOORCHANGE_EAST,
            "eastalt" to TileFlags.TILESTATE_FLOORCHANGE_EAST_ALT,
        )

        /** `FluidTypesMap` (item_parse.hpp:200-221). */
        private val fluidTypesMap: Map<String, Fluids_t> = mapOf(
            "water" to Fluids_t.FLUID_WATER,
            "blood" to Fluids_t.FLUID_BLOOD,
            "beer" to Fluids_t.FLUID_BEER,
            "slime" to Fluids_t.FLUID_SLIME,
            "lemonade" to Fluids_t.FLUID_LEMONADE,
            "milk" to Fluids_t.FLUID_MILK,
            "mana" to Fluids_t.FLUID_MANA,
            "life" to Fluids_t.FLUID_LIFE,
            "oil" to Fluids_t.FLUID_OIL,
            "urine" to Fluids_t.FLUID_URINE,
            "coconut" to Fluids_t.FLUID_COCONUTMILK,
            "wine" to Fluids_t.FLUID_WINE,
            "mud" to Fluids_t.FLUID_MUD,
            "fruitjuice" to Fluids_t.FLUID_FRUITJUICE,
            "rum" to Fluids_t.FLUID_RUM,
            "tea" to Fluids_t.FLUID_TEA,
            "mead" to Fluids_t.FLUID_MEAD,
            "ink" to Fluids_t.FLUID_INK,
            "candyfluid" to Fluids_t.FLUID_CANDY,
            "chocolate" to Fluids_t.FLUID_CHOCOLATE,
        )

        /** `WeaponTypesMap` (item_parse.hpp:223-234). */
        private val weaponTypesMap: Map<String, WeaponType_t> = mapOf(
            "fist" to WeaponType_t.WEAPON_FIST,
            "sword" to WeaponType_t.WEAPON_SWORD,
            "club" to WeaponType_t.WEAPON_CLUB,
            "axe" to WeaponType_t.WEAPON_AXE,
            "shield" to WeaponType_t.WEAPON_SHIELD,
            "spellbook" to WeaponType_t.WEAPON_SHIELD,
            "distance" to WeaponType_t.WEAPON_DISTANCE,
            "wand" to WeaponType_t.WEAPON_WAND,
            "ammunition" to WeaponType_t.WEAPON_AMMO,
            "missile" to WeaponType_t.WEAPON_MISSILE,
        )

        /** `ImbuementsTypeMap` (item_parse.hpp:236-259). */
        private val imbuementsTypeMap: Map<String, ImbuementTypes_t> = mapOf(
            "elemental damage" to ImbuementTypes_t.IMBUEMENT_ELEMENTAL_DAMAGE,
            "life leech" to ImbuementTypes_t.IMBUEMENT_LIFE_LEECH,
            "mana leech" to ImbuementTypes_t.IMBUEMENT_MANA_LEECH,
            "critical hit" to ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT,
            "elemental protection death" to ImbuementTypes_t.IMBUEMENT_ELEMENTAL_PROTECTION_DEATH,
            "elemental protection earth" to ImbuementTypes_t.IMBUEMENT_ELEMENTAL_PROTECTION_EARTH,
            "elemental protection fire" to ImbuementTypes_t.IMBUEMENT_ELEMENTAL_PROTECTION_FIRE,
            "elemental protection ice" to ImbuementTypes_t.IMBUEMENT_ELEMENTAL_PROTECTION_ICE,
            "elemental protection energy" to ImbuementTypes_t.IMBUEMENT_ELEMENTAL_PROTECTION_ENERGY,
            "elemental protection holy" to ImbuementTypes_t.IMBUEMENT_ELEMENTAL_PROTECTION_HOLY,
            "increase speed" to ImbuementTypes_t.IMBUEMENT_INCREASE_SPEED,
            "skillboost fist" to ImbuementTypes_t.IMBUEMENT_SKILLBOOST_FIST,
            "skillboost axe" to ImbuementTypes_t.IMBUEMENT_SKILLBOOST_AXE,
            "skillboost sword" to ImbuementTypes_t.IMBUEMENT_SKILLBOOST_SWORD,
            "skillboost club" to ImbuementTypes_t.IMBUEMENT_SKILLBOOST_CLUB,
            "skillboost shielding" to ImbuementTypes_t.IMBUEMENT_SKILLBOOST_SHIELDING,
            "skillboost distance" to ImbuementTypes_t.IMBUEMENT_SKILLBOOST_DISTANCE,
            "skillboost magic level" to ImbuementTypes_t.IMBUEMENT_SKILLBOOST_MAGIC_LEVEL,
            "increase capacity" to ImbuementTypes_t.IMBUEMENT_INCREASE_CAPACITY,
            "paralysis deflection" to ImbuementTypes_t.IMBUEMENT_PARALYSIS_DEFLECTION,
            "vibrancy" to ImbuementTypes_t.IMBUEMENT_PARALYSIS_DEFLECTION,
            "paralysis removal" to ImbuementTypes_t.IMBUEMENT_PARALYSIS_DEFLECTION,
        )

        /** `magicEffectNames` (src/utils/tools.cpp:686-801). */
        private val magicEffectNames: Map<String, Int> = mapOf(
            "assassin" to 62,
            "bats" to 67,
            "bigclouds" to 41,
            "bigplants" to 51,
            "blacksmoke" to 158,
            "blackspark" to 10,
            "bloodysteps" to 64,
            "bluebubble" to 2,
            "blueenergyspark" to 176,
            "bluefirework" to 31,
            "bluefireworks" to 199,
            "blueghost" to 191,
            "bluenote" to 24,
            "blueshimmer" to 13,
            "bubbles" to 26,
            "cake" to 52,
            "carniphila" to 47,
            "confettihorizontal" to 75,
            "confettivertical" to 76,
            "criticaldagame" to 173,
            "dazzling" to 233,
            "devovorga" to 239,
            "dice" to 27,
            "dodge" to 231,
            "dragonhead" to 70,
            "earlythunder" to 171,
            "energy" to 12,
            "energyarea" to 38,
            "explosion" to 6,
            "explosionarea" to 5,
            "fatal" to 230,
            "ferumbras" to 74,
            "ferumbras1" to 235,
            "ferumbras2" to 240,
            "fire" to 16,
            "firearea" to 7,
            "fireattack" to 37,
            "gazharagoth" to 236,
            "giantice" to 53,
            "giftwraps" to 28,
            "greenbubble" to 9,
            "greenenergyspark" to 178,
            "greenfireworks" to 196,
            "greennote" to 19,
            "greenshimmer" to 15,
            "greensmoke" to 169,
            "greenspark" to 17,
            "groundshaker" to 35,
            "hearts" to 36,
            "holyarea" to 50,
            "holydamage" to 40,
            "horestis" to 238,
            "hourglass" to 232,
            "icearea" to 42,
            "iceattack" to 44,
            "icetornado" to 43,
            "insects" to 69,
            "madmage" to 237,
            "magicpowder" to 182,
            "mirrorhorizontal" to 58,
            "mirrorvertical" to 59,
            "mortarea" to 18,
            "orangeenergyspark" to 177,
            "orangefireworks" to 197,
            "orcshaman" to 71,
            "orcshamanfire" to 72,
            "pinkbeam" to 195,
            "pinkenergyspark" to 179,
            "pinkfireworks" to 198,
            "pinkvortex" to 193,
            "pixiecoming" to 185,
            "pixieexplosion" to 184,
            "pixiegoing" to 186,
            "plantattack" to 55,
            "plugingfish" to 175,
            "poff" to 3,
            "poison" to 21,
            "purpleenergy" to 48,
            "purplenote" to 23,
            "purplesmoke" to 170,
            "ragiazbonecapsule" to 172,
            "redfirework" to 30,
            "rednote" to 20,
            "redshimmer" to 14,
            "redsmoke" to 167,
            "redspark" to 1,
            "skullhorizontal" to 60,
            "skullvertical" to 61,
            "sleep" to 33,
            "smallclouds" to 39,
            "smallplants" to 46,
            "smoke" to 68,
            "sparkling" to 234,
            "stepshorizontal" to 63,
            "stepsvertical" to 65,
            "stones" to 45,
            "stonestorm" to 189,
            "storm" to 188,
            "stun" to 32,
            "teleport" to 11,
            "thunder" to 73,
            "treasuremap" to 194,
            "tutorialarrow" to 56,
            "tutorialsquare" to 57,
            "watercreature" to 34,
            "watersplash" to 54,
            "whiteenergyspark" to 180,
            "whitenote" to 25,
            "yalaharighost" to 66,
            "yellowbubble" to 8,
            "yellowenergy" to 49,
            "yellowenergyspark" to 181,
            "yellowfirework" to 29,
            "yellownote" to 22,
            "yellowsmoke" to 168,
            "yellowspark" to 4,
        )

        /** `shootTypeNames` (src/utils/tools.cpp:805-877). */
        private val shootTypeNames: Map<String, Int> = mapOf(
            "arrow" to 3,
            "bolt" to 2,
            "burstarrow" to 7,
            "cake" to 42,
            "crystallinearrow" to 49,
            "death" to 11,
            "diamondarrow" to 57,
            "drillbolt" to 50,
            "earth" to 30,
            "eartharrow" to 40,
            "enchantedspear" to 18,
            "energy" to 5,
            "energyball" to 36,
            "envenomedarrow" to 51,
            "etherealspear" to 28,
            "explosion" to 41,
            "fire" to 4,
            "flammingarrow" to 34,
            "flasharrow" to 33,
            "gloothspear" to 53,
            "greenstar" to 20,
            "holy" to 31,
            "huntingspear" to 17,
            "ice" to 29,
            "infernalbolt" to 16,
            "largerock" to 12,
            "leafstar" to 56,
            "onyxarrow" to 23,
            "piercingbolt" to 24,
            "poison" to 15,
            "poisonarrow" to 6,
            "powerbolt" to 14,
            "prismaticbolt" to 48,
            "redstar" to 19,
            "royalspear" to 21,
            "royalstar" to 59,
            "shiverarrow" to 35,
            "simplearrow" to 54,
            "smallearth" to 39,
            "smallholy" to 38,
            "smallice" to 37,
            "smallstone" to 10,
            "sniperarrow" to 22,
            "snowball" to 13,
            "spear" to 1,
            "spectralbolt" to 58,
            "suddendeath" to 32,
            "tarsalarrow" to 44,
            "throwingknife" to 9,
            "throwingstar" to 8,
            "vortexbolt" to 45,
            "whirlwindaxe" to 26,
            "whirlwindclub" to 27,
            "whirlwindsword" to 25,
        )

        /** `ammoTypeNames` (src/utils/tools.cpp:879-900). */
        private val ammoTypeNames: Map<String, Int> = mapOf(
            "arrow" to 2,
            "bolt" to 1,
            "burstarrow" to 2,
            "diamondarrow" to 2,
            "eartharrow" to 2,
            "enchantedspear" to 3,
            "etherealspear" to 3,
            "flammingarrow" to 2,
            "flasharrow" to 2,
            "huntingspear" to 3,
            "infernalbolt" to 1,
            "largerock" to 6,
            "onyxarrow" to 2,
            "piercingbolt" to 1,
            "poisonarrow" to 2,
            "powerbolt" to 1,
            "royalspear" to 3,
            "shiverarrow" to 2,
            "smallstone" to 6,
            "sniperarrow" to 2,
            "snowball" to 7,
            "spear" to 3,
            "spectralbolt" to 1,
            "throwingknife" to 5,
            "throwingstar" to 4,
        )

        /** `getMagicEffect` (src/utils/tools.cpp:962-969). */
        internal fun getMagicEffect(strValue: String): Int = magicEffectNames[strValue] ?: CONST_ME_NONE

        /** `getShootType` (src/utils/tools.cpp:970-977). */
        internal fun getShootType(strValue: String): Int = shootTypeNames[strValue] ?: CONST_ANI_NONE

        /** `getAmmoType` (src/utils/tools.cpp:978-985). */
        internal fun getAmmoType(strValue: String): Int = ammoTypeNames[strValue] ?: 0

        /** `getDirection` (src/utils/tools.cpp:557-579). */
        internal fun getDirection(value: String): Direction = when (value) {
            "north", "n", "0" -> Direction.North
            "east", "e", "1" -> Direction.East
            "south", "s", "2" -> Direction.South
            "west", "w", "3" -> Direction.West
            "southwest", "south west", "south-west", "sw", "4" -> Direction.SouthWest
            "southeast", "south east", "south-east", "se", "5" -> Direction.SouthEast
            "northwest", "north west", "north-west", "nw", "6" -> Direction.NorthWest
            "northeast", "north east", "north-east", "ne", "7" -> Direction.NorthEast
            else -> Direction.North
        }

        /** `getBedPart` (src/utils/tools.cpp:547-555). */
        internal fun getBedPart(value: String): BedItemPart_t = when (value) {
            "pillow", "1" -> BedItemPart_t.BED_PILLOW_PART
            "blanket", "2" -> BedItemPart_t.BED_BLANKET_PART
            else -> BedItemPart_t.BED_NONE_PART
        }

        /** `getWeaponType` (src/utils/tools.cpp:1000-1022). */
        internal fun getWeaponType(value: String): WeaponType_t = when (value) {
            "fist" -> WeaponType_t.WEAPON_FIST
            "sword" -> WeaponType_t.WEAPON_SWORD
            "club" -> WeaponType_t.WEAPON_CLUB
            "axe" -> WeaponType_t.WEAPON_AXE
            "shield" -> WeaponType_t.WEAPON_SHIELD
            "distance" -> WeaponType_t.WEAPON_DISTANCE
            "wand" -> WeaponType_t.WEAPON_WAND
            "ammunition" -> WeaponType_t.WEAPON_AMMO
            "missile" -> WeaponType_t.WEAPON_MISSILE
            else -> WeaponType_t.WEAPON_NONE
        }

        /** `toPascalCase` (src/utils/tools.cpp:360-376). */
        internal fun toPascalCase(str: String): String {
            val result = StringBuilder()
            var capitalizeNext = true

            for (ch in str) {
                if (ch == '_' || ch.isWhitespace() || ch == '-') {
                    capitalizeNext = true
                } else {
                    if (capitalizeNext) {
                        result.append(ch.uppercaseChar())
                        capitalizeNext = false
                    } else {
                        result.append(ch)
                    }
                }
            }

            return result.toString()
        }
    }
}
