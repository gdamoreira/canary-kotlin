package org.opentibia.canary.network.protocol.forge

import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.systems.forge.ForgeHistory

/**
 * Forge skill percentages written by `ProtocolGame::sendForgeSkillStats`
 * (src/server/network/protocol/protocolgame.cpp:11100), in slot order:
 * LEFT (weapon onslaught/fatal), ARMOR (dodge), HEAD (momentum) and
 * LEGS (transcendence). Each value is the item's chance already scaled by 100.
 *
 * A `null` value mirrors `getInventoryItem` returning nothing: the C++ loop
 * `continue`s and emits no bytes for that slot, so the number of written
 * `uint16_t` pairs shrinks with the number of equipped items.
 */
data class ForgeSkillStats(
    val onslaught: Int? = null,
    val dodge: Int? = null,
    val momentum: Int? = null,
    val transcendence: Int? = null,
)

/**
 * Outbound forge serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *  - `ProtocolGame::sendForgeHistory`    (opcode 0x88, protocolgame.cpp:7483)
 *  - `ProtocolGame::sendForgeSkillStats` (no opcode; appends to a caller
 *    message, protocolgame.cpp:11100)
 *
 * The history is paged nine entries at a time, newest first: page 0 sends
 * entries `size-1 .. size-9`, and the oldest entries land on the last page.
 * Byte order is Little-Endian (`writeShortLE`, `writeIntLE`).
 */
object ForgeHistorySerializer {

    const val PACKET_FORGE_HISTORY: Int = 0x88

    private const val ENTRIES_PER_PAGE = 9

    /**
     * `page` is the client's zero-based page index. Canary's parameter is a
     * `uint8_t`, so only its low byte is used. A page past the end is clamped to
     * the last page (protocolgame.cpp:7496).
     */
    fun writeForgeHistory(out: ByteBuf, page: Int, history: List<ForgeHistory>) {
        val requestedPage = (page and 0xFF) + 1
        val historySize = history.size

        var currentPage = 1
        var lastPage = 1
        val pageEntries = ArrayList<ForgeHistory>()

        if (historySize > 0) {
            val lastPageCount = ((historySize - 1) / ENTRIES_PER_PAGE) + 1
            lastPage = minOf(lastPageCount, 0xFFFF)
            currentPage = minOf(requestedPage, lastPage)

            val pageFirstEntry = historySize - (currentPage - 1) * ENTRIES_PER_PAGE
            val pageLastEntry = if (historySize > currentPage * ENTRIES_PER_PAGE) {
                historySize - currentPage * ENTRIES_PER_PAGE
            } else {
                0
            }
            for (entry in pageFirstEntry downTo (pageLastEntry + 1)) {
                pageEntries += history[entry - 1]
            }
        }

        out.writeByte(PACKET_FORGE_HISTORY)
        out.writeShortLE(currentPage - 1) // current page
        out.writeShortLE(lastPage) // last page
        out.writeByte(pageEntries.size and 0xFF) // history entries sent

        for (entry in pageEntries) {
            out.writeIntLE(entry.createdAt.toInt())
            out.writeByte(entry.actionType.value and 0xFF)
            out.writeTibiaString(entry.description)
            out.writeByte(if (entry.bonus in 1..7) 0x01 else 0x00)
        }
    }

    /**
     * protocolgame.cpp:11100-11134. The C++ helper is currently never called;
     * the frame it feeds (skills panel) carries no forge opcode, so this writer
     * appends the duplicated `uint16_t` chances to the caller's buffer. Empty
     * inventory slots are skipped exactly like the C++ `continue`.
     */
    fun writeForgeSkillStats(out: ByteBuf, stats: ForgeSkillStats) {
        val skillBySlot = listOf(
            stats.onslaught,
            stats.dodge,
            stats.momentum,
            stats.transcendence,
        )
        for (skill in skillBySlot) {
            if (skill == null) {
                continue
            }
            out.writeShortLE(skill)
            out.writeShortLE(skill)
        }
    }
}
