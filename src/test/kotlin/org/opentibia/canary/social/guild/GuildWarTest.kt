package org.opentibia.canary.social.guild

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for Canary's guild war primitives:
 * `Player::isInWar` / `Player::isInWarList`
 * (src/creatures/players/player.cpp:7403-7418) and
 * `Player::getGuildEmblem` (player.cpp:7823-7847) over `GuildEmblems_t`
 * (src/utils/utils_definitions.hpp:532-539), plus the `guild_wars` row shape
 * (schema.sql:357-372) that `IOGuild::getWarList` (src/io/ioguild.cpp:60-77)
 * queries with `status = 1`.
 *
 * War declaration, scoring, and expiry live in the website and Lua
 * (`data/scripts/creaturescripts/player/death.lua`,
 * `data/scripts/globalevents/update_guild_war_status.lua`) and are deliberately
 * not re-implemented in Kotlin (AGENTS.md 1.4).
 */
class GuildWarTest {

    @Test
    fun `guild war row mirrors the schema columns and the active status default`() {
        val war = GuildWar(
            id = 10,
            guild1 = 1,
            guild2 = 2,
            name1 = "Red Rose",
            name2 = "Black Widows",
            started = 1_700_000_000,
            fragsLimit = 50,
            payment = 1_000_000,
            durationDays = 30,
        )

        assertThat(war.status).isEqualTo(GuildWarStatus.ACTIVE)
        assertThat(GuildWarStatus.ACTIVE).isEqualTo(1)
        assertThat(GuildWarStatus.ENDED).isEqualTo(4)
        assertThat(war.guild1).isEqualTo(1)
        assertThat(war.guild2).isEqualTo(2)
        assertThat(war.name1).isEqualTo("Red Rose")
        assertThat(war.name2).isEqualTo("Black Widows")
        assertThat(war.fragsLimit).isEqualTo(50)
        assertThat(war.payment).isEqualTo(1_000_000)
        assertThat(war.durationDays).isEqualTo(30)
    }

    @Test
    fun `isInWarList matches only the listed guilds`() {
        assertThat(isInWarList(listOf(2L, 3L), 2L)).isTrue()
        assertThat(isInWarList(listOf(2L, 3L), 4L)).isFalse()
        assertThat(isInWarList(emptyList(), 1L)).isFalse()
    }

    @Test
    fun `isInWar requires both guilds to list each other`() {
        val redRose = Guild(id = 1, name = "Red Rose")
        val blackWidows = Guild(id = 2, name = "Black Widows")

        assertThat(isInWar(redRose, listOf(2L), blackWidows, listOf(1L))).isTrue()
        assertThat(isInWar(redRose, emptyList(), blackWidows, listOf(1L))).isFalse()
        assertThat(isInWar(redRose, listOf(2L), blackWidows, emptyList())).isFalse()
        assertThat(isInWar(null, listOf(2L), blackWidows, listOf(1L))).isFalse()
        assertThat(isInWar(redRose, listOf(2L), null, listOf(1L))).isFalse()
    }

    @Test
    fun `getGuildEmblem resolves none member other ally enemy and neutral`() {
        val redRose = Guild(id = 1, name = "Red Rose")
        val blackWidows = Guild(id = 2, name = "Black Widows")
        val third = Guild(id = 3, name = "Third")

        assertThat(getGuildEmblem(redRose, listOf(2L), null, emptyList()))
            .isEqualTo(GuildEmblems_t.GUILDEMBLEM_NONE)
        assertThat(getGuildEmblem(redRose, emptyList(), redRose, emptyList()))
            .isEqualTo(GuildEmblems_t.GUILDEMBLEM_MEMBER)
        assertThat(getGuildEmblem(redRose, listOf(2L), blackWidows, emptyList()))
            .isEqualTo(GuildEmblems_t.GUILDEMBLEM_OTHER)
        assertThat(getGuildEmblem(redRose, emptyList(), redRose, listOf(2L)))
            .isEqualTo(GuildEmblems_t.GUILDEMBLEM_ALLY)
        assertThat(getGuildEmblem(redRose, listOf(2L), blackWidows, listOf(1L)))
            .isEqualTo(GuildEmblems_t.GUILDEMBLEM_ENEMY)
        assertThat(getGuildEmblem(redRose, emptyList(), blackWidows, listOf(1L)))
            .isEqualTo(GuildEmblems_t.GUILDEMBLEM_NEUTRAL)
        assertThat(getGuildEmblem(redRose, listOf(third.id), blackWidows, listOf(1L)))
            .isEqualTo(GuildEmblems_t.GUILDEMBLEM_NEUTRAL)
    }

    @Test
    fun `guild emblem values match the client wire bytes`() {
        assertThat(GuildEmblems_t.entries.map { it.value }).containsExactly(0, 1, 2, 3, 4, 5)
        assertThat(GuildEmblems_t.GUILDEMBLEM_ALLY.value).isEqualTo(1)
        assertThat(GuildEmblems_t.GUILDEMBLEM_ENEMY.value).isEqualTo(2)
        assertThat(GuildEmblems_t.GUILDEMBLEM_NEUTRAL.value).isEqualTo(3)
        assertThat(GuildEmblems_t.GUILDEMBLEM_MEMBER.value).isEqualTo(4)
        assertThat(GuildEmblems_t.GUILDEMBLEM_OTHER.value).isEqualTo(5)
    }
}
