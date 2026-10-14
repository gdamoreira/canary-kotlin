package org.opentibia.canary.network

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.account.CharacterEntry
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.imbuement.ImbuementTypes_t
import org.opentibia.canary.systems.imbuement.Imbuements

/**
 * Composition tests for the server-owned imbuement decay engine: [CanaryServer]
 * must compose `g_imbuementDecay()` (src/creatures/players/imbuements/imbuements.hpp:167)
 * once over the server dispatcher, share it with every
 * [org.opentibia.canary.network.protocol.GameHandler] connection so decay
 * survives connection churn, and drain it on [CanaryServer.stop] before the
 * bootstrap shuts the dispatcher down (AGENTS.md deferred-callback safety).
 */
class CanaryServerImbuementCompositionTest {

    private class MockAccountRepo : AccountRepository {
        override fun findByAccountName(name: String): Account? = null

        override fun authenticate(name: String, password: String): Account? =
            if (name == "1" && password == "1") {
                Account(
                    id = 1,
                    name = "1",
                    passwordHash = "1",
                    characters = listOf(CharacterEntry("Knight Hero")),
                )
            } else {
                null
            }
    }

    private lateinit var dispatcher: GameDispatcher
    private lateinit var items: Items
    private lateinit var imbuements: Imbuements
    private lateinit var server: CanaryServer

    @BeforeEach
    fun setUp() {
        dispatcher = GameDispatcher().apply { start() }
        items = Items().also { registry ->
            registry.register(
                ItemType(
                    id = 2400,
                    name = "imbued sword",
                    imbuementSlot = 2,
                    imbuementTypes = linkedMapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3),
                ),
            )
        }
        imbuements = Imbuements().apply { assertThat(loadFromXml(IMBUEMENT_XML)).isTrue() }
        server = CanaryServer(
            loginPort = 0,
            gamePort = 0,
            accountRepository = MockAccountRepo(),
            gameMap = GameMap(),
            dispatcher = dispatcher,
            itemTypes = items,
            imbuements = imbuements,
        )
    }

    @AfterEach
    fun tearDown() {
        dispatcher.shutdown()
    }

    private fun applyStrike(playerId: Long, name: String) {
        val player = Player(
            id = playerId,
            name = name,
            maxHealth = 150,
            health = 150,
            position = Position(1000, 1000, 7),
        )
        player.pzLockedDuration = 60_000
        player.addInFightTicks()
        server.game.addPlayer(player)

        val item = Item(id = 2400, type = items.get(2400))
        player.setImbuingItem(item)
        server.game.removeMoney = { _, _, _, _ -> true }
        player.stash.addItemOnStash(11444, 20)

        val strike = imbuements.imbuementMap.values.single { it.name == "Strike" }
        server.game.playerApplyImbuement(player.id, strike.id, 0)
    }

    @Test
    fun `every composed handler shares the server-owned imbuement engine`() {
        val engine = server.imbuementEngine
        assertThat(engine).isNotNull

        server.createGameHandler()
        applyStrike(playerId = 8L, name = "Imbuer")
        assertThat(engine!!.decay.trackedItemCount).isEqualTo(1)

        // A later connection must not rebind the Game seams to a private engine.
        server.createGameHandler()
        applyStrike(playerId = 9L, name = "Second Imbuer")

        assertThat(engine.decay.trackedItemCount).isEqualTo(2)
        assertThat(engine.decay.hasScheduledCheck).isTrue()
    }

    @Test
    fun `server stop drains the shared decay scheduler`() {
        server.createGameHandler()
        applyStrike(playerId = 8L, name = "Imbuer")
        val engine = server.imbuementEngine!!
        assertThat(engine.decay.hasScheduledCheck).isTrue()
        assertThat(engine.decay.trackedItemCount).isEqualTo(1)

        server.stop()

        assertThat(engine.decay.hasScheduledCheck).isFalse()
        assertThat(engine.decay.trackedItemCount).isZero()
    }
}

private const val IMBUEMENT_XML = """
<imbuements>
    <base id="1" name="Basic" price="5000" protectionPrice="10000" percent="90" removecost="15000" duration="72000"/>
    <base id="2" name="Intricate" price="30000" protectionPrice="30000" percent="70" removecost="15000" duration="72000"/>
    <base id="3" name="Powerful" price="200000" protectionPrice="50000" percent="50" removecost="15000" duration="72000"/>
    <category id="3" name="Critical Hit" agressive="1"/>
    <imbuement name="Strike" base="2" category="3" iconid="1" premium="1" storage="0">
        <attribute key="effect" type="skill" value="critical" bonus="2500" chance="1000"/>
        <attribute key="item" value="11444" count="20"/>
        <attribute key="scroll" value="51742"/>
    </imbuement>
</imbuements>
"""
