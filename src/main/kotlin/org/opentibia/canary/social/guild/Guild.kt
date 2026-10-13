package org.opentibia.canary.social.guild

import org.opentibia.canary.creatures.Player
import java.sql.SQLException
import javax.sql.DataSource

/**
 * `GuildRank` (src/creatures/players/grouping/guild.hpp:16-23): one row of the
 * `guild_ranks` table (schema.sql:406-415) with its database id, display name
 * and level (leader 3, vice-leader 2, member 1 are the ranks the
 * `oncreate_guilds` trigger inserts, schema.sql:423-427).
 *
 * The C++ struct has no `operator==`; C++ compares `GuildRank_ptr`s and
 * `PlayerTitle::checkOther` only checks `rank->level == 3`
 * (player_title.cpp:278-280). The port compares by [level] because a guild
 * keeps one rank per level; this preserves the level-3 "Guild Leader" check for
 * ranks loaded from the database.
 */
class GuildRank(
    var id: Long,
    var name: String,
    var level: Int,
) {
    override fun equals(other: Any?): Boolean = this === other || (other is GuildRank && other.level == level)

    override fun hashCode(): Int = level

    override fun toString(): String = "GuildRank(id=$id, name=$name, level=$level)"

    companion object {
        /** `oncreate_guilds` trigger rank (schema.sql:424). */
        val Leader = GuildRank(0, "The Leader", 3)

        /** `oncreate_guilds` trigger rank (schema.sql:425). */
        val ViceLeader = GuildRank(0, "Vice-Leader", 2)

        /** `oncreate_guilds` trigger rank (schema.sql:426). */
        val Member = GuildRank(0, "Member", 1)

        internal val triggerRanks = listOf(Leader, ViceLeader, Member)

        /** The `oncreate_guilds` rank for [level], or `null` for other levels. */
        internal fun byLevel(level: Int): GuildRank? = triggerRanks.firstOrNull { it.level == level }
    }
}

/**
 * `Guild` (src/creatures/players/grouping/guild.hpp:27-96, guild.cpp:14-63).
 *
 * The Kotlin port keeps Canary's method names and semantics, including the
 * `Bankable` surface (`getBankBalance`/`setBankBalance`, `isOnline`/`setOnline`,
 * `isGuild`, bank.hpp:15-28) without the interface declaration.
 *
 * PARITY GAPS: `Guild::addMember`/`Guild::removeMember` broadcast
 * `g_game().updatePlayerHelpers(member)` (guild.cpp:17, 25, 28) and
 * `Guild::removeMember` calls `g_game().removeGuild(id)` once the online list
 * is empty (guild.cpp:29-31). The helper broadcasts belong to the protocol
 * layer; the game removal is a constructor-injected [removeGuild] seam
 * (AGENTS.md 1.1).
 *
 * @param removeGuild `g_game().removeGuild(id)`, invoked once the online member
 *   list becomes empty; the default no-op keeps standalone guilds inert.
 */
