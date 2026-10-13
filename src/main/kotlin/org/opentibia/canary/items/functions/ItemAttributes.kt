package org.opentibia.canary.items.functions

import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream
import org.opentibia.canary.items.ItemAttribute as ItemAttribute_t
import java.util.TreeMap

/**
 * `ItemAttributeHelper` (src/items/functions/item/attribute.hpp:15-67).
 *
 * Integer/string classification of `ItemAttribute_t`; the value lists are the
 * exact C++ switch cases.
 */
object ItemAttributeHelper {
    fun isAttributeInteger(type: ItemAttribute_t): Boolean = ItemAttribute_t.isAttributeInteger(type)

    fun isAttributeString(type: ItemAttribute_t): Boolean = ItemAttribute_t.isAttributeString(type)
}

/**
 * `Attributes` (src/items/functions/item/attribute.hpp:69-125).
 *
 * Holds one `ItemAttribute_t` plus its `std::variant<int64_t, std::string>`
 * value. The C++ default value is a value-initialized variant, which always
 * selects the first alternative (`int64_t 0`), even for string attributes;
 * [getDefaultValueForType] reproduces that.
 */
class Attributes(
    private val type: ItemAttribute_t,
) {
    private var value: Any = getDefaultValueForType(type)

    fun getAttributeType(): ItemAttribute_t = type

    fun getDefaultValueForType(attributeType: ItemAttribute_t): Any =
        if (ItemAttributeHelper.isAttributeInteger(attributeType)) 0L else 0L

    fun setValue(newValue: Long) {
        value = newValue
    }

    fun setValue(newValue: String) {
        value = newValue
    }

    fun getInteger(): Long = value as? Long ?: 0L

    fun getString(): String = value as? String ?: ""
}

/**
 * `CustomAttribute` (src/items/functions/item/custom_attribute.hpp/.cpp).
 *
 * Custom attributes carry a `std::variant<int64_t, std::string, double, bool>`.
 * The Lua `pushToLua` binding is a scripting seam and stays out of the items
 * layer (AGENTS.md 1.4).
 */
class CustomAttribute {
    private var stringKey: String = ""

    @PublishedApi
    internal var value: Any = 0L

    constructor()

    constructor(initStringKey: String, initInt64Value: Long) {
        stringKey = initStringKey
        value = initInt64Value
    }

    constructor(initStringKey: String, initStringValue: String) {
        stringKey = initStringKey
        value = initStringValue
    }

    constructor(initStringKey: String, initDoubleValue: Double) {
        stringKey = initStringKey
        value = initDoubleValue
    }

    constructor(initStringKey: String, initBoolValue: Boolean) {
        stringKey = initStringKey
        value = initBoolValue
    }

    fun getStringKey(): String = stringKey

    fun getInteger(): Long = value as? Long ?: 0L

    fun getString(): String = value as? String ?: ""

    fun getDouble(): Double = value as? Double ?: 0.0

    fun getBool(): Boolean = value as? Boolean ?: false

    /**
     * `CustomAttribute::getAttribute<T>` (custom_attribute.hpp:26-42):
     * `string`/`double`/`bool` return the matching alternative, every other type
     * is `std::clamp(static_cast<T>(getInteger()), min, max)`.
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> getAttribute(): T = when (T::class) {
        String::class -> getString() as T
        Double::class -> getDouble() as T
        Boolean::class -> getBool() as T
        Byte::class -> getInteger().toByte().coerceIn(Byte.MIN_VALUE, Byte.MAX_VALUE) as T
        Short::class -> getInteger().toShort().coerceIn(Short.MIN_VALUE, Short.MAX_VALUE) as T
        Int::class -> getInteger().toInt() as T
        Long::class -> getInteger() as T
        UByte::class -> getInteger().toUByte().coerceIn(UByte.MIN_VALUE, UByte.MAX_VALUE) as T
        UShort::class -> getInteger().toUShort().coerceIn(UShort.MIN_VALUE, UShort.MAX_VALUE) as T
        UInt::class -> getInteger().toUInt() as T
        ULong::class -> getInteger().toULong() as T
        Float::class -> getInteger().toFloat() as T
        else -> getInteger() as T
    }

    fun setValue(newValue: Long) {
        if (value is Long) {
            value = newValue
        }
    }

    fun setValue(newValue: String) {
        if (value is String) {
            value = newValue
        }
    }

    fun setValue(newValue: Double) {
        if (value is Double) {
            value = newValue
        }
    }

    fun setValue(newValue: Boolean) {
        if (value is Boolean) {
            value = newValue
        }
    }

    /** `CustomAttribute::hasValue<T>` (custom_attribute.hpp:70-73). */
    inline fun <reified T : Any> hasValue(): Boolean = when (T::class) {
        String::class -> value is String
        Double::class -> value is Double
        Boolean::class -> value is Boolean
        Long::class -> value is Long
        Int::class -> value is Int
        Float::class -> value is Float
        else -> false
    }

