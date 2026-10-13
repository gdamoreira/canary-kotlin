package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.players.pvp.PlayerPvp
import org.opentibia.canary.creatures.players.pvp.PvpConfig
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the `Player` PvP/in-fight runtime integration.
 *
 * Source of truth: /Users/damoreira/.projects/canary
 *  - src/creatures/players/player.cpp:3900-3902 (`isPzLocked`)
 *  - src/creatures/players/player.cpp:4442-4483 (`addPzLockTicks`)
 *  - src/creatures/players/player.cpp:4487-4505 (`addInFightTicks`)
 *  - src/creatures/players/player.cpp:6522-6526 (`onAttacked`)
 *  - player.hpp:1869-1871, 1960 (`pzLocked`, `pzLockOnlyUntil`,
 *    `pzLockEventId`, `m_pvpPlayer`)
 */
class PlayerPvpRuntimeTest {

    private var clock = 5_000_000L

    private fun newPlayer(id: Long = 31, name: String = "PvP Runtime") = Player(
        id = id,
        name = name,
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    ).apply { now = { clock } }

    @Test
    fun `addInFightTicks attaches the in-fight condition with the pz locked duration`() {
        val player = newPlayer()
        player.pzLockedDuration = 30_000

        player.addInFightTicks()

        val condition = player.getCondition(ConditionType.CONDITION_INFIGHT)
        assertThat(condition).isNotNull()
        assertThat(condition!!.id).isEqualTo(ConditionId.CONDITIONID_DEFAULT)
        assertThat(condition.ticks).isEqualTo(30_000)
        assertThat(player.hasCondition(ConditionType.CONDITION_INFIGHT)).isTrue()
    }

    @Test
    fun `addInFightTicks is skipped under NotGainInFight`() {
        val player = newPlayer()
        player.setFlag(PlayerFlags_t.NotGainInFight)

        player.addInFightTicks()

        assertThat(player.hasCondition(ConditionType.CONDITION_INFIGHT)).isFalse()
    }

    @Test
    fun `addInFightTicks with pzlock opens the lock immediately`() {
        val player = newPlayer()
        var icons = 0
        player.sendIcons = { icons++ }

        player.addInFightTicks(pzlock = true)

        assertThat(player.isPzLocked()).isTrue()
        assertThat(player.pzLockOnlyUntil).isZero()
        // Once for the pzlock branch and once through Player::onAddCondition.
        assertThat(icons).isEqualTo(2)
        assertThat(player.hasCondition(ConditionType.CONDITION_INFIGHT)).isTrue()
    }

    @Test
    fun `addPzLockTicks delegates to the pvp component and shares the player state`() {
        val player = newPlayer()
        var scheduledDuration = 0L
        player.pvpPlayer = PlayerPvp(
            player = player,
            config = PvpConfig(pzLocked = 45_000),
            otsysTime = { clock },
            scheduleEvent = { duration, _ ->
                scheduledDuration = duration
                7L
            },
        )

        player.addPzLockTicks()

        assertThat(player.isPzLocked()).isTrue()
        assertThat(player.pzLockOnlyUntil).isEqualTo(clock + 45_000)
        assertThat(player.pzLockEventId).isEqualTo(7L)
        assertThat(scheduledDuration).isEqualTo(45_000L)
    }

    @Test
    fun `onAttacked opens in-fight ticks`() {
        val player = newPlayer()

        player.onAttacked()

        assertThat(player.hasCondition(ConditionType.CONDITION_INFIGHT)).isTrue()
    }

    @Test
    fun `onAttacked is skipped under NotGainInFight`() {
        val player = newPlayer()
        player.setFlag(PlayerFlags_t.NotGainInFight)

        player.onAttacked()

        assertThat(player.hasCondition(ConditionType.CONDITION_INFIGHT)).isFalse()
    }

    @Test
    fun `ending the in-fight condition clears the pvp attacked ledger`() {
        val player = newPlayer()
        val opponent = newPlayer(id = 32, name = "Opponent")
        val opponentPvp = PlayerPvp(opponent)
        player.pvpPlayer.addAttacked(opponentPvp)
        assertThat(player.pvpPlayer.getAttackedPlayerGuids()).containsExactly(32L)

        player.addInFightTicks()
        player.removeCondition(ConditionType.CONDITION_INFIGHT)

        assertThat(player.pvpPlayer.getAttackedPlayerGuids()).isEmpty()
    }

    @Test
    fun `the pvp component default reads the player pz lock state`() {
        val player = newPlayer()

        player.pzLocked = true

        assertThat(player.pvpPlayer.isPzLocked()).isTrue()
        assertThat(player.pvpPlayer.getPzLockOnlyUntil()).isZero()
    }
}