class Guild(
    val id: Long,
    val name: String,
    private val removeGuild: (Long) -> Unit = {},
) {
    /** `Guild::membersOnline` (guild.hpp:88), insertion ordered like `std::list`. */
    private val membersOnline = mutableListOf<Player>()

    /** `Guild::ranks` (guild.hpp:89), insertion ordered like `std::vector`. */
    private val ranks = mutableListOf<GuildRank>()

    /** Port bookkeeping for `Player::guildRank` (player.hpp:1852). */
    private val memberRanks = mutableMapOf<Long, GuildRank>()

    /** `Guild::bankBalance` (guild.hpp:91). */
    private var bankBalance: Long = 0

    /** `Guild::motd` (guild.hpp:92). */
    private var motd: String = ""

    /** `Guild::memberCount` (guild.hpp:94). */
    private var memberCount: Long = 0

    /** `Guild::online` (guild.hpp:95). */
    private var online: Boolean = true

    /** `Guild::isGuild` (guild.hpp:35-37). */
    fun isGuild(): Boolean = true

    /** `Guild::setOnline` (guild.hpp:39-41). */
    fun setOnline(value: Boolean) {
        online = value
    }

    /** `Guild::isOnline` (guild.hpp:42-44). */
    fun isOnline(): Boolean = online

    /** `Guild::getMembersOnline` (guild.hpp:52-54). */
    fun getMembersOnline(): List<Player> = membersOnline.toList()

    /** `Guild::getMemberCountOnline` (guild.hpp:55-57). */
    fun getMemberCountOnline(): Long = membersOnline.size.toLong()

    /** `Guild::getMemberCount` (guild.hpp:58-60). */
    fun getMemberCount(): Long = memberCount

    /** `Guild::setMemberCount` (guild.hpp:61-63). */
    fun setMemberCount(count: Long) {
        memberCount = count
    }

    /** `Guild::getBankBalance` (guild.hpp:64-66). */
    fun getBankBalance(): Long = bankBalance

    /** `Guild::setBankBalance` (guild.hpp:67-69). */
    fun setBankBalance(balance: Long) {
        bankBalance = balance
    }

    /** `Guild::getRanks` (guild.hpp:71-73). */
    fun getRanks(): List<GuildRank> = ranks.toList()

    /** `Guild::getRankById` (guild.cpp:34-41). */
    fun getRankById(rankId: Long): GuildRank? = ranks.firstOrNull { it.id == rankId }

    /** `Guild::getRankByName` (guild.cpp:43-50). */
    fun getRankByName(rankName: String): GuildRank? = ranks.firstOrNull { it.name == rankName }

    /**
     * `Guild::getRankByLevel` (guild.cpp:52-59). A guild without loaded ranks
     * resolves the three canonical `oncreate_guilds` ranks, so
     * `Player::setGuild`'s level-1 lookup (player.cpp:9331) matches Canary's
     * database-triggered state before ranks are loaded.
     */
    fun getRankByLevel(level: Int): GuildRank? {
        ranks.firstOrNull { it.level == level }?.let { return it }
        return if (ranks.isEmpty()) GuildRank.byLevel(level) else null
    }

    /** `Guild::addRank` (guild.cpp:61-63). */
    fun addRank(rankId: Long, rankName: String, level: Int) {
        ranks.add(GuildRank(rankId, rankName, level))
    }

    /** `Guild::getMotd` (guild.hpp:80-82). */
    fun getMotd(): String = motd

    /** `Guild::setMotd` (guild.hpp:83-85). */
    fun setMotd(newMotd: String) {
        motd = newMotd
    }

    /**
     * `Guild::addMember` (guild.cpp:14-19): the player joins the online list.
     * The port also records `Player::guildRank` per member, so [getRank] can
     * serve the chat/title checks without reaching into `Player`.
     */
    fun addMember(player: Player, rank: GuildRank = GuildRank.Member) {
        membersOnline.add(player)
        memberRanks[player.id] = rank
    }

    /**
     * `Guild::removeMember` (guild.cpp:21-32): every online entry of the player
     * is erased (`std::list::remove` semantics) and an empty guild is removed
     * from the game.
     */
    fun removeMember(player: Player) {
        membersOnline.removeAll { it === player }
        memberRanks.remove(player.id)
        if (membersOnline.isEmpty()) {
            removeGuild(id)
        }
    }

    /** Port `Player::getGuildRank` view for the member currently in this guild. */
    fun getRank(player: Player): GuildRank? = memberRanks[player.id]

    /** Port membership check over the online member list. */
    fun isMember(player: Player): Boolean = memberRanks.containsKey(player.id)
}

/**
 * `GuildEmblems_t` (src/utils/utils_definitions.hpp:532-539). The values cross
 * the wire in `ProtocolGame::sendCreatureType` (`getGuildEmblem`,
 * protocolgame.cpp:9729).
 */
