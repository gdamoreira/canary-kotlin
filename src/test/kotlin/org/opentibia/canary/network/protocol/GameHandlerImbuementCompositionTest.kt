package org.opentibia.canary.network.protocol

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.fail
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
import org.opentibia.canary.systems.imbuement.ImbuementEngine
import org.opentibia.canary.systems.imbuement.ImbuementItem
import org.opentibia.canary.systems.imbuement.ImbuementTypes_t
import org.opentibia.canary.systems.imbuement.Imbuements

/**
 * Production-composition tests for the imbuement engine built by [GameHandler].
 *
 * The handler must arm `ImbuementDecay`'s 1-second `DispatcherLane::Maintenance`
 * tick on the injected server dispatcher (imbuements.cpp:520-525, 621-631), so
 * applying an imbuement through `Game::playerApplyImbuement` (game.cpp:7116)
 * decays without a caller-driven `checkImbuementDecay`, and it must compose the
 * loaded `g_imbuements()` registry onto the real [Item]s it passes through the
 * apply/clear seams and market escrow, matching `Item::getImbuementInfo`'s
 * `g_imbuements().getImbuement` validation (src/items/item.cpp:472-483).
 */
class GameHandlerImbuementCompositionTest {

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
    private lateinit var handler: GameHandler

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
        handler = GameHandler(
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

    /** Applies the `Strike` imbuement through the production `Game` seams. */
    private fun applyStrike(): Pair<Player, Item> {
        val player = Player(
            id = 8L,
            name = "Imbuer",
            maxHealth = 150,
            health = 150,
            position = Position(1000, 1000, 7),
        )
        player.pzLockedDuration = 60_000
        player.addInFightTicks()
        handler.game.addPlayer(player)

        val item = Item(id = 2400, type = items.get(2400))
        player.setImbuingItem(item)
        handler.game.removeMoney = { _, _, _, _ -> true }
        player.stash.addItemOnStash(11444, 20)

        val strike = imbuements.imbuementMap.values.single { it.name == "Strike" }
        handler.game.playerApplyImbuement(player.id, strike.id, 0)
        return player to item
    }

    private fun imbuementDuration(item: Item): Int? =
        ImbuementEngine(imbuements).getImbuementInfo(ImbuementItem(item), 0)?.duration

    private fun awaitUntil(message: String, timeoutMs: Long = 3_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(10)
        }
        fail<Unit>(message)
    }

    @Test
    fun `the production handler decays the applied imbuement through the dispatcher`() {
        val (_, item) = applyStrike()
        assertThat(imbuementDuration(item)).isEqualTo(72000)

        awaitUntil("the composed engine did not run a decay tick") {
            val duration = imbuementDuration(item)
            duration != null && duration < 72000
        }
    }

    @Test
    fun `the production handler assigns the imbuement registry to the composed item`() {
        val (player, item) = applyStrike()
        assertThat(item.imbuementRegistry).isSameAs(imbuements)

        handler.game.playerClearImbuement(player.id, 0)
        assertThat(imbuementDuration(item)).isNull()

        item.setImbuement(0, 4242, 1_000)
        assertThat(item.hasImbuements()).isFalse()
    }

    @Test
    fun `the market escrow composes the imbuement registry onto delivered items`() {
        val player = Player(
            id = 7L,
            name = "Seller",
            maxHealth = 150,
            health = 150,
            position = Position(1000, 1000, 7),
        )
        handler.game.addPlayer(player)
        val handle = handler.marketEscrow.resolvePlayer(player.getGUID(), false)
        assertThat(handle).isNotNull

        assertThat(handler.marketEscrow.addItemToInbox(handle!!, 2400, 1, 0)).isTrue()

        val delivered = player.inbox.items.single()
        assertThat(delivered.imbuementRegistry).isSameAs(imbuements)
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
