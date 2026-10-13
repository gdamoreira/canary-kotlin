package org.opentibia.canary.items

import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.systems.imbuement.ImbuementTypes_t
import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.io.appearances.Appearances
import org.opentibia.canary.io.appearances.HookType
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

private val logger = KotlinLogging.logger {}

/**
 * `ItemTypes_t` (`src/items/items_definitions.hpp:140-195`). The first block (1-27)
 * is ordered to match the protobuf `ITEM_CATEGORY` cast in `protoItemCategoryToCpp`.
 */

/** `ItemAnimation_t` (`src/enums/item_attribute.hpp:60-64`). */
enum class ItemAnimation_t(val value: Int) {
    ANIMATION_NONE(0),
    ANIMATION_RANDOM(1),
    ANIMATION_DESYNC(2),
}

/**
 * Applies one `items.xml` `<attribute key= value=>` entry, mirroring the
 * `ItemParse::initParse` dispatch (`src/items/functions/item/item_parse.cpp:24-90`)
 * over the `ItemParseAttributesMap` key set (`item_parse.hpp:19-165`).
 *
 * [initParse] receives the lower-cased key, the attribute node (nested `<attribute>`
 * children are read by keys such as `field`/`augments`/`imbuementslot`/`type`) and
 * the raw value. It returns `true` when the key is known and was applied, `false`
 * when the key is unknown (the registry then logs Canary's "Unknown key value").
 * The attribute parser is injected because `ItemParse` belongs to
 * `src/items/functions/item/item_parse.cpp`; the default [UnwiredItemAttributeParse]
 * leaves every key unhandled.
 */
fun interface ItemAttributeParse {
    fun initParse(key: String, attributeNode: Element, value: String, itemType: ItemType): Boolean
}

/** Default [ItemAttributeParse] used until the `ItemParse` port is wired. */
object UnwiredItemAttributeParse : ItemAttributeParse {
    override fun initParse(key: String, attributeNode: Element, value: String, itemType: ItemType): Boolean = false
}

/**
 * `class Items` (`src/items/items.hpp:389-473`), the item registry of
 * `src/items/items.cpp`.
 *
 * The C++ singleton/global access (`Item::items`, `g_game().m_appearancesPtr`,
 * `g_configManager()`) becomes method parameters (AGENTS.md 1.1): the appearances
 * table, the `OLD_PROTOCOL` support flag, the known proficiency ids and the
 * `items.xml` content are passed in by the caller. The internal
 * `std::vector<ItemType>` is modelled as a sparse id map plus the vector length
 * (`size()`/`hasItemType` keep Canary's "any id below the vector size exists"
 * semantics), and the `nameToItems` multimap as a name -> ordered id list.
 *
 * The earlier Kotlin name `ItemTypeRegistry` is kept as a type alias.
 */
class Items {

    private val items = LinkedHashMap<Int, ItemType>()

    /**
     * `Items::nameToItems` (`src/items/items.hpp:440`). The C++
     * `std::unordered_multimap` is modelled with the insertion order of each name's
     * ids; [getItemIdByName] returns the first id stored for the name.
     */
    val nameToItems: MutableMap<String, MutableList<Int>> = LinkedHashMap()

    private val ladders = mutableListOf<Int>()
    private val dummys = mutableMapOf<Int, Int>()
    private val inventory = mutableListOf<Int>()

    /** Length of the C++ `items` vector (highest loaded id + 1). */
    private var itemCount = 0

    init {
        items[0] = ItemType(id = 0, name = "")
    }

    /**
     * Kotlin registration entry point for the existing OTB/XML loaders. Canary fills
     * the vector through `loadFromProtobuf`/`parseItemNode`; this mirrors the same
     * growth and name-map rules for ids registered from outside.
     */
    fun register(itemType: ItemType) {
        val existing = items[itemType.id]
        if (existing != null && existing.name.isNotEmpty() && existing.name != itemType.name) {
            eraseNameToItemsById(itemType.id)
        }
        items[itemType.id] = itemType
        if (itemType.id >= itemCount) {
            itemCount = itemType.id + 1
        }
        if (itemType.name.isNotEmpty()) {
            insertNameToItems(itemType.name, itemType.id)
        }
    }

