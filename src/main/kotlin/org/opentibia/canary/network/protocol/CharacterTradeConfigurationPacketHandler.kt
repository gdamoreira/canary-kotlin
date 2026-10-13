package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Character trade configuration action (client opcode `0x76`), mirroring
 * `ProtocolGame::parseCharacterTradeConfigurationAction`
 * (protocolgame.cpp:3270-3297).
 *
 * Actions `2` and `3` carry the item lists; every other action only carries
 * the action byte and leaves the lists empty. The two leading `uint32_t`
 * values of actions `2`/`3` are read and discarded by Canary and are therefore
 * not modeled.
 */
data class CharacterTradeConfigurationCommand(
    val action: Int,
    val sourceItemIds: List<Int>,
    val targetItemIds: List<Int>,
    val storeItemIds: List<Int>,
)

/**
 * Decoder for `ProtocolGame::parseCharacterTradeConfigurationAction`
 * (protocolgame.cpp:3270).
 *
 * The item lists are followed by one extra byte for every item id for which
 * Canary's `itemTypeHasSubtype` (protocolgame.cpp:166-168) is true, i.e. an
 * item type that is a fluid container, a splash, stackable or charged. The
 * port's [org.opentibia.canary.items.ItemType] only models `isStackable`, so
 * the predicate is injected by the caller (PARITY GAP for fluid/splash/charge
 * items without a registry that carries those flags).
 *
 * PARITY GAP: the decoded configuration is not applied anywhere; Canary's
 * parser itself only reads and discards it (no `Game::player*` call).
 */
object CharacterTradeConfigurationPacketHandler {

    /** Client opcode for `ProtocolGame::parseCharacterTradeConfigurationAction`. */
    const val OPCODE_CHARACTER_TRADE_CONFIGURATION: Int = 0x76

    /** First action that carries the source/target/store item lists. */
    const val ACTION_WITH_ITEM_LISTS: Int = 2

    /** Second action that carries the source/target/store item lists. */
    const val ACTION_WITH_ITEM_LISTS_EXTENDED: Int = 3

    /**
     * 0x76: reads the action byte and, for actions 2/3, the two discarded
     * `uint32_t` values, the three item lists and the per-item subtype bytes.
     *
     * @param itemTypeHasSubtype `itemTypeHasSubtype(itemId)` seam
     *   (protocolgame.cpp:166); defaults to no subtype.
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parse(
        buffer: ByteBuf,
        itemTypeHasSubtype: (Int) -> Boolean = { false },
    ): CharacterTradeConfigurationCommand? {
        if (buffer.readableBytes() < 1) {
            return null
        }
        val action = buffer.readUnsignedByte().toInt()
        if (action != ACTION_WITH_ITEM_LISTS && action != ACTION_WITH_ITEM_LISTS_EXTENDED) {
            return CharacterTradeConfigurationCommand(
                action = action,
                sourceItemIds = emptyList(),
                targetItemIds = emptyList(),
                storeItemIds = emptyList(),
            )
        }

        if (buffer.readableBytes() < 8) {
            return null
        }
        buffer.skipBytes(8) // two discarded uint32_t values

        val sourceItemIds = readItemList(buffer, itemTypeHasSubtype) ?: return null
        val targetItemIds = readItemList(buffer, itemTypeHasSubtype) ?: return null
        val storeItemIds = readStoreItemList(buffer) ?: return null

        return CharacterTradeConfigurationCommand(
            action = action,
            sourceItemIds = sourceItemIds,
            targetItemIds = targetItemIds,
            storeItemIds = storeItemIds,
        )
    }

    /** Reads a `uint8_t` count followed by `(uint16_t itemId, [subtype byte])` entries. */
    private fun readItemList(buffer: ByteBuf, itemTypeHasSubtype: (Int) -> Boolean): List<Int>? {
        if (buffer.readableBytes() < 1) {
            return null
        }
        val count = buffer.readUnsignedByte().toInt()
        val itemIds = ArrayList<Int>(count)
        repeat(count) {
            if (buffer.readableBytes() < 2) {
                return null
            }
            val itemId = buffer.readUnsignedShortLE()
            if (itemTypeHasSubtype(itemId)) {
                if (buffer.readableBytes() < 1) {
                    return null
                }
                buffer.skipBytes(1)
            }
            itemIds.add(itemId)
        }
        return itemIds
    }

    /** Reads a `uint8_t` count followed by `uint16_t` store item ids. */
    private fun readStoreItemList(buffer: ByteBuf): List<Int>? {
        if (buffer.readableBytes() < 1) {
            return null
        }
        val count = buffer.readUnsignedByte().toInt()
        val itemIds = ArrayList<Int>(count)
        repeat(count) {
            if (buffer.readableBytes() < 2) {
                return null
            }
            itemIds.add(buffer.readUnsignedShortLE())
        }
        return itemIds
    }
}
