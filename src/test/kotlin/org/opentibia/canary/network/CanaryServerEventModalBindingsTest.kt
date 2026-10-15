package org.opentibia.canary.network

import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.protocol.CLIENT_VERSION
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.account.CharacterEntry
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.crypto.XteaCipher
import org.opentibia.canary.network.message.readTibiaString
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.network.protocol.SocialWindowWriter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Wave 68 E1 production-root binding tests for the session player:
 *
 *  - `Creature::onThink` (src/creatures/creature.cpp:157-163) and the
 *    `Creature::dropCorpse` death-event loop (creature.cpp:819-824) must run
 *    through the root `ScriptingDispatch` for every creature that enters the
 *    world (`Creature.onThinkEventsHost`/`onDeathEventsHost` CALL-SITE NEED);
 *  - `Player::sendModalWindow` (player.cpp:9021-9028) must reach
 *    `ProtocolGame::sendModalWindow` (protocolgame.cpp:9630-9659, opcode
 *    0xFA) through the session modal host the network root composes.
 *
 * The harness composes the production [CanaryServer]/[GameHandler] over an
 * [EmbeddedChannel], exactly like [GameHandlerGameWiringTest], so the tests
 * exercise the real login/placement wiring instead of a hand-bound fixture.
 */
class CanaryServerEventModalBindingsTest {

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

    private val rsaCipher = RsaCipher()
    private val xteaKey = intArrayOf(0x01020304, 0x05060708, 0x090A0B0C, 0x0D0E0F10)

    private lateinit var dispatcher: GameDispatcher
    private lateinit var environment: LuaEnvironment
    private lateinit var server: CanaryServer
    private var channel: EmbeddedChannel? = null
    private var encoder: TibiaMessageEncoder? = null

    @BeforeEach
    fun setUp() {
        dispatcher = GameDispatcher().apply { start() }
        val gameMap = GameMap()
        for (y in 998..1002) {
            for (x in 998..1002) {
                gameMap.getOrCreateTile(Position(x, y, 7)).ground = Item(id = 106)
            }
        }
        environment = LuaEnvironment().apply { initState() }
        server = CanaryServer(
            loginPort = 0,
            gamePort = 0,
            accountRepository = MockAccountRepo(),
            gameMap = gameMap,
            dispatcher = dispatcher,
            luaEnvironment = environment,
        )
    }

    @AfterEach
    fun tearDown() {
        channel?.close()
        dispatcher.shutdown()
    }

    private fun openChannel(): EmbeddedChannel {
        val messageEncoder = TibiaMessageEncoder()
        encoder = messageEncoder
        return EmbeddedChannel(TibiaFrameDecoder(), messageEncoder, server.createGameHandler())
            .also { channel = it }
    }

    private fun awaitLane() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    /**
     * Settles every nested WorldCommit hop of the inbound answer route: the
     * packet router re-dispatches the parsed 0xF9 answer and the scripting
     * dispatch re-dispatches the Lua callback, so each queued hop needs its own
     * drain.
     */
    private fun settleLane() {
        repeat(4) { awaitLane() }
    }

    private fun loginPacket(): ByteBuf {
        val rsaPlain = ByteArray(RsaCipher.BLOCK_SIZE)
        val rsaBuf = Unpooled.wrappedBuffer(rsaPlain)
        rsaBuf.writerIndex(0)
        rsaBuf.writeByte(0) // check byte
        for (key in xteaKey) {
            rsaBuf.writeIntLE(key)
        }
        rsaBuf.writeByte(0) // GM flag
        rsaBuf.writeTibiaString("1\n1") // session key
        rsaBuf.writeTibiaString("Knight Hero") // character name

        val body = Unpooled.buffer()
        body.writeShortLE(2) // OS
        body.writeShortLE(CLIENT_VERSION)
        body.writeIntLE(CLIENT_VERSION)
        body.writeTibiaString("")
        body.writeTibiaString("")
        body.writeByte(0)
        body.writeBytes(rsaCipher.encrypt(rsaPlain))

        val checksum = Adler32Checksum.compute(body, 0, body.readableBytes())

        val packet = Unpooled.buffer()
        packet.writeShortLE(4 + body.readableBytes())
        packet.writeIntLE(checksum.toInt())
        packet.writeBytes(body)
        return packet
    }

    /** Drains every login frame so the next read observes the frame under test. */
    private fun drainOutbound(ch: EmbeddedChannel) {
        while (ch.readOutbound<ByteBuf>() != null) {
            // Discard the self appear / map description / VIP burst frames.
        }
    }

