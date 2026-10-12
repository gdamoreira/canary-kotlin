package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.ChaseMode
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.FightMode
import org.opentibia.canary.creatures.Player

object CombatPacketHandler {

    fun handleFightModes(player: Player, payload: ByteBuf): Boolean {
        if (payload.readableBytes() < 3) return false

        val fightModeByte = payload.readUnsignedByte().toInt()
        val chaseModeByte = payload.readUnsignedByte().toInt()
        val secureModeByte = payload.readUnsignedByte().toInt()

        player.fightMode = when (fightModeByte) {
            1 -> FightMode.Attack
            2 -> FightMode.Balanced
            3 -> FightMode.Defense
            else -> FightMode.Attack
        }

        player.chaseMode = when (chaseModeByte) {
            1 -> ChaseMode.Follow
            else -> ChaseMode.StandStill
        }

        player.secureMode = (secureModeByte != 0)
        return true
    }

    fun handleAttack(player: Player, payload: ByteBuf, potentialTargets: List<Creature>): Boolean {
        if (payload.readableBytes() < 4) return false

        val targetId = payload.readUnsignedIntLE()
        if (targetId == 0L) {
            player.targetCreatureId = null
            return true
        }

        val target = potentialTargets.find { it.id == targetId }
        return if (target != null) {
            player.targetCreatureId = targetId
            true
        } else {
            player.targetCreatureId = null
            false
        }
    }
}