    /** Kotlin look-up used by the existing loaders; `null` when the id was never stored. */
    fun get(id: Int): ItemType? = items[id]

    /** `Items::clear` (`src/items/items.cpp:26-33`). */
    fun clear() {
        items.clear()
        ladders.clear()
        dummys.clear()
        nameToItems.clear()
        inventory.clear()
        itemCount = 0
        // `items.front()` must stay resolvable for out-of-range look-ups.
        items[0] = ItemType(id = 0, name = "")
    }

    /**
     * `Items::getItemType` (`src/items/items.cpp:398-410`): out-of-range ids return
     * `items.front()` (the entry at index 0, a default `ItemType` unless id 0 was
     * defined).
     */
    fun getItemType(id: Int): ItemType = items[id] ?: items.getValue(0)

    /** `Items::hasItemType` (`src/items/items.cpp:422-427`): `hasId < items.size()`. */
    fun hasItemType(id: Int): Boolean = id < itemCount

    /** `Items::getItemIdByName` (`src/items/items.cpp:412-420`), lower-cased lookup. */
    fun getItemIdByName(name: String): Int = nameToItems[name.lowercase()]?.firstOrNull() ?: 0

    /** `Items::size` (`src/items/items.hpp:432-434`). */
    fun size(): Int = itemCount

    /** `Items::getItems` (`src/items/items.hpp:436-438`). */
    fun getItems(): Map<Int, ItemType> = items

    /** `Items::addLadderId` (`src/items/items.hpp:442-444`). */
    fun addLadderId(newId: Int) {
        ladders.add(newId)
    }

    /** `Items::addDummyId` (`src/items/items.hpp:445-447`). */
    fun addDummyId(newId: Int, rate: Int) {
        dummys[newId] = rate
    }

    /** `Items::getLadders` (`src/items/items.hpp:449-451`). */
    fun getLadders(): List<Int> = ladders

    /** `Items::getDummys` (`src/items/items.hpp:452-454`). */
    fun getDummys(): Map<Int, Int> = dummys

    /** `Items::getInventory` (`src/items/items.hpp:428-430`). */
    fun getInventory(): List<Int> = inventory

    /** `Items::buildInventoryList` (`src/items/items.cpp:319-328`). */
    fun buildInventoryList() {
        inventory.clear()
        for (type in items.values) {
            if (type.weaponType != WeaponType_t.WEAPON_NONE ||
                type.ammoType != AMMO_NONE ||
                type.attack != 0 ||
                type.defense != 0 ||
                type.extraDefense != 0 ||
                type.armor != 0 ||
                type.slotPosition and SLOTP_NECKLACE != 0 ||
                type.slotPosition and SLOTP_RING != 0 ||
                type.slotPosition and SLOTP_AMMO != 0 ||
                type.slotPosition and SLOTP_FEET != 0 ||
                type.slotPosition and SLOTP_HEAD != 0 ||
                type.slotPosition and SLOTP_ARMOR != 0 ||
                type.slotPosition and SLOTP_LEGS != 0
            ) {
                inventory.add(type.id)
            }
        }
        inventory.sort()
    }

    /**
     * `Items::reload` (`src/items/items.cpp:131-140`): clear, then merge the
     * appearances table and re-read `items.xml`. The C++ globals (`m_appearancesPtr`,
     * `CORE_DIRECTORY`) are injected: a `null` [appearances] skips the protobuf merge
     * and a `null` [xmlContent] fails the XML stage, keeping the merged items like a
     * missing `items.xml` file does in Canary.
     */
    fun reload(
        appearances: Appearances?,
        xmlContent: String?,
        supportAnimation: Boolean = false,
        knownProficiencyIds: Set<Int> = emptySet(),
        attributeParse: ItemAttributeParse = UnwiredItemAttributeParse,
    ): Boolean {
        clear()
        if (appearances != null) {
            loadFromProtobuf(appearances, supportAnimation, knownProficiencyIds)
        }
        return loadFromXml(xmlContent, attributeParse)
    }

