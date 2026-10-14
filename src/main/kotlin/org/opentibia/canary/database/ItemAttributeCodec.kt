package org.opentibia.canary.database

import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemDecayState
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Shared item-attribute codec for the flat persistence tables.
 *
 * The blob layout is exactly Canary's `Item::serializeAttr`
 * (src/items/item.cpp:1368-1539): one `AttrTypes_t` byte per attribute
 * (src/items/items_definitions.hpp:202-252), little-endian payloads via
 * `PropWriteStream` and `uint16`-length-prefixed strings
 * (src/io/fileloader.hpp:124-163). [deserializeItemAttributes] mirrors
 * `Item::readAttr` / `Item::unserializeAttr` (src/items/item.cpp:912-1225) and
 * throws [IllegalArgumentException] on an unsupported wire code, which callers
 * treat like Canary's "warn and skip item" path.
 */
internal fun serializeItemAttributes(count: Int, attributes: Map<ItemAttribute, Any>): ByteArray {
    val out = ByteArrayOutputStream()

    val store = intValue(attributes, ItemAttribute.STORE)
    if (store != 0L) {
        out.writeWireByte(ATTR_STORE)
        out.writeLongLE(store)
    }

    // ATTR_COUNT carries the subtype of stackable/fluid items. The flat tables
    // already store the subtype in their own `count` column, so the blob only
    // repeats it when it differs from the default of one.
    if (count != 1) {
        out.writeWireByte(ATTR_COUNT)
        out.writeWireByte(count)
    }

    val charges = intValue(attributes, ItemAttribute.CHARGES)
    if (charges != 0L) {
        out.writeWireByte(ATTR_CHARGES)
        out.writeShortLE(charges.toInt())
    }

    val actionId = intValue(attributes, ItemAttribute.ACTIONID)
    if (actionId != 0L) {
        out.writeWireByte(ATTR_ACTION_ID)
        out.writeShortLE(actionId.toInt())
    }

    writeStringAttribute(out, attributes, ItemAttribute.TEXT, ATTR_TEXT)

    val writtenDate = intValue(attributes, ItemAttribute.DATE)
    if (writtenDate != 0L) {
        out.writeWireByte(ATTR_WRITTENDATE)
        out.writeLongLE(writtenDate)
    }

    writeStringAttribute(out, attributes, ItemAttribute.WRITER, ATTR_WRITTENBY)
    writeStringAttribute(out, attributes, ItemAttribute.DESCRIPTION, ATTR_DESC)

    if (attributes.containsKey(ItemAttribute.DURATION)) {
        out.writeWireByte(ATTR_DURATION)
        out.writeIntLE(intValue(attributes, ItemAttribute.DURATION))
    }

    val decayState = intValue(attributes, ItemAttribute.DECAYSTATE)
    if (decayState == ItemDecayState.DECAYING_TRUE.value.toLong() ||
        decayState == ItemDecayState.DECAYING_PENDING.value.toLong()
    ) {
        out.writeWireByte(ATTR_DECAYING_STATE)
        out.writeWireByte(decayState.toInt())
    }

    writeStringAttribute(out, attributes, ItemAttribute.NAME, ATTR_NAME)
    writeStringAttribute(out, attributes, ItemAttribute.ARTICLE, ATTR_ARTICLE)
    writeStringAttribute(out, attributes, ItemAttribute.PLURALNAME, ATTR_PLURALNAME)

    writeUInt32Attribute(out, attributes, ItemAttribute.WEIGHT, ATTR_WEIGHT)
    writeInt32Attribute(out, attributes, ItemAttribute.ATTACK, ATTR_ATTACK)
    writeInt32Attribute(out, attributes, ItemAttribute.DEFENSE, ATTR_DEFENSE)
    writeInt32Attribute(out, attributes, ItemAttribute.EXTRADEFENSE, ATTR_EXTRADEFENSE)
    writeInt32Attribute(out, attributes, ItemAttribute.MANTRA, ATTR_MANTRA)
    writeInt32Attribute(out, attributes, ItemAttribute.IMBUEMENT_SLOT, ATTR_IMBUEMENT_SLOT)
    writeUInt8Attribute(out, attributes, ItemAttribute.OPENCONTAINER, ATTR_OPENCONTAINER)
    writeInt32Attribute(out, attributes, ItemAttribute.ARMOR, ATTR_ARMOR)
    writeInt8Attribute(out, attributes, ItemAttribute.HITCHANCE, ATTR_HITCHANCE)
    writeUInt8Attribute(out, attributes, ItemAttribute.SHOOTRANGE, ATTR_SHOOTRANGE)
    writeStringAttribute(out, attributes, ItemAttribute.SPECIAL, ATTR_SPECIAL)
    writeUInt32Attribute(out, attributes, ItemAttribute.QUICKLOOTCONTAINER, ATTR_QUICKLOOTCONTAINER)
    writeUInt8Attribute(out, attributes, ItemAttribute.TIER, ATTR_TIER)
    writeUInt16Attribute(out, attributes, ItemAttribute.AMOUNT, ATTR_AMOUNT)
    writeStringAttribute(out, attributes, ItemAttribute.STORE_INBOX_CATEGORY, ATTR_STORE_INBOX_CATEGORY)
    writeUInt32Attribute(out, attributes, ItemAttribute.OWNER, ATTR_OWNER)
    writeCustomAttributes(out, attributes)
    writeUInt32Attribute(out, attributes, ItemAttribute.OBTAINCONTAINER, ATTR_OBTAINCONTAINER)

    return out.toByteArray()
}