    @Test
    fun `a session player attached think event fires through the production binding`() {
        environment.loadChunk(
            """
            local attached = CreatureEvent("ProdSessionThink")
            function attached.onThink(creature, interval)
                seen_prod_think = creature:getName()
                seen_prod_think_interval = interval
            end
            attached:register()

            local unattached = CreatureEvent("ProdSessionThinkOther")
            function unattached.onThink(creature, interval)
                seen_prod_think_other = creature:getName()
            end
            unattached:register()
            """.trimIndent(),
        )

        val ch = openChannel()
        ch.writeInbound(loginPacket())
        awaitLane()

        val player = server.game.getPlayerByID(1L)
        assertThat(player).isNotNull
        assertThat(player!!.registerCreatureEvent("ProdSessionThink")).isTrue()

        val ran = CountDownLatch(1)
        assertThat(
            dispatcher.dispatch(DispatcherLane.WorldCommit) {
                server.game.addCreatureCheck(player)
                repeat(64) { server.game.checkCreatures() }
                ran.countDown()
            },
        ).isTrue()
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
        awaitLane()

        assertThat(environment.getGlobal("seen_prod_think").tojstring()).isEqualTo("Knight Hero")
        assertThat(environment.getGlobal("seen_prod_think_interval").toint()).isEqualTo(1000)
        // The attached-only set replaces the permissive global walk
        // (creature.cpp:1674-1693): the unattached event never fires.
        assertThat(environment.getGlobal("seen_prod_think_other").isnil()).isTrue()
    }

    @Test
    fun `a session player attached death event fires through the production binding`() {
        environment.loadChunk(
            """
            local attached = CreatureEvent("ProdSessionDeath")
            function attached.onDeath(creature, corpse, killer, mostDamageKiller, lastHitUnjustified, mostDamageUnjustified)
                seen_prod_death = creature:getName()
            end
            attached:register()
            """.trimIndent(),
        )

        val ch = openChannel()
        ch.writeInbound(loginPacket())
        awaitLane()

        val player = server.game.getPlayerByID(1L)
        assertThat(player).isNotNull
        assertThat(player!!.registerCreatureEvent("ProdSessionDeath")).isTrue()

        val ran = CountDownLatch(1)
        assertThat(
            dispatcher.dispatch(DispatcherLane.WorldCommit) {
                player.changeHealth(-player.health)
                ran.countDown()
            },
        ).isTrue()
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
        awaitLane()

        assertThat(environment.getGlobal("seen_prod_death").tojstring()).isEqualTo("Knight Hero")
    }

    @Test
    fun `the offline training dialog emits the 0xFA frame through the session sink`() {
        val ch = openChannel()
        ch.writeInbound(loginPacket())
        awaitLane()
        drainOutbound(ch)

        // The transport cipher is a test convenience: the frame under test is
        // the serialized `ProtocolGame::sendModalWindow` payload.
        encoder?.xteaCipher = null

        val player = server.game.getPlayerByID(1L)
        assertThat(player).isNotNull
        server.game.sendOfflineTrainingDialog(player)

        val frame = ch.readOutbound<ByteBuf>()
        assertThat(frame).isNotNull
        frame!!.readUnsignedShortLE() // outer frame length written by the encoder
        assertThat(frame.readUnsignedByte().toInt()).isEqualTo(SocialWindowWriter.PACKET_MODAL_WINDOW)
        // The wire id is the C++ `uint32_t`; `readIntLE` returns it signed.
        assertThat(frame.readIntLE().toLong() and 0xFFFFFFFFL).isEqualTo(0xFFFFFFFFL)
        assertThat(frame.readTibiaString()).isEqualTo("Choose a Skill")
        assertThat(frame.readTibiaString()).isEqualTo("Please choose a skill:")
        frame.release()
    }

    @Test
    fun `the root binder wires a live monster think event through the world dispatch`() {
        environment.loadChunk(
            """
            local attached = CreatureEvent("ProdMonsterThink")
            function attached.onThink(creature, interval)
                seen_prod_monster_think = creature:getName()
            end
            attached:register()

            local other = CreatureEvent("ProdMonsterThinkOther")
            function other.onThink(creature, interval)
                seen_prod_monster_think_other = creature:getName()
            end
            other:register()
            """.trimIndent(),
        )

        val service = MonsterComputeService().apply {
            start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        }
        try {
            val map = GameMap()
            for (y in 999..1001) {
                for (x in 999..1001) {
                    map.getOrCreateTile(Position(x, y, 7)).ground = Item(id = 106)
                }
            }
            val hostedServer = CanaryServer(
                loginPort = 0,
                gamePort = 0,
                accountRepository = MockAccountRepo(),
                gameMap = map,
                dispatcher = dispatcher,
                luaEnvironment = environment,
                monsterComputeService = service,
            )
            val host = hostedServer.monsterPipelinesHost
            assertThat(host).isNotNull
            val monster = Monster(
                id = 7L,
                name = "Rat",
                maxHealth = 100,
                health = 100,
                position = Position(1000, 1000, 7),
            )
            host!!.register(monster, MonsterType("Rat"))
            assertThat(host.typeStateOf(7L)!!.registerCreatureEvent("ProdMonsterThink")).isTrue()

            hostedServer.bindCreatureEventHosts(monster)
            monster.onThink(1000)
            awaitLane()

            assertThat(environment.getGlobal("seen_prod_monster_think").tojstring()).isEqualTo("Rat")
            // The monster host's attached-only set gates the loop (the same
            // `creatureEventsOf` resolution the world dispatch composes).
            assertThat(environment.getGlobal("seen_prod_monster_think_other").isnil()).isTrue()
        } finally {
            service.shutdown()
        }
    }

