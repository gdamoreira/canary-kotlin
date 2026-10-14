package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.systems.prey.PreySlot
import org.opentibia.canary.systems.taskhunting.TaskHuntingSlot

/**
 * Transcribes the final `Player::send*` members in player.cpp that carry real
 * logic instead of a single client forward (src/creatures/players/player.cpp):
 *
 * - `Player::sendLootMessage`      (player.cpp:12847-12859)
 * - `Player::sendMarketLeave`      (player.cpp:2456-2461)
 * - `Player::sendPlayerPartyIcons` (player.cpp:7797-7800)
 * - `Player::sendPreyData`         (player.cpp:10392-10400)
 * - `Player::sendTaskHuntingData`  (player.cpp:10630-10640)
 * - `Player::sendAddTileItem`      (player.cpp:7058-7065)
 * - `Player::sendUpdateTileCreature` (player.cpp:7082-7086)
 *
 * `Player::client` and the `ProtocolGame::send*` forwarders are call-site
 * seams per AGENTS.md 1.1, transcribed as injected sinks.
 */
class PlayerSendStateTest {

    private fun newPlayer(id: Long, name: String = "Hero$id"): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = Position(100, 100, 7))

    private fun newItem(id: Int): Item = Item(id, 1, ItemType(id = id, name = "Item$id"))

    private class RecordingCreature(
        id: Long,
        position: Position,
    ) : Creature(id, "Creature$id", maxHealth = 100, health = 100, position = position)

    // --- Player::sendLootMessage -------------------------------------------------

    @Test
    fun `sendLootMessage delivers to the player alone without a party`() {
        // player.cpp:12848-12851: `if (!party) sendTextMessage(MESSAGE_LOOT, message)`.
        val player = newPlayer(1)
        val messages = mutableListOf<Pair<MessageClass, String>>()
        player.sendTextMessageTyped = { type, text -> messages.add(type to text) }

        player.sendLootMessage("Loot of Rat: 3 gold coins.")

        assertThat(messages).containsExactly(MessageClass.MESSAGE_LOOT to "Loot of Rat: 3 gold coins.")
    }

    @Test
    fun `sendLootMessage fans out to the leader and every party member`() {
        // player.cpp:12853-12858: leader plus every non-null member.
        val leader = newPlayer(1, "Leader")
        val member = newPlayer(2, "Member")
        val other = newPlayer(3, "Other")
        val party = Party(leader = leader, autoShareExperience = false)
        assertThat(party.invitePlayer(member)).isTrue()
        assertThat(party.joinParty(member)).isTrue()

        val delivered = mutableListOf<String>()
        leader.sendTextMessageTyped = { _, text -> delivered.add("Leader:$text") }
        member.sendTextMessageTyped = { _, text -> delivered.add("Member:$text") }
        other.sendTextMessageTyped = { _, text -> delivered.add("Other:$text") }

        member.sendLootMessage("Loot of Dragon: 100 gold coins.")

        assertThat(delivered).containsExactly(
            "Leader:Loot of Dragon: 100 gold coins.",
            "Member:Loot of Dragon: 100 gold coins.",
        )
    }

    @Test
    fun `sendLootMessage tolerates a party without a leader or members`() {
        // player.cpp:12853: the leader send is guarded by a null check.
        val member = newPlayer(2, "Member")
        member.setParty(Party(leader = null, autoShareExperience = false))

        val delivered = mutableListOf<String>()
        member.sendTextMessageTyped = { _, text -> delivered.add("Member:$text") }

        member.sendLootMessage("Loot of Rat: nothing.")

        assertThat(delivered).isEmpty()
    }

    // --- Player::sendMarketLeave -------------------------------------------------

    @Test
    fun `sendMarketLeave clears the market flag and notifies the client`() {
        // player.cpp:2457-2460.
        val player = newPlayer(1)
        player.setInMarket(true)
        var sends = 0

        player.sendMarketLeave(hasClient = { true }, clientSendMarketLeave = { sends++ })

        assertThat(player.isInMarket()).isFalse()
        assertThat(sends).isEqualTo(1)
    }

    @Test
    fun `sendMarketLeave clears the market flag without a client`() {
        // player.cpp:2458: the flag change is unconditional.
        val player = newPlayer(1)
        player.setInMarket(true)
        var sends = 0

        player.sendMarketLeave(hasClient = { false }, clientSendMarketLeave = { sends++ })

        assertThat(player.isInMarket()).isFalse()
        assertThat(sends).isZero()
    }

    // --- Player::sendPlayerPartyIcons --------------------------------------------

    @Test
    fun `sendPlayerPartyIcons sends the shield then the skull for the target`() {
        // player.cpp:7798-7799.
        val player = newPlayer(1)
        val target = newPlayer(2)
        val order = mutableListOf<String>()

        player.sendPlayerPartyIcons(
            player = target,
            clientSendPartyCreatureShield = { order.add("shield:${it.getGUID()}") },
            clientSendPartyCreatureSkull = { order.add("skull:${it.getGUID()}") },
        )

        assertThat(order).containsExactly("shield:2", "skull:2")
    }

    // --- Player::sendPreyData ----------------------------------------------------

    @Test
    fun `sendPreyData is inert without a client`() {
        // player.cpp:10393: the whole body is inside `if (client)`.
        val player = newPlayer(1)
        player.features.prey.preys.add(PreySlot(id = 1))
        var sends = 0

        player.sendPreyData(
            hasClient = { false },
            clientSendPreyData = { sends++ },
            clientSendResourcesBalance = { _, _, _ -> sends++ },
        )

        assertThat(sends).isZero()
    }

    @Test
    fun `sendPreyData loops every slot then sends the prey resources balance`() {
        // player.cpp:10394-10398.
        val player = newPlayer(1)
        val slot1 = PreySlot(id = 1)
        val slot2 = PreySlot(id = 2)
        player.features.prey.preys.addAll(listOf(slot1, slot2))
        val calls = mutableListOf<String>()

        player.sendPreyData(
            hasClient = { true },
            clientSendPreyData = { slot -> calls.add("slot:${slot.id}") },
            getBankBalance = { 4242 },
            clientSendResourcesBalance = { money, bank, cards -> calls.add("balance:$money:$bank:$cards") },
        )

        assertThat(calls).containsExactly("slot:1", "slot:2", "balance:0:4242:0")
    }

    // --- Player::sendTaskHuntingData ---------------------------------------------

    @Test
    fun `sendTaskHuntingData sends the balance before every task slot`() {
        // player.cpp:10631-10638: resources balance, then each non-null slot.
        val player = newPlayer(1)
        val slot1 = TaskHuntingSlot(id = 1)
        val slot2 = TaskHuntingSlot(id = 2)
        player.features.taskHunting.taskHunting.addAll(listOf(slot1, slot2))
        val calls = mutableListOf<String>()

        player.sendTaskHuntingData(
            hasClient = { true },
            getBankBalance = { 21 },
            clientSendResourcesBalance = { money, bank, cards, points ->
                calls.add("balance:$money:$bank:$cards:$points")
            },
            clientSendTaskHuntingData = { slot -> calls.add("slot:${slot.id}") },
        )

        assertThat(calls).containsExactly("balance:0:21:0:0", "slot:1", "slot:2")
    }

    @Test
    fun `sendTaskHuntingData is inert without a client`() {
        // player.cpp:10631: the whole body is inside `if (client)`.
        val player = newPlayer(1)
        player.features.taskHunting.taskHunting.add(TaskHuntingSlot(id = 1))
        var sends = 0

        player.sendTaskHuntingData(
            hasClient = { false },
            clientSendResourcesBalance = { _, _, _, _ -> sends++ },
            clientSendTaskHuntingData = { sends++ },
        )

        assertThat(sends).isZero()
    }

    // --- Player::sendAddTileItem -------------------------------------------------

    @Test
    fun `sendAddTileItem resolves the stack position and forwards`() {
        // player.cpp:7059-7064: stackpos -1 suppresses the send.
        val player = newPlayer(1)
        val tile = Tile(Position(100, 100, 7))
        val item = newItem(2160)
        tile.ground = item
        val calls = mutableListOf<String>()

        player.sendAddTileItem(
            itemTile = tile,
            pos = tile.position,
            item = item,
            hasClient = { true },
            clientSendAddTileItem = { pos, stackpos, sent -> calls.add("$pos:$stackpos:${sent.id}") },
        )

        assertThat(calls).containsExactly("Position(x=100, y=100, z=7):0:2160")
    }

    @Test
    fun `sendAddTileItem skips an item with no visible stack position`() {
        // player.cpp:7061-7063: `if (stackpos != -1)`.
        val player = newPlayer(1)
        val tile = Tile(Position(100, 100, 7))
        val item = newItem(2160)
        var sends = 0

        player.sendAddTileItem(
            itemTile = tile,
            pos = tile.position,
            item = item,
            hasClient = { true },
            clientSendAddTileItem = { _, _, _ -> sends++ },
        )

        assertThat(sends).isZero()
    }

    @Test
    fun `sendAddTileItem is inert without a client`() {
        // player.cpp:7059: `if (client)`.
        val player = newPlayer(1)
        val tile = Tile(Position(100, 100, 7))
        val item = newItem(2160)
        tile.ground = item
        var sends = 0

        player.sendAddTileItem(
            itemTile = tile,
            pos = tile.position,
            item = item,
            hasClient = { false },
            clientSendAddTileItem = { _, _, _ -> sends++ },
        )

        assertThat(sends).isZero()
    }

    // --- Player::sendUpdateTileCreature ------------------------------------------

    @Test
    fun `sendUpdateTileCreature forwards the resolved client index`() {
        // player.cpp:7083-7085.
        val player = newPlayer(1)
        val tile = Tile(Position(100, 100, 7))
        val creature = RecordingCreature(id = 9, position = Position(100, 100, 7))
        tile.internalAddThing(creature)
        val calls = mutableListOf<String>()

        player.sendUpdateTileCreature(
            creature = creature,
            hasClient = { true },
            clientSendUpdateTileCreature = { pos, stackpos, sent -> calls.add("$pos:$stackpos:${sent.id}") },
        )

        assertThat(calls).containsExactly("Position(x=100, y=100, z=7):0:9")
    }

    @Test
    fun `sendUpdateTileCreature is inert without a client`() {
        // player.cpp:7083: `if (client)`.
        val player = newPlayer(1)
        val tile = Tile(Position(100, 100, 7))
        val creature = RecordingCreature(id = 9, position = Position(100, 100, 7))
        tile.internalAddThing(creature)
        var sends = 0

        player.sendUpdateTileCreature(
            creature = creature,
            hasClient = { false },
            clientSendUpdateTileCreature = { _, _, _ -> sends++ },
        )

        assertThat(sends).isZero()
    }
}
