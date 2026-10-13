package org.opentibia.canary.io

import org.opentibia.canary.social.guild.Guild
import org.opentibia.canary.social.guild.GuildRepository

/**
 * Port of Canary's `IOGuild` (src/io/ioguild.cpp:15-77, ioguild.hpp:15-21):
 * `loadGuild` reads the `guilds` row (name + balance) and its `guild_ranks`
 * rows, `saveGuild` updates only the balance, `getGuildIdByName` resolves the
 * unique guild name and `getWarList` selects the active (`status = 1`) wars of
 * either side.
 *
 * Canary's static functions are exposed through a constructor-injected
 * [GuildRepository] (AGENTS.md 1.1). The SQL lives in
 * [org.opentibia.canary.social.guild.JdbcGuildRepository] against the tables
 * created by `V9__guild_system.sql`; no guild schema change is needed.
 */
class IOGuild(private val guildRepository: GuildRepository) {

    /** `IOGuild::loadGuild` (ioguild.cpp:15-33); `null` when the guild row is missing. */
    fun loadGuild(guildId: Long): Guild? = guildRepository.loadGuild(guildId)

    /**
     * `IOGuild::saveGuild` (ioguild.cpp:35-45): a null guild is a no-op, and
     * only the bank balance is persisted.
     */
    fun saveGuild(guild: Guild?) {
        if (guild == null) {
            return
        }
        guildRepository.saveGuild(guild)
    }

    /** `IOGuild::getGuildIdByName` (ioguild.cpp:47-58); 0 when the name is unknown. */
    fun getGuildIdByName(name: String): Long = guildRepository.getGuildIdByName(name)

    /** `IOGuild::getWarList` (ioguild.cpp:60-77): the active wars' opponent guild ids. */
    fun getWarList(guildId: Long): List<Long> = guildRepository.getWarList(guildId)
}
