package org.opentibia.canary.items

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream
import org.opentibia.canary.map.Tile
import org.opentibia.canary.map.house.Door
import java.util.Collections
import java.util.IdentityHashMap

data class ItemType(
    val id: Int,
    val name: String,
    val isStackable: Boolean = false,
    val isContainer: Boolean = false,
    val capacity: Int = 0,
    val weight: Double = 0.0,
    /** Mirrors `ItemType::upgradeClassification` (src/items/items.hpp:329). */
    val upgradeClassification: Int = 0,
    val isPickupable: Boolean = true,
    /** Mirrors `ItemType::magicEffect` (src/items/items.hpp:318); default `CONST_ME_NONE` (0). */
    val magicEffect: Int = 0,
    /** `ItemType::movable` (src/items/items.hpp:288), the house-serialization guard. */
    val isMovable: Boolean = false,
    /** `ItemType::isBed()` (src/items/items.hpp:378). */
    val isBed: Boolean = false,
    /** `ItemType::bedPartOf` (src/items/items.hpp:468), the partner id `BedItem::isBedComplete` checks. */
    val bedPartOf: Int = 0,
    /** `ItemType::isCarpet()` (src/items/items.hpp:420). */
    val isCarpet: Boolean = false,
    /** `ItemType::isTrashHolder()` (src/items/items.hpp:410). */
    val isTrashHolder: Boolean = false,
    /** `ItemType::isDoor()` (src/items/items.hpp:414). */
    val isDoor: Boolean = false,
    /** `ItemType::isWrappable()` (src/items/items.hpp:411). */
    val isWrappable: Boolean = false,
    /** `ItemType::canWriteText` (src/items/items.hpp:342). */
    val canWriteText: Boolean = false,
    /** `ItemType::m_transformOnUse` (src/items/items.hpp:347). */
    val transformOnUse: Int = 0,
    /** `ItemType::isFluidContainer()` (src/items/items.hpp:416). */
    val isFluidContainer: Boolean = false,
    /** `ItemType::isSplash()` (src/items/items.hpp:417). */
    val isSplash: Boolean = false,
    /** `ItemType::isGroundTile()` (src/items/items.hpp:407). */
    val isGroundTile: Boolean = false,
    /** `ItemType::charges` (src/items/items.hpp:290), used by `getSubType`/`setSubType`. */
    val charges: Int = 0,
    /** `ItemType::stackSize` (src/items/items.hpp:335); Canary defaults it to 100. */
    val stackSize: Int = 100,
    /** `ItemType::isCorpse` (src/items/items.hpp:378), read by `Item::isCorpse`. */
    val isCorpse: Boolean = false,
    /** `ItemType::weaponType` (src/items/items.hpp:296); `WEAPON_NONE` (0) by default. */
    val weaponType: Int = 0,
    /** `ItemType::m_primaryType` (src/items/items.hpp:272), the store-inbox category source. */
    val primaryType: String = "",
    /** `ItemType::isStoreItem()` (src/items/items.hpp), compared by `Item::equals`. */
    val isStoreItem: Boolean = false,
    /** `ItemType::isQuiver()` (src/items/items.hpp:410), checked by `Container::queryAdd`. */
    val isQuiver: Boolean = false,
)

class ItemTypeRegistry {
    private val types = mutableMapOf<Int, ItemType>()

    fun register(itemType: ItemType) {
        types[itemType.id] = itemType
    }

    fun get(id: Int): ItemType? = types[id]
}

/**
 * Mirrors Canary `ItemAttribute_t` (src/enums/item_attribute.hpp:12) with the exact
 * underlying values. Integer vs string classification mirrors `ItemAttributeHelper`
 * (src/items/functions/item/attribute.hpp:15).
 */
enum class ItemAttribute(val value: Long) {
    NONE(0),
    ACTIONID(1),
    UNIQUEID(2),
    DESCRIPTION(3),
    TEXT(4),
    DATE(5),
    WRITER(6),
    NAME(7),
    ARTICLE(8),
    PLURALNAME(9),
    WEIGHT(10),
    ATTACK(11),
    DEFENSE(12),
    EXTRADEFENSE(13),
    ARMOR(14),
    HITCHANCE(15),
    SHOOTRANGE(16),
    OWNER(17),
    DURATION(18),
    DECAYSTATE(19),
    CORPSEOWNER(20),
    CHARGES(21),
    FLUIDTYPE(22),
    DOORID(23),
    SPECIAL(24),
    IMBUEMENT_SLOT(25),
    OPENCONTAINER(26),
    QUICKLOOTCONTAINER(27),
    DURATION_TIMESTAMP(28),
    AMOUNT(29),
    TIER(30),
    STORE(31),
    CUSTOM(32),
    LOOTMESSAGE_SUFFIX(33),
    STORE_INBOX_CATEGORY(34),
    OBTAINCONTAINER(35),
    AUGMENTS(36),
    MANTRA(37);

