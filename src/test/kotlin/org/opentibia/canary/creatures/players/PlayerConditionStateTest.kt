package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

/**
 * Transcribes `Player::getMuteConditions` (player.cpp:9293-9315): the stored
 * conditions whose remaining ticks are positive and whose type is one of
 * `CONDITION_MUTED`, `CONDITION_CHANNELMUTEDTICKS` or `CONDITION_YELLTICKS`.
 */
class PlayerConditionStateTest {

    private fun newPlayer() = Player(
        id = 21,
        name = "Muted Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    private fun condition(type: ConditionType, ticks: Int, subId: Int = 0): Condition =
        Condition.createCondition(ConditionId.CONDITIONID_DEFAULT, type, ticks, subId = subId)!!

    @Test
    fun `no conditions yields no mute conditions`() {
        // player.cpp:9294-9296 over the empty vector.
        assertThat(newPlayer().getMuteConditions()).isEmpty()
    }

    @Test
    fun `muted channel-muted and yell condition types are returned in order`() {
        // player.cpp:9300-9310: the type filter keeps exactly these three.
        val player = newPlayer()
        val muted = condition(ConditionType.CONDITION_MUTED, 60_000)
        val channel = condition(ConditionType.CONDITION_CHANNELMUTEDTICKS, 30_000)
        val yell = condition(ConditionType.CONDITION_YELLTICKS, 10_000)

        player.addCondition(muted)
        player.addCondition(channel)
        player.addCondition(yell)

        assertThat(player.getMuteConditions()).containsExactly(muted, channel, yell)
    }

    @Test
    fun `other condition types are filtered out`() {
        // player.cpp:9301-9303.
        val player = newPlayer()
        val inFight = condition(ConditionType.CONDITION_INFIGHT, 60_000)
        val muted = condition(ConditionType.CONDITION_MUTED, 60_000)

        player.addCondition(inFight)
        player.addCondition(muted)

        assertThat(player.getMuteConditions()).containsExactly(muted)
    }

    @Test
    fun `conditions without remaining ticks are filtered out`() {
        // player.cpp:9297-9299: `condition->getTicks() <= 0` is skipped, which
        // covers the immortal `ticks == -1` condition too.
        val player = newPlayer()
        // Distinct subIds so both instances are stored instead of the second
        // refreshing the immortal one.
        val immortal = condition(ConditionType.CONDITION_MUTED, -1, subId = 1)
        val ticking = condition(ConditionType.CONDITION_MUTED, 60_000, subId = 2)

        player.addCondition(immortal)
        player.addCondition(ticking)

        assertThat(player.getMuteConditions()).containsExactly(ticking)
    }
}