    /**
     * `Items::loadFromProtobuf` (`src/items/items.cpp:153-279`) over the parsed
     * `appearances.dat` tables.
     *
     * @param supportAnimation mirrors the `OLD_PROTOCOL` config boolean
     *   (items.cpp:156); the animation frame scan only runs when it is on.
     * @param knownProficiencyIds mirrors `WeaponProficiency::getProficiencies()`
     *   (items.cpp:263); unknown ids are warned and not stored.
     */
    fun loadFromProtobuf(
        appearances: Appearances,
        supportAnimation: Boolean = false,
        knownProficiencyIds: Set<Int> = emptySet(),
    ) {
        for (objectAppearance in appearances.objects.values) {
            val flags = objectAppearance.flags
            if (flags == null) {
                logger.warn {
                    "[Items::loadFromProtobuf] - Item with id '${objectAppearance.id}' is invalid and was ignored."
                }
                continue
            }

            // `object.id() >= items.size()` -> `items.resize(object.id() + 1)`.
            if (objectAppearance.id >= itemCount) {
                itemCount = objectAppearance.id + 1
            }

            // `object.has_id()` cannot be distinguished from an unset id once the
            // Kotlin [Appearance] stores a plain Int; id 0 is never a valid object.
            if (objectAppearance.id == 0) {
                continue
            }

            val itemType = items.getOrPut(objectAppearance.id) {
                ItemType(id = objectAppearance.id, name = "")
            }

            if (flags.container) {
                itemType.type = ItemTypes_t.ITEM_TYPE_CONTAINER
                itemType.group = ItemGroup_t.ITEM_GROUP_CONTAINER
            } else if (flags.bank != null) {
                itemType.group = ItemGroup_t.ITEM_GROUP_GROUND
            } else if (flags.liquidcontainer) {
                itemType.group = ItemGroup_t.ITEM_GROUP_FLUID
            } else if (flags.liquidpool) {
                itemType.group = ItemGroup_t.ITEM_GROUP_SPLASH
            }

            if (supportAnimation) {
                for (frameGroup in objectAppearance.frameGroups) {
                    val spriteInfo = frameGroup.spriteInfo ?: continue
                    val animation = spriteInfo.animation ?: continue
                    itemType.animationType = if (animation.randomStartPhase) {
                        ItemAnimation_t.ANIMATION_RANDOM
                    } else {
                        ItemAnimation_t.ANIMATION_DESYNC
                    }
                }
            }

            if (flags.clip) {
                itemType.alwaysOnTopOrder = 1
            } else if (flags.top) {
                itemType.alwaysOnTopOrder = 3
            } else if (flags.bottom) {
                itemType.alwaysOnTopOrder = 2
            }

            flags.clothes?.let { clothes ->
                // C++ shifts `1 << (slot - 1)` into a uint16_t; slot 0 is undefined
                // behaviour there and truncates to 0 on x86, which the mask preserves.
                itemType.slotPosition = itemType.slotPosition or ((1 shl (clothes.slot - 1)) and 0xFFFF)
            }

            flags.market?.let { market ->
                itemType.type = Items.protoItemCategoryToCpp(market.category?.value ?: 0)
            }

            itemType.name = objectAppearance.name
            itemType.description = objectAppearance.description

            itemType.upgradeClassification = flags.upgradeclassification?.upgradeClassification ?: 0
            itemType.lightLevel = flags.light?.brightness ?: 0
            itemType.lightColor = flags.light?.color ?: 0

            itemType.id = objectAppearance.id
            itemType.speed = flags.bank?.waypoints ?: 0
            itemType.wareId = flags.market?.tradeAsObjectId ?: 0

            itemType.isCorpse = flags.corpse || flags.playerCorpse
            itemType.forceUse = flags.forceuse
            itemType.hasHeight = flags.height != null
            itemType.blockSolid = flags.unpass
            itemType.blockProjectile = flags.unsight
            itemType.blockPathFind = flags.avoid
            itemType.isPickupable = flags.take
            itemType.rotatable = flags.rotate
            itemType.wrapContainer = flags.wrap || flags.unwrap
            if (itemType.wrapContainer) {
                itemType.wrapableTo = ITEM_DECORATION_KIT
                itemType.wrapable = true
            }
            itemType.multiUse = flags.multiuse
            itemType.isMovable = !flags.unmove
            // Canary computes a lenshelp/write expression and immediately overwrites
            // it (items.cpp:248-249); only the final assignment is transcribed.
            itemType.canReadText = flags.write != null || flags.writeOnce != null
            itemType.isVertical = flags.hook?.direction == HookType.SOUTH
            itemType.isHorizontal = flags.hook?.direction == HookType.EAST
            itemType.isHangable = flags.hang
            itemType.lookThrough = flags.ignoreLook
            itemType.isStackable = flags.cumulative
            itemType.isPodium = flags.showOffSocket
            itemType.wearOut = flags.wearout
            itemType.clockExpire = flags.clockexpire
            itemType.expire = flags.expire
            itemType.expireStop = flags.expirestop
            itemType.isWrapKit = flags.wrapkit
            itemType.isDualWielding = flags.dualWielding

            flags.proficiency?.let { proficiency ->
                // `has_proficiency_id()` cannot be distinguished from a stored 0.
                if (proficiency.proficiencyId != 0) {
                    if (proficiency.proficiencyId !in knownProficiencyIds) {
                        logger.warn {
                            "[Items::loadFromProtobuf] - Unknown Proficiency ID '${proficiency.proficiencyId}'"
                        }
                    } else {
                        itemType.proficiencyId = proficiency.proficiencyId
                    }
                }
            }

            if (itemType.name.isNotEmpty()) {
                insertNameToItems(itemType.name, itemType.id)
            }

            itemType.syncConvenienceFlags()
        }
    }

