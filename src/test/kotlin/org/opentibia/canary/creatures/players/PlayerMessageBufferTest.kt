package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.network.protocol.MessageClass

/**
 * Transcribes the Player chat anti-spam bookkeeping
 * (src/creatures/players/player.cpp:3508-3543):
 *
 * - `Player::addMessageBuffer` (player.cpp:3508-3512)
 * - `Player::removeMessageBuffer` (player.cpp:3514-3543)
 *
 * The config `MAX_MESSAGEBUFFER` (configmanager.cpp:315, default 4), the
 * `PlayerFlags_t::CannotBeMuted` gate, the shared `Player::muteCountMap`
 * (player.hpp:281, a static map in Canary), the condition sink and the
 * `sendTextMessage(MESSAGE_FAILURE, ...)` sink are constructor seams per
 * AGENTS.md 1.1. `Player::onThink` feeds `addMessageBuffer` every 1500 ms
 * (player.cpp:8619-8627) and `Game::playerSay` calls `removeMessageBuffer`
 * per accepted message (game.cpp:7437).
 */
class PlayerMessageBufferTest {

    private val guid = 77L

    private class Harness(
        maxMessageBuffer: Int = 4,
        cannotBeMuted: Boolean = false,
    ) {
        val max = maxMessageBuffer
        val muted = cannotBeMuted
        val conditions = mutableListOf<Condition>()
        val messages = mutableListOf<Pair<MessageClass, String>>()
        val buffer = PlayerMessageBuffer(
            maxMessageBuffer = { max },
            cannotBeMuted = { muted },
            guid = { 77L },
            addCondition = { conditions.add(it) },
            sendTextMessage = { type, text -> messages.add(type to text) },
        )
    }

    @Test
    fun `removeMessageBuffer mutes on the overflow message with five seconds`() {
        // player.cpp:3514-3543 with muteCount 1: 5 * 1 * 1 seconds.
        val harness = Harness()

        repeat(4) { harness.buffer.removeMessageBuffer() }
        assertThat(harness.conditions).isEmpty()
        assertThat(harness.messages).isEmpty()

        harness.buffer.removeMessageBuffer()

        assertThat(harness.buffer.messageBufferCount).isEqualTo(5)
        assertThat(harness.conditions).hasSize(1)
        assertThat(harness.conditions.single().type).isEqualTo(ConditionType.CONDITION_MUTED)
        assertThat(harness.conditions.single().ticks).isEqualTo(5_000)
        assertThat(harness.messages).containsExactly(
            MessageClass.MESSAGE_FAILURE to "You are muted for 5 seconds.",
        )
        assertThat(harness.buffer.muteCountMap).containsEntry(guid, 2)
    }

    @Test
    fun `removeMessageBuffer escalates the mute time quadratically per guid`() {
        // player.cpp:3522-3529: muteTime = 5 * muteCount^2.
        val harness = Harness()

        repeat(6) { harness.buffer.removeMessageBuffer() }

        assertThat(harness.conditions).hasSize(2)
        assertThat(harness.conditions[0].ticks).isEqualTo(5_000)
        assertThat(harness.conditions[1].ticks).isEqualTo(20_000)
        assertThat(harness.messages).containsExactly(
            MessageClass.MESSAGE_FAILURE to "You are muted for 5 seconds.",
            MessageClass.MESSAGE_FAILURE to "You are muted for 20 seconds.",
        )
        assertThat(harness.buffer.muteCountMap).containsEntry(guid, 3)
    }

    @Test
    fun `removeMessageBuffer stops counting above the max plus one entry gate`() {
        // player.cpp:3520: `MessageBufferCount <= maxMessageBuffer + 1` gate.
        val harness = Harness()

        repeat(7) { harness.buffer.removeMessageBuffer() }

        assertThat(harness.buffer.messageBufferCount).isEqualTo(6)
        assertThat(harness.conditions).hasSize(2)
    }

    @Test
    fun `addMessageBuffer decrements the count and unlocks the next mute`() {
        // Player::addMessageBuffer (player.cpp:3508-3512).
        val harness = Harness()

        repeat(6) { harness.buffer.removeMessageBuffer() }
        harness.buffer.addMessageBuffer()

        assertThat(harness.buffer.messageBufferCount).isEqualTo(5)

        harness.buffer.removeMessageBuffer()

        assertThat(harness.buffer.messageBufferCount).isEqualTo(6)
        assertThat(harness.conditions).hasSize(3)
        assertThat(harness.conditions[2].ticks).isEqualTo(45_000)
        assertThat(harness.buffer.muteCountMap).containsEntry(guid, 4)
    }

    @Test
    fun `addMessageBuffer never drops below zero`() {
        // player.cpp:3509: `MessageBufferCount > 0`.
        val harness = Harness()

        repeat(3) { harness.buffer.addMessageBuffer() }

        assertThat(harness.buffer.messageBufferCount).isZero()
    }

    @Test
    fun `cannotBeMuted players are never counted or muted`() {
        // player.cpp:3515-3517 and 3509.
        val harness = Harness(cannotBeMuted = true)

        repeat(10) { harness.buffer.removeMessageBuffer() }
        harness.buffer.addMessageBuffer()

        assertThat(harness.buffer.messageBufferCount).isZero()
        assertThat(harness.conditions).isEmpty()
        assertThat(harness.messages).isEmpty()
    }

    @Test
    fun `a zero maxMessageBuffer disables the mute pipeline`() {
        // player.cpp:3520: `maxMessageBuffer != 0`.
        val harness = Harness(maxMessageBuffer = 0)

        repeat(10) { harness.buffer.removeMessageBuffer() }

        assertThat(harness.buffer.messageBufferCount).isZero()
        assertThat(harness.conditions).isEmpty()
        assertThat(harness.messages).isEmpty()
    }
}
