package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.ChaseMode
import org.opentibia.canary.creatures.FightMode
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

class CombatPacketHandlerTest {

    @Test
    fun `parses fight mode packet 0xA0 and updates player stances`() {
        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))

        val payload = Unpooled.buffer()
        payload.writeByte(1) // Full Attack (FIGHTMODE_ATTACK)
        payload.writeByte(1) // Chase Mode (CHASEMODE_FOLLOW)
        payload.writeByte(0) // Secure Mode OFF

        val handled = CombatPacketHandler.handleFightModes(player, payload)
        assertThat(handled).isTrue()
        assertThat(player.fightMode).isEqualTo(FightMode.Attack)
        assertThat(player.chaseMode).isEqualTo(ChaseMode.Follow)
        assertThat(player.secureMode).isFalse()
    }

    @Test
    fun `parses attack packet 0xA1 and locks combat target`() {
        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        val targetMonster = Monster(1001, "Troll", 50, 50, position = Position(101, 100, 7))

        val payload = Unpooled.buffer()
        payload.writeIntLE(1001) // Target Creature ID

        val handled = CombatPacketHandler.handleAttack(player, payload, listOf(targetMonster))
        assertThat(handled).isTrue()
        assertThat(player.targetCreatureId).isEqualTo(1001L)
    }
}