internal fun deserializeItemAttributes(blob: ByteArray?): Map<ItemAttribute, Any> {
    if (blob == null || blob.isEmpty()) {
        return emptyMap()
    }

    val input = BlobReader(blob)
    val attributes = linkedMapOf<ItemAttribute, Any>()
    while (input.hasRemaining()) {
        val code = input.readByte().toInt() and 0xFF
        if (code == ATTR_NONE) {
            break
        }
        when (code) {
            ATTR_STORE -> attributes[ItemAttribute.STORE] = input.readLongLE()
            // The subtype travels in the flat table's `count` column;
            // `readAttr` applies it through `setSubType` (src/items/item.cpp:923-932).
            ATTR_COUNT, ATTR_RUNE_CHARGES -> input.skip(1)
            ATTR_ACTION_ID -> attributes[ItemAttribute.ACTIONID] = input.readShortLE().toLong()
            // `addUniqueId` is runtime-only and never written by serializeAttr.
            ATTR_UNIQUE_ID -> input.skip(2)
            // `readAttr` restores charges through `setSubType`
            // (src/items/item.cpp:994-1002).
            ATTR_CHARGES -> attributes[ItemAttribute.CHARGES] = input.readShortLE().toLong()
            ATTR_TEXT -> attributes[ItemAttribute.TEXT] = input.readString()
            ATTR_WRITTENDATE -> attributes[ItemAttribute.DATE] = input.readLongLE()
            ATTR_WRITTENBY -> attributes[ItemAttribute.WRITER] = input.readString()
            ATTR_DESC -> attributes[ItemAttribute.DESCRIPTION] = input.readString()
            ATTR_DURATION -> attributes[ItemAttribute.DURATION] = input.readIntLE().toLong()
            // Any non-false state becomes pending, like `readAttr`
            // (src/items/item.cpp:1014-1023).
            ATTR_DECAYING_STATE -> if (input.readByte().toInt() != 0) {
                attributes[ItemAttribute.DECAYSTATE] = ItemDecayState.DECAYING_PENDING.value.toLong()
            }
            ATTR_NAME -> attributes[ItemAttribute.NAME] = input.readString()
            ATTR_ARTICLE -> attributes[ItemAttribute.ARTICLE] = input.readString()
            ATTR_PLURALNAME -> attributes[ItemAttribute.PLURALNAME] = input.readString()
            ATTR_WEIGHT -> attributes[ItemAttribute.WEIGHT] = input.readIntLE().toLong() and 0xFFFFFFFFL
            ATTR_ATTACK -> attributes[ItemAttribute.ATTACK] = input.readIntLE().toLong()
            ATTR_DEFENSE -> attributes[ItemAttribute.DEFENSE] = input.readIntLE().toLong()
            ATTR_EXTRADEFENSE -> attributes[ItemAttribute.EXTRADEFENSE] = input.readIntLE().toLong()
            ATTR_MANTRA -> attributes[ItemAttribute.MANTRA] = input.readIntLE().toLong()
            ATTR_IMBUEMENT_SLOT -> attributes[ItemAttribute.IMBUEMENT_SLOT] = input.readIntLE().toLong()
            ATTR_OPENCONTAINER -> attributes[ItemAttribute.OPENCONTAINER] = input.readByte().toLong() and 0xFF
            ATTR_ARMOR -> attributes[ItemAttribute.ARMOR] = input.readIntLE().toLong()
            ATTR_HITCHANCE -> attributes[ItemAttribute.HITCHANCE] = input.readByte().toLong()
            ATTR_SHOOTRANGE -> attributes[ItemAttribute.SHOOTRANGE] = input.readByte().toLong() and 0xFF
            ATTR_SPECIAL -> attributes[ItemAttribute.SPECIAL] = input.readString()
            ATTR_QUICKLOOTCONTAINER ->
                attributes[ItemAttribute.QUICKLOOTCONTAINER] = input.readIntLE().toLong() and 0xFFFFFFFFL
            ATTR_TIER -> attributes[ItemAttribute.TIER] = input.readByte().toLong() and 0xFF
            ATTR_AMOUNT -> attributes[ItemAttribute.AMOUNT] = input.readShortLE().toLong()
            ATTR_STORE_INBOX_CATEGORY -> attributes[ItemAttribute.STORE_INBOX_CATEGORY] = input.readString()
            ATTR_OWNER -> attributes[ItemAttribute.OWNER] = input.readIntLE().toLong() and 0xFFFFFFFFL
            ATTR_CUSTOM -> {
                val custom = customAttributes(attributes)
                readCustomAttributeBlock(input) { key, value -> custom[key] = value }
            }
            // Deprecated `ATTR_CUSTOM_ATTRIBUTES`; `readAttr` migrates it into
            // the same store (src/items/item.cpp:1240-1268).
            ATTR_CUSTOM_ATTRIBUTES -> {
                val custom = customAttributes(attributes)
                readCustomAttributeBlock(input) { key, value -> custom[key] = value }
            }
            ATTR_OBTAINCONTAINER ->
                attributes[ItemAttribute.OBTAINCONTAINER] = input.readIntLE().toLong() and 0xFFFFFFFFL
            // Read-only map/derived-class attributes handled by `readAttr`
            // (src/items/item.cpp:1184-1225).
            ATTR_DEPOT_ID -> input.skip(2)
            ATTR_HOUSEDOORID -> input.skip(1)
            ATTR_SLEEPERGUID, ATTR_SLEEPSTART -> input.skip(4)
            ATTR_TELE_DEST -> input.skip(5)
            else -> throw IllegalArgumentException("unsupported item attribute wire code $code")
        }
    }
    return attributes
}