    companion object {
        fun isAttributeInteger(attribute: ItemAttribute): Boolean = when (attribute) {
            STORE, ACTIONID, UNIQUEID, DATE, WEIGHT, ATTACK, MANTRA, DEFENSE, EXTRADEFENSE,
            ARMOR, HITCHANCE, SHOOTRANGE, OWNER, DURATION, DECAYSTATE, CORPSEOWNER, CHARGES,
            FLUIDTYPE, DOORID, IMBUEMENT_SLOT, OPENCONTAINER, QUICKLOOTCONTAINER, OBTAINCONTAINER,
            DURATION_TIMESTAMP, TIER, AMOUNT -> true

            else -> false
        }

        fun isAttributeString(attribute: ItemAttribute): Boolean = when (attribute) {
            DESCRIPTION, TEXT, WRITER, NAME, ARTICLE, PLURALNAME, SPECIAL,
            LOOTMESSAGE_SUFFIX, STORE_INBOX_CATEGORY -> true

            else -> false
        }
    }
}

/** Mirrors Canary `ItemDecayState_t` (src/enums/item_attribute.hpp:53). */
enum class ItemDecayState(val value: Int) {
    DECAYING_FALSE(0),
    DECAYING_TRUE(1),
    DECAYING_PENDING(2),
    DECAYING_STOPPING(3);

    companion object {
        fun fromValue(value: Int): ItemDecayState =
            entries.firstOrNull { it.value == value } ?: DECAYING_FALSE
    }
}

/** `Attr_ReadValue` (src/items/item.hpp:69-73). */
enum class AttrReadValue {
    ATTR_READ_CONTINUE,
    ATTR_READ_END,
    ATTR_READ_ERROR,
}

// AttrTypes_t wire codes (src/items/items_definitions.hpp:202-252).
internal const val ATTR_NONE = 0
internal const val ATTR_STORE = 1
internal const val ATTR_ACTION_ID = 4
internal const val ATTR_UNIQUE_ID = 5
internal const val ATTR_TEXT = 6
internal const val ATTR_DESC = 7
internal const val ATTR_TELE_DEST = 8
internal const val ATTR_DEPOT_ID = 10
internal const val ATTR_RUNE_CHARGES = 12
internal const val ATTR_HOUSEDOORID = 14
internal const val ATTR_COUNT = 15
internal const val ATTR_DURATION = 16
internal const val ATTR_DECAYING_STATE = 17
internal const val ATTR_WRITTENDATE = 18
internal const val ATTR_WRITTENBY = 19
internal const val ATTR_SLEEPERGUID = 20
internal const val ATTR_SLEEPSTART = 21
internal const val ATTR_CHARGES = 22
internal const val ATTR_CONTAINER_ITEMS = 23
internal const val ATTR_NAME = 24
internal const val ATTR_ARTICLE = 25
internal const val ATTR_PLURALNAME = 26
internal const val ATTR_WEIGHT = 27
internal const val ATTR_ATTACK = 28
internal const val ATTR_DEFENSE = 29
internal const val ATTR_EXTRADEFENSE = 30
internal const val ATTR_ARMOR = 31
internal const val ATTR_HITCHANCE = 32
internal const val ATTR_SHOOTRANGE = 33
internal const val ATTR_SPECIAL = 34
internal const val ATTR_IMBUEMENT_SLOT = 35
internal const val ATTR_OPENCONTAINER = 36
internal const val ATTR_QUICKLOOTCONTAINER = 38
internal const val ATTR_AMOUNT = 39
internal const val ATTR_TIER = 40
internal const val ATTR_CUSTOM = 41
internal const val ATTR_STORE_INBOX_CATEGORY = 42
internal const val ATTR_OWNER = 43
internal const val ATTR_OBTAINCONTAINER = 44
internal const val ATTR_MANTRA = 45

