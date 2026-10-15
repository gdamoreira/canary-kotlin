package org.opentibia.canary.game

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item
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

    /**
     * Canary fires the scripting `onDeath` events from `Creature::dropCorpse`
     * (src/creatures/creature.cpp:819-824) while the creature is still dead and
     * the corpse exists, before `Player::death` revives the player at the
     * temple. The engine routes that call through the creature's bound
     * `onDeathEventsHost` seam — the same seam `CanaryServer`/`GameHandler` bind
     * to the world `ScriptingDispatch` — with the full
     * `CreatureEvent::executeOnDeath` argument set
     * (src/lua/creature/creatureevent.cpp:292-326): corpse, last-hit killer,
     * most-damage killer, last-hit-unjustified and most-damage-unjustified.
     */
    fun processDeath(
        player: Player,
        templePosition: Position,
        blessingsCount: Int,
        killer: Creature? = null,
        mostDamageKiller: Creature? = null,
        lastHitUnjustified: Boolean = false,
        mostDamageUnjustified: Boolean = false,
    ): DeathResult {
        val expLost = calculateExperienceLoss(player, blessingsCount)
        player.experience = kotlin.math.max(0L, player.experience - expLost)

        player.onDeathEventsHost(
            player,
            Item(CORPSE_ITEM_ID),
            killer,
            mostDamageKiller,
            lastHitUnjustified,
            mostDamageUnjustified,
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