    @Test
    fun `the Lua modal flow emits through the session sink and the 0xF9 answer routes back`() {
        environment.loadChunk(
            """
            local helper = CreatureEvent("ProdModalEvent")
            function helper.onModalWindow(player, modalWindowId, buttonId, choiceId)
                seen_modal_count = (seen_modal_count or 0) + 1
                seen_modal_window = modalWindowId
                seen_modal_button = buttonId
                seen_modal_choice = choiceId
            end
            helper:register()

            function open_prod_modal(hero)
                local window = ModalWindow(42, "Prod Quest", "Accept?")
                window:addButton(1, "Yes")
                window:sendToPlayer(hero)
            end
            """.trimIndent(),
        )

        val ch = openChannel()
        ch.writeInbound(loginPacket())
        awaitLane()
        drainOutbound(ch)

        val player = server.game.getPlayerByID(1L)
        assertThat(player).isNotNull
        assertThat(player!!.registerCreatureEvent("ProdModalEvent")).isTrue()

        // `ModalWindowFunctions::luaModalWindowSendToPlayer`
        // (modal_window_functions.cpp:234-252): the root-attached operations
        // resolve the target player's session modal host and emit the 0xFA.
        encoder?.xteaCipher = null
        environment.globals.set("hero", environment.userdata.wrap("Player", player))
        environment.loadChunk("open_prod_modal(hero)")

        val frame = ch.readOutbound<ByteBuf>()
        assertThat(frame).isNotNull
        frame!!.readUnsignedShortLE()
        assertThat(frame.readUnsignedByte().toInt()).isEqualTo(SocialWindowWriter.PACKET_MODAL_WINDOW)
        assertThat(frame.readIntLE()).isEqualTo(42)
        assertThat(frame.readTibiaString()).isEqualTo("Prod Quest")
        assertThat(frame.readTibiaString()).isEqualTo("Accept?")
        frame.release()

        // `ProtocolGame::parseModalWindowAnswer` (0xF9,
        // protocolgame.cpp:4484-4489) routes through
        // `Game::playerAnswerModalWindow` to the session host that recorded 42.
        ch.writeInbound(modalAnswerPacket(windowId = 42, button = 1, choice = 0))
        settleLane()

        assertThat(environment.getGlobal("seen_modal_count").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("seen_modal_window").toint()).isEqualTo(42)
        assertThat(environment.getGlobal("seen_modal_button").toint()).isEqualTo(1)

        // The accepted answer erased the window (`Player::onModalWindowHandled`,
        // player.cpp:8318-8320): a repeated answer dispatches nothing.
        ch.writeInbound(modalAnswerPacket(windowId = 42, button = 1, choice = 0))
        settleLane()

        assertThat(environment.getGlobal("seen_modal_count").toint()).isEqualTo(1)
    }

    /** Frames a post-login 0xF9 modal answer exactly as the client does. */
    private fun modalAnswerPacket(windowId: Int, button: Int, choice: Int): ByteBuf {
        val payload = Unpooled.buffer()
        payload.writeByte(0xF9)
        payload.writeIntLE(windowId)
        payload.writeByte(button)
        payload.writeByte(choice)
        val padding = (8 - (payload.readableBytes() % 8)) % 8
        repeat(padding) { payload.writeByte(0x33) }

        val cipher = XteaCipher(xteaKey)
        val encrypted = payload.copy()
        cipher.encrypt(encrypted, 0, encrypted.readableBytes())
        val checksum = Adler32Checksum.compute(encrypted, 0, encrypted.readableBytes())

        val packet = Unpooled.buffer()
        packet.writeShortLE(4 + encrypted.readableBytes())
        packet.writeIntLE(checksum.toInt())
        packet.writeBytes(encrypted)
        return packet
    }
}