private fun intValue(attributes: Map<ItemAttribute, Any>, attribute: ItemAttribute): Long =
    (attributes[attribute] as? Number)?.toLong() ?: 0L

// AttrTypes_t wire codes (src/items/items_definitions.hpp:202-252).
private const val ATTR_NONE = 0
private const val ATTR_STORE = 1
private const val ATTR_ACTION_ID = 4
private const val ATTR_UNIQUE_ID = 5
private const val ATTR_TEXT = 6
private const val ATTR_DESC = 7
private const val ATTR_TELE_DEST = 8
private const val ATTR_DEPOT_ID = 10
private const val ATTR_RUNE_CHARGES = 12
private const val ATTR_HOUSEDOORID = 14
private const val ATTR_COUNT = 15
private const val ATTR_DURATION = 16
private const val ATTR_DECAYING_STATE = 17
private const val ATTR_WRITTENDATE = 18
private const val ATTR_WRITTENBY = 19
private const val ATTR_SLEEPERGUID = 20
private const val ATTR_SLEEPSTART = 21
private const val ATTR_CHARGES = 22
private const val ATTR_NAME = 24
private const val ATTR_ARTICLE = 25
private const val ATTR_PLURALNAME = 26
private const val ATTR_WEIGHT = 27
private const val ATTR_ATTACK = 28
private const val ATTR_DEFENSE = 29
private const val ATTR_EXTRADEFENSE = 30
private const val ATTR_ARMOR = 31
private const val ATTR_HITCHANCE = 32
private const val ATTR_SHOOTRANGE = 33
private const val ATTR_SPECIAL = 34
private const val ATTR_IMBUEMENT_SLOT = 35
private const val ATTR_OPENCONTAINER = 36
private const val ATTR_CUSTOM_ATTRIBUTES = 37
private const val ATTR_QUICKLOOTCONTAINER = 38
private const val ATTR_AMOUNT = 39
private const val ATTR_TIER = 40
private const val ATTR_CUSTOM = 41
private const val ATTR_STORE_INBOX_CATEGORY = 42
private const val ATTR_OWNER = 43
private const val ATTR_OBTAINCONTAINER = 44
private const val ATTR_MANTRA = 45

// CustomAttribute wire variants (custom_attribute.cpp:88-153). The store is
// carried on ItemAttribute.CUSTOM as `Map<String, Any>` with these four value
// types; the map keys are the lowercased custom attribute keys like Canary's
// `std::map<std::string, CustomAttribute>` (attribute.hpp:166).
private const val CUSTOM_ATTRIBUTE_STRING = 1
private const val CUSTOM_ATTRIBUTE_INTEGER = 2
private const val CUSTOM_ATTRIBUTE_DOUBLE = 3
private const val CUSTOM_ATTRIBUTE_BOOLEAN = 4

