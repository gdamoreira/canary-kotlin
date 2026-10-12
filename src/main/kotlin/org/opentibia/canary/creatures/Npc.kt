package org.opentibia.canary.creatures

import org.opentibia.canary.map.Position

data class ShopItem(
    val itemId: Int,
    val name: String,
    val price: Int
)

class Npc(
    id: Long,
    name: String,
    speed: Int = 100,
    position: Position
) : Creature(id, name, maxHealth = 100, health = 100, maxMana = 0, mana = 0, speed = speed, position = position) {

    private val shopItems = mutableListOf<ShopItem>()
    private val interactingPlayers = mutableSetOf<Long>()

    fun addShopItem(itemId: Int, name: String, price: Int) {
        shopItems.add(ShopItem(itemId, name, price))
    }

    fun getShopItems(): List<ShopItem> = shopItems

    fun setPlayerInteraction(playerId: Long) {
        interactingPlayers.add(playerId)
    }

    fun removePlayerInteraction(playerId: Long) {
        interactingPlayers.remove(playerId)
    }

    fun isInteractingWithPlayer(playerId: Long): Boolean = interactingPlayers.contains(playerId)

    /**
     * Mirrors Canary C++ Npc::onCreatureSay(creature, type, text)
     * src/creatures/npcs/npc.cpp:670
     */
    fun onCreatureSay(player: Player, type: Int, text: String): String {
        return when (text.lowercase().trim()) {
            "hi", "hello" -> {
                setPlayerInteraction(player.id)
                "Hello, ${player.name}!"
            }
            "bye", "farewell" -> {
                removePlayerInteraction(player.id)
                "Good bye, ${player.name}."
            }
            "trade" -> {
                if (isInteractingWithPlayer(player.id)) {
                    "Take a look at my wares."
                } else {
                    ""
                }
            }
            else -> {
                if (isInteractingWithPlayer(player.id)) "I don't understand." else ""
            }
        }
    }
}
