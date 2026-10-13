package org.opentibia.canary.protobuf.kv

/** Signals a structurally invalid or truncated protobuf payload. */
internal class KvProtoWireFormatException(message: String) : Exception(message)

/**
 * Dependency-free protobuf wire-format writer for the `Canary.protobuf.kv` messages.
 *
 * Fields are emitted in ascending field-number order, matching the canonical
 * `SerializeToString` output; `int32` values are sign-extended to the 64-bit varint
 * protoc emits for negatives.
 */
internal class KvProtoWireWriter {

    private val out = java.io.ByteArrayOutputStream()

    fun writeString(fieldNumber: Int, value: String) {
        writeTag(fieldNumber, WIRE_TYPE_LENGTH_DELIMITED)
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeVarint(bytes.size.toLong())
        out.write(bytes)
    }

    fun writeInt32(fieldNumber: Int, value: Int) {
        writeTag(fieldNumber, WIRE_TYPE_VARINT)
        writeVarint(value.toLong())
    }

    fun writeBool(fieldNumber: Int, value: Boolean) {
        writeTag(fieldNumber, WIRE_TYPE_VARINT)
        writeVarint(if (value) 1L else 0L)
    }

    fun writeDouble(fieldNumber: Int, value: Double) {
        writeTag(fieldNumber, WIRE_TYPE_FIXED64)
        writeFixed64(value.toRawBits())
    }

    fun writeMessage(fieldNumber: Int, payload: ByteArray) {
        writeTag(fieldNumber, WIRE_TYPE_LENGTH_DELIMITED)
        writeVarint(payload.size.toLong())
        out.write(payload)
    }

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun writeTag(fieldNumber: Int, wireType: Int) {
        writeVarint(((fieldNumber shl FIELD_NUMBER_SHIFT) or wireType).toLong())
    }

    private fun writeVarint(value: Long) {
        var remaining = value
        while (true) {
            if (remaining and 0x7FL.inv() == 0L) {
                out.write(remaining.toInt())
                return
            }
            out.write(((remaining and 0x7F) or 0x80).toInt())
            remaining = remaining ushr 7
        }
    }

    private fun writeFixed64(value: Long) {
        for (index in 0 until Long.SIZE_BYTES) {
            out.write(((value ushr (index * Byte.SIZE_BITS)) and 0xFF).toInt())
        }
    }

    companion object {
        const val WIRE_TYPE_VARINT = 0
        const val WIRE_TYPE_FIXED64 = 1
        const val WIRE_TYPE_LENGTH_DELIMITED = 2
        const val FIELD_NUMBER_SHIFT = 3
    }
}

/**
 * Dependency-free protobuf wire-format reader for the `Canary.protobuf.kv` messages.
 * Supports the wire types the schema uses: varint, fixed64 and length-delimited.
 */
internal class KvProtoWireReader(
    private val bytes: ByteArray,
    private var position: Int = 0,
    private val limit: Int = bytes.size
) {

    fun hasRemaining(): Boolean = position < limit

    fun readTag(): Int = readVarint().toInt()

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (position >= limit) {
                throw KvProtoWireFormatException("truncated varint at $position")
            }
            val current = bytes[position++].toInt()
            result = result or ((current and 0x7F).toLong() shl shift)
            if (current and 0x80 == 0) {
                return result
            }
            shift += 7
            if (shift >= 70) {
                throw KvProtoWireFormatException("varint overflow at $position")
            }
        }
    }

    fun readString(): String = readBytes().toString(Charsets.UTF_8)

    fun readFixed64(): Long {
        requireAvailable(Long.SIZE_BYTES)
        var result = 0L
        for (index in 0 until Long.SIZE_BYTES) {
            result = result or ((bytes[position + index].toLong() and 0xFF) shl (index * Byte.SIZE_BITS))
        }
        position += Long.SIZE_BYTES
        return result
    }

    fun readLengthDelimitedReader(): KvProtoWireReader {
        val length = readLength()
        val start = position
        position += length
        return KvProtoWireReader(bytes, start, position)
    }

    fun skip(wireType: Int) {
        when (wireType) {
            KvProtoWireWriter.WIRE_TYPE_VARINT -> readVarint()
            KvProtoWireWriter.WIRE_TYPE_FIXED64 -> readFixed64()
            KvProtoWireWriter.WIRE_TYPE_LENGTH_DELIMITED -> {
                val length = readLength()
                position += length
            }

            WIRE_TYPE_FIXED32 -> {
                requireAvailable(Int.SIZE_BYTES)
                position += Int.SIZE_BYTES
            }

            else -> throw KvProtoWireFormatException("unsupported wire type $wireType")
        }
    }

    private fun readBytes(): ByteArray {
        val length = readLength()
        val start = position
        position += length
        return bytes.copyOfRange(start, position)
    }

    private fun readLength(): Int {
        val length = readVarint()
        if (length < 0 || length > limit - position) {
            throw KvProtoWireFormatException("length $length exceeds remaining ${limit - position}")
        }
        return length.toInt()
    }

    private fun requireAvailable(count: Int) {
        if (count > limit - position) {
            throw KvProtoWireFormatException("truncated fixed-width value at $position")
        }
    }

    companion object {
        const val WIRE_TYPE_FIXED32 = 5
        const val FIELD_NUMBER_SHIFT = 3
        const val WIRE_TYPE_MASK = 0x7
    }
}

