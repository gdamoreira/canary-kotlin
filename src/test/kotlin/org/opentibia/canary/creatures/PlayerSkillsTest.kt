package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

/**
 * Transcribes the skill primitives of Canary's Player:
 * `Player::getSkillLevel` (src/creatures/players/player.cpp:7468),
 * `Player::addSkillAdvance` (player.cpp:1190-1249) and the Lua accessors
 * `player:getSkillTries` / `player:addSkillTries`
 * (src/lua/functions/creatures/player/player_functions.cpp:1662-1686).
 * The `skills_t` enum mirrors src/creatures/creatures_definitions.hpp:466-487.
 */
class PlayerSkillsTest {

    private fun newPlayer() = Player(
        id = 7,
        name = "Skill Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    @Test
    fun `skills enum matches Canary values`() {
        assertThat(skills_t.SKILL_NONE.value).isEqualTo(-1)
        assertThat(skills_t.SKILL_FIST.value).isEqualTo(0)
        assertThat(skills_t.SKILL_CLUB.value).isEqualTo(1)
        assertThat(skills_t.SKILL_SWORD.value).isEqualTo(2)
        assertThat(skills_t.SKILL_AXE.value).isEqualTo(3)
        assertThat(skills_t.SKILL_DISTANCE.value).isEqualTo(4)
        assertThat(skills_t.SKILL_SHIELD.value).isEqualTo(5)
        assertThat(skills_t.SKILL_FISHING.value).isEqualTo(6)
        assertThat(skills_t.SKILL_CRITICAL_HIT_CHANCE.value).isEqualTo(7)
        assertThat(skills_t.SKILL_CRITICAL_HIT_DAMAGE.value).isEqualTo(8)
        assertThat(skills_t.SKILL_LIFE_LEECH_CHANCE.value).isEqualTo(9)
        assertThat(skills_t.SKILL_LIFE_LEECH_AMOUNT.value).isEqualTo(10)
        assertThat(skills_t.SKILL_MANA_LEECH_CHANCE.value).isEqualTo(11)
        assertThat(skills_t.SKILL_MANA_LEECH_AMOUNT.value).isEqualTo(12)
        assertThat(skills_t.SKILL_MAGLEVEL.value).isEqualTo(13)
        assertThat(skills_t.SKILL_LEVEL.value).isEqualTo(14)
        assertThat(skills_t.FIRST).isEqualTo(0)
        assertThat(skills_t.LAST).isEqualTo(12)
    }

    @Test
    fun `new player starts at the Skill struct default level`() {
        val player = newPlayer()

        assertThat(player.getSkillLevel(skills_t.SKILL_FIST)).isEqualTo(10)
        assertThat(player.getSkillLevel(skills_t.SKILL_SWORD)).isEqualTo(10)
        assertThat(player.getSkillTries(skills_t.SKILL_SWORD)).isEqualTo(0L)
    }

    @Test
    fun `addSkillTries accumulates tries per skill`() {
        val player = newPlayer()

        player.addSkillTries(skills_t.SKILL_SWORD, 500)
        player.addSkillTries(skills_t.SKILL_SWORD, 250)

        assertThat(player.getSkillTries(skills_t.SKILL_SWORD)).isEqualTo(750L)
        assertThat(player.getSkillTries(skills_t.SKILL_CLUB)).isEqualTo(0L)
    }
}
