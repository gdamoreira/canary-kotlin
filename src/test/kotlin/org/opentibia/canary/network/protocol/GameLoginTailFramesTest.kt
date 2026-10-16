package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.account.CharacterEntry
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.systems.proficiency.WeaponProficiencyData
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Login-sequence transcript tests for the `ProtocolGame::sendAddCreature` tail
 * (src/server/network/protocol/protocolgame.cpp:8686-8694): the bug-report
 * toggle (`sendAllowBugReport`, 0x1A), the world time (`sendTibiaTime`, 0xEF
 * with `Game::getLightHour`), the pending-state marker
 * (`sendPendingStateEntered`, 0x0A) and the enter-world marker
 * (`sendEnterWorld`, 0x0F), queued between the placement self appear frame and
 * the map description.
 *
 * The current-profile game login also parses the `clientVersionString` build
 * string (`ProtocolGame::onRecvFirstMessage`, protocolgame.cpp:1228-1231) that
 * gates the weapon-proficiency detail list (`shouldSendWeaponProficiencyDetailList`,
 * protocolgame.cpp:109-132) and is exercised here through the `0xCD` inspection
 * and the `0xB3` tracked-weapons paths.
 */
class GameLoginTailFramesTest {

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
    private lateinit var gameMap: GameMap
    private lateinit var dispatcher: GameDispatcher
    private var channel: EmbeddedChannel? = null

