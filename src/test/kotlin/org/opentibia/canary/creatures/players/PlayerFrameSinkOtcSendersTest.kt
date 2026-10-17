package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.InMemoryProtocolSink

/**
 * The OTCR-only frames behind `ProtocolGame::isOTCR` (protocolgame.hpp:642):
 *
 *  - `sendAttachedEffect` (0x34, protocolgame.cpp:11809-11819)
 *  - `sendDetachEffect` (0x35, protocolgame.cpp:11821-11831)
 *  - `sendShader` (0x36, protocolgame.cpp:11833-11843)
 *  - `sendMapShader` (0x37, protocolgame.cpp:11845-11855)
 *  - `sendPlayerTyping` (0x38, protocolgame.cpp:11857-11867)
 *
 * Every one returns early without the flag, so a non-OTClient session never
 * sees them.
 */
class PlayerFrameSinkOtcSendersTest {

    private fun player(id: Long): Player = Player(
        id = id,
        name = "Hero$id",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `an OTCR sink writes the attached effect and detach frames`() {
        val player = player(1)
        val creature = player(2)
        val sink = InMemoryProtocolSink(player, GameMap()).apply { isOTCR = true }

        sink.sendAttachedEffect(creature, 5)
        sink.sendDetachEffect(creature, 6)

        assertThat(sink.frames).containsExactly(
            bytes(0x34, 0x02, 0x00, 0x00, 0x00, 0x05, 0x00),
            bytes(0x35, 0x02, 0x00, 0x00, 0x00, 0x06, 0x00),
        )
    }

    @Test
    fun `an OTCR sink writes the shader, map shader and typing frames`() {
        val player = player(3)
        val creature = player(4)
        val sink = InMemoryProtocolSink(player, GameMap()).apply { isOTCR = true }

        sink.sendShader(creature, "Wing One")
        sink.sendMapShader("Map Shader")
        sink.sendPlayerTyping(creature, 1)

        assertThat(sink.frames).containsExactly(
            bytes(0x36, 0x04, 0x00, 0x00, 0x00, 8, 0x00, 'W'.code, 'i'.code, 'n'.code, 'g'.code, ' '.code, 'O'.code, 'n'.code, 'e'.code),
            bytes(0x37, 10, 0x00, 'M'.code, 'a'.code, 'p'.code, ' '.code, 'S'.code, 'h'.code, 'a'.code, 'd'.code, 'e'.code, 'r'.code),
            bytes(0x38, 0x04, 0x00, 0x00, 0x00, 0x01),
        )
    }

    @Test
    fun `a non-OTCR sink suppresses the OTCR frames`() {
        val player = player(5)
        val creature = player(6)
        val sink = InMemoryProtocolSink(player, GameMap())

        sink.sendAttachedEffect(creature, 5)
        sink.sendDetachEffect(creature, 6)
        sink.sendShader(creature, "Wing One")
        sink.sendMapShader("Map Shader")
        sink.sendPlayerTyping(creature, 1)

        assertThat(sink.frames).isEmpty()
    }

    @Test
    fun `the composed attached-effects client forwards to the session sink`() {
        val player = player(7)
        val sink = InMemoryProtocolSink(player, GameMap()).apply { isOTCR = true }
        player.protocolSink = sink

        // `PlayerAttachedEffects::sendMapShader` (player_attached_effects.cpp:868)
        // forwards through `m_player.client` (8xx), the seam the session sink
        // now fills.
        player.features.attachedEffects.sendMapShader("Map Shader")

        assertThat(sink.frames.single()).isEqualTo(
            bytes(0x37, 10, 0x00, 'M'.code, 'a'.code, 'p'.code, ' '.code, 'S'.code, 'h'.code, 'a'.code, 'd'.code, 'e'.code, 'r'.code),
        )
    }

    @Test
    fun `the composed attached-effects client stays silent for a non-OTCR session`() {
        val player = player(8)
        val sink = InMemoryProtocolSink(player, GameMap())
        player.protocolSink = sink

        player.features.attachedEffects.sendMapShader("Map Shader")

        assertThat(sink.frames).isEmpty()
    }
}