open class Item(
    var id: Int,
    var count: Int = 1,
    val type: ItemType? = null
) {
    private val attributes = mutableMapOf<ItemAttribute, Any>()

    open val weight: Double
        get() = (type?.weight ?: 0.0) * if (type?.isStackable == true) count else 1

    // --- Thing/Cylinder identity (src/items/item.hpp:236-243, 681-694) --------

    private var m_parent: Cylinder? = null

    /** `Item::getParent` (src/items/item.hpp:681). */
    open fun getParent(): Cylinder? = m_parent

    /** `Item::setParent` (src/items/item.hpp:684). */
    open fun setParent(cylinder: Cylinder?) {
        m_parent = cylinder
    }

    /** `Item::resetParent` (src/items/item.hpp:687). */
    fun resetParent() {
        m_parent = null
    }

    /**
     * `Item::getTopParent` (src/items/item.cpp:832-849). The C++ initial
     * `prevaux` is a `dynamic_pointer_cast<Cylinder>` of the item itself, so a
     * plain [Item] starts with `null` while a [Container] starts with itself.
     * A visited set bounds a corrupted self/cyclic parent chain (the C++ loop
     * would spin forever).
     */
    fun getTopParent(): Cylinder? {
        val firstParent = getParent()
        val initial: Cylinder? = if (this is Cylinder) this else null
        if (firstParent == null) {
            return initial
        }

        val visited = Collections.newSetFromMap(IdentityHashMap<Cylinder, Boolean>())
        var previous: Cylinder? = initial
        var current: Cylinder = firstParent
        while (visited.add(current)) {
            val parent = current.getParent() ?: return previous ?: current
            previous = current
            current = parent
        }
        return previous ?: initial
    }

    /** `Item::getTile` (src/items/item.cpp:851-857). */
    fun getTile(): Tile? {
        var cylinder: Cylinder? = getTopParent()
        if (cylinder?.getParent() != null) {
            cylinder = cylinder.getParent()
        }
        return cylinder as? Tile
    }

    /** `Item::isRemoved` (src/items/item.cpp:859-864). */
    open fun isRemoved(): Boolean {
        val parent = getParent() ?: return true
        return parent.isRemoved()
    }

    /** `Item::canRemove` (src/items/item.hpp:644). */
    open fun canRemove(): Boolean = true

    /** `Item::onRemoved` (src/items/item.cpp:785-791); game side effects are a PARITY GAP. */
    open fun onRemoved() {
        // ScriptEnvironment/unique-item bookkeeping needs g_game (PARITY GAP).
    }

    /** `Item::stopDecaying` (src/items/item.cpp:3497-3499); decay engine is a PARITY GAP. */
    open fun stopDecaying() {
        // g_decay().stopDecay(item) needs the decay scheduler (PARITY GAP).
    }

    // --- item type predicates (src/items/item.hpp:488-620) ---------------------

    /** `Item::isStackable` (src/items/item.hpp:488). */
    fun isStackable(): Boolean = type?.isStackable == true

    /** `Item::isPickupable` (src/items/item.hpp:514). */
    fun isPickupable(): Boolean = type?.isPickupable ?: true

    /** `Item::isMovable` (`ItemType::movable`, src/items/item.hpp:501). */
    fun isMovable(): Boolean = type?.isMovable == true

    /** `Item::isCorpse` (src/items/item.hpp:511). */
    fun isCorpse(): Boolean = type?.isCorpse == true

    /** `Item::isQuiver` (src/items/item.hpp:544). */
    fun isQuiver(): Boolean = type?.isQuiver == true

    /** `Item::getWeaponType` (src/items/item.hpp:339). */
    fun getWeaponType(): Int = type?.weaponType ?: 0

    /** `Item::getStackSize` (src/items/item.hpp:595). */
    fun getStackSize(): Int {
        if (isStackable()) {
            return type?.stackSize ?: 100
        }
        return 1
    }

    /** `Item::getItemCount` (src/items/item.hpp:603). */
    fun getItemCount(): Int = count

    /** `Item::setItemCount` (src/items/item.hpp:610). */
    fun setItemCount(n: Int) {
        count = n
    }

    /** `Item::getItemAmount` (src/items/item.hpp:607). */
    fun getItemAmount(): Long = count.toLong()

    /** `Item::isStoreItem` (src/items/item.hpp:290). */
    fun isStoreItem(): Boolean = type?.isStoreItem == true

    // --- ownership (src/items/item.hpp:279-292, item.cpp:818-830, 1549-1560) ---

    /** `Item::getOwnerId` (src/items/item.cpp:809-815); [Container] falls back to its children. */
    open fun getOwnerId(): Long = ((attributes[ItemAttribute.OWNER] as? Number)?.toLong() ?: 0L) and 0xFFFFFFFFL

    /** `Item::hasOwner` (src/items/item.hpp:287). */
    fun hasOwner(): Boolean = getOwnerId() != 0L

    /**
     * `Item::isOwner(uint32_t)` (src/items/item.cpp:818-830). The
     * `Player::getFirstID`/`getPlayerByID`/`getPlayerByGUID` game fallbacks
     * need `g_game` and are a PARITY GAP.
     */
    fun isOwner(ownerId: Long): Boolean = getOwnerId() == ownerId

    /** `Item::isOwner(creature)` (src/items/item.cpp:1549-1560). */
    fun isOwner(owner: Creature?): Boolean {
        if (owner == null) {
            return false
        }
        if (isOwner(owner.id)) {
            return true
        }
        val player = owner as? Player
        return player != null && isOwner(player.getGUID())
    }

    /** `Item::equals` (src/items/item.cpp:730-766). */
    fun equals(compareItem: Item?): Boolean {
        if (compareItem == null) {
            return false
        }
        if (id != compareItem.id) {
            return false
        }
        if (isStoreItem() != compareItem.isStoreItem()) {
            return false
        }
        if (getOwnerId() != compareItem.getOwnerId()) {
            return false
        }
        for ((attribute, value) in attributes) {
            if (attribute == ItemAttribute.STORE) {
                continue
            }
            val compareValue = compareItem.attributes[attribute] ?: continue
            if (ItemAttribute.isAttributeInteger(attribute) &&
                (value as? Number)?.toLong() != (compareValue as? Number)?.toLong()
            ) {
                return false
            }
            if (ItemAttribute.isAttributeString(attribute) && value != compareValue) {
                return false
            }
        }
        return true
    }

    /** Mirrors `Item::getClassification` (src/items/item.hpp:753). */
    fun getClassification(): Int = type?.upgradeClassification ?: 0

    fun hasAttribute(attribute: ItemAttribute): Boolean = attributes.containsKey(attribute)

    fun getAttribute(attribute: ItemAttribute): Any? = when {
        ItemAttribute.isAttributeInteger(attribute) -> attributes[attribute] as? Long ?: 0L
        ItemAttribute.isAttributeString(attribute) -> attributes[attribute] as? String ?: ""
        else -> null
    }

    fun setAttribute(attribute: ItemAttribute, value: Any?) {
        when {
            ItemAttribute.isAttributeInteger(attribute) -> {
                val intValue = (value as? Number)?.toLong() ?: return
                attributes[attribute] = intValue
            }

            ItemAttribute.isAttributeString(attribute) -> {
                val stringValue = value as? String ?: return
                if (stringValue.isEmpty()) return
                attributes[attribute] = stringValue
            }
        }
    }

    fun removeAttribute(attribute: ItemAttribute): Boolean = attributes.remove(attribute) != null

    /** Mirrors `item:getActionId()` / `item:setActionId()` (src/lua/functions/items/item_functions.cpp:291). */
    fun getActionId(): Int = ((attributes[ItemAttribute.ACTIONID] as? Long ?: 0L) and 0xFFFFL).toInt()

    fun setActionId(actionId: Int) {
        setAttribute(ItemAttribute.ACTIONID, actionId.toLong() and 0xFFFFL)
    }

    /** Mirrors `item:getUniqueId()` (src/lua/functions/items/item_functions.cpp:276). */
    fun getUniqueId(): Long = (attributes[ItemAttribute.UNIQUEID] as? Long ?: 0L) and 0xFFFFL

    fun setUniqueId(uniqueId: Long) {
        setAttribute(ItemAttribute.UNIQUEID, uniqueId and 0xFFFFL)
    }

    fun setDuration(time: Int) {
        setAttribute(ItemAttribute.DURATION, maxOf(0, time).toLong())
    }

    /** Mirrors `ItemProperties::getDuration` (src/items/item.cpp:3621). */
    fun getDuration(): Int {
        val decayState = getDecaying()
        return if (decayState == ItemDecayState.DECAYING_TRUE || decayState == ItemDecayState.DECAYING_STOPPING) {
            val timestamp = attributes[ItemAttribute.DURATION_TIMESTAMP] as? Long ?: 0L
            maxOf(0L, timestamp - otsysTime()).toInt()
        } else {
            (attributes[ItemAttribute.DURATION] as? Long ?: 0L).toInt()
        }
    }

    /** Mirrors `ItemProperties::setDecaying` (src/items/item.hpp:121). */
    fun setDecaying(decayState: ItemDecayState) {
        setAttribute(ItemAttribute.DECAYSTATE, decayState.value.toLong())
        if (decayState == ItemDecayState.DECAYING_FALSE) {
            removeAttribute(ItemAttribute.DURATION_TIMESTAMP)
        }
    }

    fun getDecaying(): ItemDecayState =
        ItemDecayState.fromValue((attributes[ItemAttribute.DECAYSTATE] as? Long ?: 0L).toInt())

    /** `Item::getSubType` (src/items/item.cpp:867-877). */
    fun getSubType(): Int {
        val itemType = type
        return when {
            itemType?.isFluidContainer == true || itemType?.isSplash == true ->
                ((attributes[ItemAttribute.FLUIDTYPE] as? Number)?.toInt() ?: 0)

            itemType?.isStackable == true -> count
            (itemType?.charges ?: 0) != 0 -> ((attributes[ItemAttribute.CHARGES] as? Number)?.toInt() ?: 0)
            else -> count
        }
    }

    /** `Item::setSubType` (src/items/item.cpp:899-910). */
    fun setSubType(n: Int) {
        val itemType = type
        when {
            itemType?.isFluidContainer == true || itemType?.isSplash == true ->
                setAttribute(ItemAttribute.FLUIDTYPE, n.toLong())

            itemType?.isStackable == true -> count = n
            (itemType?.charges ?: 0) != 0 -> setAttribute(ItemAttribute.CHARGES, n.toLong())
            else -> count = n
        }
    }

    /** `Item::getBed` (src/items/item.hpp:257). */
    open fun getBed(): BedItem? = null

    /** `Item::getDoor` (src/items/item.hpp:253). */
    open fun getDoor(): Door? = null

    /** `Item::getContainer` (src/items/item.hpp:249). */
    open fun getContainer(): Container? = null

    /** `Item::isSavedToHouses` (src/items/item.cpp:2332-2335). */
    fun isSavedToHouses(): Boolean {
        val itemType = type ?: return false
        val container = getContainer()
        return itemType.isMovable ||
            itemType.isWrappable ||
            itemType.isCarpet ||
            getDoor() != null ||
            (container != null && !container.isEmpty()) ||
            itemType.canWriteText ||
            getBed() != null ||
            itemType.transformOnUse != 0
    }

    /** `Item::serializeAttr` (src/items/item.cpp:1368-1539). */
    open fun serializeAttr(propWriteStream: PropWriteStream) {
        val itemType = type

        val timeStamp = intAttribute(ItemAttribute.STORE)
        if (timeStamp != 0L) {
            propWriteStream.writeU8(ATTR_STORE)
            propWriteStream.writeI64(timeStamp)
        }

        if (itemType?.isStackable == true || itemType?.isFluidContainer == true || itemType?.isSplash == true) {
            propWriteStream.writeU8(ATTR_COUNT)
            propWriteStream.writeU8(getSubType())
        }

        val charges = intAttribute(ItemAttribute.CHARGES)
        if (charges != 0L) {
            propWriteStream.writeU8(ATTR_CHARGES)
            propWriteStream.writeU16(charges.toInt())
        }

        if (itemType?.isMovable == true) {
            val actionId = intAttribute(ItemAttribute.ACTIONID)
            if (actionId != 0L) {
                propWriteStream.writeU8(ATTR_ACTION_ID)
                propWriteStream.writeU16(actionId.toInt())
            }
        }

        val text = stringAttribute(ItemAttribute.TEXT)
        if (text.isNotEmpty()) {
            propWriteStream.writeU8(ATTR_TEXT)
            propWriteStream.writeString(text)
        }

        val writtenDate = intAttribute(ItemAttribute.DATE)
        if (writtenDate != 0L) {
            propWriteStream.writeU8(ATTR_WRITTENDATE)
            propWriteStream.writeU64(writtenDate)
        }

        val writer = stringAttribute(ItemAttribute.WRITER)
        if (writer.isNotEmpty()) {
            propWriteStream.writeU8(ATTR_WRITTENBY)
            propWriteStream.writeString(writer)
        }

        val description = stringAttribute(ItemAttribute.DESCRIPTION)
        if (description.isNotEmpty()) {
            propWriteStream.writeU8(ATTR_DESC)
            propWriteStream.writeString(description)
        }

        if (attributes.containsKey(ItemAttribute.DURATION)) {
            propWriteStream.writeU8(ATTR_DURATION)
            propWriteStream.writeI32(getDuration())
        }

        val decayState = getDecaying()
        if (decayState == ItemDecayState.DECAYING_TRUE || decayState == ItemDecayState.DECAYING_PENDING) {
            propWriteStream.writeU8(ATTR_DECAYING_STATE)
            propWriteStream.writeU8(decayState.value)
        }

        if (attributes.containsKey(ItemAttribute.NAME)) {
            propWriteStream.writeU8(ATTR_NAME)
            propWriteStream.writeString(stringAttribute(ItemAttribute.NAME))
        }

        if (attributes.containsKey(ItemAttribute.ARTICLE)) {
            propWriteStream.writeU8(ATTR_ARTICLE)
            propWriteStream.writeString(stringAttribute(ItemAttribute.ARTICLE))
        }

        if (attributes.containsKey(ItemAttribute.PLURALNAME)) {
            propWriteStream.writeU8(ATTR_PLURALNAME)
            propWriteStream.writeString(stringAttribute(ItemAttribute.PLURALNAME))
        }

        if (attributes.containsKey(ItemAttribute.WEIGHT)) {
            propWriteStream.writeU8(ATTR_WEIGHT)
            propWriteStream.writeU32(intAttribute(ItemAttribute.WEIGHT) and 0xFFFFFFFFL)
        }

        if (attributes.containsKey(ItemAttribute.ATTACK)) {
            propWriteStream.writeU8(ATTR_ATTACK)
            propWriteStream.writeI32(intAttribute(ItemAttribute.ATTACK).toInt())
        }

        if (attributes.containsKey(ItemAttribute.DEFENSE)) {
            propWriteStream.writeU8(ATTR_DEFENSE)
            propWriteStream.writeI32(intAttribute(ItemAttribute.DEFENSE).toInt())
        }

        if (attributes.containsKey(ItemAttribute.EXTRADEFENSE)) {
            propWriteStream.writeU8(ATTR_EXTRADEFENSE)
            propWriteStream.writeI32(intAttribute(ItemAttribute.EXTRADEFENSE).toInt())
        }

        if (attributes.containsKey(ItemAttribute.MANTRA)) {
            propWriteStream.writeU8(ATTR_MANTRA)
            propWriteStream.writeI32(intAttribute(ItemAttribute.MANTRA).toInt())
        }

        if (attributes.containsKey(ItemAttribute.IMBUEMENT_SLOT)) {
            propWriteStream.writeU8(ATTR_IMBUEMENT_SLOT)
            propWriteStream.writeI32(intAttribute(ItemAttribute.IMBUEMENT_SLOT).toInt())
        }

        if (attributes.containsKey(ItemAttribute.OPENCONTAINER)) {
            propWriteStream.writeU8(ATTR_OPENCONTAINER)
            propWriteStream.writeU8(intAttribute(ItemAttribute.OPENCONTAINER).toInt())
        }

        if (attributes.containsKey(ItemAttribute.ARMOR)) {
            propWriteStream.writeU8(ATTR_ARMOR)
            propWriteStream.writeI32(intAttribute(ItemAttribute.ARMOR).toInt())
        }

        if (attributes.containsKey(ItemAttribute.HITCHANCE)) {
            propWriteStream.writeU8(ATTR_HITCHANCE)
            propWriteStream.writeU8(intAttribute(ItemAttribute.HITCHANCE).toInt())
        }

        if (attributes.containsKey(ItemAttribute.SHOOTRANGE)) {
            propWriteStream.writeU8(ATTR_SHOOTRANGE)
            propWriteStream.writeU8(intAttribute(ItemAttribute.SHOOTRANGE).toInt())
        }

        if (attributes.containsKey(ItemAttribute.SPECIAL)) {
            propWriteStream.writeU8(ATTR_SPECIAL)
            propWriteStream.writeString(stringAttribute(ItemAttribute.SPECIAL))
        }

        if (attributes.containsKey(ItemAttribute.QUICKLOOTCONTAINER)) {
            propWriteStream.writeU8(ATTR_QUICKLOOTCONTAINER)
            propWriteStream.writeU32(intAttribute(ItemAttribute.QUICKLOOTCONTAINER) and 0xFFFFFFFFL)
        }

        if (attributes.containsKey(ItemAttribute.TIER)) {
            propWriteStream.writeU8(ATTR_TIER)
            propWriteStream.writeU8(intAttribute(ItemAttribute.TIER).toInt())
        }

        if (attributes.containsKey(ItemAttribute.AMOUNT)) {
            propWriteStream.writeU8(ATTR_AMOUNT)
            propWriteStream.writeU16(intAttribute(ItemAttribute.AMOUNT).toInt())
        }

        if (attributes.containsKey(ItemAttribute.STORE_INBOX_CATEGORY)) {
            propWriteStream.writeU8(ATTR_STORE_INBOX_CATEGORY)
            propWriteStream.writeString(stringAttribute(ItemAttribute.STORE_INBOX_CATEGORY))
        }

        if (attributes.containsKey(ItemAttribute.OWNER)) {
            propWriteStream.writeU8(ATTR_OWNER)
            propWriteStream.writeU32(intAttribute(ItemAttribute.OWNER) and 0xFFFFFFFFL)
        }

        // ATTR_CUSTOM custom attributes have no Kotlin store yet (PARITY GAP).

        if (attributes.containsKey(ItemAttribute.OBTAINCONTAINER)) {
            propWriteStream.writeU8(ATTR_OBTAINCONTAINER)
            propWriteStream.writeU32(intAttribute(ItemAttribute.OBTAINCONTAINER) and 0xFFFFFFFFL)
        }
    }

    /** `Item::readAttr` (src/items/item.cpp:912-1349). */
    open fun readAttr(attr: Int, propStream: PropStream): AttrReadValue {
        when (attr) {
            ATTR_STORE -> {
                val timeStamp = propStream.readI64() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.STORE, timeStamp)
            }

            ATTR_COUNT, ATTR_RUNE_CHARGES -> {
                val charges = propStream.readU8() ?: return AttrReadValue.ATTR_READ_ERROR
                setSubType(charges)
            }

            ATTR_ACTION_ID -> {
                val actionId = propStream.readU16() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.ACTIONID, actionId.toLong())
            }

            ATTR_UNIQUE_ID -> {
                val uniqueId = propStream.readU16() ?: return AttrReadValue.ATTR_READ_ERROR
                setUniqueId(uniqueId.toLong())
            }

            ATTR_TEXT -> {
                val text = propStream.readString() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.TEXT, text)
            }

            ATTR_WRITTENDATE -> {
                val writtenDate = propStream.readU64() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.DATE, writtenDate)
            }

            ATTR_WRITTENBY -> {
                val writer = propStream.readString() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.WRITER, writer)
            }

            ATTR_DESC -> {
                val text = propStream.readString() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.DESCRIPTION, text)
            }

            ATTR_CHARGES -> {
                val charges = propStream.readU16() ?: return AttrReadValue.ATTR_READ_ERROR
                setSubType(charges)
            }

            ATTR_DURATION -> {
                val duration = propStream.readI32() ?: return AttrReadValue.ATTR_READ_ERROR
                setDuration(duration)
            }

            ATTR_DECAYING_STATE -> {
                val state = propStream.readU8() ?: return AttrReadValue.ATTR_READ_ERROR
                if (state != ItemDecayState.DECAYING_FALSE.value) {
                    setDecaying(ItemDecayState.DECAYING_PENDING)
                }
            }

            ATTR_NAME -> {
                val name = propStream.readString() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.NAME, name)
            }

            ATTR_ARTICLE -> {
                val article = propStream.readString() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.ARTICLE, article)
            }

            ATTR_PLURALNAME -> {
                val pluralName = propStream.readString() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.PLURALNAME, pluralName)
            }

            ATTR_WEIGHT -> {
                val weight = propStream.readU32() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.WEIGHT, weight)
            }

            ATTR_ATTACK -> {
                val attack = propStream.readI32() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.ATTACK, attack.toLong())
            }

            ATTR_DEFENSE -> {
                val defense = propStream.readI32() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.DEFENSE, defense.toLong())
            }

            ATTR_EXTRADEFENSE -> {
                val extraDefense = propStream.readI32() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.EXTRADEFENSE, extraDefense.toLong())
            }

            ATTR_MANTRA -> {
                val mantra = propStream.readI32() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.MANTRA, mantra.toLong())
            }

            ATTR_IMBUEMENT_SLOT -> {
                val imbuementSlot = propStream.readI32() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.IMBUEMENT_SLOT, imbuementSlot.toLong())
            }

            ATTR_OPENCONTAINER -> {
                val openContainer = propStream.readU8() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.OPENCONTAINER, openContainer.toLong())
            }

            ATTR_ARMOR -> {
                val armor = propStream.readI32() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.ARMOR, armor.toLong())
            }

            ATTR_HITCHANCE -> {
                val hitChance = propStream.readU8() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.HITCHANCE, hitChance.toByte().toLong())
            }

            ATTR_SHOOTRANGE -> {
                val shootRange = propStream.readU8() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.SHOOTRANGE, shootRange.toLong())
            }

            ATTR_SPECIAL -> {
                val special = propStream.readString() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.SPECIAL, special)
            }

            ATTR_QUICKLOOTCONTAINER -> {
                val flags = propStream.readU32() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.QUICKLOOTCONTAINER, flags)
            }

            // These are handled through derived classes; when they reach the
            // base item the items.xml changed since the map was saved, so the
            // payload is skipped (src/items/item.cpp:1184-1210).
            ATTR_DEPOT_ID -> {
                if (!propStream.skip(2)) return AttrReadValue.ATTR_READ_ERROR
            }

            ATTR_HOUSEDOORID -> {
                if (!propStream.skip(1)) return AttrReadValue.ATTR_READ_ERROR
            }

            ATTR_SLEEPERGUID -> {
                if (!propStream.skip(4)) return AttrReadValue.ATTR_READ_ERROR
            }

            ATTR_SLEEPSTART -> {
                if (!propStream.skip(4)) return AttrReadValue.ATTR_READ_ERROR
            }

            ATTR_TELE_DEST -> {
                if (!propStream.skip(5)) return AttrReadValue.ATTR_READ_ERROR
            }

            ATTR_TIER -> {
                val tier = propStream.readU8() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.TIER, tier.toLong())
            }

            ATTR_AMOUNT -> {
                val amount = propStream.readU16() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.AMOUNT, amount.toLong())
            }

            ATTR_STORE_INBOX_CATEGORY -> {
                val category = propStream.readString() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.STORE_INBOX_CATEGORY, category)
            }

            ATTR_OWNER -> {
                val ownerId = propStream.readU32() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.OWNER, ownerId)
            }

            ATTR_OBTAINCONTAINER -> {
                val flags = propStream.readU32() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.OBTAINCONTAINER, flags)
            }

            // ATTR_CUSTOM / ATTR_CUSTOM_ATTRIBUTES have no Kotlin store (PARITY GAP).
            ATTR_CUSTOM -> {
                val size = propStream.readU64() ?: return AttrReadValue.ATTR_READ_ERROR
                for (index in 0 until size) {
                    if (propStream.readString() == null) return AttrReadValue.ATTR_READ_ERROR
                    if (!skipCustomAttribute(propStream)) return AttrReadValue.ATTR_READ_ERROR
                }
            }

            ATTR_CONTAINER_ITEMS -> return AttrReadValue.ATTR_READ_ERROR
            else -> return AttrReadValue.ATTR_READ_ERROR
        }

        return AttrReadValue.ATTR_READ_CONTINUE
    }

    /** `Item::unserializeAttr` (src/items/item.cpp:1351-1366). */
    fun unserializeAttr(propStream: PropStream): Boolean {
        while (true) {
            val attrType = propStream.readU8() ?: break
            if (attrType == ATTR_NONE) {
                break
            }
            when (readAttr(attrType, propStream)) {
                AttrReadValue.ATTR_READ_ERROR -> return false
                AttrReadValue.ATTR_READ_END -> return true
                AttrReadValue.ATTR_READ_CONTINUE -> {}
            }
        }
        return true
    }

    private fun intAttribute(attribute: ItemAttribute): Long =
        (attributes[attribute] as? Number)?.toLong() ?: 0L

    private fun stringAttribute(attribute: ItemAttribute): String =
        attributes[attribute] as? String ?: ""

    /**
     * Consumes one `CustomAttribute` payload (src/items/functions/item/custom_attribute.cpp:105-153).
     * Custom attributes are not stored by the Kotlin item yet; the payload
     * walk only keeps the stream readable (PARITY GAP).
     */
    private fun skipCustomAttribute(propStream: PropStream): Boolean {
        val type = propStream.readU8() ?: return false
        return when (type) {
            1 -> propStream.readString() != null
            2 -> propStream.skip(8)
            3 -> propStream.skip(8)
            4 -> propStream.skip(1)
            else -> false
        }
    }

    private fun otsysTime(): Long = System.currentTimeMillis()

    companion object {
        /** `Item::CreateItem` (src/items/item.cpp:393-472), including the derived item types. */
        fun create(id: Int, count: Int = 1, registry: ItemTypeRegistry? = null): Item {
            val type = registry?.get(id)
            return when {
                type?.isBed == true -> BedItem(id, type = type)
                type?.isContainer == true -> Container.create(id, registry)
                type?.isDoor == true -> Door(id, type = type)
                else -> Item(id, count = count, type = type)
            }
        }

        /** `Item::countByType` (src/items/item.hpp:616-623). */
        fun countByType(item: Item?, subType: Int): Int {
            if (item == null) {
                return 0
            }
            if (subType == -1 || subType == item.getSubType()) {
                return item.getItemCount()
            }
            return 0
        }
    }
}
