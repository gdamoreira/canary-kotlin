package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.imbuement.Imbuement
import org.opentibia.canary.systems.imbuement.Imbuements
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router application tests for the imbuement window opcodes:
 * `0xD7` (`Game::playerCloseImbuementWindow`, game.cpp:7178) clears
 * `Player::imbuingItem`, `0xB2` `PickItem`
 * (`ProtocolGame::parseImbuementAction` + `openImbuementWindow`,
 * protocolgame.cpp:3065, 10193) stores the resolved tile item, and `0xD5`/`0xD6`
 * (`Game::playerApplyImbuement`/`playerClearImbuement`, game.cpp:7116, 7153)
 * drive the imbuement engine over the `Game` seams.
 */
class OpcodeApplicationImbuementRouterTest {

    private lateinit var dispatcher: GameDispatcher
    private lateinit var map: GameMap
    private lateinit var game: Game

    private fun start(imbuements: Imbuements? = null) {
        dispatcher = GameDispatcher()
        dispatcher.start()
        map = GameMap()
        game = Game(map, dispatcher, imbuements = imbuements)
        game.isUIExhausted = { it.isUIExhausted() }
        game.updateUIExhausted = { it.updateUIExhausted() }
    }

    private fun imbuements(): Imbuements = Imbuements().apply {
        assertThat(loadFromXml(IMBUEMENT_XML)).isTrue()
    }

    private fun strike(imbuements: Imbuements): Imbuement =
        imbuements.imbuementMap.values.single { it.name == "Strike" }

    private fun stop() {
        dispatcher.shutdown()
    }

    private fun context(): GamePacketContext = GamePacketContext(
        map = map,
        dispatcher = dispatcher,
        luaEnvironment = LuaEnvironment().apply { initState() },
        game = game,
    )

    private fun hero(): Player =
        Player(1, "Hero", 150, 150, position = Position(1000, 1000, 7)).also { game.addPlayer(it) }

    private fun route(frame: ByteBuf, player: Player): Int {
        val handled = GamePacketRouter.route(player, frame, context())
        awaitWorldCommit()
        return handled
    }

