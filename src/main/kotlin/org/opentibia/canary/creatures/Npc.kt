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

    fun isInteractingWith(player: Player): Boolean = interactingPlayers.contains(player.id)

    fun onHear(player: Player, message: String): String {
        return when (message.lowercase().trim()) {
            "hi", "hello" -> {
                interactingPlayers.add(player.id)
                "Hello, ${player.name}!"
            }
            "bye", "farewell" -> {
                interactingPlayers.remove(player.id)
                "Good bye, ${player.name}."
            }
            "trade" -> {
                if (isInteractingWith(player)) {
                    "Take a look at my wares."
                } else {
                    ""
                }
            }
            else -> {
                if (isInteractingWith(player)) "I don't understand." else ""
            }
        }
    }
}
