package org.opentibia.canary.game

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.ScriptingDispatch
import org.opentibia.canary.map.Position

data class DeathResult(
    val expLost: Long,
    val corpseItemId: Int,
    val templePosition: Position
)

object DeathPenaltyEngine {

    /**
     * Base loss is 10% without blessings, reduced by 1.6% per blessing down to 2% with all 5 blessings.
     */
    fun calculateExperienceLoss(player: Player, blessingsCount: Int): Long {
        val clampedBlessings = kotlin.math.min(5, kotlin.math.max(0, blessingsCount))
        val lossPercentage = when (clampedBlessings) {
            0 -> 0.10
            1 -> 0.084
            2 -> 0.068
            3 -> 0.052
            4 -> 0.036
            else -> 0.02
        }
        return (player.experience * lossPercentage).toLong()
    }

    fun processDeath(
        player: Player,
        templePosition: Position,
        blessingsCount: Int,
        scripting: ScriptingDispatch? = null,
    ): DeathResult {
        val expLost = calculateExperienceLoss(player, blessingsCount)
        player.experience = kotlin.math.max(0L, player.experience - expLost)

        // Canary fires the scripting `onDeath` events from `Creature::dropCorpse`
        // (src/creatures/creature.cpp:820-824) while the creature is still dead and the
        // corpse exists, before `Player::death` revives the player at the temple.
        scripting?.creatureOnDeath(
            creature = player,
            corpse = Item(CORPSE_ITEM_ID),
            killer = null,
            mostDamageKiller = null,
            lastHitUnjustified = false,
            mostDamageUnjustified = false,
        )

        // Revive vitals and teleport to temple
        player.health = player.maxHealth
        player.mana = player.maxMana
        player.position = templePosition

        return DeathResult(
            expLost = expLost,
            corpseItemId = CORPSE_ITEM_ID,
            templePosition = templePosition
        )
    }

    private const val CORPSE_ITEM_ID = 3058
}
