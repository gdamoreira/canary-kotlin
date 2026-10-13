package org.opentibia.canary.io.appearances

/**
 * Signals a structurally invalid or truncated protobuf payload.
 *
 * Callers that must tolerate damaged assets (see [Appearances.load]) catch this and keep
 * every message parsed before the failure, mirroring the C++ loader's defensive posture in
 * `Game::loadAppearanceProtobuf` (src/game/game.cpp:1657).
 */
internal class WireFormatException(message: String) : Exception(message)

/**
 * Dependency-free protobuf wire-format reader (proto2, LITE subset) for
 * `src/protobuf/appearances.proto`.
 *
 * Supports the wire types the schema uses: varint (0), fixed64 (1), length-delimited (2)
 * and fixed32 (5). Deprecated groups (3/4) are rejected. Negative enum values are encoded
 * as 10-byte sign-extended varints by protoc and decode correctly through [readVarint].
 */
internal class ProtobufWireReader(
    private val bytes: ByteArray,
    private var position: Int = 0,
    private val limit: Int = bytes.size
) {

    fun hasRemaining(): Boolean = position < limit

    /** Reads a field key as `(fieldNumber shl 3) or wireType`. */
    fun readTag(): Int = readVarint().toInt()

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (position >= limit) {
                throw WireFormatException("truncated varint at $position")
            }
            val current = bytes[position++].toInt()
            result = result or ((current and 0x7F).toLong() shl shift)
            if (current and 0x80 == 0) {
                return result
            }
            shift += 7
            if (shift >= 70) {
                throw WireFormatException("varint overflow at $position")
            }
        }
    }

    fun readBytes(): ByteArray {
        val length = readLength()
        val start = position
        position += length
        return bytes.copyOfRange(start, position)
    }

    fun readString(): String = readBytes().toString(Charsets.UTF_8)

    fun readFixed32(): Int {
        requireAvailable(Int.SIZE_BYTES / Byte.SIZE_BYTES)
        val start = position
        position += Int.SIZE_BYTES / Byte.SIZE_BYTES
        return (bytes[start].toInt() and 0xFF) or
            ((bytes[start + 1].toInt() and 0xFF) shl 8) or
            ((bytes[start + 2].toInt() and 0xFF) shl 16) or
            ((bytes[start + 3].toInt() and 0xFF) shl 24)
    }

    fun readFixed64(): Long {
        requireAvailable(Long.SIZE_BYTES / Byte.SIZE_BYTES)
        var result = 0L
        for (index in 0 until Long.SIZE_BYTES) {
            result = result or ((bytes[position + index].toLong() and 0xFF) shl (index * Byte.SIZE_BITS))
        }
        position += Long.SIZE_BYTES
        return result
    }

    /** Returns a view over the next length-delimited payload and advances past it. */
    fun readLengthDelimitedReader(): ProtobufWireReader {
        val length = readLength()
        val start = position
        position += length
        return ProtobufWireReader(bytes, start, position)
    }

    fun skip(wireType: Int) {
        when (wireType) {
            WIRE_TYPE_VARINT -> readVarint()
            WIRE_TYPE_FIXED64 -> readFixed64()
            WIRE_TYPE_LENGTH_DELIMITED -> {
                val length = readLength()
                position += length
            }

            WIRE_TYPE_FIXED32 -> readFixed32()
            else -> throw WireFormatException("unsupported wire type $wireType")
        }
    }

    private fun readLength(): Int {
        val length = readVarint()
        if (length < 0 || length > limit - position) {
            throw WireFormatException("length $length exceeds remaining ${limit - position}")
        }
        return length.toInt()
    }

    private fun requireAvailable(count: Int) {
        if (count > limit - position) {
            throw WireFormatException("truncated fixed-width value at $position")
        }
    }

    companion object {
        const val WIRE_TYPE_VARINT = 0
        const val WIRE_TYPE_FIXED64 = 1
        const val WIRE_TYPE_LENGTH_DELIMITED = 2
        const val WIRE_TYPE_FIXED32 = 5

        const val FIELD_NUMBER_SHIFT = 3
        const val WIRE_TYPE_MASK = 0x7
    }
}
