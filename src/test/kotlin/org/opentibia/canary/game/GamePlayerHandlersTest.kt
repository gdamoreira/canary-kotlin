package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.imbuement.Imbuement
import org.opentibia.canary.systems.imbuement.Imbuements
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the remaining `player*` Game handlers:
 *
 *  - `Game::playerApplyImbuement` (game.cpp:7116)
 *  - `Game::playerClearImbuement` (game.cpp:7153)
 *  - `Game::playerRequestInventoryImbuements` (game.cpp:11487)
 *  - `Game::playerToggleMount` (game.cpp:7219)
 *  - `Game::playerRequestOutfit` (game.cpp:7206)
 *  - `Game::playerReceivePing` (game.cpp:4454) / `Game::playerReceivePingBack` (game.cpp:4463)
 *  - `Game::playerCloseNpcChannel` (game.cpp:4443)
 *  - `Game::playerBroadcastMessage` (game.cpp:4310)
 */
class GamePlayerHandlersTest {

    private val center = Position(1000, 1000, 7)
    private val imbuements = Imbuements().also {
        assertThat(
            it.loadFromXml(
                """
                <imbuements>
                    <base id="1" name="Basic" price="5000" protectionPrice="10000" removecost="15000" duration="72000" percent="90" />
                    <category id="0" name="Elemental Damage" agressive="1" />
                    <imbuement base="1" iconid="1" premium="1" category="0" name="Scorch">
                        <item key="item" value="9636" count="25" />
                        <effect key="effect" type="damage" combat="fire" value="10" />
                    </imbuement>
                </imbuements>
                """,
            ),
        ).isTrue()
    }

    private fun newGame(): Game = Game(GameMap(), GameDispatcher(), imbuements = imbuements)

