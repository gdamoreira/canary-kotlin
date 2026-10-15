package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

/**
 * `Game::playerLookInShop` (src/game/game.cpp:6328-6358) renderer transcription:
 * the shop look sends `"You see " + Item::getDescription(it, 1, nullptr, count)`
 * through the [Game.getItemDescription] seam, whose production default composes
 * the ported renderer with the booted registries.
 */
class ShopLookRendererTest {

    @Test
    fun `the default shop look seam renders the item type through the renderer`() {
        val items = Items().apply {
            register(
                ItemType(
                    id = 2160, name = "gold coin", isStackable = true, pluralName = "gold coins", weight = 0.1,
                ),
            )
        }
        val game = Game(GameMap(), GameDispatcher(), itemTypes = items)
        val texts = mutableListOf<String>()
        game.sendTextMessage = { _, _, text -> texts.add(text) }
        val player = Player(id = 1, name = "Hero", maxHealth = 100, health = 100, position = Position(1000, 1000, 7))
        game.addPlayer(player)
        val merchant = Npc(id = 50, name = "Merchant", position = Position(1001, 1000, 7))
        game.getShopOwner = { merchant }

        game.playerLookInShop(player.id, 2160, 10)

        assertThat(texts.single()).isEqualTo("You see 10 gold coins.\nIt weighs 0.10 oz.")
    }

    @Test
    fun `the shop look renders an unstacked type with the close-range weight`() {
        val items = Items().apply {
            register(ItemType(id = 2385, name = "backpack", article = "a", isContainer = true, maxItems = 20, weight = 18.0))
        }
        val game = Game(GameMap(), GameDispatcher(), itemTypes = items)
        val texts = mutableListOf<String>()
        game.sendTextMessage = { _, _, text -> texts.add(text) }
        val player = Player(id = 1, name = "Hero", maxHealth = 100, health = 100, position = Position(1000, 1000, 7))
        game.addPlayer(player)
        val merchant = Npc(id = 50, name = "Merchant", position = Position(1001, 1000, 7))
        game.getShopOwner = { merchant }

        game.playerLookInShop(player.id, 2385, 1)

        assertThat(texts.single()).isEqualTo("You see a backpack (Vol:20).\nIt weighs 18.00 oz.")
    }
}