enum class GuildEmblems_t(val value: Int) {
    GUILDEMBLEM_NONE(0),
    GUILDEMBLEM_ALLY(1),
    GUILDEMBLEM_ENEMY(2),
    GUILDEMBLEM_NEUTRAL(3),
    GUILDEMBLEM_MEMBER(4),
    GUILDEMBLEM_OTHER(5),
}

/**
 * `Player::isInWarList` (player.cpp:7416-7418): the guild-war vector loaded by
 * `IOGuild::getWarList` (ioguild.cpp:60-77, player.hpp:1692) contains [guildId].
 */
fun isInWarList(guildWarVector: Collection<Long>, guildId: Long): Boolean = guildId in guildWarVector

/**
 * `Player::isInWar` (player.cpp:7403-7414): both guilds must list each other.
 * `null` guilds model the C++ null player/guild early returns.
 */
fun isInWar(
    guild: Guild?,
    guildWarVector: Collection<Long>,
    playerGuild: Guild?,
    playerGuildWarVector: Collection<Long>,
): Boolean {
    if (guild == null || playerGuild == null) {
        return false
    }
    return isInWarList(guildWarVector, playerGuild.id) && isInWarList(playerGuildWarVector, guild.id)
}

/**
 * `Player::getGuildEmblem` (player.cpp:7823-7847): the emblem the viewer sees
 * over `playerGuild`'s nameplate. [playerGuildWarVector] is the subject's
 * `guildWarVector`; the empty-vector branch is resolved before any war check.
 */
fun getGuildEmblem(
    guild: Guild?,
    guildWarVector: Collection<Long>,
    playerGuild: Guild?,
    playerGuildWarVector: Collection<Long>,
): GuildEmblems_t {
    if (playerGuild == null) {
        return GuildEmblems_t.GUILDEMBLEM_NONE
    }

    if (playerGuildWarVector.isEmpty()) {
        return if (guild === playerGuild) {
            GuildEmblems_t.GUILDEMBLEM_MEMBER
        } else {
            GuildEmblems_t.GUILDEMBLEM_OTHER
        }
    }

    if (guild === playerGuild) {
        return GuildEmblems_t.GUILDEMBLEM_ALLY
    }

    if (isInWar(guild, guildWarVector, playerGuild, playerGuildWarVector)) {
        return GuildEmblems_t.GUILDEMBLEM_ENEMY
    }

    return GuildEmblems_t.GUILDEMBLEM_NEUTRAL
}

/**
 * The `guild_wars`.`status` values Canary queries: `IOGuild::getWarList` selects
 * `status = 1` (ioguild.cpp:62) and
 * `data/scripts/globalevents/update_guild_war_status.lua` expires active wars
 * to `status = 4`. War declaration and kill scoring stay in the website/Lua
 * (AGENTS.md 1.4).
 */
object GuildWarStatus {
    /** Active war; the only status `IOGuild::getWarList` returns (ioguild.cpp:62). */
    const val ACTIVE = 1

    /** Expired war written by `update_guild_war_status.lua`. */
    const val ENDED = 4
}

/**
 * One `guild_wars` row (schema.sql:357-372). Canary has no C++ `GuildWar`
 * class: rows are written by the website and read by `IOGuild::getWarList`;
 * kill records live in `guildwar_kills` and are maintained by Lua
 * (`data/scripts/creaturescripts/player/death.lua`).
 */
data class GuildWar(
    val id: Long = 0,
    val guild1: Long,
    val guild2: Long,
    val name1: String = "",
    val name2: String = "",
    val status: Int = GuildWarStatus.ACTIVE,
    val started: Long = 0,
    val ended: Long = 0,
    val fragsLimit: Int = 0,
    val payment: Long = 0,
    val durationDays: Int = 0,
)

/** One `guild_invites` row (schema.sql:391-403). */
data class GuildInvite(
    val playerId: Long,
    val guildId: Long,
    val date: Long,
)

