package org.opentibia.canary.social.guild

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

class GuildWarTest {

    @Test
    fun `manages guild membership, ranks and permissions`() {
        val guild = Guild(id = 1, name = "Red Rose")
        val leader = Player(1, "LeaderPlayer", 100, 100, position = Position(100, 100, 7))
        val member = Player(2, "MemberPlayer", 100, 100, position = Position(100, 100, 7))

        guild.addMember(leader, GuildRank.Leader)
        guild.addMember(member, GuildRank.Member)

        assertThat(guild.getRank(leader)).isEqualTo(GuildRank.Leader)
        assertThat(guild.getRank(member)).isEqualTo(GuildRank.Member)
        assertThat(guild.isMember(leader)).isTrue()
    }

    @Test
    fun `tracks guild war frags and declares victory upon frag limit`() {
        val guildA = Guild(id = 1, name = "Red Rose")
        val guildB = Guild(id = 2, name = "Black Widows")

        val war = GuildWar(
            id = 101,
            guildAId = guildA.id,
            guildBId = guildB.id,
            fragLimit = 3
        )

        assertThat(war.state).isEqualTo(GuildWarState.Active)

        war.registerKill(killerGuildId = guildA.id)
        war.registerKill(killerGuildId = guildA.id)
        assertThat(war.killsGuildA).isEqualTo(2)
        assertThat(war.state).isEqualTo(GuildWarState.Active)

        war.registerKill(killerGuildId = guildA.id)
        assertThat(war.killsGuildA).isEqualTo(3)
        assertThat(war.state).isEqualTo(GuildWarState.Ended)
        assertThat(war.winnerGuildId).isEqualTo(guildA.id)
    }
}
