package org.opentibia.canary.creatures.players

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.appearance.outfit.ProtocolOutfit
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.InMemoryProtocolSink
import org.opentibia.canary.network.protocol.outfit.OutfitWindowComposer
import org.opentibia.canary.network.protocol.outfit.OutfitWindowPayload
import org.opentibia.canary.network.protocol.outfit.OutfitWindowWriter
import org.opentibia.canary.systems.mount.MountProtocol

/**
 * `ProtocolGame::sendOutfitWindow()` (protocolgame.cpp:9027-9258) over the
 * session sink: the composed payload picks the `oldProtocol` layout, and an
 * absent payload keeps the sender silent.
 */
class PlayerFrameSinkOutfitWindowTest {

    private fun player(id: Long): Player = Player(
        id = id,
        name = "Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    private fun sink(player: Player, payload: (Player, Boolean) -> OutfitWindowPayload?): InMemoryProtocolSink =
        InMemoryProtocolSink(player, GameMap(), outfitWindowPayload = payload)

    private fun expectedCurrent(frame: OutfitWindowWriter.OutfitWindowFrame): ByteArray {
        val out = Unpooled.buffer()
        OutfitWindowWriter.writeOutfitWindow(out, frame)
        val bytes = ByteArray(out.readableBytes())
        out.readBytes(bytes)
        out.release()
        return bytes
    }

    @Test
    fun `writes the current-profile frame from the composed payload`() {
        val player = player(1)
        val frame = OutfitWindowWriter.OutfitWindowFrame(
            currentOutfit = Outfit(lookType = 136),
            supportOutfitOrNoMount = true,
            mounted = false,
            randomMounted = false,
            outfits = listOf(OutfitWindowWriter.OutfitWindowOutfitEntry(136, "Citizen", 3, 0x00)),
            mounts = listOf(MountProtocol.OutfitWindowMountEntry(clientId = 368, name = "Widow Queen", flag = 0x01, price = 0L)),
            familiars = listOf(OutfitWindowWriter.OutfitWindowFamiliarEntry(lookType = 10, name = "Wolf")),
        )
        val sink = sink(player) { _, legacy ->
            assertThat(legacy).isFalse()
            OutfitWindowComposer.CurrentPayload(frame)
        }

        sink.sendOutfitWindow()

        assertThat(sink.frames.single()).isEqualTo(expectedCurrent(frame))
    }

    @Test
    fun `writes the legacy layout when the session profile is old`() {
        val player = player(2)
        val legacy = OutfitWindowComposer.LegacyPayload(
            currentOutfit = Outfit(lookType = 136),
            outfits = listOf(ProtocolOutfit(name = "Citizen", lookType = 136, addons = 1)),
            mounts = listOf(OutfitWindowWriter.ProtocolOutfitMount(clientId = 368, name = "Widow Queen")),
        )
        val sink = sink(player) { _, legacyFlag ->
            assertThat(legacyFlag).isTrue()
            legacy
        }
        sink.oldProtocol = true

        sink.sendOutfitWindow()

        val out = Unpooled.buffer()
        OutfitWindowWriter.writeOutfitWindowLegacy(
            out,
            legacy.currentOutfit,
            legacy.outfits,
            legacy.mounts,
            legacy.isOTCR,
            legacy.customOtcr,
        )
        val expected = ByteArray(out.readableBytes())
        out.readBytes(expected)
        out.release()

        assertThat(sink.frames.single()).isEqualTo(expected)
    }

    @Test
    fun `toggleMount without a selected mount opens the outfit window through the sink`() {
        val mounts = org.opentibia.canary.systems.mount.Mounts().also {
            it.loadFromXml(
                """<mounts><mount id="1" clientid="368" name="Widow Queen" speed="10" premium="0" type="quest"/></mounts>""",
            )
        }
        val player = Player(
            id = 4,
            name = "Hero",
            maxHealth = 150,
            health = 150,
            position = Position(100, 100, 7),
            mountDefinitions = mounts,
        )
        val frame = OutfitWindowWriter.OutfitWindowFrame(
            currentOutfit = Outfit(),
            supportOutfitOrNoMount = true,
            mounted = false,
            randomMounted = false,
            outfits = emptyList(),
            mounts = emptyList(),
            familiars = emptyList(),
        )
        val sink = sink(player) { _, _ -> OutfitWindowComposer.CurrentPayload(frame) }
        player.protocolSink = sink

        assertThat(player.features.mounts.toggleMount(true)).isFalse()

        assertThat(sink.frames).hasSize(1)
        assertThat(sink.frames.single()).isEqualTo(expectedCurrent(frame))
    }

    @Test
    fun `stays silent without a composed payload`() {
        val sink = sink(player(3)) { _, _ -> null }

        sink.sendOutfitWindow()

        assertThat(sink.frames).isEmpty()
    }
}
