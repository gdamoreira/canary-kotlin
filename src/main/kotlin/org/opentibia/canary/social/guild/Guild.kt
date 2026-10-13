package org.opentibia.canary.social.guild

import org.opentibia.canary.creatures.Player

/**
 * Rank levels mirror the Canary `GuildRank::level` values loaded from
 * `data/XML/guilds.xml` (`guild.hpp:16-23`): member 1, vice-leader 2,
 * leader 3. `Player::setGuild` looks the level-1 rank up (player.cpp:9331) and
 * `Chat::talkToChannel` promotes ranks above level 1 to `TALKTYPE_CHANNEL_O`
 * (chat.cpp:577-581).
 */
enum class GuildRank(val level: Int) {
    Leader(3),
    ViceLeader(2),
    Member(1)
}

class Guild(
    val id: Long,
    val name: String
) {
    private val memberRanks = mutableMapOf<Long, GuildRank>()

    fun addMember(player: Player, rank: GuildRank = GuildRank.Member) {
        memberRanks[player.id] = rank
    }

    fun removeMember(player: Player) {
        memberRanks.remove(player.id)
    }

    fun getRank(player: Player): GuildRank? = memberRanks[player.id]

    /** `Guild::getRankByLevel` (`guild.hpp:77`); the port keeps one rank per level. */
    fun getRankByLevel(level: Int): GuildRank? = GuildRank.entries.firstOrNull { it.level == level }

    fun isMember(player: Player): Boolean = memberRanks.containsKey(player.id)
}

enum class GuildWarState {
    Pending,
    Active,
    Ended
}

class GuildWar(
    val id: Long,
    val guildAId: Long,
    val guildBId: Long,
    val fragLimit: Int
) {
    var killsGuildA: Int = 0
        private set
    var killsGuildB: Int = 0
        private set

    var state: GuildWarState = GuildWarState.Active
        private set

    var winnerGuildId: Long? = null
        private set

    fun registerKill(killerGuildId: Long) {
        if (state != GuildWarState.Active) return

        if (killerGuildId == guildAId) {
            killsGuildA++
            if (killsGuildA >= fragLimit) {
                state = GuildWarState.Ended
                winnerGuildId = guildAId
            }
        } else if (killerGuildId == guildBId) {
            killsGuildB++
            if (killsGuildB >= fragLimit) {
                state = GuildWarState.Ended
                winnerGuildId = guildBId
            }
        }
    }
}