// PropWriteStream/PropStream primitives (src/io/fileloader.hpp:57-163):
// little-endian native words and uint16-length-prefixed raw string bytes.
private fun ByteArrayOutputStream.writeWireByte(value: Int) {
    write(value and 0xFF)
}

private fun ByteArrayOutputStream.writeShortLE(value: Int) {
    writeWireByte(value)
    writeWireByte(value ushr 8)
}

private fun ByteArrayOutputStream.writeIntLE(value: Long) {
    writeWireByte(value.toInt())
    writeWireByte(value.toInt() ushr 8)
    writeWireByte(value.toInt() ushr 16)
    writeWireByte(value.toInt() ushr 24)
}

private fun ByteArrayOutputStream.writeLongLE(value: Long) {
    for (shift in 0 until 64 step 8) {
        writeWireByte((value ushr shift).toInt())
    }
}

private fun ByteArrayOutputStream.writeString(value: String) {
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    if (bytes.size > 0xFFFF) {
        writeShortLE(0)
        return
    }
    writeShortLE(bytes.size)
    write(bytes)
}

private fun writeInt32Attribute(
    out: ByteArrayOutputStream,
    attributes: Map<ItemAttribute, Any>,
    attribute: ItemAttribute,
    code: Int,
) {
    if (attributes.containsKey(attribute)) {
        out.writeWireByte(code)
        out.writeIntLE((attributes[attribute] as? Number)?.toLong() ?: 0L)
    }
}

private fun writeUInt32Attribute(
    out: ByteArrayOutputStream,
    attributes: Map<ItemAttribute, Any>,
    attribute: ItemAttribute,
    code: Int,
) {
    if (attributes.containsKey(attribute)) {
        out.writeWireByte(code)
        out.writeIntLE(((attributes[attribute] as? Number)?.toLong() ?: 0L) and 0xFFFFFFFFL)
    }
}

private fun writeUInt16Attribute(
    out: ByteArrayOutputStream,
    attributes: Map<ItemAttribute, Any>,
    attribute: ItemAttribute,
    code: Int,
) {
    if (attributes.containsKey(attribute)) {
        out.writeWireByte(code)
        out.writeShortLE(((attributes[attribute] as? Number)?.toInt() ?: 0) and 0xFFFF)
    }
}

private fun writeUInt8Attribute(
    out: ByteArrayOutputStream,
    attributes: Map<ItemAttribute, Any>,
    attribute: ItemAttribute,
    code: Int,
) {
    if (attributes.containsKey(attribute)) {
        out.writeWireByte(code)
        out.writeWireByte((attributes[attribute] as? Number)?.toInt() ?: 0)
    }
}

private fun writeInt8Attribute(
    out: ByteArrayOutputStream,
    attributes: Map<ItemAttribute, Any>,
    attribute: ItemAttribute,
    code: Int,
) {
    if (attributes.containsKey(attribute)) {
        out.writeWireByte(code)
        out.writeWireByte((attributes[attribute] as? Number)?.toInt() ?: 0)
    }
}

private fun writeStringAttribute(
    out: ByteArrayOutputStream,
    attributes: Map<ItemAttribute, Any>,
    attribute: ItemAttribute,
    code: Int,
) {
    val value = attributes[attribute] as? String
    if (!value.isNullOrEmpty()) {
        out.writeWireByte(code)
        out.writeString(value)
    }
}

/**
 * `Item::serializeAttr`'s `hasCustomAttribute()` block (src/items/item.cpp:1827-1834):
 * `ATTR_CUSTOM`, the `uint64` entry count and each lowercased key followed by
 * the `CustomAttribute` variant. Keys iterate in the store's ascending order
 * like Canary's `std::map` (attribute.hpp:166).
 */
private fun writeCustomAttributes(out: ByteArrayOutputStream, attributes: Map<ItemAttribute, Any>) {
    val store = attributes[ItemAttribute.CUSTOM] as? Map<*, *> ?: return
    if (store.isEmpty()) {
        return
    }

    out.writeWireByte(ATTR_CUSTOM)
    out.writeLongLE(store.size.toLong())
    val entries = store.entries
        .map { entry ->
            val key = entry.key as? String
                ?: throw IllegalArgumentException("custom attribute key must be a string")
            key.lowercase() to entry.value
        }
        .sortedBy { it.first }
    for ((key, value) in entries) {
        out.writeString(key)
        out.writeCustomAttributeValue(value)
    }
}

