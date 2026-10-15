package org.opentibia.canary.creatures.players.components

import org.opentibia.canary.database.SqlDialect
import org.opentibia.canary.kv.KV
import org.opentibia.canary.kv.ValueWrapper

/**
 * `Summary_t` (`src/enums/player_cyclopedia.hpp:40-48`) and the
 * `m_summaryCategories` keys (`game.cpp:735-742`).
 */
enum class SummaryCategory(val value: Int) {
    HOUSE_ITEMS(9),
    BOOSTS(10),
    PREY_CARDS(12),
    BLESSINGS(14),

    /** Handled only by `PlayerCyclopedia::updateStoreSummary`; has no category key. */
    ALL_BLESSINGS(17),
    INSTANT_REWARDS(18),
    HIRELINGS(20),
}

/**
 * `enum class Blessings : uint8_t` (`src/enums/player_blessings.hpp:16-25`).
 * Entry names are the `fmt::format("{}", blessIt)` strings
 * `updateStoreSummary` stores (`player_cyclopedia.cpp:211-213`).
 */
enum class Blessings(val value: Int) {
    TwistOfFate(1),
    TheWisdomOfSolitude(2),
    TheSparkOfThePhoenix(3),
    TheFireOfTheSuns(4),
    TheSpiritualShielding(5),
    TheEmbraceOfTibia(6),
    HearthOfTheMountain(7),
    BloodOfTheMountain(8),
}

/**
 * `Game::getSummaryKeyByType` (`game.cpp:12596-12598`) over the
 * `m_summaryCategories` map (`game.cpp:735-742`). The missing
 * `ALL_BLESSINGS` key yields `""` exactly like the C++ `operator[]` insert.
 */
fun defaultSummaryKeyByType(type: Int): String = when (type) {
    SummaryCategory.HOUSE_ITEMS.value -> "house-items"
    SummaryCategory.BOOSTS.value -> "xp-boosts"
    SummaryCategory.PREY_CARDS.value -> "prey-cards"
    SummaryCategory.BLESSINGS.value -> "blessings"
    SummaryCategory.INSTANT_REWARDS.value -> "instant-rewards"
    SummaryCategory.HIRELINGS.value -> "hirelings"
    else -> ""
}

/** `struct Summary` (`player_cyclopedia.hpp:15-22`). */
data class Summary(
    val preyWildcards: Int = 0,
    val instantRewards: Int = 0,
    val hirelings: Int = 0,
)

/** `struct RecentDeathEntry` (`creatures_definitions.hpp:1640-1647`). */
data class RecentDeathEntry(
    val cause: String,
    val timestamp: Int,
)

/** `struct RecentPvPKillEntry` (`creatures_definitions.hpp:1649-1658`). */
data class RecentPvPKillEntry(
    val description: String,
    val timestamp: Int,
    val status: Int,
)

/** Recent-kill statuses (`src/game/game_definitions.hpp:84-88`). */
object RecentKillStatus {
    const val JUSTIFIED = 0
    const val UNJUSTIFIED = 1
    const val GUILDWAR = 2
    const val ASSISTED = 3
    const val ARENA = 4
}

/** `PlayerAsyncTask_*` (`creatures_definitions.hpp:856-858`). */
enum class PlayerAsyncTask(val value: Int) {
    Highscore(1 shl 0),
    RecentDeaths(1 shl 1),
    RecentPvPKills(1 shl 2),
}

/**
 * Database result seam for `DBResult_ptr`: `getNumber<T>(name)` is modelled as
 * a `Long` narrowed at the call sites (`player_cyclopedia.cpp:29-31, 54-57,
 * 67-72, 142, 172-194`).
 */
interface CyclopediaQueryResult {
    fun getNumber(name: String): Long

    fun getString(name: String): String

    fun countResults(): Int

    fun next(): Boolean
}

/** `g_database()` seam (`player_cyclopedia.cpp:29, 155, 157`). */
interface CyclopediaDatabase {
    /** `g_database().escapeString(name)` (`player_cyclopedia.cpp:155`). */
    fun escapeString(value: String): String = value

    /** `g_database().storeQuery(query)` (`player_cyclopedia.cpp:29`). */
    fun storeQuery(query: String): CyclopediaQueryResult?
}

