package org.opentibia.canary.social.guild

import org.opentibia.canary.creatures.Player

enum class GuildRank {
    Leader,
    ViceLeader,
    Member
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
