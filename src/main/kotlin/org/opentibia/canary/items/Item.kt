package org.opentibia.canary.items

import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream
import org.opentibia.canary.map.house.Door

data class ItemType(
    var id: Int,
    var name: String,
    var isStackable: Boolean = false,
    var isContainer: Boolean = false,
    var capacity: Int = 0,
    var weight: Double = 0.0,
    /** Mirrors `ItemType::upgradeClassification` (src/items/items.hpp:329). */
    var upgradeClassification: Int = 0,
    var isPickupable: Boolean = true,
    /** Mirrors `ItemType::magicEffect` (src/items/items.hpp:318); default `CONST_ME_NONE` (0). */
    var magicEffect: Int = 0,
    /** `ItemType::movable` (src/items/items.hpp:288), the house-serialization guard. */
    var isMovable: Boolean = false,
    /** `ItemType::isBed()` (src/items/items.hpp:378). */
    var isBed: Boolean = false,
    /** `ItemType::bedPartOf` (src/items/items.hpp:468), the partner id `BedItem::isBedComplete` checks. */
    var bedPartOf: Int = 0,
    /** `ItemType::isCarpet()` (src/items/items.hpp:420). */
    var isCarpet: Boolean = false,
    /** `ItemType::isTrashHolder()` (src/items/items.hpp:410). */
    var isTrashHolder: Boolean = false,
    /** `ItemType::isDoor()` (src/items/items.hpp:414). */
    var isDoor: Boolean = false,
    /** `ItemType::isWrappable()` (src/items/items.hpp:411). */
    var isWrappable: Boolean = false,
    /** `ItemType::canWriteText` (src/items/items.hpp:342). */
    var canWriteText: Boolean = false,
    /** `ItemType::m_transformOnUse` (src/items/items.hpp:347). */
    var transformOnUse: Int = 0,
    /** `ItemType::isFluidContainer()` (src/items/items.hpp:416). */
    var isFluidContainer: Boolean = false,
    /** `ItemType::isSplash()` (src/items/items.hpp:417). */
    var isSplash: Boolean = false,
    /** `ItemType::isGroundTile()` (src/items/items.hpp:407). */
    var isGroundTile: Boolean = false,
    /** `ItemType::charges` (src/items/items.hpp:290), used by `getSubType`/`setSubType`. */
    var charges: Int = 0,
    /** `ItemType::group` (src/items/items.hpp:262), default `ITEM_GROUP_NONE`. */
    var group: ItemGroup_t = ItemGroup_t.ITEM_GROUP_NONE,
    /** `ItemType::type` (src/items/items.hpp:263), default `ITEM_TYPE_NONE`. */
    var type: ItemTypes_t = ItemTypes_t.ITEM_TYPE_NONE,
    /** `ItemType::description` (src/items/items.hpp:269). */
    var description: String = "",
    /** `ItemType::article` (src/items/items.hpp:267). */
    var article: String = "",
    /** `ItemType::pluralName` (src/items/items.hpp:268). */
    var pluralName: String = "",
    /** `ItemType::showCount` (src/items/items.hpp:375), Canary's default is true. */
    var showCount: Boolean = true,
    /** `ItemType::animationType` (src/items/items.hpp:302). */
    var animationType: ItemAnimation_t = ItemAnimation_t.ANIMATION_NONE,
    /** `ItemType::alwaysOnTopOrder` (src/items/items.hpp:330). */
    var alwaysOnTopOrder: Int = 0,
    /** `ItemType::slotPosition` (src/items/items.hpp:312), default `SLOTP_HAND` (48). */
    var slotPosition: Int = Items.SLOTP_HAND,
    /** `ItemType::lightLevel` (src/items/items.hpp:331). */
    var lightLevel: Int = 0,
    /** `ItemType::lightColor` (src/items/items.hpp:332). */
    var lightColor: Int = 0,
    /** `ItemType::speed` (src/items/items.hpp:313). */
    var speed: Int = 0,
    /** `ItemType::wareId` (src/items/items.hpp:314). */
    var wareId: Int = 0,
    /** `ItemType::isCorpse` (src/items/items.hpp:378). */
    var isCorpse: Boolean = false,
    /** `ItemType::forceUse` (src/items/items.hpp:349). */
    var forceUse: Boolean = false,
    /** `ItemType::hasHeight` (src/items/items.hpp:350). */
    var hasHeight: Boolean = false,
    /** `ItemType::blockSolid` (src/items/items.hpp:352). */
    var blockSolid: Boolean = false,
    /** `ItemType::blockProjectile` (src/items/items.hpp:354). */
    var blockProjectile: Boolean = false,
    /** `ItemType::blockPathFind` (src/items/items.hpp:355). */
    var blockPathFind: Boolean = false,
    /** `ItemType::rotatable` (src/items/items.hpp:361). */
    var rotatable: Boolean = false,
    /** `ItemType::wrapContainer` (src/items/items.hpp:363). */
    var wrapContainer: Boolean = false,
    /** `ItemType::wrapableTo` (src/items/items.hpp:297). */
    var wrapableTo: Int = 0,
    /** `ItemType::wrapable` (src/items/items.hpp:362). */
    var wrapable: Boolean = false,
    /** `ItemType::multiUse` (src/items/items.hpp:364). */
    var multiUse: Boolean = false,
    /** `ItemType::canReadText` (src/items/items.hpp:366). */
    var canReadText: Boolean = false,
    /** `ItemType::isVertical` (src/items/items.hpp:368). */
    var isVertical: Boolean = false,
    /** `ItemType::isHorizontal` (src/items/items.hpp:369). */
    var isHorizontal: Boolean = false,
    /** `ItemType::isHangable` (src/items/items.hpp:370). */
    var isHangable: Boolean = false,
    /** `ItemType::lookThrough` (src/items/items.hpp:372). */
    var lookThrough: Boolean = false,
    /** `ItemType::isPodium` (src/items/items.hpp:377). */
    var isPodium: Boolean = false,
    /** `ItemType::wearOut` (src/items/items.hpp:344). */
    var wearOut: Boolean = false,
    /** `ItemType::clockExpire` (src/items/items.hpp:345). */
    var clockExpire: Boolean = false,
    /** `ItemType::expire` (src/items/items.hpp:346). */
    var expire: Boolean = false,
    /** `ItemType::expireStop` (src/items/items.hpp:347). */
    var expireStop: Boolean = false,
    /** `ItemType::isWrapKit` (src/items/items.hpp:381). */
    var isWrapKit: Boolean = false,
    /** `ItemType::isDualWielding` (src/items/items.hpp:385). */
    var isDualWielding: Boolean = false,
    /** `ItemType::proficiencyId` (src/items/items.hpp:337). */
    var proficiencyId: Int = 0,
    /** `ItemType::m_isMagicShieldPotion` (src/items/items.hpp:386). */
    var isMagicShieldPotion: Boolean = false,
    /** `ItemType::loaded` (src/items/items.hpp:379). */
    var loaded: Boolean = false,
    /** `ItemType::weaponType` (src/items/items.hpp:321), default `WEAPON_NONE` (0). */
    var weaponType: Int = Items.WEAPON_NONE,
    /** `ItemType::ammoType` (src/items/items.hpp:322), default `AMMO_NONE` (0). */
    var ammoType: Int = Items.AMMO_NONE,
    /** `ItemType::attack` (src/items/items.hpp:289). */
    var attack: Int = 0,
    /** `ItemType::defense` (src/items/items.hpp:290). */
    var defense: Int = 0,
    /** `ItemType::extraDefense` (src/items/items.hpp:291). */
    var extraDefense: Int = 0,
    /** `ItemType::armor` (src/items/items.hpp:292). */
    var armor: Int = 0,
    /** `ItemType::transformToFree` (src/items/items.hpp:305). */
    var transformToFree: Int = 0,
    /** `ItemType::transformToOnUse[2]` (src/items/items.hpp:304), index 0 female, 1 male. */
    var transformToOnUse: List<Int> = listOf(0, 0),
    /** `ItemType::augments` (src/items/items.hpp:341). */
    var augments: MutableList<AugmentInfo> = mutableListOf(),
    /** `ItemType::imbuementTypes` (src/items/items.hpp:327). */
    var imbuementTypes: MutableMap<ImbuementTypes_t, Int> = mutableMapOf(),
) {

    /** `ItemType::parseAugmentDescription` (src/items/items.cpp:82-97). */
    fun parseAugmentDescription(inspect: Boolean = false): String {
        if (augments.isEmpty()) {
            return ""
        }

        val descriptions = augments.joinToString(", ") { getFormattedAugmentDescription(it) }
        return if (inspect) {
            "$descriptions."
        } else {
            "\nAugments: ($descriptions)."
        }
    }

    /** `ItemType::getFormattedAugmentDescription` (src/items/items.cpp:99-116). */
    fun getFormattedAugmentDescription(augmentInfo: AugmentInfo): String {
        val augmentName = Items.getAugmentNameByType(augmentInfo.type)
        val augmentSpellNameCapitalized = capitalizeWordsIgnoringString(augmentInfo.spellName, " of ")

        val signal = if (augmentInfo.value > 0) '-' else '+'

        if (Items.isAugmentWithoutValueDescription(augmentInfo.type)) {
            return "$augmentSpellNameCapitalized -> $augmentName"
        }
        if (augmentInfo.type == Augment_t.Cooldown) {
            return "$augmentSpellNameCapitalized -> $signal${augmentInfo.value / 1000}s $augmentName"
        }
        return "$augmentSpellNameCapitalized -> ${signedPercent(augmentInfo.value)}% $augmentName"
    }

    /** `ItemType::addAugment` (src/items/items.cpp:118-121). */
    fun addAugment(spellName: String, augmentType: Augment_t, value: Int) {
        augments.add(AugmentInfo(spellName, augmentType, value))
    }

    /** `ItemType::setImbuementType` (src/items/items.cpp:123-125). */
    fun setImbuementType(imbuementType: ImbuementTypes_t, slotMaxTier: Int) {
        imbuementTypes[imbuementType] = minOf(Items.IMBUEMENT_MAX_TIER, slotMaxTier)
    }

    /** `ItemType::triggerExhaustion` (src/items/items.cpp:127-129). */
    fun triggerExhaustion(): Boolean =
        type == ItemTypes_t.ITEM_TYPE_RUNE ||
            (type == ItemTypes_t.ITEM_TYPE_POTION && !isMagicShieldPotion)

    /** `ItemType::getPluralName` (src/items/items.hpp:239-253). */
    @JvmName("getPluralNameValue")
    fun getPluralName(): String {
        if (pluralName.isNotEmpty()) {
            return pluralName
        }
        if (!showCount) {
            return name
        }
        return name + "s"
    }
}