    private fun Game.player(id: Long, name: String): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = center).also { addPlayer(it) }

    private fun registeredImbuement(): Imbuement = imbuements.imbuementMap.values.first()

    // ----- playerApplyImbuement -----

    @Test
    fun `playerApplyImbuement ignores an unknown player`() {
        val game = newGame()
        var applied = false
        game.createScrollImbuement = { _, _ -> applied = true }

        game.playerApplyImbuement(404, registeredImbuement().id, 0)

        assertThat(applied).isFalse()
    }

    @Test
    fun `playerApplyImbuement rejects an exhausted player`() {
        val game = newGame()
        val player = game.player(1, "Mage")
        game.isUIExhausted = { true }
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }
        var applied = false
        game.createScrollImbuement = { _, _ -> applied = true }

        game.playerApplyImbuement(player.id, registeredImbuement().id, 0)

        assertThat(cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)
        assertThat(applied).isFalse()
    }

    @Test
    fun `playerApplyImbuement ignores an unknown imbuement id`() {
        val game = newGame()
        val player = game.player(1, "Mage")
        var applied = false
        game.createScrollImbuement = { _, _ -> applied = true }

        game.playerApplyImbuement(player.id, 9999, 0)

        assertThat(applied).isFalse()
    }

    @Test
    fun `playerApplyImbuement creates a scroll imbuement without an imbuing item`() {
        val game = newGame()
        val player = game.player(1, "Mage")
        val created = mutableListOf<Imbuement>()
        game.createScrollImbuement = { _, imbuement -> created.add(imbuement) }

        game.playerApplyImbuement(player.id, registeredImbuement().id, 0)

        assertThat(created).containsExactly(registeredImbuement())
    }

    @Test
    fun `playerApplyImbuement applies to the carried imbuing item`() {
        val game = newGame()
        val player = game.player(1, "Mage")
        val item = Item(id = 2160)
        player.setImbuingItem(item)
        game.getImbuingItem = { item }
        val applied = mutableListOf<Triple<Imbuement, Item, Int>>()
        game.onApplyImbuement = { _, imbuement, target, slot -> applied.add(Triple(imbuement, target, slot)) }

        game.playerApplyImbuement(player.id, registeredImbuement().id, 2)

        assertThat(applied).containsExactly(Triple(registeredImbuement(), item, 2))
    }

    @Test
    fun `playerApplyImbuement reports an item that left the player`() {
        val game = newGame()
        val player = game.player(1, "Mage")
        val item = Item(id = 2160)
        player.setImbuingItem(item)
        game.getImbuingItem = { item }
        game.isImbuingItemCarriedByPlayer = { _, _ -> false }
        val results = mutableListOf<String>()
        game.sendImbuementResult = { _, message -> results.add(message) }
        var applied = false
        game.onApplyImbuement = { _, _, _, _ -> applied = true }

        game.playerApplyImbuement(player.id, registeredImbuement().id, 0)

        assertThat(results).hasSize(1)
        assertThat(applied).isFalse()
    }

    // ----- playerClearImbuement -----

    @Test
    fun `playerClearImbuement rejects an exhausted player`() {
        val game = newGame()
        val player = game.player(1, "Mage")
        game.isUIExhausted = { true }
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerClearImbuement(player.id, 0)

        assertThat(cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)
    }

    @Test
    fun `playerClearImbuement ignores a player without an imbuing item`() {
        val game = newGame()
        val player = game.player(1, "Mage")
        var cleared = false
        game.onClearImbuement = { _, _, _ -> cleared = true }

        game.playerClearImbuement(player.id, 0)

        assertThat(cleared).isFalse()
    }

    @Test
    fun `playerClearImbuement clears the carried item slot`() {
        val game = newGame()
        val player = game.player(1, "Mage")
        val item = Item(id = 2160)
        player.setImbuingItem(item)
        game.getImbuingItem = { item }
        val cleared = mutableListOf<Pair<Item, Int>>()
        game.onClearImbuement = { _, target, slot -> cleared.add(target to slot) }

        game.playerClearImbuement(player.id, 1)

        assertThat(cleared).containsExactly(item to 1)
    }

    // ----- playerRequestInventoryImbuements -----

    @Test
    fun `playerRequestInventoryImbuements only sends while the tracker is open`() {
        val game = newGame()
        val player = game.player(1, "Mage")
        val sends = mutableListOf<Map<Slots_t, Item>>()
        game.sendInventoryImbuements = { _, items -> sends.add(items) }

        game.playerRequestInventoryImbuements(player.id, false)
        assertThat(sends).isEmpty()

        game.playerRequestInventoryImbuements(player.id, true)
        assertThat(sends).hasSize(1)
        assertThat(sends[0]).isEmpty()
    }

    @Test
    fun `playerRequestInventoryImbuements collects the equipped items`() {
        val game = newGame()
        val player = game.player(1, "Mage")
        val helmet = Item(id = 2160)
        val armor = Item(id = 2161)
        assertThat(player.addItem(helmet, Slots_t.CONST_SLOT_HEAD)).isEqualTo(ReturnValue.NOERROR)
        assertThat(player.addItem(armor, Slots_t.CONST_SLOT_ARMOR)).isEqualTo(ReturnValue.NOERROR)
        val sends = mutableListOf<Map<Slots_t, Item>>()
        game.sendInventoryImbuements = { _, items -> sends.add(items) }

        game.playerRequestInventoryImbuements(player.id, true)

        assertThat(sends).hasSize(1)
        assertThat(sends[0]).containsEntry(Slots_t.CONST_SLOT_HEAD, helmet)
        assertThat(sends[0]).containsEntry(Slots_t.CONST_SLOT_ARMOR, armor)
    }

    // ----- mount / outfit -----

    @Test
    fun `playerToggleMount delegates to the player`() {
        val game = newGame()
        val player = game.player(1, "Rider")
        val calls = mutableListOf<Pair<Long, Boolean>>()
        game.toggleMount = { target, mount -> calls.add(target.id to mount) }

        game.playerToggleMount(player.id, true)

        assertThat(calls).containsExactly(player.id to true)
    }

    @Test
    fun `playerRequestOutfit is gated by the change-outfit config`() {
        val game = newGame()
        val player = game.player(1, "Rider")
        var sent = false
        game.sendOutfitWindow = { sent = true }

        game.allowChangeOutfit = false
        game.playerRequestOutfit(player.id)
        assertThat(sent).isFalse()

        game.allowChangeOutfit = true
        game.playerRequestOutfit(player.id)
        assertThat(sent).isTrue()
    }

    // ----- ping -----

    @Test
    fun `playerReceivePing and playerReceivePingBack delegate to the player`() {
        val game = newGame()
        val player = game.player(1, "Pinger")
        val calls = mutableListOf<String>()
        game.receivePing = { calls.add("ping") }
        game.sendPingBack = { calls.add("pingBack") }

        game.playerReceivePing(player.id)
        game.playerReceivePingBack(player.id)

        assertThat(calls).containsExactly("ping", "pingBack")
    }

    // ----- playerCloseNpcChannel -----

    @Test
    fun `playerCloseNpcChannel notifies the NPCs on the player tile`() {
        val game = newGame()
        val player = game.player(1, "Talker")
        game.map.getOrCreateTile(center).ground = Item(id = 100)
        assertThat(game.internalPlaceCreature(player, center)).isTrue()
        val npc = Npc(id = 2, name = "Merchant", position = Position(1001, 1000, 7))
        game.map.getOrCreateTile(npc.position).ground = Item(id = 100)
        assertThat(game.internalPlaceCreature(npc, npc.position)).isTrue()
        val closed = mutableListOf<Player>()
        npc.registerOnPlayerCloseChannelHandler { _, closedPlayer -> closed.add(closedPlayer) }

        game.playerCloseNpcChannel(player.id)

        assertThat(closed).containsExactly(player)
    }

    // ----- playerBroadcastMessage -----

    @Test
    fun `playerBroadcastMessage requires the broadcast flag`() {
        val game = newGame()
        val player = game.player(1, "Announcer")
        var sent = false
        game.sendPrivateMessage = { _, _, _, _ -> sent = true }

        assertThat(game.playerBroadcastMessage(player, "hello")).isFalse()
        assertThat(sent).isFalse()
    }

    @Test
    fun `playerBroadcastMessage sends the broadcast to every player`() {
        val game = newGame()
        val first = game.player(1, "Announcer")
        val second = game.player(2, "Listener")
        first.setFlag(PlayerFlags_t.CanBroadcast)
        val messages = mutableListOf<Triple<Player, SpeakClasses, String>>()
        game.sendPrivateMessage = { target, _, type, text -> messages.add(Triple(target, type, text)) }

        assertThat(game.playerBroadcastMessage(first, "hello")).isTrue()

        assertThat(messages).containsExactly(
            Triple(first, SpeakClasses.TALKTYPE_BROADCAST, "hello"),
            Triple(second, SpeakClasses.TALKTYPE_BROADCAST, "hello"),
        )
    }
}
