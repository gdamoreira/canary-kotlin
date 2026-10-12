package org.opentibia.canary.items

data class ItemType(
    val id: Int,
    val name: String,
    val isStackable: Boolean = false,
    val isContainer: Boolean = false,
    val capacity: Int = 0,
    val weight: Double = 0.0,
    val isPickupable: Boolean = true
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

open class Item(
    val id: Int,
    var count: Int = 1,
    val type: ItemType? = null
) {
    private val attributes = mutableMapOf<ItemAttribute, Any>()

    open val weight: Double
        get() = (type?.weight ?: 0.0) * if (type?.isStackable == true) count else 1

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

    private fun otsysTime(): Long = System.currentTimeMillis()

    companion object {
        fun create(id: Int, count: Int = 1, registry: ItemTypeRegistry? = null): Item {
            val type = registry?.get(id)
            return if (type?.isContainer == true) {
                Container(id, capacity = type.capacity, type = type)
            } else {
                Item(id, count = count, type = type)
            }
        }
    }
}

class Container(
    id: Int,
    val capacity: Int,
    type: ItemType? = null
) : Item(id, count = 1, type = type) {
    private val _items = mutableListOf<Item>()
    val items: List<Item> get() = _items

    val totalWeight: Double
        get() = (type?.weight ?: 0.0) + _items.sumOf { it.weight }

    fun addItem(item: Item): Boolean {
        if (_items.size >= capacity) return false
        _items.add(item)
        return true
    }

    companion object {
        fun create(id: Int, capacity: Int, registry: ItemTypeRegistry? = null): Container {
            val type = registry?.get(id)
            return Container(id, capacity = capacity, type = type)
        }
    }
}