/** `fmt::format("{:+}", value)` for the int32 augment value. */
private fun signedPercent(value: Int): String = if (value >= 0) "+$value" else "$value"

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
    val id: Int,
    var count: Int = 1,
    val type: ItemType? = null
) {
    private val attributes = mutableMapOf<ItemAttribute, Any>()

    open val weight: Double
        get() = (type?.weight ?: 0.0) * if (type?.isStackable == true) count else 1

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
                type?.isContainer == true -> Container(id, capacity = type.capacity, type = type)
                type?.isDoor == true -> Door(id, type = type)
                else -> Item(id, count = count, type = type)
            }
        }
    }
}

class Container(
    id: Int,
    val capacity: Int,
    type: ItemType? = null
) : Item(id, count = 1, type = type), Cylinder {
    private val _items = mutableListOf<Item>()
    val items: List<Item> get() = _items

    /** `Container::serializationCount` (src/items/containers/container.hpp:341). */
    var serializationCount: Int = 0

    val totalWeight: Double
        get() = (type?.weight ?: 0.0) + _items.sumOf { it.weight }

    fun addItem(item: Item): Boolean {
        if (_items.size >= capacity) return false
        _items.add(item)
        return true
    }

    /** `Container::empty` (src/items/containers/container.hpp:216-218). */
    fun isEmpty(): Boolean = _items.isEmpty()

    fun size(): Int = _items.size

    /** `Container::getReversedItems` (src/items/containers/container.hpp:227-232). */
    fun getReversedItems(): List<Item> = _items.asReversed()

    /** `Container::internalAddThing` (`src/items/containers/container.hpp`). */
    override fun internalAddThing(item: Item) {
        _items.add(item)
    }

    override fun getContainer(): Container = this

    /** `Container::readAttr` (src/items/containers/container.cpp:171-181). */
    override fun readAttr(attr: Int, propStream: PropStream): AttrReadValue {
        if (attr == ATTR_CONTAINER_ITEMS) {
            val count = propStream.readU32() ?: return AttrReadValue.ATTR_READ_ERROR
            serializationCount = count.toInt()
            return AttrReadValue.ATTR_READ_END
        }
        return super.readAttr(attr, propStream)
    }

    companion object {
        fun create(id: Int, capacity: Int, registry: ItemTypeRegistry? = null): Container {
            val type = registry?.get(id)
            return Container(id, capacity = capacity, type = type)
        }
    }
}
