package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

class NpcDialogueTest {

    @Test
    fun `handles player greeting and trade state transitions`() {
        val npc = Npc(
            id = 500,
            name = "Frodo",
            position = Position(100, 100, 7)
        )

        npc.addShopItem(itemId = 2006, name = "Mana Potion", price = 50)

        val player = Player(1, "Adventurer", 100, 100, position = Position(100, 101, 7))

        // Greeting (Canary Npc::onCreatureSay)
        val greetingResponse = npc.onCreatureSay(player, type = 1, text = "hi")
        assertThat(greetingResponse).contains("Hello, Adventurer!")
        assertThat(npc.isInteractingWithPlayer(player.id)).isTrue()

        // Shop query
        val shopItems = npc.getShopItems()
        assertThat(shopItems).hasSize(1)
        assertThat(shopItems.first().name).isEqualTo("Mana Potion")

        // Farewell
        val farewellResponse = npc.onCreatureSay(player, type = 1, text = "bye")
        assertThat(farewellResponse).contains("Good bye")
        assertThat(npc.isInteractingWithPlayer(player.id)).isFalse()
    }
}
