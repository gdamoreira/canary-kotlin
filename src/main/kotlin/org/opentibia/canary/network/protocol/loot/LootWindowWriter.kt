package org.opentibia.canary.network.protocol.loot

import io.netty.buffer.ByteBuf
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ObjectCategory_t
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.network.protocol.GameWindowWriter
import org.opentibia.canary.utils.Tools

/**
 * Outbound loot serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendLootContainers`   (0xC0, protocolgame.cpp:6469-6506)
 *  - `ProtocolGame::sendLootStats`        (0xCF, protocolgame.cpp:6508-6530)
 *  - `ProtocolGame::sendKillTrackerUpdate`(0xD1, protocolgame.cpp:10400-10425)
 *
 * The `!player`/`oldProtocol` guards stay with the caller. The managed
 * containers map arrives in the caller's ascending `std::map` order and
 * invalid categories are filtered exactly like `isValidObjectCategory`
 * (tools.cpp:1812). `sendLootStats` clones the item and stamps the looted
 * count before serializing it; the `Item::setIsLootTrackeable(false)` state
 * write stays with the caller (the port's [Item] does not model the loot
 * tracker flag yet). The kill tracker writes the `21`/zero fallback outfit for
 * a lifeless creature outfit. Every multi-byte field is Little-Endian and the
 * item payload is the shared [GameWindowWriter.writeItem] body.
 */
object LootWindowWriter {

    const val PACKET_LOOT_CONTAINERS: Int = 0xC0
    const val PACKET_LOOT_STATS: Int = 0xCF
    const val PACKET_KILL_TRACKER_UPDATE: Int = 0xD1

    /** `sendKillTrackerUpdate` fallback look type for a lifeless creature (protocolgame.cpp:10410). */
    private const val FALLBACK_LOOK_TYPE = 21

    /** One `m_managedContainers` row (`ObjectCategory_t` plus the loot/obtain container pair). */
    data class LootContainerEntry(
        val category: ObjectCategory_t,
        val lootContainerId: Int,
        val obtainContainerId: Int,
    )

    /** `sendLootContainers` (protocolgame.cpp:6469-6506). */
    fun writeLootContainers(out: ByteBuf, fallbackToMainContainer: Boolean, entries: List<LootContainerEntry>) {
        out.writeByte(PACKET_LOOT_CONTAINERS)
        out.writeByte(if (fallbackToMainContainer) 0x01 else 0x00)

        val validEntries = entries.filter { Tools.isValidObjectCategory(it.category) }
        out.writeByte(validEntries.size)
        for (entry in validEntries) {
            out.writeByte(entry.category.value)
            out.writeShortLE(entry.lootContainerId)
            out.writeShortLE(entry.obtainContainerId)
        }
    }

    /** `sendLootStats` (protocolgame.cpp:6508-6530). */
    fun writeLootStats(out: ByteBuf, item: Item, count: Int) {
        val lootedItem = item.clone()
        lootedItem.setItemCount(count)

        out.writeByte(PACKET_LOOT_STATS)
        GameWindowWriter.writeItem(out, lootedItem)
        out.writeTibiaString(lootedItem.getName())
    }

    /** `sendKillTrackerUpdate` (protocolgame.cpp:10400-10425). */
    fun writeKillTrackerUpdate(out: ByteBuf, name: String, creatureOutfit: Outfit, corpse: Container) {
        val isCorpseEmpty = corpse.empty()

        out.writeByte(PACKET_KILL_TRACKER_UPDATE)
        out.writeTibiaString(name)
        out.writeShortLE(if (creatureOutfit.lookType != 0) creatureOutfit.lookType else FALLBACK_LOOK_TYPE)
        out.writeByte(if (creatureOutfit.lookType != 0) creatureOutfit.lookHead else 0x00)
        out.writeByte(if (creatureOutfit.lookType != 0) creatureOutfit.lookBody else 0x00)
        out.writeByte(if (creatureOutfit.lookType != 0) creatureOutfit.lookLegs else 0x00)
        out.writeByte(if (creatureOutfit.lookType != 0) creatureOutfit.lookFeet else 0x00)
        out.writeByte(if (creatureOutfit.lookType != 0) creatureOutfit.lookAddons else 0x00)
        out.writeByte(if (isCorpseEmpty) 0 else corpse.size())

        if (!isCorpseEmpty) {
            for (item in corpse.getItemList()) {
                GameWindowWriter.writeItem(out, item)
            }
        }
    }
}
