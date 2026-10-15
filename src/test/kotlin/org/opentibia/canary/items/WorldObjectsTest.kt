package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

class WorldObjectsTest {

    @Test
    fun `stepping on magic field inflicts damage and triggers condition`() {
        val fireField = MagicField(id = 1487, damage = 20)
        val player = Player(1, "Adventurer", 100, 100, position = Position(100, 100, 7))

        fireField.onStepInField(player)
        assertThat(player.health).isEqualTo(80)
    }

    @Test
    fun `stepping on an owned magic field credits the field owner`() {
        // `MagicField::onStepInField` stamps the owner on the field's damage
        // (src/creatures/combat/combat.cpp:2590-2641); `Item::getOwnerId` and
        // the `g_game()` catalog resolution feed the drain credit
        // (condition.cpp:2000-2004, creature.cpp:893-899).
        val owner = Player(7, "Owner", 100, 100, position = Position(99, 100, 7))
        val fireField = MagicField(id = 1487, damage = 20)
        fireField.setOwner(7)
        fireField.ownerCreatureProvider = { ownerId: Long -> if (ownerId == 7L) owner else null }

        val player = Player(1, "Adventurer", 100, 100, position = Position(100, 100, 7))
        fireField.onStepInField(player)

        assertThat(player.health).isEqualTo(80)
        assertThat(player.getDamageMap()[7L]?.total).isEqualTo(20)
        assertThat(player.lastHitCreatureId).isEqualTo(7L)
    }

    @Test
    fun `stepping on a magic field with an unresolved owner stays unattributed`() {
        val fireField = MagicField(id = 1487, damage = 20)
        fireField.setOwner(7)

        val player = Player(1, "Adventurer", 100, 100, position = Position(100, 100, 7))
        fireField.onStepInField(player)

        assertThat(player.health).isEqualTo(80)
        assertThat(player.getDamageMap()).isEmpty()
    }

    @Test
    fun `stepping on teleporter moves player to destination coordinates`() {
        val destination = Position(32000, 32000, 7)
        val teleporter = TeleportItem(id = 1387, destination = destination)
        val player = Player(1, "Adventurer", 100, 100, position = Position(100, 100, 7))

        teleporter.onStepIn(player)
        assertThat(player.position).isEqualTo(destination)
    }

    @Test
    fun `using closed door toggles state to open door`() {
        val door = DoorItem(closedId = 1210, openId = 1211, isOpen = false)
        assertThat(door.currentId).isEqualTo(1210)

        door.toggle()
        assertThat(door.isOpen).isTrue()
        assertThat(door.currentId).isEqualTo(1211)

        door.toggle()
        assertThat(door.isOpen).isFalse()
        assertThat(door.currentId).isEqualTo(1210)
    }
}