    /**
     * `Items::loadFromXml` (`src/items/items.cpp:281-317`). The C++
     * `{CORE_DIRECTORY}/items/items.xml` file read is injected as [xmlContent].
     */
    fun loadFromXml(
        xmlContent: String?,
        attributeParse: ItemAttributeParse = UnwiredItemAttributeParse,
    ): Boolean {
        if (xmlContent == null) {
            return false
        }

        val doc = try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            builder.parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
        } catch (exception: Exception) {
            logger.error { "[Items::loadFromXml] - Failed to parse items.xml: ${exception.message}" }
            return false
        }

        // pugixml iterates a missing `items` child as an empty range, so a document
        // without the root still reports success.
        val root = doc.getElementsByTagName("items").item(0) as? Element ?: return true

        val children = root.childNodes
        for (index in 0 until children.length) {
            val itemNode = children.item(index) as? Element ?: continue

            if (itemNode.hasAttribute("id")) {
                parseItemNode(itemNode, pugiCastU16(itemNode.getAttribute("id")), attributeParse)
                continue
            }

            if (!itemNode.hasAttribute("fromid")) {
                logger.warn { "[Items::loadFromXml] - No item id found, use id or fromid" }
                continue
            }

            if (!itemNode.hasAttribute("toid")) {
                logger.warn {
                    "[Items::loadFromXml] - tag fromid: ${itemNode.getAttribute("fromid")} without toid"
                }
                continue
            }

            var id = pugiCastU16(itemNode.getAttribute("fromid"))
            val toId = pugiCastU16(itemNode.getAttribute("toid"))
            while (id <= toId) {
                parseItemNode(itemNode, id, attributeParse)
                id++
            }
        }