    private fun awaitWorldCommit() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun `close imbuement window opcode 0xD7 clears the imbuing item`() {
        start()
        try {
            val player = hero()
            player.setImbuingItem(Item(id = 2160))

            val frame = Unpooled.buffer()
            frame.writeByte(ImbuementPacketHandler.OPCODE_CLOSE_IMBUEMENT_WINDOW)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.hasImbuingItem()).isFalse()
        } finally {
            stop()
        }
    }

    @Test
    fun `close imbuement window opcode 0xD7 is inert without an imbuing item`() {
        start()
        try {
            val player = hero()

            val frame = Unpooled.buffer()
            frame.writeByte(ImbuementPacketHandler.OPCODE_CLOSE_IMBUEMENT_WINDOW)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.hasImbuingItem()).isFalse()
        } finally {
            stop()
        }
    }

    private fun writePickItem(frame: ByteBuf, position: Position, itemId: Int, stackPosition: Int) {
        frame.writeByte(ImbuementPacketHandler.OPCODE_IMBUEMENT_ACTION)
        frame.writeByte(ImbuementPacketHandler.IMBUEMENT_ACTION_PICK_ITEM)
        frame.writeShortLE(position.x)
        frame.writeShortLE(position.y)
        frame.writeByte(position.z)
        frame.writeShortLE(itemId)
        frame.writeByte(stackPosition)
    }

    private fun imbuableItem(itemId: Int, slots: Int = 2): Item = Item(
        id = itemId,
        count = 1,
        type = ItemType(id = itemId, name = "item $itemId", imbuementSlot = slots),
    )

    @Test
    fun `pick item opcode 0xB2 stores the imbuable tile item as the imbuing item`() {
        start()
        try {
            val player = hero()
            val position = Position(1000, 1000, 7)
            map.getOrCreateTile(position).addItem(imbuableItem(2160))

            val frame = Unpooled.buffer()
            writePickItem(frame, position, itemId = 2160, stackPosition = 0)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.hasImbuingItem()).isTrue()
        } finally {
            stop()
        }
    }

    @Test
    fun `pick item opcode 0xB2 ignores a tile item without imbuement slots`() {
        start()
        try {
            val player = hero()
            val position = Position(1000, 1000, 7)
            map.getOrCreateTile(position).addItem(imbuableItem(2160, slots = 0))

            val frame = Unpooled.buffer()
            writePickItem(frame, position, itemId = 2160, stackPosition = 0)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.hasImbuingItem()).isFalse()
        } finally {
            stop()
        }
    }

    @Test
    fun `pick item opcode 0xB2 ignores a mismatched tile item id`() {
        start()
        try {
            val player = hero()
            val position = Position(1000, 1000, 7)
            map.getOrCreateTile(position).addItem(imbuableItem(2160))

            val frame = Unpooled.buffer()
            writePickItem(frame, position, itemId = 2161, stackPosition = 0)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.hasImbuingItem()).isFalse()
        } finally {
            stop()
        }
    }

    @Test
    fun `open action opcode 0xB2 keeps the previously picked imbuing item`() {
        start()
        try {
            val player = hero()
            player.setImbuingItem(Item(id = 2160, count = 1))

            val frame = Unpooled.buffer()
            frame.writeByte(ImbuementPacketHandler.OPCODE_IMBUEMENT_ACTION)
            frame.writeByte(ImbuementPacketHandler.IMBUEMENT_ACTION_OPEN)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.hasImbuingItem()).isTrue()
        } finally {
            stop()
        }
    }

    private fun writeApplyImbuement(frame: ByteBuf, slot: Int, imbuementId: Long) {
        frame.writeByte(ImbuementPacketHandler.OPCODE_APPLY_IMBUEMENT)
        frame.writeByte(slot)
        frame.writeIntLE(imbuementId.toInt())
    }

    private fun writeClearImbuement(frame: ByteBuf, slot: Int) {
        frame.writeByte(ImbuementPacketHandler.OPCODE_CLEAR_IMBUEMENT)
        frame.writeByte(slot)
    }

    @Test
    fun `apply imbuement opcode 0xD5 applies through the game handler and consumes the UI interaction`() {
        val imbuements = imbuements()
        start(imbuements)
        try {
            val player = hero()
            val item = Item(id = 2160, count = 1)
            player.setImbuingItem(item)
            game.getImbuingItem = { if (it === player) item else null }
            val applied = mutableListOf<Pair<Int, Int>>()
            game.onApplyImbuement = { _, imbuement, appliedItem, slot ->
                applied.add(imbuement.id to slot)
                assertThat(appliedItem).isSameAs(item)
            }
            val strike = strike(imbuements)
            assertThat(player.isUIExhausted(500)).isFalse()

            val frame = Unpooled.buffer()
            writeApplyImbuement(frame, slot = 1, imbuementId = strike.id.toLong())

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(applied).containsExactly(strike.id to 1)
            assertThat(player.isUIExhausted(500)).isTrue()
        } finally {
            stop()
        }
    }

    @Test
    fun `apply imbuement opcode 0xD5 drops an id above uint16 max`() {
        val imbuements = imbuements()
        start(imbuements)
        try {
            val player = hero()
            var applied = false
            game.onApplyImbuement = { _, _, _, _ -> applied = true }

            val frame = Unpooled.buffer()
            writeApplyImbuement(frame, slot = 0, imbuementId = 0x1_0000L)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(applied).isFalse()
            assertThat(player.isUIExhausted(500)).isFalse()
        } finally {
            stop()
        }
    }

    @Test
    fun `apply imbuement opcode 0xD5 without an imbuing item creates the scroll imbuement`() {
        val imbuements = imbuements()
        start(imbuements)
        try {
            val player = hero()
            val scrolls = mutableListOf<Int>()
            game.createScrollImbuement = { _, imbuement -> scrolls.add(imbuement.id) }
            val strike = strike(imbuements)

            val frame = Unpooled.buffer()
            writeApplyImbuement(frame, slot = 0, imbuementId = strike.id.toLong())

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(scrolls).containsExactly(strike.id)
            assertThat(player.isUIExhausted(500)).isTrue()
        } finally {
            stop()
        }
    }

    @Test
    fun `apply imbuement opcode 0xD5 ignores an unknown imbuement id`() {
        val imbuements = imbuements()
        start(imbuements)
        try {
            val player = hero()
            var applied = false
            var scrolled = false
            game.onApplyImbuement = { _, _, _, _ -> applied = true }
            game.createScrollImbuement = { _, _ -> scrolled = true }

            val frame = Unpooled.buffer()
            writeApplyImbuement(frame, slot = 0, imbuementId = 4242)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(applied).isFalse()
            assertThat(scrolled).isFalse()
            assertThat(player.isUIExhausted(500)).isTrue()
        } finally {
            stop()
        }
    }

    @Test
    fun `clear imbuement opcode 0xD6 clears the slot through the game handler`() {
        val imbuements = imbuements()
        start(imbuements)
        try {
            val player = hero()
            val item = Item(id = 2160, count = 1)
            player.setImbuingItem(item)
            game.getImbuingItem = { if (it === player) item else null }
            val cleared = mutableListOf<Pair<Int, Int>>()
            game.onClearImbuement = { _, clearedItem, slot ->
                cleared.add(clearedItem.id to slot)
            }

            val frame = Unpooled.buffer()
            writeClearImbuement(frame, slot = 2)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(cleared).containsExactly(2160 to 2)
            assertThat(player.isUIExhausted(500)).isTrue()
        } finally {
            stop()
        }
    }

    @Test
    fun `clear imbuement opcode 0xD6 without an imbuing item is inert but consumes the interaction`() {
        val imbuements = imbuements()
        start(imbuements)
        try {
            val player = hero()
            var cleared = false
            game.onClearImbuement = { _, _, _ -> cleared = true }

            val frame = Unpooled.buffer()
            writeClearImbuement(frame, slot = 0)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(cleared).isFalse()
            assertThat(player.isUIExhausted(500)).isTrue()
        } finally {
            stop()
        }
    }
}

private const val IMBUEMENT_XML = """
<imbuements>
    <base id="1" name="Basic" price="5000" protectionPrice="10000" percent="90" removecost="15000" duration="72000"/>
    <category id="3" name="Critical Hit" agressive="1"/>
    <imbuement name="Strike" base="1" category="3" iconid="1" premium="0" storage="0">
        <attribute key="description" value="Raises crit hit damage by 15% and crit hit chance by 10%."/>
        <attribute key="effect" type="skill" value="critical" bonus="1500" chance="1000"/>
        <attribute key="item" value="11444" count="20"/>
    </imbuement>
</imbuements>
"""
