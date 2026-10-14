package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Stat
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.players.components.PlayerSex_t
import org.opentibia.canary.map.Position

/**
 * Transcribes the remaining Player stat accessors:
 *
 * - `Player::getDefaultStats` (player.cpp:1279-1290)
 * - `Player::getLookCorpse` (player.cpp:1383-1388) with
 *   `ITEM_MALE_CORPSE = 4240` / `ITEM_FEMALE_CORPSE = 4247`
 *   (src/utils/utils_definitions.hpp:626-627)
 */
class PlayerStatsAccessorsTest {

    private fun newPlayer() = Player(
        id = 51,
        name = "Stat Hero",
        maxHealth = 150,
        health = 150,
        maxMana = 80,
        position = Position(100, 100, 7),
    )

    @Test
    fun `default stats expose the health mana and base magic maxima`() {
        // player.cpp:1279-1290: STAT_MAXHITPOINTS -> healthMax,
        // STAT_MAXMANAPOINTS -> manaMax, STAT_MAGICPOINTS -> getBaseMagicLevel,
        // default 0.
        val player = newPlayer()
        player.magLevel = 4

        assertThat(player.getDefaultStats(Stat.STAT_MAXHITPOINTS)).isEqualTo(150)
        assertThat(player.getDefaultStats(Stat.STAT_MAXMANAPOINTS)).isEqualTo(80)
        assertThat(player.getDefaultStats(Stat.STAT_MAGICPOINTS)).isEqualTo(4)
        assertThat(player.getDefaultStats(Stat.STAT_SOULPOINTS)).isZero()
        assertThat(player.getDefaultStats(Stat.STAT_CAPACITY)).isZero()
    }

    @Test
    fun `look corpse follows the player sex`() {
        // player.cpp:1383-1388 over utils_definitions.hpp:626-627.
        val player = newPlayer()

        player.setSex(PlayerSex_t.MALE)
        assertThat(player.getLookCorpse()).isEqualTo(4240)

        player.setSex(PlayerSex_t.FEMALE)
        assertThat(player.getLookCorpse()).isEqualTo(4247)
    }
}