/** `g_databaseTasks()` seam (`player_cyclopedia.cpp:146, 198`). */
interface CyclopediaDatabaseTasks {
    /** `g_databaseTasks().store(query, callback)` (`player_cyclopedia.cpp:146, 198`). */
    fun store(query: String, callback: (CyclopediaQueryResult?, Boolean) -> Unit)
}

/** Database stand-in for local runs and tests. */
class NullCyclopediaDatabase : CyclopediaDatabase {
    override fun storeQuery(query: String): CyclopediaQueryResult? = null
}

/** Database-task stand-in that drops scheduled queries. */
class NullCyclopediaDatabaseTasks : CyclopediaDatabaseTasks {
    override fun store(query: String, callback: (CyclopediaQueryResult?, Boolean) -> Unit) = Unit
}

/**
 * Injected `Player` view for the cyclopedia component. Every member transcribes
 * the exact `Player` symbol `PlayerCyclopedia` calls, with the C++ line cited.
 */
interface CyclopediaPlayer {
    /** `Player::getGUID()` (`player_cyclopedia.cpp:29, 39`). */
    fun getGUID(): Long

    /** `Player::getID()` (`player_cyclopedia.cpp:41, 159`). */
    fun getID(): Int

    /** `Player::getName()` (`player_cyclopedia.cpp:149, 155, 184, 188, 201`). */
    fun getName(): String

    /** `m_player.kv()` (`player_cyclopedia.cpp:30, 222, 228, 232, 252`). */
    fun kv(): KV

    /** `Player::addAsyncOngoingTask(task)` (`player_cyclopedia.cpp:147, 199`). */
    fun addAsyncOngoingTask(task: PlayerAsyncTask)

    /** `Player::resetAsyncOngoingTask(task)` (`player_cyclopedia.cpp:48, 166`). */
    fun resetAsyncOngoingTask(task: PlayerAsyncTask)

    /** `Player::sendCyclopediaCharacterRecentDeaths` (`player_cyclopedia.cpp:50, 144`). */
    fun sendCyclopediaCharacterRecentDeaths(page: Int, pages: Int, entries: List<RecentDeathEntry>)

    /** `Player::sendCyclopediaCharacterRecentPvPKills` (`player_cyclopedia.cpp:168, 196`). */
    fun sendCyclopediaCharacterRecentPvPKills(page: Int, pages: Int, entries: List<RecentPvPKillEntry>)
}

/**
 * Mirror of Canary `PlayerCyclopedia`
 * (`src/creatures/players/components/player_cyclopedia.hpp:24-42`,
 * player_cyclopedia.cpp:19-254).
 *
 * The class is intentionally *not* `Player.kt` (per task constraints). The
 * global services become constructor-injected seams (AGENTS.md 1.1):
 * [playersByID] replaces `g_game().getPlayerByID`, [summaryKeyByType] replaces
 * `g_game().getSummaryKeyByType`, [database]/[databaseTasks] replace
 * `g_database()`/`g_databaseTasks()` and the loggers replace `g_logger()`.
 *
 * PARITY NOTES:
 *  - The `summary` scope values are `ValueWrapper::getNumber()` reads and
 *    `ValueWrapper(int)` writes exactly like the C++ (`player_cyclopedia.cpp:
 *    211-254`); the `uint16_t` amount narrowings are applied at the C++ cast
 *    sites.
 *  - The `std::istringstream` participant parser is transcribed with an
 *    index-backed line cursor that reproduces `seekg` on a malformed entry.
 *  - `std::stoll` throwing on a non-numeric id is hardened into an error log
 *    and a skip (the C++ callback would propagate the exception).
 */