        return true
    }

    /**
     * `Items::parseItemNode` (`src/items/items.cpp:330-396`).
     *
     * The `ItemParse::initParse` call is delegated to [attributeParse]; the registry
     * keeps the id/name/loaded/name-map maintenance, the `id >= 100` reserved-sprite
     * guard, the article/plural attributes and the bed-type warning.
     */
    fun parseItemNode(
        itemNode: Element,
        id: Int,
        attributeParse: ItemAttributeParse = UnwiredItemAttributeParse,
    ) {
        val itemType = items.getOrPut(id) { ItemType(id = 0, name = "") }
        if (id >= itemCount) {
            itemCount = id + 1
        }

        // Ids 0-100 are used for fluids in the XML.
        if (id >= 100 && itemType.id == 0 && (itemType.name.isEmpty() || itemType.name == "reserved sprite")) {
            return
        }

        itemType.id = id

        if (itemType.loaded) {
            logger.warn { "[Items::parseItemNode] - Duplicate item with id: $id" }
            return
        }

        if (itemNode.hasAttribute("name")) {
            val xmlName = itemNode.getAttribute("name")
            if (xmlName.isNotEmpty() && itemType.name != xmlName) {
                if (itemType.name.isNotEmpty()) {
                    eraseNameToItemsById(id)
                }
                itemType.name = xmlName
                insertNameToItems(itemType.name, id)
            }
        }

        itemType.loaded = true

        if (itemNode.hasAttribute("article")) {
            itemType.article = itemNode.getAttribute("article")
        }

        if (itemNode.hasAttribute("plural")) {
            itemType.pluralName = itemNode.getAttribute("plural")
        }

        val children = itemNode.childNodes
        for (index in 0 until children.length) {
            val attributeNode = children.item(index) as? Element ?: continue
            if (!attributeNode.hasAttribute("key") || !attributeNode.hasAttribute("value")) {
                continue
            }

            val key = attributeNode.getAttribute("key")
            val value = attributeNode.getAttribute("value")
            if (!attributeParse.initParse(key.lowercase(), attributeNode, value, itemType)) {
                logger.warn { "[Items::parseItemNode] - Unknown key value: $key" }
            }
        }

        // Check bed items.
        if ((itemType.transformToFree != 0 ||
                itemType.transformToOnUse[0] != 0 ||
                itemType.transformToOnUse[1] != 0) &&
            itemType.type != ItemTypes_t.ITEM_TYPE_BED
        ) {
            logger.warn { "[Items::parseItemNode] - Item ${itemType.id} is not set as a bed-type" }
        }

        itemType.syncConvenienceFlags()
    }

    private fun insertNameToItems(name: String, id: Int) {
        nameToItems.getOrPut(name.lowercase()) { mutableListOf() }.add(id)
    }

    /** Erases the first `name -> id` entry, like `std::ranges::find_if` + `erase`. */
    private fun eraseNameToItemsById(id: Int) {
        val iterator = nameToItems.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val ids = entry.value
            val position = ids.indexOf(id)
            if (position >= 0) {
                ids.removeAt(position)
                if (ids.isEmpty()) {
                    iterator.remove()
                }
                return
            }
        }
    }

    /**
     * Keeps the earlier Kotlin convenience flags (`isContainer`, `isBed`, `isDoor`,
     * ...) in sync with the `type`/`group` enums set by the protobuf merge and the
     * XML attribute parser. The C++ `ItemType` exposes these as getters; the Kotlin
     * model stores them, so they are recomputed after every load stage.
     */
    private fun ItemType.syncConvenienceFlags() {
        isContainer = group == ItemGroup_t.ITEM_GROUP_CONTAINER || type == ItemTypes_t.ITEM_TYPE_CONTAINER
        isGroundTile = group == ItemGroup_t.ITEM_GROUP_GROUND
        isFluidContainer = group == ItemGroup_t.ITEM_GROUP_FLUID
        isSplash = group == ItemGroup_t.ITEM_GROUP_SPLASH
        isBed = type == ItemTypes_t.ITEM_TYPE_BED
        isDoor = type == ItemTypes_t.ITEM_TYPE_DOOR
        isTrashHolder = type == ItemTypes_t.ITEM_TYPE_TRASHHOLDER
        isCarpet = type == ItemTypes_t.ITEM_TYPE_CARPET
        isWrappable = wrapableTo > 0
    }

    companion object {
        /** `ITEM_DECORATION_KIT` (`src/utils/utils_definitions.hpp:554`). */
        const val ITEM_DECORATION_KIT: Int = 23398

        /** `IMBUEMENT_MAX_TIER` (`src/utils/const.hpp:27`). */
        const val IMBUEMENT_MAX_TIER: Int = 3

        /** `WEAPON_NONE` (`src/items/weapons/weapons.hpp`). */
        const val WEAPON_NONE: Int = 0

        /** `AMMO_NONE` (`src/items/items_definitions.hpp`). */
        const val AMMO_NONE: Int = 0

        // SlotPositionBits (src/items/items_definitions.hpp:426-441).
        const val SLOTP_HEAD: Int = 1 shl 0
        const val SLOTP_NECKLACE: Int = 1 shl 1
        const val SLOTP_ARMOR: Int = 1 shl 3
        const val SLOTP_LEGS: Int = 1 shl 6
        const val SLOTP_FEET: Int = 1 shl 7
        const val SLOTP_RING: Int = 1 shl 8
        const val SLOTP_AMMO: Int = 1 shl 9
        const val SLOTP_LEFT: Int = 1 shl 5
        const val SLOTP_RIGHT: Int = 1 shl 4
        const val SLOTP_HAND: Int = SLOTP_LEFT or SLOTP_RIGHT

        private val lootTypeNames: Map<String, ItemTypes_t> = linkedMapOf(
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

        private val augmentNamesWithoutValueDescription: Set<Augment_t> = setOf(
            Augment_t.IncreasedDamage,
            Augment_t.PowerfulImpact,
            Augment_t.StrongImpact,
        )

        /** `Items::getLootType` (`src/items/items.cpp:65-71`). */
        fun getLootType(strValue: String): ItemTypes_t =
            lootTypeNames[strValue] ?: ItemTypes_t.ITEM_TYPE_NONE

        /** `Items::isAugmentWithoutValueDescription` (`src/items/items.hpp:458-466`). */
        fun isAugmentWithoutValueDescription(augmentType: Augment_t): Boolean =
            augmentType in augmentNamesWithoutValueDescription

        /** `Items::getAugmentNameByType` (`src/items/items.cpp:73-80`). */
        fun getAugmentNameByType(augmentType: Augment_t): String {
            val augmentTypeName = toStartCaseWithSpace(augmentType.name)
            return if (isAugmentWithoutValueDescription(augmentType)) {
                augmentTypeName
            } else {
                augmentTypeName.lowercase()
            }
        }

        /**
         * `protoItemCategoryToCpp` (`src/items/items.cpp:142-151`): protobuf
         * categories 1-27 map directly, everything else (including the extended
         * 28-31 categories) becomes `ITEM_TYPE_OTHER`.
         */
        fun protoItemCategoryToCpp(protoCategory: Int): ItemTypes_t =
            if (protoCategory in 1..27) {
                ItemTypes_t.entries.first { it.value == protoCategory }
            } else {
                ItemTypes_t.ITEM_TYPE_OTHER
            }
    }
}