/**
 * Codec for `src/protobuf/kv.proto`: the field numbers and oneof semantics of the
 * generated `kv.pb.cc` serializer/parser.
 */
internal object KvProtoCodec {

    private const val VALUE_WRAPPER_STR_FIELD = 1
    private const val VALUE_WRAPPER_INT_FIELD = 2
    private const val VALUE_WRAPPER_DOUBLE_FIELD = 3
    private const val VALUE_WRAPPER_ARRAY_FIELD = 4
    private const val VALUE_WRAPPER_MAP_FIELD = 5
    private const val VALUE_WRAPPER_BOOL_FIELD = 6

    private const val ARRAY_VALUES_FIELD = 1
    private const val KEY_VALUE_KEY_FIELD = 1
    private const val KEY_VALUE_VALUE_FIELD = 2
    private const val MAP_ITEMS_FIELD = 1

    fun serializeValueWrapper(message: ValueWrapper): ByteArray {
        val writer = KvProtoWireWriter()
        when (message.valueCase()) {
            ValueWrapper.ValueCase.VALUE_NOT_SET -> {}
            ValueWrapper.ValueCase.STR_VALUE -> writer.writeString(VALUE_WRAPPER_STR_FIELD, message.strValue())
            ValueWrapper.ValueCase.INT_VALUE -> writer.writeInt32(VALUE_WRAPPER_INT_FIELD, message.intValue())
            ValueWrapper.ValueCase.DOUBLE_VALUE -> writer.writeDouble(VALUE_WRAPPER_DOUBLE_FIELD, message.doubleValue())
            ValueWrapper.ValueCase.ARRAY_VALUE ->
                writer.writeMessage(VALUE_WRAPPER_ARRAY_FIELD, serializeArray(message.arrayValue()))

            ValueWrapper.ValueCase.MAP_VALUE ->
                writer.writeMessage(VALUE_WRAPPER_MAP_FIELD, serializeMap(message.mapValue()))

            ValueWrapper.ValueCase.BOOL_VALUE -> writer.writeBool(VALUE_WRAPPER_BOOL_FIELD, message.boolValue())
        }
        return writer.toByteArray()
    }

    fun parseValueWrapper(bytes: ByteArray): ValueWrapper = parseValueWrapper(KvProtoWireReader(bytes))

