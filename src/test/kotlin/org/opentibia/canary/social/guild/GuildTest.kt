package org.opentibia.canary.social.guild

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

/**
 * Transcript tests for `Guild` and `GuildRank`
 * (src/creatures/players/grouping/guild.hpp, guild.cpp:14-63) and their
 * `Player::setGuild` integration (src/creatures/players/player.cpp:9316-9355).
 */
class GuildTest {

    private fun player(id: Long, name: String): Player =
        Player(id, name, 100, 100, position = Position(100, 100, 7))

    @Test
    fun `default guild mirrors the canary constructor state`() {
        val guild = Guild(id = 1, name = "Red Rose")

        assertThat(guild.id).isEqualTo(1)
        assertThat(guild.name).isEqualTo("Red Rose")
        assertThat(guild.getMembersOnline()).isEmpty()
        assertThat(guild.getMemberCountOnline()).isZero()
        assertThat(guild.getMemberCount()).isZero()
        assertThat(guild.getBankBalance()).isZero()
        assertThat(guild.getMotd()).isEmpty()
        assertThat(guild.getRanks()).isEmpty()
        assertThat(guild.isGuild()).isTrue()
        assertThat(guild.isOnline()).isTrue()
    }

    @Test
    fun `addRank resolves ranks by id name and level with the first match`() {
        val guild = Guild(id = 1, name = "Red Rose")
        guild.addRank(10, "The Leader", 3)
        guild.addRank(11, "Vice-Leader", 2)
        guild.addRank(12, "Custom", 2)

        assertThat(guild.getRanks()).hasSize(3)
        assertThat(guild.getRankById(11)?.name).isEqualTo("Vice-Leader")
        assertThat(guild.getRankById(99)).isNull()
        assertThat(guild.getRankByName("Custom")?.id).isEqualTo(12)
        assertThat(guild.getRankByName("Missing")).isNull()
        assertThat(guild.getRankByLevel(2)?.id).isEqualTo(11)
        assertThat(guild.getRankByLevel(4)).isNull()
    }

    @Test
    fun `getRankByLevel resolves the trigger ranks until ranks are loaded`() {
        val unloaded = Guild(id = 1, name = "Red Rose")

        assertThat(unloaded.getRankByLevel(3)).isEqualTo(GuildRank.Leader)
        assertThat(unloaded.getRankByLevel(2)).isEqualTo(GuildRank.ViceLeader)
        assertThat(unloaded.getRankByLevel(1)).isEqualTo(GuildRank.Member)

        val loaded = Guild(id = 2, name = "Black Widows")
        loaded.addRank(1, "The Leader", 3)

        assertThat(loaded.getRankByLevel(1)).isNull()
    }

    @Test
    fun `addMember stores the rank and tracks online members in order`() {
        val guild = Guild(id = 1, name = "Red Rose")
        val leader = player(1, "Leader")
        val member = player(2, "Member")

        guild.addMember(leader, GuildRank.Leader)
        guild.addMember(member)

        assertThat(guild.getMembersOnline()).containsExactly(leader, member)
        assertThat(guild.getMemberCountOnline()).isEqualTo(2)
        assertThat(guild.getRank(leader)).isEqualTo(GuildRank.Leader)
        assertThat(guild.getRank(member)).isEqualTo(GuildRank.Member)
        assertThat(guild.isMember(member)).isTrue()
    }

    @Test
    fun `removeMember erases every entry and asks the game to remove an empty guild`() {
        val removedGuilds = mutableListOf<Long>()
        val guild = Guild(id = 1, name = "Red Rose", removeGuild = { removedGuilds.add(it) })
        val first = player(1, "First")
        val second = player(2, "Second")

        guild.addMember(first)
        guild.addMember(first)
        guild.addMember(second)

        guild.removeMember(first)

        assertThat(guild.getMembersOnline()).containsExactly(second)
        assertThat(guild.getRank(first)).isNull()
        assertThat(removedGuilds).isEmpty()

        guild.removeMember(second)

        assertThat(guild.getMembersOnline()).isEmpty()
        assertThat(guild.getMemberCountOnline()).isZero()
        assertThat(removedGuilds).containsExactly(1L)
    }

    @Test
    fun `motd balance member count and online flag round trip`() {
        val guild = Guild(id = 1, name = "Red Rose")

        guild.setMotd("Welcome to Red Rose")
        guild.setBankBalance(123_456)
        guild.setMemberCount(50)
        guild.setOnline(false)

        assertThat(guild.getMotd()).isEqualTo("Welcome to Red Rose")
        assertThat(guild.getBankBalance()).isEqualTo(123_456)
        assertThat(guild.getMemberCount()).isEqualTo(50)
        assertThat(guild.isOnline()).isFalse()
    }

    @Test
    fun `setGuild assigns the level one rank and membership`() {
        val guild = Guild(id = 1, name = "Red Rose")
        guild.addRank(5, "Member", 1)
        val player = player(1, "Recruit")

        player.setGuild(guild)

        assertThat(player.getGuild()).isSameAs(guild)
        assertThat(player.getGuildRank()).isEqualTo(GuildRank.Member)
        assertThat(guild.isMember(player)).isTrue()

        player.setGuild(null)

        assertThat(player.getGuild()).isNull()
        assertThat(guild.isMember(player)).isFalse()
    }

    @Test
    fun `setGuild leaves the player guildless without a level one rank`() {
        val guild = Guild(id = 1, name = "Black Widows")
        guild.addRank(1, "The Leader", 3)
        val player = player(1, "Recruit")

        player.setGuild(guild)

        assertThat(player.getGuild()).isNull()
        assertThat(guild.isMember(player)).isFalse()
    }
}