class PlayerCyclopedia(
    private val player: CyclopediaPlayer,
    /** `g_database()` (`player_cyclopedia.cpp:29, 155, 157`). */
    private val database: CyclopediaDatabase = NullCyclopediaDatabase(),
    /** `g_databaseTasks()` (`player_cyclopedia.cpp:146, 198`). */
    private val databaseTasks: CyclopediaDatabaseTasks = NullCyclopediaDatabaseTasks(),
    /**
     * Runtime SQL dialect of the cyclopedia statements: MariaDB keeps upstream
     * Canary's backtick queries with `UNIX_TIMESTAMP(DATE_SUB(NOW(), ...))` and
     * `LIMIT offset, count`, while PostgreSQL/H2 get the portable equivalents
     * (`EXTRACT(EPOCH FROM ...)`, `LIMIT count OFFSET offset`). Defaults to
     * upstream's MariaDB until the composition root wires the detected dialect
     * (`SqlDialect.from`, wave 73 K3).
     */
    var sqlDialect: () -> SqlDialect = { SqlDialect.MARIADB },
    /** `g_game().getPlayerByID(playerID)` (`player_cyclopedia.cpp:43, 161`). */
    private val playersByID: (Int) -> CyclopediaPlayer? = { null },
    /** `g_game().getSummaryKeyByType(type)` (`player_cyclopedia.cpp:30, 222, 228, 232, 252`). */
    private val summaryKeyByType: (Int) -> String = ::defaultSummaryKeyByType,
    /** `Benchmark::duration()` in milliseconds (`player_cyclopedia.cpp:38, 153`). */
    private val benchmarkDuration: () -> Long = { 0L },
    /** `g_logger().debug` (`player_cyclopedia.cpp:65, 139, 149, 201, 253`). */
    private val logDebug: (String) -> Unit = {},
    /** `g_logger().warn` (`player_cyclopedia.cpp:108, 127`). */
    private val logWarn: (String) -> Unit = {},
    /** `g_logger().error` (`player_cyclopedia.cpp:237`). */
    private val logError: (String) -> Unit = {},
) {

    /** `PlayerCyclopedia::getSummary` (player_cyclopedia.cpp:22-26). */
    fun getSummary(): Summary = Summary(
        getAmount(SummaryCategory.PREY_CARDS),
        getAmount(SummaryCategory.INSTANT_REWARDS),
        getAmount(SummaryCategory.HIRELINGS),
    )

    /** `PlayerCyclopedia::loadSummaryData` (player_cyclopedia.cpp:28-34). */
    fun loadSummaryData() {
        val result = database.storeQuery(summaryQuery())
        val kvScoped = summaryScope(SummaryCategory.HIRELINGS)
        if (result != null && kvScoped.get("amount") == null) {
            kvScoped.set("amount", ValueWrapper(result.getNumber("count").toShort().toInt()))
        }
    }

    /** `SELECT COUNT(*) ... player_hirelings` (player_cyclopedia.cpp:29) per [SqlDialect]. */
    private fun summaryQuery(): String =
        if (sqlDialect() == SqlDialect.MARIADB) {
            "SELECT COUNT(*) as `count` FROM `player_hirelings` WHERE `player_id` = ${player.getGUID()}"
        } else {
            "SELECT COUNT(*) AS count FROM player_hirelings WHERE player_id = ${player.getGUID()}"
        }

    /**
     * The 30-day recent-deaths window (player_cyclopedia.cpp:39) per
     * [SqlDialect]: MariaDB keeps `UNIX_TIMESTAMP(DATE_SUB(NOW(), INTERVAL 30
     * DAY))` and `LIMIT offset, count`; PostgreSQL/H2 use `EXTRACT(EPOCH FROM
     * NOW() - INTERVAL '30' DAY)` and `LIMIT count OFFSET offset`.
     */
    private fun deathHistoryQuery(offset: Int, entriesPerPage: Int): String =
        if (sqlDialect() == SqlDialect.MARIADB) {
            "SELECT `time`, `level`, `killed_by`, `mostdamage_by`, `participants`, " +
                "(select count(*) FROM `player_deaths` WHERE `player_id` = ${player.getGUID()} AND " +
                "`time` >= UNIX_TIMESTAMP(DATE_SUB(NOW(), INTERVAL 30 DAY))) as `entries` " +
                "FROM `player_deaths` WHERE `player_id` = ${player.getGUID()} AND " +
                "`time` >= UNIX_TIMESTAMP(DATE_SUB(NOW(), INTERVAL 30 DAY)) " +
                "ORDER BY `time` DESC LIMIT $offset, $entriesPerPage"
        } else {
            "SELECT \"time\", level, killed_by, mostdamage_by, participants, " +
                "(SELECT COUNT(*) FROM player_deaths WHERE player_id = ${player.getGUID()} AND " +
                "\"time\" >= EXTRACT(EPOCH FROM NOW() - INTERVAL '30' DAY)) AS entries " +
                "FROM player_deaths WHERE player_id = ${player.getGUID()} AND " +
                "\"time\" >= EXTRACT(EPOCH FROM NOW() - INTERVAL '30' DAY) " +
                "ORDER BY \"time\" DESC LIMIT $entriesPerPage OFFSET $offset"
        }

    /**
     * The 70-day recent-kills window (player_cyclopedia.cpp:158) per
     * [SqlDialect], with the same function/pagination substitutions as
     * [deathHistoryQuery].
     */
    private fun recentKillsQuery(escapedName: String, offset: Int, entriesPerPage: Int): String =
        if (sqlDialect() == SqlDialect.MARIADB) {
            "SELECT `d`.`time`, `d`.`killed_by`, `d`.`mostdamage_by`, `d`.`unjustified`, " +
                "`d`.`mostdamage_unjustified`, `p`.`name`, " +
                "(select count(*) FROM `player_deaths` WHERE ((`killed_by` = $escapedName AND `is_player` = 1) OR " +
                "(`mostdamage_by` = $escapedName AND `mostdamage_is_player` = 1))) as `entries` " +
                "FROM `player_deaths` AS `d` INNER JOIN `players` AS `p` ON `d`.`player_id` = `p`.`id` " +
                "WHERE ((`d`.`killed_by` = $escapedName AND `d`.`is_player` = 1) OR " +
                "(`d`.`mostdamage_by` = $escapedName AND `d`.`mostdamage_is_player` = 1)) AND " +
                "`time` >= UNIX_TIMESTAMP(DATE_SUB(NOW(), INTERVAL 70 DAY)) " +
                "ORDER BY `time` DESC LIMIT $offset, $entriesPerPage"
        } else {
            "SELECT d.\"time\", d.killed_by, d.mostdamage_by, d.unjustified, " +
                "d.mostdamage_unjustified, p.name, " +
                "(SELECT COUNT(*) FROM player_deaths WHERE ((killed_by = $escapedName AND is_player = 1) OR " +
                "(mostdamage_by = $escapedName AND mostdamage_is_player = 1))) AS entries " +
                "FROM player_deaths AS d INNER JOIN players AS p ON d.player_id = p.id " +
                "WHERE ((d.killed_by = $escapedName AND d.is_player = 1) OR " +
                "(d.mostdamage_by = $escapedName AND d.mostdamage_is_player = 1)) AND " +
                "\"time\" >= EXTRACT(EPOCH FROM NOW() - INTERVAL '70' DAY) " +
                "ORDER BY \"time\" DESC LIMIT $entriesPerPage OFFSET $offset"
        }

    /** `PlayerCyclopedia::loadDeathHistory` (player_cyclopedia.cpp:36-150). */
    fun loadDeathHistory(page: Int, entriesPerPage: Int) {
        val offset = (page - 1) * entriesPerPage
        val query = deathHistoryQuery(offset, entriesPerPage)

        val playerID = player.getID()
        val callback: (CyclopediaQueryResult?, Boolean) -> Unit = { result, _ ->
            val resolvedPlayer = playersByID(playerID)
            if (resolvedPlayer != null) {
                resolvedPlayer.resetAsyncOngoingTask(PlayerAsyncTask.RecentDeaths)
                if (result == null) {
                    resolvedPlayer.sendCyclopediaCharacterRecentDeaths(0, 0, emptyList())
                } else {
                    val pages = queryPages(result, entriesPerPage)
                    val entries = mutableListOf<RecentDeathEntry>()
                    do {
                        val killedBy = result.getString("killed_by")
                        val mostdamageBy = result.getString("mostdamage_by")
                        val participants = result.getString("participants")

                        var cause = "Died at level ${result.getNumber("level")}"
                        if (killedBy.isNotEmpty()) {
                            cause += " by $killedBy"
                        }
                        if (mostdamageBy.isNotEmpty()) {
                            cause += if (killedBy.isEmpty()) {
                                " by $mostdamageBy"
                            } else if (mostdamageBy != killedBy) {
                                " and by $mostdamageBy"
                            } else {
                                " <b>(soloed)</b>"
                            }
                        }
                        if (participants.isNotEmpty()) {
                            cause = appendParticipants(cause, participants, killedBy, mostdamageBy)
                        }

                        entries.add(RecentDeathEntry(cause, result.getNumber("time").toInt()))
                    } while (result.next())
                    resolvedPlayer.sendCyclopediaCharacterRecentDeaths(page, pages, entries)
                }
            }
        }
        databaseTasks.store(query, callback)
        player.addAsyncOngoingTask(PlayerAsyncTask.RecentDeaths)

        logDebug(
            "Loading death history from the player ${player.getName()} took ${benchmarkDuration()} milliseconds.",
        )
    }

    /** `PlayerCyclopedia::loadRecentKills` (player_cyclopedia.cpp:152-202). */
    fun loadRecentKills(page: Int, entriesPerPage: Int) {
        val escapedName = database.escapeString(player.getName())
        val offset = (page - 1) * entriesPerPage
        val query = recentKillsQuery(escapedName, offset, entriesPerPage)

        val playerID = player.getID()
        val callback: (CyclopediaQueryResult?, Boolean) -> Unit = { result, _ ->
            val resolvedPlayer = playersByID(playerID)
            if (resolvedPlayer != null) {
                resolvedPlayer.resetAsyncOngoingTask(PlayerAsyncTask.RecentPvPKills)
                if (result == null) {
                    resolvedPlayer.sendCyclopediaCharacterRecentPvPKills(0, 0, emptyList())
                } else {
                    val pages = queryPages(result, entriesPerPage)
                    val entries = mutableListOf<RecentPvPKillEntry>()
                    do {
                        val cause1 = result.getString("killed_by")
                        val cause2 = result.getString("mostdamage_by")
                        val name = result.getString("name")

                        var status = RecentKillStatus.JUSTIFIED
                        if (resolvedPlayer.getName() == cause1) {
                            if (result.getNumber("unjustified") == 1L) {
                                status = RecentKillStatus.UNJUSTIFIED
                            }
                        } else if (resolvedPlayer.getName() == cause2) {
                            if (result.getNumber("mostdamage_unjustified") == 1L) {
                                status = RecentKillStatus.UNJUSTIFIED
                            }
                        }

                        entries.add(RecentPvPKillEntry("Killed $name.", result.getNumber("time").toInt(), status))
                    } while (result.next())
                    resolvedPlayer.sendCyclopediaCharacterRecentPvPKills(page, pages, entries)
                }
            }
        }
        databaseTasks.store(query, callback)
        player.addAsyncOngoingTask(PlayerAsyncTask.RecentPvPKills)

        logDebug(
            "Loading recent kills from the player ${player.getName()} took ${benchmarkDuration()} milliseconds.",
        )
    }

    /** `PlayerCyclopedia::updateStoreSummary` (player_cyclopedia.cpp:204-219). */
    fun updateStoreSummary(type: SummaryCategory, amount: Int = 1, id: String = "") {
        when (type) {
            SummaryCategory.HOUSE_ITEMS, SummaryCategory.BLESSINGS -> insertValue(type, amount, id)
            SummaryCategory.ALL_BLESSINGS ->
                for (blessing in Blessings.entries) {
                    // `fmt::format("{}", blessIt)` renders the enum through the
                    // pch.hpp `format_as` fallback (pch.hpp:110-115), i.e. the
                    // underlying integer, which `std::stoll` accepts.
                    insertValue(SummaryCategory.BLESSINGS, amount, blessing.value.toString())
                }

            else -> updateAmount(type, amount)
        }
    }

    /** `PlayerCyclopedia::getAmount` (player_cyclopedia.cpp:221-224). */
    fun getAmount(type: SummaryCategory): Int {
        val value = summaryScope(type).get("amount")?.getNumber() ?: return 0
        return value.toInt() and 0xFFFF
    }

    /** `PlayerCyclopedia::updateAmount` (player_cyclopedia.cpp:226-229). */
    fun updateAmount(type: SummaryCategory, amount: Int) {
        val oldAmount = getAmount(type)
        summaryScope(type).set("amount", ValueWrapper(oldAmount + amount))
    }

    /** `PlayerCyclopedia::getResult` (player_cyclopedia.cpp:231-245). */
    fun getResult(type: SummaryCategory): Map<Int, Int> {
        val kvScope = summaryScope(type)
        val result = sortedMapOf<Int, Int>()
        for (scope in kvScope.keys()) {
            val pos = scope.indexOf('.')
            if (pos == -1) {
                logError("[getResult] Invalid key format: $scope")
                continue
            }

            val id = scope.substring(0, pos)
            val numericId = id.toLongOrNull()
            if (numericId == null) {
                logError("[getResult] Invalid key format: $scope")
                continue
            }

            val amount = kvScope.scoped(id).get("amount")?.getNumber()?.toInt() ?: 0
            result[(numericId and 0xFFFF).toInt()] = amount and 0xFFFF
        }
        return result
    }

    /** `PlayerCyclopedia::insertValue` (player_cyclopedia.cpp:247-254). */
    fun insertValue(type: SummaryCategory, amount: Int = 1, id: String = "") {
        val numericId = id.toLongOrNull()
        if (numericId == null) {
            logError("[insertValue] Invalid id: $id")
            return
        }

        val result = getResult(type)
        val oldAmount = result[(numericId and 0xFFFF).toInt()] ?: 0
        val newAmount = oldAmount + amount
        summaryScope(type).scoped(id).set("amount", ValueWrapper(newAmount))
        logDebug(
            "[insertValue] type: ${type.value}, id: $id, old amount: $oldAmount, added amount: $amount, new amount: $newAmount",
        )
    }

    /** `m_player.kv()->scoped("summary")->scoped(getSummaryKeyByType(type))`. */
    private fun summaryScope(type: SummaryCategory): KV =
        player.kv().scoped("summary").scoped(summaryKeyByType(type.value))

    /**
     * `auto pages = result->getNumber<uint32_t>("entries"); pages +=
     * entriesPerPage - 1; pages /= entriesPerPage;` (player_cyclopedia.cpp:172-174,
     * 54-56) with a zero-page guard that Canary does not have.
     */
    private fun queryPages(result: CyclopediaQueryResult, entriesPerPage: Int): Int {
        if (entriesPerPage == 0) {
            return 0
        }
        var pages = result.getNumber("entries")
        pages += entriesPerPage - 1
        pages /= entriesPerPage.toLong()
        return (pages and 0xFFFF).toInt()
    }

    /**
     * The `participants` block of `loadDeathHistory`
     * (player_cyclopedia.cpp:81-140) plus the `, ` join, returned as the new
     * cause. The `istringstream` cursor is emulated with `lines`/`index`; on a
     * malformed entry Canary `seekg`s back to the `name` position so the next
     * loop iteration re-reads the line.
     */
    private fun appendParticipants(
        cause: String,
        participants: String,
        killedBy: String,
        mostdamageBy: String,
    ): String {
        val participantCount = sortedMapOf<String, Pair<Int, String>>()
        val skipped = mutableSetOf<String>()
        val lines = participants.split('\n')
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            index++
            if (!line.startsWith("Name: ")) {
                continue
            }

            val name = trimCr(line.substring(6))
            val pos = index
            if (index < lines.size) {
                val typeLine = lines[index]
                index++
                if (typeLine.startsWith("Type: ")) {
                    val type = trimCr(typeLine.substring(6))
                    if ((name == killedBy || name == mostdamageBy) && !skipped.contains(name)) {
                        skipped.add(name)
                        continue
                    }
                    val previous = participantCount[name]
                    participantCount[name] = (previous?.first ?: 0) + 1 to type
                } else {
                    logWarn("Malformed participant entry for name '$name': expected 'Type: ' line")
                    index = pos
                }
            } else {
                logWarn("Malformed participant entry for name '$name': expected 'Type: ' line")
            }
        }

        val players = mutableListOf<String>()
        val monsters = mutableListOf<String>()
        for ((name, data) in participantCount) {
            val count = data.first
            val type = data.second
            val entry = if (count > 1) "${count}x $name" else name
            when (type) {
                "player" -> players.add(entry)
                "monster" -> monsters.add(entry)
                else -> logWarn("Unrecognized participant type '$type' for name '$name'")
            }
        }

        if (players.isNotEmpty() || monsters.isNotEmpty()) {
            return cause + ", " + (players + monsters).joinToString(", ")
        }
        return cause
    }

    /** `trim_cr` (player_cyclopedia.cpp:88-92): drop one trailing `\r`. */
    private fun trimCr(value: String): String =
        if (value.isNotEmpty() && value.last() == '\r') value.dropLast(1) else value
}