/** `pugi::cast<uint16_t>` over an XML attribute value (`strtol` + truncation). */
private fun pugiCastU16(value: String): Int = (value.toIntOrNull() ?: 0) and 0xFFFF

/**
 * `toStartCaseWithSpace` (`src/utils/tools.cpp:412-426`): upper-case the first and
 * every upper-case character, lower-case the rest, inserting a space before each
 * upper-case character after the first.
 */
internal fun toStartCaseWithSpace(str: String): String {
    val result = StringBuilder()
    for ((index, char) in str.withIndex()) {
        if (index == 0 || char.isUpperCase()) {
            if (index > 0) {
                result.append(' ')
            }
            result.append(char.uppercaseChar())
        } else {
            result.append(char.lowercaseChar())
        }
    }
    return result.toString()
}

/**
 * `capitalizeWordsIgnoringString` (`src/utils/tools.cpp:1687-1700`): lower-case the
 * source, then upper-case the first character of each word, leaving the first
 * `stringToIgnore` occurrence untouched.
 */
internal fun capitalizeWordsIgnoringString(source: String, stringToIgnore: String): String {
    val lowered = source.lowercase()
    val ignoredStart = lowered.indexOf(stringToIgnore)
    val result = StringBuilder(lowered.length)
    for (index in lowered.indices) {
        val char = lowered[index]
        if (ignoredStart > 0 &&
            index >= ignoredStart &&
            index < ignoredStart + stringToIgnore.length
        ) {
            result.append(char)
            continue
        }
        result.append(
            if (index == 0 || lowered[index - 1] == ' ' || lowered[index - 1] == '\'') {
                char.uppercaseChar()
            } else {
                char
            }
        )
    }
    return result.toString()
}
