package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.npcs.NpcType
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position

/**
 * Transcripts of the `Npc` player-interaction/focus state machine
 * (src/creatures/npcs/npc.cpp:554-575, 1156-1187) and `onCreatureSay`
 * dispatch (npc.cpp:670-689).
 */
class NpcInteractionTest {

    private val npcPos = Position(100, 100, 7)

    private fun playerAt(id: Long, pos: Position, world: NpcTestWorld): Player {
        val player = Player(id, "Player$id", 100, 100, position = pos)
        world.creaturesById[id] = player
        world.playersByGUID[id] = player
        return player
    }

    @Test
    fun `setPlayerInteraction queues the player, focuses the npc and stores the topic`() {
        val world = NpcTestWorld()
        val npc = Npc(id = 1, name = "Frodo", position = npcPos, world = world)
        playerAt(7, Position(90, 100, 7), world)

        npc.setPlayerInteraction(7, topicId = 3)

        assertThat(npc.isInteractingWithPlayer(7)).isTrue()
        assertThat(npc.isPlayerInteractingOnTopic(7, 3)).isTrue()
        assertThat(npc.isPlayerInteractingOnTopic(7, 0)).isFalse()
        assertThat(npc.direction).isEqualTo(Direction.West)
    }

    @Test
    fun `setPlayerInteraction turns to each newly queued player only`() {
        val world = NpcTestWorld()
        val npc = Npc(id = 1, name = "Frodo", position = npcPos, world = world)
        playerAt(1, Position(110, 100, 7), world)
        playerAt(2, Position(100, 90, 7), world)

        npc.setPlayerInteraction(1)
        assertThat(npc.direction).isEqualTo(Direction.East)

        npc.setPlayerInteraction(2)
        assertThat(npc.direction).isEqualTo(Direction.North)

        npc.setPlayerInteraction(1, topicId = 5)
        assertThat(npc.direction).isEqualTo(Direction.North)
        assertThat(npc.isPlayerInteractingOnTopic(1, 5)).isTrue()
    }

    @Test
    fun `setPlayerInteraction ignores a player id the world cannot resolve`() {
        val world = NpcTestWorld()
        val npc = Npc(id = 1, name = "Frodo", position = npcPos, world = world)

        npc.setPlayerInteraction(7, topicId = 2)

        assertThat(npc.isInteractingWithPlayer(7)).isFalse()
        assertThat(npc.isPlayerInteractingOnTopic(7, 2)).isFalse()
    }

    @Test
    fun `a npc without an injected world keeps the legacy interaction bookkeeping`() {
        val npc = Npc(id = 1, name = "Frodo", position = npcPos)

        npc.setPlayerInteraction(7, topicId = 4)

        assertThat(npc.isInteractingWithPlayer(7)).isTrue()
        assertThat(npc.isPlayerInteractingOnTopic(7, 4)).isTrue()
    }

    @Test
    fun `removePlayerInteraction drops the queue entry, closes the shop window and refocuses the previous player`() {
        val world = NpcTestWorld()
        val npc = Npc(id = 1, name = "Frodo", position = npcPos, world = world)
        playerAt(1, Position(110, 100, 7), world)
        val second = playerAt(2, Position(90, 100, 7), world)

        npc.setPlayerInteraction(1)
        npc.setPlayerInteraction(2)
        assertThat(npc.direction).isEqualTo(Direction.West)

        npc.removePlayerInteraction(second)

        assertThat(npc.isInteractingWithPlayer(2)).isFalse()
        assertThat(world.closedShopWindows).containsExactly(second)
        assertThat(npc.direction).isEqualTo(Direction.East)
    }

    @Test
    fun `removePlayerInteraction does not close a shop window for a player that was not interacting`() {
        val world = NpcTestWorld()
        val npc = Npc(id = 1, name = "Frodo", position = npcPos, world = world)
        val player = Player(9, "Stranger", 100, 100, position = Position(90, 100, 7))

        npc.removePlayerInteraction(player)

        assertThat(world.closedShopWindows).isEmpty()
        assertThat(npc.direction).isEqualTo(Direction.South)
    }

    @Test
    fun `resetPlayerInteractions clears the topics but keeps the focus order`() {
        val world = NpcTestWorld()
        val npc = Npc(id = 1, name = "Frodo", position = npcPos, world = world)
        playerAt(7, Position(90, 100, 7), world)

        npc.setPlayerInteraction(7, topicId = 3)
        npc.direction = Direction.North

        npc.resetPlayerInteractions()

        assertThat(npc.isInteractingWithPlayer(7)).isFalse()
        assertThat(npc.isPlayerInteractingOnTopic(7, 3)).isFalse()

        npc.setPlayerInteraction(7, topicId = 9)

        assertThat(npc.direction).isEqualTo(Direction.North)
        assertThat(npc.isPlayerInteractingOnTopic(7, 9)).isTrue()
    }

    @Test
    fun `turnToCreature reproduces the Canary tangent quadrants`() {
        val world = NpcTestWorld()
        val npc = Npc(id = 1, name = "Frodo", position = npcPos, world = world)

        npc.turnToCreature(Player(1, "A", 100, 100, position = Position(110, 100, 7)))
        assertThat(npc.direction).isEqualTo(Direction.East)

        npc.turnToCreature(Player(2, "B", 100, 100, position = Position(90, 100, 7)))
        assertThat(npc.direction).isEqualTo(Direction.West)

        npc.turnToCreature(Player(3, "C", 100, 100, position = Position(100, 90, 7)))
        assertThat(npc.direction).isEqualTo(Direction.North)

        npc.turnToCreature(Player(4, "D", 100, 100, position = Position(100, 110, 7)))
        assertThat(npc.direction).isEqualTo(Direction.South)

        npc.turnToCreature(Player(5, "E", 100, 100, position = npcPos))
        assertThat(npc.direction).isEqualTo(Direction.South)
    }

    @Test
    fun `canInteract accepts only same floor positions within the sight range`() {
        val npc = Npc(id = 1, name = "Frodo", position = npcPos)

        assertThat(npc.canInteract(Position(104, 104, 7))).isTrue()
        assertThat(npc.canInteract(Position(105, 100, 7))).isFalse()
        assertThat(npc.canInteract(Position(100, 100, 8))).isFalse()
        assertThat(npc.canInteract(Position(107, 100, 7), range = 7)).isTrue()
    }

    @Test
    fun `canSeeInvisibility is always true for a npc`() {
        val npc = Npc(id = 1, name = "Frodo", position = npcPos)

        assertThat(npc.canSeeInvisibility()).isTrue()
    }

    @Test
    fun `onCreatureSay dispatches player speech to the registered engine handlers`() {
        val npc = Npc(id = 1, name = "Frodo", position = npcPos)
        val player = Player(7, "Aria", 100, 100, position = Position(100, 101, 7))
        val seen = mutableListOf<Triple<Creature, Int, String>>()

        npc.registerOnCreatureSayHandler { _, creature, type, text -> seen.add(Triple(creature, type, text)) }
        npc.onCreatureSay(player as Creature, type = 1, text = "hi")

        assertThat(seen).containsExactly(Triple(player as Creature, 1, "hi"))
    }

    @Test
    fun `onCreatureSay ignores creatures that are not players`() {
        val npc = Npc(id = 1, name = "Frodo", position = npcPos)
        val monster = Monster(9, "Rat", 10, 10, position = Position(100, 101, 7))
        var calls = 0

        npc.registerOnCreatureSayHandler { _, _, _, _ -> calls++ }
        npc.onCreatureSay(monster, type = 1, text = "hi")

        assertThat(calls).isZero()
    }
}