/** `CustomAttribute::serialize` (custom_attribute.cpp:88-103). */
private fun ByteArrayOutputStream.writeCustomAttributeValue(value: Any?) {
    when (value) {
        is String -> {
            writeWireByte(CUSTOM_ATTRIBUTE_STRING)
            writeString(value)
        }

        is Boolean -> {
            writeWireByte(CUSTOM_ATTRIBUTE_BOOLEAN)
            writeWireByte(if (value) 1 else 0)
        }

        is Double -> {
            writeWireByte(CUSTOM_ATTRIBUTE_DOUBLE)
            writeLongLE(java.lang.Double.doubleToRawLongBits(value))
        }

        is Float -> {
            writeWireByte(CUSTOM_ATTRIBUTE_DOUBLE)
            writeLongLE(java.lang.Double.doubleToRawLongBits(value.toDouble()))
        }

        is Number -> {
            writeWireByte(CUSTOM_ATTRIBUTE_INTEGER)
            writeLongLE(value.toLong())
        }

        else -> throw IllegalArgumentException(
            "unsupported custom attribute value type ${value?.javaClass?.name ?: "null"}",
        )
    }
}

/**
 * Resolves (and creates on demand) the `ItemAttribute.CUSTOM` store of the
 * decoded [target] map, keyed by the lowercased custom attribute key exactly
 * like `ItemProperties::addCustomAttribute` (attribute.cpp:141).
 */
private fun customAttributes(target: MutableMap<ItemAttribute, Any>): MutableMap<String, Any> {
    val existing = target[ItemAttribute.CUSTOM]
    if (existing is MutableMap<*, *>) {
        @Suppress("UNCHECKED_CAST")
        return existing as MutableMap<String, Any>
    }
    val created = linkedMapOf<String, Any>()
    target[ItemAttribute.CUSTOM] = created
    return created
}

/** `Item::readAttr`'s `ATTR_CUSTOM`/`ATTR_CUSTOM_ATTRIBUTES` block (src/items/item.cpp:2028-2058). */
private fun readCustomAttributeBlock(input: BlobReader, store: (String, Any) -> Unit) {
    val size = input.readLongLE()
    var index = 0L
    while (index < size) {
        val key = input.readString()
        store(key.lowercase(), input.readCustomAttributeValue())
        index++
    }
}

private class BlobReader(private val bytes: ByteArray) {
    private var position = 0

    fun hasRemaining(): Boolean = position < bytes.size

    fun readByte(): Byte {
        requireBytes(1)
        return bytes[position++]
    }

    fun readShortLE(): Int {
        requireBytes(2)
        val value = (bytes[position].toInt() and 0xFF) or ((bytes[position + 1].toInt() and 0xFF) shl 8)
        position += 2
        return value
    }

    fun readIntLE(): Int {
        requireBytes(4)
        val value = (bytes[position].toInt() and 0xFF) or
            ((bytes[position + 1].toInt() and 0xFF) shl 8) or
            ((bytes[position + 2].toInt() and 0xFF) shl 16) or
            ((bytes[position + 3].toInt() and 0xFF) shl 24)
        position += 4
        return value
    }

    fun readLongLE(): Long {
        var value = 0L
        for (shift in 0 until 64 step 8) {
            value = value or ((readByte().toLong() and 0xFF) shl shift)
        }
        return value
    }

    fun readString(): String {
        val length = readShortLE()
        requireBytes(length)
        val value = String(bytes, position, length, StandardCharsets.UTF_8)
        position += length
        return value
    }

    /** `CustomAttribute::unserialize` (custom_attribute.cpp:105-153). */
    fun readCustomAttributeValue(): Any {
        val type = readByte().toInt() and 0xFF
        return when (type) {
            CUSTOM_ATTRIBUTE_STRING -> readString()
            CUSTOM_ATTRIBUTE_INTEGER -> readLongLE()
            CUSTOM_ATTRIBUTE_DOUBLE -> java.lang.Double.longBitsToDouble(readLongLE())
            CUSTOM_ATTRIBUTE_BOOLEAN -> readByte().toInt() != 0
            else -> throw IllegalArgumentException("unsupported custom attribute wire type $type")
        }
    }

    fun skip(count: Int) {
        requireBytes(count)
        position += count
    }

    private fun requireBytes(count: Int) {
        if (count < 0 || position + count > bytes.size) {
            throw IllegalArgumentException("truncated item attribute blob")
        }
    }
}
