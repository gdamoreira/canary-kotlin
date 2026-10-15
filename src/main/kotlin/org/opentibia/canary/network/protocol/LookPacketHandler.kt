package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.game.movement.getDistanceX
import org.opentibia.canary.game.movement.getDistanceY
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.Items
import org.opentibia.canary.items.ObjectCategory_t
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.spells.Spells

object DescriptionFormatter {

    /**
     * `data/scripts/eventcallbacks/player/on_look.lua` `handleItemDescription`
     * base case: `"You see " .. inspectedThing:getDescription(lookDistance)`.
     * The magic wall/rush wood overrides and the refilling text are Lua logic
     * and stay in Lua (AGENTS.md 1.4).
     *
     * The renderer's `Item::items`/`g_spells()`/`g_vocations()`/
     * `Game::getObjectCategory` globals are injected as the matching seams; a
     * `null` seam degrades exactly like the absent global lookup.
     */
    fun formatItem(
        item: Item,
        lookDistance: Int,
        items: Items? = null,
        spells: Spells? = null,
        vocations: Vocations? = null,
        objectCategory: ((Item) -> ObjectCategory_t)? = null,
    ): String = "You see " + item.getDescription(lookDistance, items, spells, vocations, objectCategory)

    fun formatCreature(creature: Creature): String {
        val healthPercent = if (creature.maxHealth > 0) (creature.health * 100) / creature.maxHealth else 0
        return "You see a ${creature.name}. Health: $healthPercent%"
    }
}

object LookPacketHandler {

    const val PACKET_MESSAGE_TEXT: Byte = 0xB4.toByte()
    const val MESSAGE_INFO_DESCR: Byte = 0x13

    fun handleLook(
        player: Player,
        payload: ByteBuf,
        map: GameMap,
        items: Items? = null,
        spells: Spells? = null,
        vocations: Vocations? = null,
        objectCategory: ((Item) -> ObjectCategory_t)? = null,
    ): ByteBuf? {
        if (payload.readableBytes() < 8) return null

        val x = payload.readUnsignedShortLE()
        val y = payload.readUnsignedShortLE()
        val z = payload.readUnsignedByte().toInt()
        val itemId = payload.readUnsignedShortLE()
        val stackpos = payload.readUnsignedByte().toInt()

        val tile = map.getTile(Position(x, y, z)) ?: return null
        val item = tile.items.find { it.id == itemId } ?: (if (tile.ground?.id == itemId) tile.ground else null) ?: Item(itemId)

        // `Game::playerLookAt` (src/game/game.cpp:6376-6385): the Chebyshev
        // distance, plus the 15-floor offset for a different z.
        val playerPos = player.position
        var lookDistance = maxOf(getDistanceX(playerPos, tile.position), getDistanceY(playerPos, tile.position))
        if (playerPos.z != tile.position.z) {
            lookDistance += 15
        }

        val descriptionText =
            DescriptionFormatter.formatItem(item, lookDistance, items, spells, vocations, objectCategory)

        val out = Unpooled.buffer()
        out.writeByte(PACKET_MESSAGE_TEXT.toInt())
        out.writeByte(MESSAGE_INFO_DESCR.toInt())
        out.writeTibiaString(descriptionText)
        return out
    }
}
