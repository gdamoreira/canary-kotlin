package org.opentibia.canary.network.protocol

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Skill
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

/**
 * Host binding tests for the imbuement adapter stats the wave-50 audit found
 * inert (`Player::setVarSkill`/`Player::bonusCapacity`/`Player::sendStats`,
 * src/creatures/players/player.cpp:352-354, 1780, 2347-2352): the
 * [ImbuementPlayerAdapter] must mutate the live [Player] and the mutated state
 * must feed `Player::getSkillLevel` (player.cpp:7468) and
 * `Player::getFreeCapacity` (player.cpp:5468-5476).
 */
class ImbuementPlayerAdapterStatsTest {

    private fun newPlayer() = Player(
        id = 1,
        name = "Hero",
        maxHealth = 100,
        health = 100,
        position = Position(100, 100, 7),
    )

    private fun newAdapter(player: Player): ImbuementPlayerAdapter =
        ImbuementPlayerAdapter(Game(GameMap(), GameDispatcher()), player, null)

    @Test
    fun `adapter accumulates the var skill on the host player`() {
        val player = newPlayer()
        val adapter = newAdapter(player)

        adapter.setVarSkill(Skill.SKILL_SWORD, 3)
        adapter.setVarSkill(Skill.SKILL_SWORD, -1)

        assertThat(player.varSkills[Skill.SKILL_SWORD.value]).isEqualTo(2)
        assertThat(player.getSkillLevel(skills_t.SKILL_SWORD)).isEqualTo(12)
    }

    @Test
    fun `adapter stores the bonus capacity on the host player`() {
        val player = newPlayer()
        val adapter = newAdapter(player)

        adapter.setBonusCapacity(48)

        assertThat(player.bonusCapacity).isEqualTo(48)
        assertThat(player.getFreeCapacity()).isEqualTo(448.0)

        adapter.setBonusCapacity(0)

        assertThat(player.getFreeCapacity()).isEqualTo(400.0)
    }

    @Test
    fun `adapter forwards the stat and skill sends to the host player`() {
        val player = newPlayer()
        var statsSent = 0
        player.sendStats = { statsSent++ }
        var skillsSent = 0
        player.sendSkills = { skillsSent++ }
        val adapter = newAdapter(player)

        adapter.sendStats()
        adapter.sendSkills()

        assertThat(statsSent).isEqualTo(1)
        assertThat(skillsSent).isEqualTo(1)
    }
}