/**
 * Persistence surface of Canary's `IOGuild` statics (src/io/ioguild.hpp:15-21,
 * ioguild.cpp:15-77) plus the `guild_invites` table, which Canary's C++ engine
 * never reads or writes: MyAAC owns guild invite creation, so the port only
 * models the table for the website-facing side (PARITY NOTE; no C++ counterpart
 * exists for these three methods).
 */
interface GuildRepository {
    /** `IOGuild::loadGuild` (ioguild.cpp:15-33); `null` when the guild row is missing. */
    fun loadGuild(guildId: Long): Guild?

    /** `IOGuild::saveGuild` (ioguild.cpp:35-45): updates only the balance column. */
    fun saveGuild(guild: Guild)

    /** `IOGuild::getGuildIdByName` (ioguild.cpp:47-58); 0 when not found. */
    fun getGuildIdByName(name: String): Long

    /** `IOGuild::getWarList` (ioguild.cpp:60-77): active (`status = 1`) opponents. */
    fun getWarList(guildId: Long): List<Long>

    /** `guild_invites` rows of a player (schema.sql:391-403). */
    fun getGuildInvites(playerId: Long): List<GuildInvite>

    /**
     * Inserts a `(player_id, guild_id, date)` invite, mirroring Canary's
     * `Database::executeQuery` boolean contract; `false` when the primary key
     * already exists.
     */
    fun addGuildInvite(playerId: Long, guildId: Long, date: Long): Boolean

    /** Deletes the `(player_id, guild_id)` invite row. */
    fun removeGuildInvite(playerId: Long, guildId: Long)
}

/**
 * In-memory stand-in for the `guilds`, `guild_ranks`, `guild_wars` and
 * `guild_invites` tables, used for local runs and tests. Like Canary's
 * `IOGuild::saveGuild`, [saveGuild] persists only the balance; ranks and rank
 * additions are rebuilt from the seeded records on [loadGuild].
 *
 * [wars] and [invites] are exposed for seeding, mirroring the website-owned
 * rows the engine only reads.
 */
class InMemoryGuildRepository(
    initialGuilds: List<Guild> = emptyList(),
    val wars: MutableList<GuildWar> = mutableListOf(),
    val invites: MutableSet<GuildInvite> = mutableSetOf(),
) : GuildRepository {

    private class GuildRecord(
        val id: Long,
        val name: String,
        var balance: Long,
        val ranks: List<GuildRank>,
    )

    private val guilds = linkedMapOf<Long, GuildRecord>()

    init {
        for (guild in initialGuilds) {
            guilds[guild.id] = GuildRecord(guild.id, guild.name, guild.getBankBalance(), guild.getRanks())
        }
    }

    override fun loadGuild(guildId: Long): Guild? {
        val record = guilds[guildId] ?: return null
        val guild = Guild(record.id, record.name)
        guild.setBankBalance(record.balance)
        for (rank in record.ranks) {
            guild.addRank(rank.id, rank.name, rank.level)
        }
        return guild
    }

    override fun saveGuild(guild: Guild) {
        guilds[guild.id]?.balance = guild.getBankBalance()
    }

    override fun getGuildIdByName(name: String): Long =
        guilds.values.firstOrNull { it.name == name }?.id ?: 0

    override fun getWarList(guildId: Long): List<Long> =
        wars.filter { it.status == GuildWarStatus.ACTIVE && (it.guild1 == guildId || it.guild2 == guildId) }
            .map { if (it.guild1 != guildId) it.guild1 else it.guild2 }

    override fun getGuildInvites(playerId: Long): List<GuildInvite> =
        invites.filter { it.playerId == playerId }

    override fun addGuildInvite(playerId: Long, guildId: Long, date: Long): Boolean {
        if (invites.any { it.playerId == playerId && it.guildId == guildId }) {
            return false
        }
        return invites.add(GuildInvite(playerId, guildId, date))
    }

    override fun removeGuildInvite(playerId: Long, guildId: Long) {
        invites.removeIf { it.playerId == playerId && it.guildId == guildId }
    }
}