    /** `CustomAttribute::serialize` (custom_attribute.cpp:88-103). */
    fun serialize(propWriteStream: PropWriteStream) {
        when (val current = value) {
            is String -> {
                propWriteStream.writeU8(1)
                propWriteStream.writeString(current)
            }

            is Long -> {
                propWriteStream.writeU8(2)
                propWriteStream.writeI64(current)
            }

            is Double -> {
                propWriteStream.writeU8(3)
                propWriteStream.writeU64(java.lang.Double.doubleToRawLongBits(current))
            }

            is Boolean -> {
                propWriteStream.writeU8(4)
                propWriteStream.writeU8(if (current) 1 else 0)
            }
        }
    }

    /**
     * `CustomAttribute::unserialize` (custom_attribute.cpp:105-153).
     *
     * The `function` argument is only used by the C++ error log; it is kept so
     * callers stay signature-compatible.
     */
    fun unserialize(propStream: PropStream, @Suppress("UNUSED_PARAMETER") function: String): Boolean {
        val type = propStream.readU8() ?: return false

        when (type) {
            1 -> value = propStream.readString() ?: return false
            2 -> value = propStream.readI64() ?: return false
            3 -> value = java.lang.Double.longBitsToDouble(propStream.readU64() ?: return false)
            4 -> value = (propStream.readU8() ?: return false) != 0
            else -> return false
        }
        return true
    }
}

/**
 * `ItemAttribute` (src/items/functions/item/attribute.hpp:127-168).
 *
 * Owns the custom-attribute map (case-insensitive keys, ordered like the C++
 * `std::map`) and the attribute vector. [`removeAttribute`] swaps the removed
 * entry with the vector's last element, exactly like the C++ implementation.
 */
class ItemAttribute {
    private val customAttributeMap = TreeMap<String, CustomAttribute>()
    private val attributeVector = mutableListOf<Attributes>()

    // CustomAttribute map methods

    fun getCustomAttributeMap(): Map<String, CustomAttribute> = customAttributeMap

    // CustomAttribute object methods

    fun getCustomAttribute(attributeName: String): CustomAttribute? =
        customAttributeMap[attributeName.lowercase()]

    fun setCustomAttribute(key: String, value: Long) {
        customAttributeMap[key.lowercase()] = CustomAttribute(key, value)
    }

    fun setCustomAttribute(key: String, value: String) {
        customAttributeMap[key.lowercase()] = CustomAttribute(key, value)
    }

    fun setCustomAttribute(key: String, value: Double) {
        customAttributeMap[key.lowercase()] = CustomAttribute(key, value)
    }

    fun setCustomAttribute(key: String, value: Boolean) {
        customAttributeMap[key.lowercase()] = CustomAttribute(key, value)
    }

    fun addCustomAttribute(key: String, customAttribute: CustomAttribute) {
        customAttributeMap[key.lowercase()] = customAttribute
    }

    fun removeCustomAttribute(attributeName: String): Boolean =
        customAttributeMap.remove(attributeName.lowercase()) != null

    fun setAttribute(type: ItemAttribute_t, value: Long) {
        if (!ItemAttributeHelper.isAttributeInteger(type)) {
            return
        }

        getAttributesByType(type).setValue(value)
    }

    fun setAttribute(type: ItemAttribute_t, value: String) {
        if (!ItemAttributeHelper.isAttributeString(type)) {
            return
        }

        if (value.isEmpty()) {
            return
        }

        getAttributesByType(type).setValue(value)
    }

    fun removeAttribute(type: ItemAttribute_t): Boolean {
        for (index in attributeVector.indices) {
            if (attributeVector[index].getAttributeType() == type) {
                attributeVector[index] = attributeVector.last()
                attributeVector.removeAt(attributeVector.size - 1)
                return true
            }
        }
        return false
    }

    fun getAttributeString(type: ItemAttribute_t): String {
        if (!ItemAttributeHelper.isAttributeString(type)) {
            return ""
        }

        return getAttribute(type)?.getString() ?: ""
    }

    fun getAttributeValue(type: ItemAttribute_t): Long {
        if (!ItemAttributeHelper.isAttributeInteger(type)) {
            return 0L
        }

        return getAttribute(type)?.getInteger() ?: 0L
    }

    fun getAttributeVector(): List<Attributes> = attributeVector

    fun hasAttribute(type: ItemAttribute_t): Boolean =
        attributeVector.any { it.getAttributeType() == type }

    fun getAttribute(type: ItemAttribute_t): Attributes? =
        attributeVector.firstOrNull { it.getAttributeType() == type }

    fun getAttributesByType(type: ItemAttribute_t): Attributes {
        attributeVector.firstOrNull { it.getAttributeType() == type }?.let { return it }

        val attribute = Attributes(type)
        attributeVector.add(attribute)
        return attribute
    }
}
