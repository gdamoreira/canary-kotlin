package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.systems.taskhunting.PreyTaskAction

/**
 * Decode tests for `0xBA` `ProtocolGame::parseTaskHuntingAction`
 * (src/server/network/protocol/protocolgame.cpp:3204-3219).
 */
class TaskHuntingPacketHandlerTest {

    @Test
    fun `decodes slot action upgrade byte and race id`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1) // slot
        payload.writeByte(PreyTaskAction.MonsterSelection.value) // action = 3
        payload.writeByte(1) // upgrade
        payload.writeShortLE(0x00FA) // race id

        val command = TaskHuntingPacketHandler.parse(payload)

        assertThat(command).isEqualTo(
            TaskHuntingActionCommand(
                slot = 1,
                action = PreyTaskAction.MonsterSelection,
                upgrade = true,
                raceId = 0x00FA,
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes an upgrade byte of zero as false`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)
        payload.writeByte(PreyTaskAction.ListReroll.value)
        payload.writeByte(0)
        payload.writeShortLE(0)

        val command = TaskHuntingPacketHandler.parse(payload)

        assertThat(command).isEqualTo(
            TaskHuntingActionCommand(
                slot = 2,
                action = PreyTaskAction.ListReroll,
                upgrade = false,
                raceId = 0,
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes an unknown action byte as a null action`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)
        payload.writeByte(0x7F)
        payload.writeByte(0)
        payload.writeShortLE(1)

        val command = TaskHuntingPacketHandler.parse(payload)

        assertThat(command).isEqualTo(
            TaskHuntingActionCommand(slot = 0, action = null, upgrade = false, raceId = 1),
        )
    }

    @Test
    fun `returns null when any task hunting field is truncated`() {
        val missingRaceId = Unpooled.buffer()
        missingRaceId.writeByte(0)
        missingRaceId.writeByte(0)
        missingRaceId.writeByte(0)
        missingRaceId.writeByte(0xFA)

        assertThat(TaskHuntingPacketHandler.parse(missingRaceId)).isNull()
        assertThat(TaskHuntingPacketHandler.parse(Unpooled.buffer())).isNull()
    }
}
