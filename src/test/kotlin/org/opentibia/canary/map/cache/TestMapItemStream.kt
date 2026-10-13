package org.opentibia.canary.map.cache

/**
 * Byte-backed [MapItemStream] test utility mirroring the node/escape semantics
 * of Canary `FileStream` (src/io/filestream.hpp:19-139,
 * src/io/filestream.cpp:40-94): reads inside a node unescape the `0xFD` byte,
 * `getString` reads a uint16 length (escaped) and then raw bytes.
 */
class TestMapItemStream(
    private val data: IntArray,
    private var nodes: Int = 1,
) : MapItemStream {

    private var position: Int = 0

    override fun getU8(): Int {
        if (nodes > 0) {
            return readEscapedByte()
        }
        if (position >= data.size) {
            return 0
        }
        return data[position++]
    }

    override fun getU16(): Int {
        val low = getU8()
        val high = getU8()
        return low or (high shl 8)
    }

    override fun getString(): String {
        val length = getU16()
        if (length == 0 || position + length > data.size) {
            return ""
        }
        val bytes = ByteArray(length) { data[position + it].toByte() }
        position += length
        return String(bytes)
    }

    override fun isProp(prop: Int): Boolean {
        if (getU8() == prop) {
            return true
        }
        back()
        return false
    }

    override fun back() {
        --position
    }

    override fun startNode(): Boolean {
        if (getU8() == NODE_START) {
            ++nodes
            return true
        }
        back()
        return false
    }

    override fun endNode(): Boolean {
        if (getU8() == NODE_END) {
            --nodes
            return true
        }
        back()
        return false
    }

    private fun readEscapedByte(): Int {
        if (position >= data.size) {
            return 0
        }
        if (data[position] == ESCAPE_BYTE) {
            ++position
            if (position >= data.size) {
                return 0
            }
        }
        return data[position++]
    }

    companion object {
        const val NODE_START: Int = 0xFE
        const val NODE_END: Int = 0xFF
        private const val ESCAPE_BYTE: Int = 0xFD

        fun bytes(vararg values: Int): IntArray = values

        /** Encodes a `FileStream::getString` payload: uint16 length + raw bytes. */
        fun string(value: String): IntArray {
            val raw = value.toByteArray()
            return intArrayOf(raw.size and 0xFF, (raw.size shr 8) and 0xFF) +
                raw.map { it.toInt() and 0xFF }
        }
    }
}
