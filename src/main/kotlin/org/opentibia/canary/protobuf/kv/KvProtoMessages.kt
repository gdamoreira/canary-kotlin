package org.opentibia.canary.protobuf.kv

/**
 * Hand-ported `Canary.protobuf.kv` messages (`src/protobuf/kv.proto`) used by the KV
 * engine's value serialization (`src/kv/value_wrapper_proto.cpp`).
 *
 * The generated C++ API (`Canary::protobuf::kv::ValueWrapper` and friends) is
 * reproduced method-for-method: `value_case()`, the oneof setters, `mutable_*()` and
 * the repeated-field `add_*()` accessors. `SerializeToString`/`ParseFromArray` are
 * exposed as [serializeToByteArray]/[parseFromArray] over the dependency-free codec in
 * [KvProtoCodec], following the same convention as the appearances reader
 * (`io/appearances/ProtobufWireReader.kt`).
 */
class ValueWrapper {

    /** Mirrors the generated `ValueWrapper::ValueCase` enum. */
    enum class ValueCase(val fieldNumber: Int) {
        VALUE_NOT_SET(0),
        STR_VALUE(1),
        INT_VALUE(2),
        DOUBLE_VALUE(3),
        ARRAY_VALUE(4),
        MAP_VALUE(5),
        BOOL_VALUE(6)
    }

    private var case: ValueCase = ValueCase.VALUE_NOT_SET
    private var str: String = ""
    private var int: Int = 0
    private var double: Double = 0.0
    private var bool: Boolean = false
    private var array: ArrayType? = null
    private var map: MapType? = null

    fun valueCase(): ValueCase = case

    fun hasStrValue(): Boolean = case == ValueCase.STR_VALUE
    fun hasIntValue(): Boolean = case == ValueCase.INT_VALUE
    fun hasDoubleValue(): Boolean = case == ValueCase.DOUBLE_VALUE
    fun hasBoolValue(): Boolean = case == ValueCase.BOOL_VALUE
    fun hasArrayValue(): Boolean = case == ValueCase.ARRAY_VALUE
    fun hasMapValue(): Boolean = case == ValueCase.MAP_VALUE

    fun strValue(): String = str
    fun intValue(): Int = int
    fun doubleValue(): Double = double
    fun boolValue(): Boolean = bool
    fun arrayValue(): ArrayType = array ?: ArrayType()
    fun mapValue(): MapType = map ?: MapType()

    fun setStrValue(value: String) {
        clearValue()
        str = value
        case = ValueCase.STR_VALUE
    }

    fun setIntValue(value: Int) {
        clearValue()
        int = value
        case = ValueCase.INT_VALUE
    }

    fun setDoubleValue(value: Double) {
        clearValue()
        double = value
        case = ValueCase.DOUBLE_VALUE
    }

    fun setBoolValue(value: Boolean) {
        clearValue()
        bool = value
        case = ValueCase.BOOL_VALUE
    }

    fun mutableArrayValue(): ArrayType {
        val existing = array
        if (existing != null && case == ValueCase.ARRAY_VALUE) {
            return existing
        }
        clearValue()
        val created = ArrayType()
        array = created
        case = ValueCase.ARRAY_VALUE
        return created
    }

    fun mutableMapValue(): MapType {
        val existing = map
        if (existing != null && case == ValueCase.MAP_VALUE) {
            return existing
        }
        clearValue()
        val created = MapType()
        map = created
        case = ValueCase.MAP_VALUE
        return created
    }

    fun clearValue() {
        case = ValueCase.VALUE_NOT_SET
        str = ""
        int = 0
        double = 0.0
        bool = false
        array = null
        map = null
    }

    /** Mirrors the generated `MergeFrom`: copies the active oneof member. */
    fun mergeFrom(other: ValueWrapper) {
        when (other.case) {
            ValueCase.VALUE_NOT_SET -> {}
            ValueCase.STR_VALUE -> setStrValue(other.str)
            ValueCase.INT_VALUE -> setIntValue(other.int)
            ValueCase.DOUBLE_VALUE -> setDoubleValue(other.double)
            ValueCase.BOOL_VALUE -> setBoolValue(other.bool)
            ValueCase.ARRAY_VALUE -> mutableArrayValue().mergeFrom(other.arrayValue())
            ValueCase.MAP_VALUE -> mutableMapValue().mergeFrom(other.mapValue())
        }
    }

    /** Mirrors `SerializeToString`: serializes the active oneof member. */
    fun serializeToByteArray(): ByteArray = KvProtoCodec.serializeValueWrapper(this)

    /** Mirrors `ParseFromArray`: replaces this message or returns `false` when malformed. */
    fun parseFromArray(data: ByteArray): Boolean {
        val parsed = try {
            KvProtoCodec.parseValueWrapper(data)
        } catch (_: KvProtoWireFormatException) {
            return false
        }
        clearValue()
        mergeFrom(parsed)
        return true
    }
}

/** Port of the generated `Canary::protobuf::kv::ArrayType` message. */
class ArrayType {

    private val values = mutableListOf<ValueWrapper>()

    fun valuesCount(): Int = values.size

    fun values(): List<ValueWrapper> = values

    fun getValues(index: Int): ValueWrapper = values[index]

    fun addValues(): ValueWrapper = ValueWrapper().also { values += it }

    fun addValues(value: ValueWrapper): ArrayType {
        values += value
        return this
    }

    /** Mirrors the generated repeated-field `MergeFrom`: appends the other's entries. */
    fun mergeFrom(other: ArrayType) {
        values += other.values
    }
}

/** Port of the generated `Canary::protobuf::kv::KeyValuePair` message. */
class KeyValuePair {

    private var key: String = ""
    private var value: ValueWrapper? = null

    fun key(): String = key

    fun setKey(value: String) {
        key = value
    }

    fun hasValue(): Boolean = value != null

    fun value(): ValueWrapper = value ?: ValueWrapper()

    fun mutableValue(): ValueWrapper {
        val existing = value ?: ValueWrapper().also { value = it }
        return existing
    }

    /** Mirrors the generated `MergeFrom` for the key and optional value message. */
    fun mergeFrom(other: KeyValuePair) {
        key = other.key
        other.value?.let { mutableValue().mergeFrom(it) }
    }
}

/** Port of the generated `Canary::protobuf::kv::MapType` message. */
class MapType {

    private val items = mutableListOf<KeyValuePair>()

    fun itemsCount(): Int = items.size

    fun items(): List<KeyValuePair> = items

    fun getItems(index: Int): KeyValuePair = items[index]

    fun addItems(): KeyValuePair = KeyValuePair().also { items += it }

    fun addItems(value: KeyValuePair): MapType {
        items += value
        return this
    }

    /** Mirrors the generated repeated-field `MergeFrom`: appends the other's entries. */
    fun mergeFrom(other: MapType) {
        items += other.items
    }
}