    private fun parseValueWrapper(reader: KvProtoWireReader): ValueWrapper {
        val message = ValueWrapper()
        while (reader.hasRemaining()) {
            val tag = reader.readTag()
            val field = tag ushr KvProtoWireReader.FIELD_NUMBER_SHIFT
            val wireType = tag and KvProtoWireReader.WIRE_TYPE_MASK
            when {
                field == VALUE_WRAPPER_STR_FIELD && wireType == KvProtoWireWriter.WIRE_TYPE_LENGTH_DELIMITED ->
                    message.setStrValue(reader.readString())

                field == VALUE_WRAPPER_INT_FIELD && wireType == KvProtoWireWriter.WIRE_TYPE_VARINT ->
                    message.setIntValue(reader.readVarint().toInt())

                field == VALUE_WRAPPER_DOUBLE_FIELD && wireType == KvProtoWireWriter.WIRE_TYPE_FIXED64 ->
                    message.setDoubleValue(Double.fromBits(reader.readFixed64()))

                field == VALUE_WRAPPER_ARRAY_FIELD && wireType == KvProtoWireWriter.WIRE_TYPE_LENGTH_DELIMITED ->
                    message.mutableArrayValue().mergeFrom(parseArray(reader.readLengthDelimitedReader()))

                field == VALUE_WRAPPER_MAP_FIELD && wireType == KvProtoWireWriter.WIRE_TYPE_LENGTH_DELIMITED ->
                    message.mutableMapValue().mergeFrom(parseMap(reader.readLengthDelimitedReader()))

                field == VALUE_WRAPPER_BOOL_FIELD && wireType == KvProtoWireWriter.WIRE_TYPE_VARINT ->
                    message.setBoolValue(reader.readVarint() != 0L)

                else -> reader.skip(wireType)
            }
        }
        return message
    }

    private fun serializeArray(array: ArrayType): ByteArray {
        val writer = KvProtoWireWriter()
        for (value in array.values()) {
            writer.writeMessage(ARRAY_VALUES_FIELD, serializeValueWrapper(value))
        }
        return writer.toByteArray()
    }

    private fun parseArray(reader: KvProtoWireReader): ArrayType {
        val array = ArrayType()
        while (reader.hasRemaining()) {
            val tag = reader.readTag()
            val field = tag ushr KvProtoWireReader.FIELD_NUMBER_SHIFT
            val wireType = tag and KvProtoWireReader.WIRE_TYPE_MASK
            if (field == ARRAY_VALUES_FIELD && wireType == KvProtoWireWriter.WIRE_TYPE_LENGTH_DELIMITED) {
                array.addValues(parseValueWrapper(reader.readLengthDelimitedReader()))
            } else {
                reader.skip(wireType)
            }
        }
        return array
    }

    private fun serializeMap(map: MapType): ByteArray {
        val writer = KvProtoWireWriter()
        for (item in map.items()) {
            writer.writeMessage(MAP_ITEMS_FIELD, serializeKeyValuePair(item))
        }
        return writer.toByteArray()
    }

    private fun parseMap(reader: KvProtoWireReader): MapType {
        val map = MapType()
        while (reader.hasRemaining()) {
            val tag = reader.readTag()
            val field = tag ushr KvProtoWireReader.FIELD_NUMBER_SHIFT
            val wireType = tag and KvProtoWireReader.WIRE_TYPE_MASK
            if (field == MAP_ITEMS_FIELD && wireType == KvProtoWireWriter.WIRE_TYPE_LENGTH_DELIMITED) {
                map.addItems(parseKeyValuePair(reader.readLengthDelimitedReader()))
            } else {
                reader.skip(wireType)
            }
        }
        return map
    }

    private fun serializeKeyValuePair(item: KeyValuePair): ByteArray {
        val writer = KvProtoWireWriter()
        writer.writeString(KEY_VALUE_KEY_FIELD, item.key())
        if (item.hasValue()) {
            writer.writeMessage(KEY_VALUE_VALUE_FIELD, serializeValueWrapper(item.value()))
        }
        return writer.toByteArray()
    }

    private fun parseKeyValuePair(reader: KvProtoWireReader): KeyValuePair {
        val item = KeyValuePair()
        while (reader.hasRemaining()) {
            val tag = reader.readTag()
            val field = tag ushr KvProtoWireReader.FIELD_NUMBER_SHIFT
            val wireType = tag and KvProtoWireReader.WIRE_TYPE_MASK
            when {
                field == KEY_VALUE_KEY_FIELD && wireType == KvProtoWireWriter.WIRE_TYPE_LENGTH_DELIMITED ->
                    item.setKey(reader.readString())

                field == KEY_VALUE_VALUE_FIELD && wireType == KvProtoWireWriter.WIRE_TYPE_LENGTH_DELIMITED ->
                    item.mutableValue().mergeFrom(parseValueWrapper(reader.readLengthDelimitedReader()))

                else -> reader.skip(wireType)
            }
        }
        return item
    }
}
