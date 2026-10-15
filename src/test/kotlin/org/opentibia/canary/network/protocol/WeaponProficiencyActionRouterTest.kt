package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttributeParse
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.items.Items
import org.opentibia.canary.items.functions.ItemParse
import org.opentibia.canary.items.functions.ItemParseRegistry
import org.opentibia.canary.items.functions.XmlAttribute
import org.opentibia.canary.items.functions.XmlNode
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.proficiency.Proficiency
import org.opentibia.canary.systems.proficiency.ProficiencyLevel
import org.opentibia.canary.systems.proficiency.ProficiencyPerk
import org.opentibia.canary.systems.proficiency.WeaponProficiencyBonus
import org.opentibia.canary.systems.proficiency.WeaponProficiencyData
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Transcript tests for the `0xB3` weapon-proficiency action paths
 * (`ProtocolGame::parseWeaponProficiency`, protocolgame.cpp:3130-3202):
 * action `0x02` clears the stored selection, action `0x03` replaces it with the
 * resolved registry perks, and every action ends with the trailing
 * `sendWeaponProficiencyWindow` (0xC4) and `sendWeaponProficiency` (0x5C)
 * frames.
 */
class WeaponProficiencyActionRouterTest {

    private val perkAttack = ProficiencyPerk(
        level = 0,
        index = 0,
        value = 1.5,
        type = WeaponProficiencyBonus.ATTACK_DAMAGE,
    )

    private val perkDefense = ProficiencyPerk(
        level = 1,
        index = 0,
        value = 2.5,
        type = WeaponProficiencyBonus.DEFENSE_BONUS,
    )

    private val registry: Map<Int, Proficiency> = mapOf(
        1 to Proficiency(
            id = 1,
            level = mutableListOf(
                ProficiencyLevel(mutableListOf(perkAttack)),
                ProficiencyLevel(mutableListOf(perkDefense)),
            ),
            maxLevel = 2,
        ),
    )

    private fun items(): Items {
        val items = Items()
        val itemParse = ItemParse(
            registry = object : ItemParseRegistry {
                override fun getItemType(id: Int): ItemType = items.getItemType(id)
                override fun addLadderId(newId: Int) = Unit
                override fun addDummyId(newId: Int, rate: Int) = Unit
            },
            proficiencyIds = setOf(1),
        )
        items.loadFromXml(
            """
            <items>
                <item id="21" name="Test Sword">
                    <attribute key="weaponType" value="sword"/>
                    <attribute key="proficiency" value="1"/>
                </item>
            </items>
            """.trimIndent(),
            ItemAttributeParse { key, node, value, itemType ->
                itemParse.initParse(key, XmlNode(node), XmlAttribute(value), itemType)
                true
            },
        )
        return items
    }