/**
 * JDBC persistence for the guild tables, mirroring Canary's `IOGuild` queries
 * (src/io/ioguild.cpp:15-77) against the schema created by
 * `V9__guild_system.sql`.
 *
 * `loadGuild` reads the `guilds` row (name + balance) and its `guild_ranks`
 * rows, `saveGuild` updates only `guilds.balance` (Canary never persists ranks
 * or MOTD from the engine), `getGuildIdByName` resolves the unique guild name
 * and `getWarList` selects the `status = 1` wars of either side.
 */
class JdbcGuildRepository(private val dataSource: DataSource) : GuildRepository {

    override fun loadGuild(guildId: Long): Guild? = dataSource.connection.use { conn ->
        val guild = conn.prepareStatement("SELECT name, balance FROM guilds WHERE id = ?").use { stmt ->
            stmt.setLong(1, guildId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    Guild(guildId, rs.getString("name")).also { it.setBankBalance(rs.getLong("balance")) }
                } else {
                    null
                }
            }
        } ?: return@use null

        conn.prepareStatement("SELECT id, name, level FROM guild_ranks WHERE guild_id = ?").use { stmt ->
            stmt.setLong(1, guildId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    guild.addRank(rs.getLong("id"), rs.getString("name"), rs.getInt("level"))
                }
            }
        }
        guild
    }

    override fun saveGuild(guild: Guild) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE guilds SET balance = ? WHERE id = ?").use { stmt ->
                stmt.setLong(1, guild.getBankBalance())
                stmt.setLong(2, guild.id)
                stmt.executeUpdate()
            }
        }
    }

    override fun getGuildIdByName(name: String): Long = dataSource.connection.use { conn ->
        conn.prepareStatement("SELECT id FROM guilds WHERE name = ?").use { stmt ->
            stmt.setString(1, name)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getLong("id") else 0L
            }
        }
    }

    override fun getWarList(guildId: Long): List<Long> = dataSource.connection.use { conn ->
        val opponents = mutableListOf<Long>()
        conn.prepareStatement(
            "SELECT guild1, guild2 FROM guild_wars WHERE (guild1 = ? OR guild2 = ?) AND status = ?",
        ).use { stmt ->
            stmt.setLong(1, guildId)
            stmt.setLong(2, guildId)
            stmt.setInt(3, GuildWarStatus.ACTIVE)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val guild1 = rs.getLong("guild1")
                    opponents.add(if (guildId != guild1) guild1 else rs.getLong("guild2"))
                }
            }
        }
        opponents
    }

    override fun getGuildInvites(playerId: Long): List<GuildInvite> = dataSource.connection.use { conn ->
        val invites = mutableListOf<GuildInvite>()
        conn.prepareStatement("SELECT player_id, guild_id, \"date\" FROM guild_invites WHERE player_id = ?").use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    invites.add(
                        GuildInvite(
                            playerId = rs.getLong("player_id"),
                            guildId = rs.getLong("guild_id"),
                            date = rs.getLong("date"),
                        ),
                    )
                }
            }
        }
        invites
    }

    override fun addGuildInvite(playerId: Long, guildId: Long, date: Long): Boolean = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT INTO guild_invites (player_id, guild_id, \"date\") VALUES (?, ?, ?)").use { stmt ->
                stmt.setLong(1, playerId)
                stmt.setLong(2, guildId)
                stmt.setLong(3, date)
                stmt.executeUpdate()
            }
        }
        true
    } catch (e: SQLException) {
        false
    }

    override fun removeGuildInvite(playerId: Long, guildId: Long) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM guild_invites WHERE player_id = ? AND guild_id = ?").use { stmt ->
                stmt.setLong(1, playerId)
                stmt.setLong(2, guildId)
                stmt.executeUpdate()
            }
        }
    }
}
