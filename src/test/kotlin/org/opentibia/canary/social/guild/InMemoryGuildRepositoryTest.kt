package org.opentibia.canary.social.guild

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Behaviour tests for the in-memory [GuildRepository], the local stand-in for
 * the `guilds`, `guild_ranks`, `guild_wars` and `guild_invites` tables
 * (schema.sql:338-455) backing `IOGuild::loadGuild` / `saveGuild` / `getWarList`
 * (src/io/ioguild.cpp:15-77).
 */
class InMemoryGuildRepositoryTest {

    private fun guild(id: Long, name: String, balance: Long = 0): Guild =
        Guild(id, name).also { it.setBankBalance(balance) }

    @Test
    fun `loadGuild rebuilds the guild with its balance and ranks`() {
        val seeded = guild(10, "Red Rose", balance = 5000)
        seeded.addRank(100, "The Leader", 3)
        seeded.addRank(101, "Vice-Leader", 2)
        seeded.addRank(102, "Member", 1)
        val repository = InMemoryGuildRepository(initialGuilds = listOf(seeded))

        val loaded = repository.loadGuild(10)

        assertThat(loaded).isNotNull
        assertThat(loaded?.id).isEqualTo(10)
        assertThat(loaded?.name).isEqualTo("Red Rose")
        assertThat(loaded?.getBankBalance()).isEqualTo(5000)
        assertThat(loaded?.getRanks()?.map { it.name })
            .containsExactlyInAnyOrder("The Leader", "Vice-Leader", "Member")
    }

    @Test
    fun `loadGuild returns null for an unknown guild`() {
        val repository = InMemoryGuildRepository()

        assertThat(repository.loadGuild(404)).isNull()
    }

    @Test
    fun `saveGuild persists only the balance like canary`() {
        val seeded = guild(10, "Red Rose", balance = 100)
        seeded.addRank(102, "Member", 1)
        seeded.setMotd("old motd")
        val repository = InMemoryGuildRepository(initialGuilds = listOf(seeded))

        val loaded = repository.loadGuild(10)!!
        loaded.setMotd("new motd")
        loaded.addRank(103, "Extra", 2)
        loaded.setBankBalance(999)
        repository.saveGuild(loaded)

        val reloaded = repository.loadGuild(10)!!
        assertThat(reloaded.getBankBalance()).isEqualTo(999)
        assertThat(reloaded.getMotd()).isEmpty()
        assertThat(reloaded.getRanks().map { it.name }).containsExactly("Member")
    }

    @Test
    fun `getGuildIdByName returns the id or zero`() {
        val repository = InMemoryGuildRepository(initialGuilds = listOf(guild(10, "Red Rose")))

        assertThat(repository.getGuildIdByName("Red Rose")).isEqualTo(10)
        assertThat(repository.getGuildIdByName("Unknown")).isZero()
    }

    @Test
    fun `getWarList filters active wars and returns the opponent`() {
        val repository = InMemoryGuildRepository(
            initialGuilds = listOf(guild(10, "Red Rose")),
            wars = mutableListOf(
                GuildWar(guild1 = 10, guild2 = 20, status = GuildWarStatus.ACTIVE),
                GuildWar(guild1 = 30, guild2 = 10, status = GuildWarStatus.ACTIVE),
                GuildWar(guild1 = 10, guild2 = 40, status = 2),
                GuildWar(guild1 = 10, guild2 = 50, status = GuildWarStatus.ENDED),
            ),
        )

        assertThat(repository.getWarList(10)).containsExactlyInAnyOrder(20L, 30L)
        assertThat(repository.getWarList(20)).containsExactly(10L)
        assertThat(repository.getWarList(99)).isEmpty()
    }

    @Test
    fun `invites reject duplicates and remove the matching pair`() {
        val repository = InMemoryGuildRepository(initialGuilds = listOf(guild(10, "Red Rose")))

        repository.addGuildInvite(1, 10, 1000)
        assertThat(repository.addGuildInvite(1, 10, 2000)).isFalse()
        repository.addGuildInvite(2, 10, 3000)

        assertThat(repository.getGuildInvites(1))
            .containsExactly(GuildInvite(playerId = 1, guildId = 10, date = 1000))
        assertThat(repository.getGuildInvites(2))
            .containsExactly(GuildInvite(playerId = 2, guildId = 10, date = 3000))

        repository.removeGuildInvite(1, 10)
        assertThat(repository.getGuildInvites(1)).isEmpty()
        assertThat(repository.getGuildInvites(2)).hasSize(1)

        repository.removeGuildInvite(1, 10)
        assertThat(repository.getGuildInvites(2)).hasSize(1)
    }
}