    private fun hero(): Player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7)).apply {
        weaponTypeProvider = { id -> if (id == 21) WeaponType_t.WEAPON_SWORD else WeaponType_t.WEAPON_NONE }
    }

    private fun context(
        dispatcher: GameDispatcher,
        items: ItemTypeRegistry?,
        clientVersionString: String = "",
    ): GamePacketContext = GamePacketContext(
        map = GameMap(),
        dispatcher = dispatcher,
        luaEnvironment = LuaEnvironment().apply { initState() },
        itemTypes = items,
        clientVersionString = clientVersionString,
        protocolProfile = ProtocolProfileRegistry.getCurrentProfile(),
        weaponProficiencyRegistry = registry,
    )

    private fun awaitWorldCommit(dispatcher: GameDispatcher) {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    private fun bytes(build: ByteBuf.() -> Unit): ByteArray {
        val out = Unpooled.buffer()
        out.build()
        val result = ByteArray(out.readableBytes())
        out.readBytes(result)
        out.release()
        return result
    }

    @Test
    fun `action 0x02 clears the selected perks and sends the trailing window and proficiency frames`() {
        val dispatcher = GameDispatcher().apply { start() }
        try {
            val player = hero()
            val sink = InMemoryProtocolSink(player, GameMap())
            player.protocolSink = sink
            player.features.weaponProficiencies[21] =
                WeaponProficiencyData(experience = 5_000, perks = mutableListOf(perkAttack))

            val frame = Unpooled.buffer()
            frame.writeByte(WeaponProficiencyPacketHandler.OPCODE_WEAPON_PROFICIENCY)
            frame.writeByte(WeaponProficiencyPacketHandler.ACTION_CLEAR_SELECTED_PERKS)
            frame.writeShortLE(21)

            assertThat(GamePacketRouter.route(player, frame, context(dispatcher, items()))).isEqualTo(1)
            awaitWorldCommit(dispatcher)

            assertThat(player.features.weaponProficiencies.getValue(21).perks).isEmpty()
            assertThat(sink.frames).containsExactly(
                // 0xC4 empty window (no detail list without a recognized build).
                bytes {
                    writeByte(0xC4)
                    writeShortLE(21)
                    writeIntLE(5_000)
                    writeByte(0)
                },
                // 0x5C proficiency frame; the upgrade byte is 0 without a resolver.
                bytes {
                    writeByte(0x5C)
                    writeShortLE(21)
                    writeIntLE(5_000)
                    writeByte(0)
                },
            )
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `action 0x03 replaces the selection with the resolved registry perk`() {
        val dispatcher = GameDispatcher().apply { start() }
        try {
            val player = hero()
            val sink = InMemoryProtocolSink(player, GameMap())
            player.protocolSink = sink
            player.features.weaponProficiencies[21] =
                WeaponProficiencyData(experience = 5_000, perks = mutableListOf(perkDefense))

            val frame = Unpooled.buffer()
            frame.writeByte(WeaponProficiencyPacketHandler.OPCODE_WEAPON_PROFICIENCY)
            frame.writeByte(WeaponProficiencyPacketHandler.ACTION_SET_SELECTED_PERKS)
            frame.writeShortLE(21)
            frame.writeByte(1) // one selection
            frame.writeByte(0) // level 0
            frame.writeByte(0) // perk index 0

            assertThat(GamePacketRouter.route(player, frame, context(dispatcher, items()))).isEqualTo(1)
            awaitWorldCommit(dispatcher)

            assertThat(player.features.weaponProficiencies.getValue(21).perks)
                .containsExactly(perkAttack)

            assertThat(sink.frames).containsExactly(
                bytes {
                    writeByte(0xC4)
                    writeShortLE(21)
                    writeIntLE(5_000)
                    writeByte(1)
                    writeByte(0)
                    writeByte(0)
                },
                bytes {
                    writeByte(0x5C)
                    writeShortLE(21)
                    writeIntLE(5_000)
                    writeByte(0)
                },
            )
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `a locked level selection is rejected and the stored selection stays empty`() {
        val dispatcher = GameDispatcher().apply { start() }
        try {
            val player = hero()
            val sink = InMemoryProtocolSink(player, GameMap())
            player.protocolSink = sink
            // Level 1 unlocks at 25000 experience (standard table); 5000 only
            // unlocks level 0, so selecting level 1 is rejected.
            player.features.weaponProficiencies[21] = WeaponProficiencyData(experience = 5_000)

            val frame = Unpooled.buffer()
            frame.writeByte(WeaponProficiencyPacketHandler.OPCODE_WEAPON_PROFICIENCY)
            frame.writeByte(WeaponProficiencyPacketHandler.ACTION_SET_SELECTED_PERKS)
            frame.writeShortLE(21)
            frame.writeByte(1)
            frame.writeByte(1) // locked level
            frame.writeByte(0)

            assertThat(GamePacketRouter.route(player, frame, context(dispatcher, items()))).isEqualTo(1)
            awaitWorldCommit(dispatcher)

            assertThat(player.features.weaponProficiencies.getValue(21).perks).isEmpty()
            assertThat(sink.frames).hasSize(2)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `the official payload actions keep the selection and still send the trailing frames`() {
        val dispatcher = GameDispatcher().apply { start() }
        try {
            val player = hero()
            val sink = InMemoryProtocolSink(player, GameMap())
            player.protocolSink = sink
            player.features.weaponProficiencies[21] =
                WeaponProficiencyData(experience = 5_000, perks = mutableListOf(perkAttack))

            val frame = Unpooled.buffer()
            frame.writeByte(WeaponProficiencyPacketHandler.OPCODE_WEAPON_PROFICIENCY)
            frame.writeByte(0x04)
            frame.writeShortLE(21)
            frame.writeByte(0x11) // consumed official payload byte
            frame.writeByte(0x22) // consumed official payload byte

            assertThat(GamePacketRouter.route(player, frame, context(dispatcher, items()))).isEqualTo(1)
            awaitWorldCommit(dispatcher)

            assertThat(player.features.weaponProficiencies.getValue(21).perks)
                .containsExactly(perkAttack)
            assertThat(sink.frames).hasSize(2)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `the window carries the detail list for a recognized build and the 0x5C upgrade byte resolves through the session`() {
        val dispatcher = GameDispatcher().apply { start() }
        try {
            val player = hero()
            val ch = EmbeddedChannel()
            val sink = NettyProtocolSink(
                ch,
                player,
                GameMap(),
                weaponProficiencyUpgradeAvailable = { it == 21 },
            )
            player.protocolSink = sink
            // 30000 experience unlocks level 1 but only one perk is selected, so
            // `isUpgradeAvailable` is true through the injected resolver.
            player.features.weaponProficiencies[21] =
                WeaponProficiencyData(experience = 30_000, perks = mutableListOf(perkAttack))

            val frame = Unpooled.buffer()
            frame.writeByte(WeaponProficiencyPacketHandler.OPCODE_WEAPON_PROFICIENCY)
            frame.writeByte(WeaponProficiencyPacketHandler.ACTION_CLEAR_SELECTED_PERKS)
            frame.writeShortLE(21)

            assertThat(
                GamePacketRouter.route(
                    player,
                    frame,
                    context(dispatcher, items(), clientVersionString = "15.25.794c2e.1400"),
                ),
            ).isEqualTo(1)
            awaitWorldCommit(dispatcher)

            ch.runPendingTasks()
            val frames = mutableListOf<ByteArray>()
            while (true) {
                val out = ch.readOutbound<ByteBuf>() ?: break
                frames.add(ByteArray(out.readableBytes()).also { outBytes -> out.readBytes(outBytes) })
                out.release()
            }
            ch.finishAndReleaseAll()

            assertThat(frames).containsExactly(
                bytes {
                    writeByte(0xC4)
                    writeShortLE(21)
                    writeIntLE(30_000)
                    writeByte(0)
                    writeByte(0) // detail-list boundary
                },
                bytes {
                    writeByte(0x5C)
                    writeShortLE(21)
                    writeIntLE(30_000)
                    writeByte(1) // upgrade available through the session resolver
                },
            )
        } finally {
            dispatcher.shutdown()
        }
    }
}
