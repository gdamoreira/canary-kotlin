package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
import org.opentibia.canary.items.ItemAttributeParse
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.items.functions.ItemParse
import org.opentibia.canary.items.functions.ItemParseRegistry
import org.opentibia.canary.items.functions.XmlAttribute
import org.opentibia.canary.items.functions.XmlNode
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.crypto.XteaCipher
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.systems.proficiency.Proficiency
import org.opentibia.canary.systems.proficiency.ProficiencyLevel
import org.opentibia.canary.systems.proficiency.ProficiencyPerk
import org.opentibia.canary.systems.proficiency.WeaponProficiencyBonus
import org.opentibia.canary.systems.proficiency.WeaponProficiencyData

/**
 * Injection test for the `proficiencies.json` registry behind the `0xB3`
 * perk-selection validation (`ProtocolGame::parseWeaponProficiency` action
 * `0x03`, protocolgame.cpp:3167-3185 -> `WeaponProficiency::setSelectedPerk`,
 * weapon_proficiency.cpp:586-610): the boot-loaded registry carried by the
 * item registry must reach the session packet context so a selection resolves
 * against it in production.
 */
class WeaponProficiencyRegistryInjectionTest {

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
    private val xteaCipher = XteaCipher(xteaKey)

    private lateinit var dispatcher: GameDispatcher
    private lateinit var gameMap: GameMap
    private lateinit var items: Items
    private lateinit var handler: GameHandler
    private var channel: EmbeddedChannel? = null

    private val perkAttack = ProficiencyPerk(
        level = 0,
        index = 0,
        value = 1.5,
        type = WeaponProficiencyBonus.ATTACK_DAMAGE,
    )

    private val registry: Map<Int, Proficiency> = mapOf(
        1 to Proficiency(
            id = 1,
            level = mutableListOf(
                ProficiencyLevel(mutableListOf(perkAttack)),
                ProficiencyLevel(mutableListOf()),
            ),
            maxLevel = 2,
        ),
    )

    @BeforeEach
    fun setUp() {
        dispatcher = GameDispatcher().apply { start() }
        gameMap = GameMap()
        gameMap.getOrCreateTile(Position(1000, 1000, 7)).ground = Item(id = 106)
        items = proficiencyItems().also { it.weaponProficiencyRegistry = registry }
        handler = GameHandler(
            rsaCipher = rsaCipher,
            accountRepository = MockAccountRepo(),
            gameMap = gameMap,
            dispatcher = dispatcher,
            itemTypes = items,
        )
    }

    @AfterEach
    fun tearDown() {
        channel?.close()
        dispatcher.shutdown()
    }

    private fun proficiencyItems(): Items {
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

    private fun openChannel(): EmbeddedChannel =
        EmbeddedChannel(TibiaFrameDecoder(), TibiaMessageEncoder(), handler).also { channel = it }

    private fun awaitLane() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    /**
     * The router arms dispatch their application as a *nested* WorldCommit
     * event, so one latch round-trip is not enough; poll the tracked state
     * through repeated lane barriers until [expectedSize] perks are stored.
     */
    private fun awaitSelectedPerks(player: Player, weaponId: Int, expectedSize: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (System.nanoTime() < deadline &&
            player.features.weaponProficiencies[weaponId]?.perks?.size != expectedSize
        ) {
            val ran = CountDownLatch(1)
            dispatcher.dispatch { ran.countDown() }
            ran.await(50, TimeUnit.MILLISECONDS)
        }
    }

    private fun login(ch: EmbeddedChannel): Player {
        ch.writeInbound(loginPacket())
        assertThat(ch.readOutbound<ByteBuf>()).isNotNull
        awaitLane()
        return handler.game.getPlayerByID(1L)!!
    }

    private fun loginPacket(): ByteBuf {
        val rsaPlain = ByteArray(RsaCipher.BLOCK_SIZE)
        val rsaBuf = Unpooled.wrappedBuffer(rsaPlain)
        rsaBuf.writerIndex(0)
        rsaBuf.writeByte(0)
        for (key in xteaKey) {
            rsaBuf.writeIntLE(key)
        }
        rsaBuf.writeByte(0)
        rsaBuf.writeTibiaString("1\n1")
        rsaBuf.writeTibiaString("Knight Hero")

        val body = Unpooled.buffer()
        body.writeShortLE(2)
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

    private fun encryptedGamePacket(writePayload: (ByteBuf) -> Unit): ByteBuf {
        val payload = Unpooled.buffer()
        writePayload(payload)
        val padding = (8 - (payload.readableBytes() % 8)) % 8
        repeat(padding) { payload.writeByte(0x33) }

        val encrypted = payload.copy()
        xteaCipher.encrypt(encrypted, 0, encrypted.readableBytes())
        val checksum = Adler32Checksum.compute(encrypted, 0, encrypted.readableBytes())

        val packet = Unpooled.buffer()
        packet.writeShortLE(4 + encrypted.readableBytes())
        packet.writeIntLE(checksum.toInt())
        packet.writeBytes(encrypted)
        return packet
    }

    @Test
    fun `a 0xB3 selection resolves against the registry carried by the item boot`() {
        val ch = openChannel()
        val player = login(ch)
        player.features.weaponProficiencies[21] = WeaponProficiencyData(experience = 5_000)

        ch.writeInbound(
            encryptedGamePacket { frame ->
                frame.writeByte(WeaponProficiencyPacketHandler.OPCODE_WEAPON_PROFICIENCY)
                frame.writeByte(WeaponProficiencyPacketHandler.ACTION_SET_SELECTED_PERKS)
                frame.writeShortLE(21)
                frame.writeByte(1)
                frame.writeByte(0)
                frame.writeByte(0)
            },
        )
        awaitSelectedPerks(player, 21, expectedSize = 1)

        assertThat(player.features.weaponProficiencies.getValue(21).perks.toList()).containsExactly(perkAttack)
    }

    @Test
    fun `without an injected registry the selection stays empty like the C++ missing-registry branch`() {
        items.weaponProficiencyRegistry = emptyMap()
        val ch = openChannel()
        val player = login(ch)
        // The initial selection is non-empty, so the awaited clear proves the
        // router applied the action before the assertion.
        player.features.weaponProficiencies[21] =
            WeaponProficiencyData(experience = 5_000, perks = mutableListOf(perkAttack))

        ch.writeInbound(
            encryptedGamePacket { frame ->
                frame.writeByte(WeaponProficiencyPacketHandler.OPCODE_WEAPON_PROFICIENCY)
                frame.writeByte(WeaponProficiencyPacketHandler.ACTION_SET_SELECTED_PERKS)
                frame.writeShortLE(21)
                frame.writeByte(1)
                frame.writeByte(0)
                frame.writeByte(0)
            },
        )
        awaitSelectedPerks(player, 21, expectedSize = 0)

        assertThat(player.features.weaponProficiencies.getValue(21).perks.toList()).isEmpty()
    }
}
