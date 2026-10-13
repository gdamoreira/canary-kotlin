package org.opentibia.canary.kv

/**
 * The typed alternatives of Canary's `ValueVariant`
 * (`src/kv/kv_definitions.hpp:27`): string, boolean, int, double, array and map.
 *
 * The C++ container aliases are mapped to their Kotlin equivalents
 * (`ArrayType = std::vector<ValueWrapper>`, `MapType = phmap::flat_hash_map<...>`);
 * a map entry's value is the wrapper itself instead of a `std::shared_ptr`.
 */
sealed interface ValueVariant {
    data class StringValue(val value: String) : ValueVariant
    data class BooleanValue(val value: Boolean) : ValueVariant
    data class IntValue(val value: Int) : ValueVariant
    data class DoubleValue(val value: Double) : ValueVariant
    data class ArrayValue(val values: List<ValueWrapper>) : ValueVariant
    data class MapValue(val values: Map<String, ValueWrapper>) : ValueVariant
}

/**
 * Port of `ValueWrapper` (`src/kv/value_wrapper.hpp:24-140`,
 * `src/kv/value_wrapper.cpp`): a typed KV value with an optional timestamp and the
 * deletion marker `KV::remove` writes (`KV::remove`, `kv.cpp:120-122`).
 *
 * The constructors mirror the implicit C++ overloads; the typed accessors are the
 * Kotlin instantiations of `ValueWrapper::get<T>()` and each falls back to the C++
 * default-constructed `T{}` on a variant mismatch.
 */
class ValueWrapper {

    private val variant: ValueVariant

    var timestamp: Long = 0L
        private set

    var isDeleted: Boolean = false
        private set

    /** `ValueWrapper(uint64_t timestamp = 0)`; the default variant is an empty string. */
    constructor(timestamp: Long = 0L) : this(ValueVariant.StringValue(""), timestamp)

    /** `ValueWrapper(ValueVariant value, uint64_t timestamp = 0)`. */
    constructor(value: ValueVariant, timestamp: Long = 0L) {
        variant = value
        this.timestamp = if (timestamp == 0L) System.currentTimeMillis() else timestamp
    }

    constructor(value: String, timestamp: Long = 0L) : this(ValueVariant.StringValue(value), timestamp)

    constructor(value: Boolean, timestamp: Long = 0L) : this(ValueVariant.BooleanValue(value), timestamp)

    constructor(value: Int, timestamp: Long = 0L) : this(ValueVariant.IntValue(value), timestamp)

    constructor(value: Double, timestamp: Long = 0L) : this(ValueVariant.DoubleValue(value), timestamp)

    constructor(value: List<ValueWrapper>, timestamp: Long = 0L) : this(ValueVariant.ArrayValue(value.toList()), timestamp)

    constructor(value: Map<String, ValueWrapper>, timestamp: Long = 0L) : this(ValueVariant.MapValue(value.toMap()), timestamp)

    fun getVariant(): ValueVariant = variant

    /** `ValueWrapper::get<std::string>()`. */
    fun getString(): String = (variant as? ValueVariant.StringValue)?.value ?: ""

    /** `ValueWrapper::get<bool>()`. */
    fun getBoolean(): Boolean = (variant as? ValueVariant.BooleanValue)?.value ?: false

    /** `ValueWrapper::get<int>()`. */
    fun getInt(): Int = (variant as? ValueVariant.IntValue)?.value ?: 0

    /** `ValueWrapper::get<double>()`. */
    fun getDouble(): Double = (variant as? ValueVariant.DoubleValue)?.value ?: 0.0

    /** `ValueWrapper::get<ArrayType>()`. */
    fun getArray(): List<ValueWrapper> = (variant as? ValueVariant.ArrayValue)?.values ?: emptyList()

    /** `ValueWrapper::get<MapType>()`. */
    fun getMap(): Map<String, ValueWrapper> = (variant as? ValueVariant.MapValue)?.values ?: emptyMap()

    /** `ValueWrapper::getNumber()`: int or double, otherwise `0.0`. */
    fun getNumber(): Double = when (val data = variant) {
        is ValueVariant.IntValue -> data.value.toDouble()
        is ValueVariant.DoubleValue -> data.value
        else -> 0.0
    }

    /** `ValueWrapper::get(key)` for map entries. */
    fun get(key: String): ValueWrapper? = (variant as? ValueVariant.MapValue)?.values?.get(key)

    /** `ValueWrapper::get(index)` for array entries. */
    fun get(index: Int): ValueWrapper? = (variant as? ValueVariant.ArrayValue)?.values?.getOrNull(index)

    /** `ValueWrapper::setTimestamp`. */
    fun setTimestamp(timestamp: Long) {
        this.timestamp = timestamp
    }

    /** `ValueWrapper::setDeleted`. */
    fun setDeleted(deleted: Boolean) {
        isDeleted = deleted
    }

    /**
     * `ValueWrapper::operator==`: compares the variant and ignores timestamp/deletion
     * (Canary's shallow map comparison compares entry pointers; this port compares the
     * entries structurally).
     */
    override fun equals(other: Any?): Boolean = other is ValueWrapper && variant == other.variant

    override fun hashCode(): Int = variant.hashCode()

    override fun toString(): String = variant.toString()

    companion object {
        private val deletedValue: ValueWrapper = ValueWrapper().also { it.setDeleted(true) }

        /** `ValueWrapper::deleted()` (`value_wrapper.hpp:35-39`). */
        fun deleted(): ValueWrapper =
            ValueWrapper(deletedValue.getVariant(), deletedValue.timestamp).also { it.setDeleted(true) }
    }
}
