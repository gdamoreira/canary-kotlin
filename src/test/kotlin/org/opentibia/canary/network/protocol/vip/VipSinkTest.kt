package org.opentibia.canary.network.protocol.vip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.players.vip.VipStatus
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.InMemoryProtocolSink

/**
 * Frame-level tests for the VIP senders on the sink surface and for the
 * `PlayerVip` client seam that reaches it.
 *
 * Source of truth: ../.projects/canary
 *  - src/server/network/protocol/protocolgame.cpp:9355-9425
 *    (`sendUpdatedVIPStatus` 0xD3, `sendVIP` 0xD2, `sendVIPGroups` 0xD4)
 *  - src/creatures/players/components/player_vip.cpp:41-61, 76-96, 165-226
 *    (`m_player.client` sends on status/add/group changes)
 *
 * The sink resolves the frame from the player's own `PlayerVIP` state exactly
 * like `player->vip()` does in C++: `getGroupsIdGuidBelongs` for the 0xD2
 * group block, `getGroups`/`getMaxGroupEntries` for 0xD4. The wiring cases go
 * one layer up: with `Player.protocolSink` attached, `PlayerVip` mutations
 * must land as the Canary bytes on the in-memory transport.
 */
class VipSinkTest {

    private fun observer(premium: Boolean = false): Player {
        val player = Player(
            id = 1,
            name = "Observer",
            maxHealth = 150,
            health = 150,
            maxMana = 50,
            mana = 50,
            capacity = 400.0,
            position = Position(100, 200, 7),
        )
        player.premium = premium
        return player
    }

    private fun bind(player: Player): InMemoryProtocolSink {
        val sink = InMemoryProtocolSink(player)
        player.protocolSink = sink
        return sink
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun tibiaString(value: String): ByteArray {
        val payload = value.toByteArray()
        return bytes(payload.size, 0x00) + payload
    }

    @Test
    fun `sendVip writes the 0xD2 list frame with the belonging group ids`() {
        val player = observer()
        val vip = player.features.vip
        vip.addGroupInternal(1, "Friends", true)
        vip.addGroupInternal(2, "Party", false)
        vip.addGuidToGroupInternal(2, 1000L)
        val sink = bind(player)

        sink.sendVip(1000L, "Alice", "friend", icon = 3, notify = true, status = VipStatus.Online)

        assertThat(sink.frames).containsExactly(
            bytes(0xD2, 0xE8, 0x03, 0x00, 0x00) +
                tibiaString("Alice") +
                tibiaString("friend") +
                bytes(
                    0x03, 0x00, 0x00, 0x00,
                    0x01,
                    0x01,
                    0x01, 0x02,
                ),
        )
    }

    @Test
    fun `sendVip caps the icon at ten and writes no groups for an ungrouped guid`() {
        val sink = bind(observer())

        sink.sendVip(1001L, "Eve", "", icon = 12, notify = false, status = VipStatus.Offline)

        assertThat(sink.frames).containsExactly(
            bytes(0xD2, 0xE9, 0x03, 0x00, 0x00) +
                tibiaString("Eve") +
                bytes(
                    0x00, 0x00,
                    0x0A, 0x00, 0x00, 0x00,
                    0x00,
                    0x00,
                    0x00,
                ),
        )
    }

    @Test
    fun `sendVipGroups writes the 0xD4 frame with the remaining slots`() {
        val player = observer(premium = true)
        val vip = player.features.vip
        vip.addGroupInternal(1, "Friends", true)
        vip.addGroupInternal(2, "Enemies", false)
        val sink = bind(player)

        sink.sendVipGroups()

        assertThat(sink.frames).containsExactly(
            bytes(0xD4, 0x02, 0x01) +
                tibiaString("Friends") +
                bytes(0x01, 0x02) +
                tibiaString("Enemies") +
                bytes(0x00, 0x06),
        )
    }

    @Test
    fun `sendUpdatedVipStatus writes the 0xD3 frame`() {
        val sink = bind(observer())

        sink.sendUpdatedVipStatus(1000L, VipStatus.Training)

        assertThat(sink.frames).containsExactly(
            bytes(0xD3, 0xE8, 0x03, 0x00, 0x00, 0x03),
        )
    }

    @Test
    fun `PlayerVip add sends the 0xD2 frame through the attached sink`() {
        val player = observer()
        val sink = bind(player)

        assertThat(player.features.vip.add(1000L, "Alice", VipStatus.Online)).isTrue()

        assertThat(sink.frames).containsExactly(
            bytes(0xD2, 0xE8, 0x03, 0x00, 0x00) +
                tibiaString("Alice") +
                bytes(
                    0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00,
                    0x01,
                    0x00,
                ),
        )
    }

    @Test
    fun `PlayerVip addGroup sends the 0xD4 frame through the attached sink`() {
        val player = observer(premium = true)
        val sink = bind(player)

        player.features.vip.addGroup("Friends")

        assertThat(sink.frames).containsExactly(
            bytes(0xD4, 0x01, 0x01) + tibiaString("Friends") + bytes(0x01, 0x07),
        )
    }

    @Test
    fun `PlayerVip notifyStatusChange sends the 0xD3 frame through the attached sink`() {
        val player = observer()
        val sink = bind(player)
        player.features.vip.add(1000L, "Alice", VipStatus.Offline)
        sink.frames.clear()

        player.features.vip.notifyStatusChange(1000L, "Alice", VipStatus.Online, message = false)

        assertThat(sink.frames).containsExactly(
            bytes(0xD3, 0xE8, 0x03, 0x00, 0x00, 0x01),
        )
    }

    @Test
    fun `PlayerVip removeGroup sends the 0xD4 frame through the attached sink`() {
        val player = observer(premium = true)
        val sink = bind(player)
        player.features.vip.addGroup("Friends")
        sink.frames.clear()

        player.features.vip.removeGroup(1)

        assertThat(sink.frames).containsExactly(
            bytes(0xD4, 0x00, 0x08),
        )
    }

    @Test
    fun `PlayerVip remove leaves the wire untouched like Canary`() {
        val player = observer()
        val sink = bind(player)
        player.features.vip.add(1000L, "Alice", VipStatus.Online)
        sink.frames.clear()

        assertThat(player.features.vip.remove(1000L)).isTrue()

        assertThat(sink.frames).isEmpty()
    }
}