    @BeforeEach
    fun setUp() {
        gameMap = GameMap()
        gameMap.getOrCreateTile(Position(1000, 1000, 7)).ground = Item(id = 106)
        dispatcher = GameDispatcher().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        channel?.finishAndReleaseAll()
        dispatcher.shutdown()
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun loginPacket(
        version: Int = CLIENT_VERSION,
        clientVersionString: String = "15.25.794c2e.1400",
    ): ByteBuf {
        val xteaKey = intArrayOf(0x01020304, 0x05060708, 0x090A0B0C, 0x0D0E0F10)

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
        body.writeShortLE(version)
        if (version == CLIENT_VERSION) {
            // `GameLoginLayout` current profile: client version u32, build
            // string, asset hash string, preview state byte (protocolgame.cpp:1222-1246).
            body.writeIntLE(CLIENT_VERSION)
            body.writeTibiaString(clientVersionString)
            body.writeTibiaString("asset-hash")
            body.writeByte(0)
        } else if (version == 1100) {
            // Tibia 11.00 layout: client version u32, dat revision u16, preview byte.
            body.writeIntLE(version)
            body.writeShortLE(0)
            body.writeByte(0)
        }
        body.writeBytes(rsaCipher.encrypt(rsaPlain))

        val checksum = Adler32Checksum.compute(body, 0, body.readableBytes())
        val packet = Unpooled.buffer()
        packet.writeIntLE(checksum.toInt())
        packet.writeBytes(body)
        return packet
    }

    /** Wraps [payload] with a deliberately wrong checksum so the handler treats it as raw. */
    private fun writeRawInbound(channel: EmbeddedChannel, payload: ByteBuf) {
        val checksum = Adler32Checksum.compute(payload, 0, payload.readableBytes())
        val frame = Unpooled.buffer()
        frame.writeIntLE(checksum.toInt() xor 0x1)
        frame.writeBytes(payload)
        payload.release()
        channel.writeInbound(frame)
    }

    private fun awaitLane() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    /**
     * Waits for the routed packet's WorldCommit application: the router task
     * runs first on the default lane, then its application task on the
     * WorldCommit lane.
     */
    private fun awaitRouted() {
        awaitLane()
        val ran = CountDownLatch(1)
        dispatcher.dispatch(DispatcherLane.WorldCommit) { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    private fun readFrame(channel: EmbeddedChannel): ByteBuf {
        channel.runPendingTasks()
        return channel.readOutbound<ByteBuf>() ?: error("no outbound frame was written")
    }

    private fun readBytes(channel: EmbeddedChannel): ByteArray {
        val frame = readFrame(channel)
        val result = ByteArray(frame.readableBytes())
        frame.readBytes(result)
        frame.release()
        return result
    }

    @Test
    fun `login queues the self appear frame, the four tail frames and the map description in order`() {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        val ch = EmbeddedChannel(handler).also { channel = it }
        ch.writeInbound(loginPacket())
        awaitLane()

        // 1. `sendAddCreature` self branch (`version >= 980` = 0x17, the speed
        // doubles, both expert bytes, the store pair and the exiva byte).
        assertThat(readBytes(ch)).isEqualTo(
            bytes(
                0x17,
                0x01, 0x00, 0x00, 0x00,
                0x32, 0x00,
                0x03, 0x0F, 0x15, 0x0D, 0x80,
                0x03, 0xA9, 0xFC, 0x03, 0x80,
                0x03, 0x7D, 0xD5, 0xB6, 0x7F,
                0x00,
                0x00,
                0x00, 0x00,
                0x19, 0x00,
                0x00,
            ),
        )

        // 2. `sendAllowBugReport` (protocolgame.cpp:8595-8603).
        assertThat(readBytes(ch)).isEqualTo(bytes(0x1A, 0x00))

        // 3. `sendTibiaTime(g_game().getLightHour())` (protocolgame.cpp:4629-4639):
        // the default light hour 705 = 11h45m.
        assertThat(readBytes(ch)).isEqualTo(bytes(0xEF, 0x0B, 0x2D))

        // 4. `sendPendingStateEntered` (protocolgame.cpp:8561-8569).
        assertThat(readBytes(ch)).isEqualTo(bytes(0x0A))

        // 5. `sendEnterWorld` (protocolgame.cpp:8571-8575).
        assertThat(readBytes(ch)).isEqualTo(bytes(0x0F))

        // 6. `sendMapDescription` (protocolgame.cpp:8694).
        val map = readFrame(ch)
        assertThat(map.readUnsignedByte().toInt()).isEqualTo(MapPacketWriter.PACKET_MAP_DESCRIPTION.toInt())
        map.release()

        // 7. `sendAddCreature`'s login tail (protocolgame.cpp:8701-8703):
        // one inventory-slot frame per slot precedes the stats/skills pair.
        for (slot in org.opentibia.canary.creatures.Slots_t.FIRST..org.opentibia.canary.creatures.Slots_t.LAST) {
            val slotFrame = readFrame(ch)
            assertThat(slotFrame.readUnsignedByte().toInt())
                .describedAs("slot $slot")
                .isIn(GameWindowWriter.PACKET_INVENTORY_ITEM, GameWindowWriter.PACKET_INVENTORY_ITEM_REMOVE)
        }

        // 8/9. The stats (0xA0) and skills (0xA1) continuation.
        assertThat(readFrame(ch).readUnsignedByte().toInt()).isEqualTo(GameWindowWriter.PACKET_STATS)
        assertThat(readFrame(ch).readUnsignedByte().toInt()).isEqualTo(GameWindowWriter.PACKET_SKILLS)
    }

    @Test
    fun `an old-protocol profile skips the bug report, tibia time and pending markers`() {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        val ch = EmbeddedChannel(handler).also { channel = it }
        ch.writeInbound(loginPacket(version = 1100))
        awaitLane()

        // The 11.00 profile is `OldProtocolCompat`: no exiva byte in the self
        // frame (protocolgame.cpp:8676-8682), but the version is >= 1054 so the
        // inline bug-report flag byte precedes the expert-PvP byte
        // (protocolgame.cpp:8653-8658, account type NORMAL -> 0x01).
        assertThat(readBytes(ch)).isEqualTo(
            bytes(
                0x17,
                0x01, 0x00, 0x00, 0x00,
                0x32, 0x00,
                0x03, 0x0F, 0x15, 0x0D, 0x80,
                0x03, 0xA9, 0xFC, 0x03, 0x80,
                0x03, 0x7D, 0xD5, 0xB6, 0x7F,
                0x01,
                0x00,
                0x00,
                0x00, 0x00,
                0x19, 0x00,
            ),
        )

        // `sendAllowBugReport`/`sendTibiaTime`/`sendPendingStateEntered` all
        // return early for `oldProtocol`; only `sendEnterWorld` remains
        // (protocolgame.cpp:8686-8693).
        assertThat(readBytes(ch)).isEqualTo(bytes(0x0F))
        val map = readFrame(ch)
        assertThat(map.readUnsignedByte().toInt()).isEqualTo(MapPacketWriter.PACKET_MAP_DESCRIPTION.toInt())
        map.release()
    }

    @Test
    fun `the 0xCD inspection appends the detail list only for a known build string`() {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        val ch = EmbeddedChannel(handler).also { channel = it }
        ch.writeInbound(loginPacket(clientVersionString = "15.25.794c2e.1400"))
        awaitLane()
        drain(ch)

        val player = requireNotNull(handler.game.getPlayerByID(1L))
        player.features.weaponProficiencies[1234] = WeaponProficiencyData(experience = 77)

        val knownBuild = Unpooled.buffer()
        knownBuild.writeByte(InspectionPacketHandler.OPCODE_INSPECTION_OBJECT)
        knownBuild.writeByte(InspectionPacketHandler.INSPECT_PROFICIENCY)
        knownBuild.writeShortLE(1234)
        knownBuild.writeByte(0)
        writeRawInbound(ch, knownBuild)
        awaitRouted()

        // `shouldSendWeaponProficiencyDetailList` true: the empty (0x00) list
        // boundary trails the selected perks (protocolgame.cpp:12354-12356).
        assertThat(readBytes(ch)).isEqualTo(
            bytes(
                0xC4,
                0xD2, 0x04, // weapon id
                0x4D, 0x00, 0x00, 0x00, // experience
                0x00, // perk count
                0x00, // detail-list boundary
            ),
        )

        // An unrecognized 15.25 build keeps the shorter shape. A fresh map keeps
        // the second session's placement independent from the first world.
        val unknownMap = GameMap()
        unknownMap.getOrCreateTile(Position(1000, 1000, 7)).ground = Item(id = 106)
        val unknownHandler = GameHandler(rsaCipher, MockAccountRepo(), unknownMap, dispatcher)
        val unknownChannel = EmbeddedChannel(unknownHandler)
        unknownChannel.writeInbound(loginPacket(clientVersionString = "15.25.abcdef.1"))
        awaitLane()
        drain(unknownChannel)

        val unknownPlayer = requireNotNull(unknownHandler.game.getPlayerByID(1L))
        unknownPlayer.features.weaponProficiencies[1234] = WeaponProficiencyData(experience = 77)

        val unknownBuild = Unpooled.buffer()
        unknownBuild.writeByte(InspectionPacketHandler.OPCODE_INSPECTION_OBJECT)
        unknownBuild.writeByte(InspectionPacketHandler.INSPECT_PROFICIENCY)
        unknownBuild.writeShortLE(1234)
        unknownBuild.writeByte(0)
        writeRawInbound(unknownChannel, unknownBuild)
        awaitRouted()

        assertThat(readBytes(unknownChannel)).isEqualTo(
            bytes(
                0xC4,
                0xD2, 0x04,
                0x4D, 0x00, 0x00, 0x00,
                0x00,
            ),
        )
        unknownChannel.finishAndReleaseAll()
    }

    @Test
    fun `the 0xB3 action 1 emits one window per tracked weapon in ascending order`() {
        val handler = GameHandler(rsaCipher, MockAccountRepo(), gameMap, dispatcher)
        val ch = EmbeddedChannel(handler).also { channel = it }
        ch.writeInbound(loginPacket())
        awaitLane()
        drain(ch)

        val player = requireNotNull(handler.game.getPlayerByID(1L))
        player.features.weaponProficiencies[2160] = WeaponProficiencyData(experience = 5)
        player.features.weaponProficiencies[1234] = WeaponProficiencyData(experience = 9)

        val trackedWeapons = Unpooled.buffer()
        trackedWeapons.writeByte(WeaponProficiencyPacketHandler.OPCODE_WEAPON_PROFICIENCY)
        trackedWeapons.writeByte(WeaponProficiencyPacketHandler.ACTION_SEND_TRACKED_WEAPONS)
        writeRawInbound(ch, trackedWeapons)
        awaitRouted()

        // `WeaponProficiency::getTrackedWeaponIds` sorts ascending
        // (weapon_proficiency.cpp:320-329); each window is its own frame and no
        // `0x5C` proficiency frame follows the action-1 early return
        // (protocolgame.cpp:3135-3141).
        assertThat(readBytes(ch)).isEqualTo(
            bytes(
                0xC4,
                0xD2, 0x04,
                0x09, 0x00, 0x00, 0x00,
                0x00,
                0x00,
            ),
        )
        assertThat(readBytes(ch)).isEqualTo(
            bytes(
                0xC4,
                0x70, 0x08,
                0x05, 0x00, 0x00, 0x00,
                0x00,
                0x00,
            ),
        )
        assertThat(ch.readOutbound<ByteBuf>()).isNull()
    }

    private fun drain(channel: EmbeddedChannel) {
        channel.runPendingTasks()
        while (channel.readOutbound<ByteBuf>() != null) {
            // drain the login frames
        }
    }
}
