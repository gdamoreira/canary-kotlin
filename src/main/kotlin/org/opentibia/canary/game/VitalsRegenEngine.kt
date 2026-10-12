package org.opentibia.canary.game

import org.opentibia.canary.creatures.Player
import kotlin.math.min

data class VitalsRegenRule(
    val healthGain: Int = 1,
    val manaGain: Int = 1,
    val intervalSeconds: Int = 3
)

object VitalsRegenEngine {

    fun applyRegenerationTick(player: Player, rule: VitalsRegenRule) {
        if (player.isDead) return

        if (player.health < player.maxHealth) {
            player.health = min(player.maxHealth, player.health + rule.healthGain)
        }

        if (player.mana < player.maxMana) {
            player.mana = min(player.maxMana, player.mana + rule.manaGain)
        }
    }
}
