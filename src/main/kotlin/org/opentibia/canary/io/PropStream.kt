package org.opentibia.canary.io

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Port of `PropStream` (src/io/fileloader.hpp:57-122): a little-endian reader
 * over a byte range. Every failed read leaves the cursor untouched, exactly
 * like the C++ `size() < sizeof(T)` guard.
 */
class PropStream(
    private val data: ByteArray,
    private var position: Int = 0,
) {

    fun size(): Int = data.size - position

    fun hasRemaining(): Boolean = position < data.size

    fun readU8(): Int? {
        if (size() < 1) return null
        return data[position++].toInt() and 0xFF
    }

    fun readU16(): Int? {
        if (size() < 2) return null
        val value = (data[position].toInt() and 0xFF) or ((data[position + 1].toInt() and 0xFF) shl 8)
        position += 2
        return value
    }

    fun readU32(): Long? {
        if (size() < 4) return null
        var value = 0L
        for (index in 0 until 4) {
            value = value or ((data[position + index].toLong() and 0xFF) shl (index * 8))
        }
        position += 4
        return value
    }

    fun readU64(): Long? {
        if (size() < 8) return null
        var value = 0L
        for (index in 0 until 8) {
            value = value or ((data[position + index].toLong() and 0xFF) shl (index * 8))
        }
        position += 8
        return value
    }

    fun readI32(): Int? = readU32()?.toInt()

    fun readI64(): Long? = readU64()

    /**
     * `PropStream::readString` (fileloader.hpp:89-108): a uint16 length
     * prefix followed by raw bytes. A failed length or payload read leaves the
     * cursor where the C++ version left it (after the length prefix).
     */
    fun readString(): String? {
        val length = readU16() ?: return null
        if (size() < length) {
            return null
        }
        val value = String(data, position, length, StandardCharsets.UTF_8)
        position += length
        return value
    }

    /** `PropStream::skip` (fileloader.hpp:110-117). */
    fun skip(count: Int): Boolean {
        if (count < 0 || size() < count) {
            return false
        }
        position += count
        return true
    }
}

/**
 * Port of `PropWriteStream` (src/io/fileloader.hpp:124-163): a little-endian
 * append-only writer with `getStream`/`clear`, the buffer
 * `IOMapSerialize::saveTile` reuses across tiles.
 */
class PropWriteStream {

    private val buffer = ByteArrayOutputStream()

    fun writeU8(value: Int) {
        buffer.write(value and 0xFF)
    }

    fun writeU16(value: Int) {
        writeU8(value)
        writeU8(value ushr 8)
    }

    fun writeU32(value: Long) {
        for (shift in 0 until 32 step 8) {
            writeU8((value ushr shift).toInt())
        }
    }

    fun writeU64(value: Long) {
        for (shift in 0 until 64 step 8) {
            writeU8((value ushr shift).toInt())
        }
    }

    fun writeI32(value: Int) {
        writeU32(value.toLong() and 0xFFFFFFFFL)
    }

    fun writeI64(value: Long) {
        writeU64(value)
    }

    /** `PropWriteStream::writeString` (fileloader.hpp:150-159). */
    fun writeString(str: String) {
        val bytes = str.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > 0xFFFF) {
            writeU16(0)
            return
        }
        writeU16(bytes.size)
        buffer.write(bytes)
    }

    /** `PropWriteStream::getStream` (fileloader.hpp:132-135). */
    fun getStream(): ByteArray = buffer.toByteArray()

    /** `PropWriteStream::clear` (fileloader.hpp:137-139). */
    fun clear() {
        buffer.reset()
    }

    fun size(): Int = buffer.size()
}
