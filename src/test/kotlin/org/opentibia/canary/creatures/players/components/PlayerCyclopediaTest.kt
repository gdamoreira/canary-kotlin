package org.opentibia.canary.creatures.players.components

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for the Canary `PlayerCyclopedia` component.
 *
 * Source of truth: ../.projects/canary
 *  - src/creatures/players/components/player_cyclopedia.hpp:15-43
 *  - src/creatures/players/components/player_cyclopedia.cpp:19-254
 *  - src/enums/player_cyclopedia.hpp:40-48 (`Summary_t`)
 *  - src/enums/player_blessings.hpp:16-25 (`Blessings`)
 *  - src/game/game.cpp:735-742 (`m_summaryCategories`),
 *    12596-12598 (`getSummaryKeyByType`)
 *  - src/creatures/creatures_definitions.hpp:1640-1658 (`RecentDeathEntry`,
 *    `RecentPvPKillEntry`), 856-858 (`PlayerAsyncTask_*`)
 *  - src/game/game_definitions.hpp:84-88 (recent-kill statuses)
 *
 * `g_game()`, `g_database()`/`g_databaseTasks()` and the protocol senders are
 * constructor-injected seams (AGENTS.md 1.1).
 */
class PlayerCyclopediaTest {

    // ---- summary amounts -------------------------------------------------------

    @Test
    fun `getSummary reads prey cards instant rewards and hirelings amounts`() {
        val f = fixture()
        f.kv.scoped("summary").scoped("prey-cards").set("amount", 1L)
        f.kv.scoped("summary").scoped("instant-rewards").set("amount", 2L)
        f.kv.scoped("summary").scoped("hirelings").set("amount", 3L)

        assertThat(f.cyclopedia.getSummary()).isEqualTo(Summary(1, 2, 3))
    }

    @Test
    fun `getAmount narrows the stored value to uint16`() {
        val f = fixture()
        f.kv.scoped("summary").scoped("prey-cards").set("amount", 70_000L)

        assertThat(f.cyclopedia.getAmount(SummaryCategory.PREY_CARDS)).isEqualTo(4_464)
    }

    @Test
    fun `updateAmount accumulates on top of the old amount`() {
        val f = fixture()

        f.cyclopedia.updateAmount(SummaryCategory.PREY_CARDS, 5)
        f.cyclopedia.updateAmount(SummaryCategory.PREY_CARDS, 5)

        assertThat(f.cyclopedia.getAmount(SummaryCategory.PREY_CARDS)).isEqualTo(10)
    }

    // ---- store summary ---------------------------------------------------------

    @Test
    fun `updateStoreSummary routes house items through insertValue by id`() {
        val f = fixture()

        f.cyclopedia.updateStoreSummary(SummaryCategory.HOUSE_ITEMS, 2, "123")
        f.cyclopedia.updateStoreSummary(SummaryCategory.HOUSE_ITEMS, 3, "123")

        assertThat(f.kv.scoped("summary").scoped("house-items").scoped("123").get("amount")).isEqualTo(5L)
    }

    @Test
    fun `updateStoreSummary gives every blessing the selected amount`() {
        val f = fixture()

        f.cyclopedia.updateStoreSummary(SummaryCategory.ALL_BLESSINGS, 3)

        val blessings = f.kv.scoped("summary").scoped("blessings")
        assertThat(Blessings.entries).hasSize(8)
        for (blessing in Blessings.entries) {
            assertThat(blessings.scoped(blessing.value.toString()).get("amount")).isEqualTo(3L)
        }
    }

    @Test
    fun `updateStoreSummary routes the default types through updateAmount`() {
        val f = fixture()

        f.cyclopedia.updateStoreSummary(SummaryCategory.BOOSTS, 7)

        assertThat(f.cyclopedia.getAmount(SummaryCategory.BOOSTS)).isEqualTo(7)
    }

    @Test
    fun `getResult reads the ids and amounts below the summary scope`() {
        val f = fixture()
        val scope = f.kv.scoped("summary").scoped("house-items")
        scope.scoped("123").set("amount", 4L)
        scope.scoped("45").set("amount", 9L)
        scope.set("broken", 1L)

        assertThat(f.cyclopedia.getResult(SummaryCategory.HOUSE_ITEMS)).containsExactlyInAnyOrderEntriesOf(
            mapOf(123 to 4, 45 to 9),
        )
        assertThat(f.errorLogs).containsExactly("[getResult] Invalid key format: broken")
    }

    @Test
    fun `insertValue accumulates by id`() {
        val f = fixture()

        f.cyclopedia.insertValue(SummaryCategory.HOUSE_ITEMS, 2, "123")
        f.cyclopedia.insertValue(SummaryCategory.HOUSE_ITEMS, 3, "123")

        assertThat(f.kv.scoped("summary").scoped("house-items").scoped("123").get("amount")).isEqualTo(5L)
    }

    // ---- loadSummaryData -------------------------------------------------------

    @Test
    fun `loadSummaryData stores the hireling count when no amount exists`() {
        val f = fixture()
        f.database.storeQueryResult = FakeQueryResult(listOf(mapOf("count" to 12)))

        f.cyclopedia.loadSummaryData()

        assertThat(f.database.queries).containsExactly(
            "SELECT COUNT(*) as `count` FROM `player_hirelings` WHERE `player_id` = 42",
        )
        assertThat(f.kv.scoped("summary").scoped("hirelings").get("amount")).isEqualTo(12L)
    }

    @Test
    fun `loadSummaryData keeps an existing amount`() {
        val f = fixture()
        f.kv.scoped("summary").scoped("hirelings").set("amount", 7L)
        f.database.storeQueryResult = FakeQueryResult(listOf(mapOf("count" to 12)))

        f.cyclopedia.loadSummaryData()

        assertThat(f.kv.scoped("summary").scoped("hirelings").get("amount")).isEqualTo(7L)
    }

    @Test
    fun `loadSummaryData narrows the count to int16`() {
        val f = fixture()
        f.database.storeQueryResult = FakeQueryResult(listOf(mapOf("count" to 70_000)))

        f.cyclopedia.loadSummaryData()

        assertThat(f.kv.scoped("summary").scoped("hirelings").get("amount")).isEqualTo(4_464L)
    }

    // ---- loadDeathHistory ------------------------------------------------------

    @Test
    fun `loadDeathHistory builds causes and pages and dispatches them`() {
        val f = fixture()
        f.tasks.callbackOnStore = true
        f.tasks.result = FakeQueryResult(
            listOf(
                mapOf(
                    "time" to 111,
                    "level" to 100,
                    "killed_by" to "Dragon",
                    "mostdamage_by" to "Dragon",
                    "participants" to "",
                    "entries" to 5,
                ),
                mapOf(
                    "time" to 222,
                    "level" to 50,
                    "killed_by" to "PlayerA",
                    "mostdamage_by" to "PlayerB",
                    "participants" to
                        "Name: PlayerC\r\nType: player\r\nName: PlayerA\r\nType: player\r\n" +
                        "Name: MonsterX\r\nType: monster\r\nName: PlayerC\r\nType: player\r\n",
                    "entries" to 5,
                ),
            ),
        )

        f.cyclopedia.loadDeathHistory(1, 2)

        assertThat(f.player.deathSends).containsExactly(
            DeathSend(
                page = 1,
                pages = 3,
                entries = listOf(
                    RecentDeathEntry("Died at level 100 by Dragon <b>(soloed)</b>", 111),
                    RecentDeathEntry("Died at level 50 by PlayerA and by PlayerB, 2x PlayerC, MonsterX", 222),
                ),
            ),
        )
    }

    @Test
    fun `loadDeathHistory sends an empty page when the query fails`() {
        val f = fixture()
        f.tasks.callbackOnStore = true
        f.tasks.result = null

        f.cyclopedia.loadDeathHistory(2, 5)

        assertThat(f.player.deathSends).containsExactly(DeathSend(0, 0, emptyList()))
    }

    @Test
    fun `loadDeathHistory ignores the results when the player is gone`() {
        val f = fixture()
        f.tasks.callbackOnStore = true
        f.tasks.result = FakeQueryResult(listOf(mapOf("time" to 1, "level" to 1, "entries" to 1)))
        f.playersByID = { null }

        f.cyclopedia.loadDeathHistory(1, 5)

        assertThat(f.player.deathSends).isEmpty()
    }

    @Test
    fun `loadDeathHistory registers the async task and records the query`() {
        val f = fixture()

        f.cyclopedia.loadDeathHistory(1, 10)

        assertThat(f.player.addedAsyncTasks).containsExactly(PlayerAsyncTask.RecentDeaths)
        assertThat(f.tasks.queries).hasSize(1)
        assertThat(f.tasks.queries.single())
            .contains("FROM `player_deaths` WHERE `player_id` = 42")
            .contains("LIMIT 0, 10")
    }

    @Test
    fun `loadDeathHistory resets the async task on the resolved player`() {
        val f = fixture()

        f.cyclopedia.loadDeathHistory(1, 10)
        f.tasks.invoke()

        assertThat(f.player.resetAsyncTasks).containsExactly(PlayerAsyncTask.RecentDeaths)
    }

    @Test
    fun `loadDeathHistory warns and re-reads a malformed participant entry`() {
        val f = fixture()
        f.tasks.callbackOnStore = true
        f.tasks.result = FakeQueryResult(
            listOf(
                mapOf(
                    "time" to 1,
                    "level" to 1,
                    "killed_by" to "",
                    "mostdamage_by" to "",
                    "participants" to "Name: Foo\nGarbage\n",
                    "entries" to 1,
                ),
            ),
        )

        f.cyclopedia.loadDeathHistory(1, 10)

        assertThat(f.warnLogs)
            .containsExactly("Malformed participant entry for name 'Foo': expected 'Type: ' line")
        assertThat(f.player.deathSends.single().entries.single().cause).isEqualTo("Died at level 1")
    }

    // ---- loadRecentKills -------------------------------------------------------

    @Test
    fun `loadRecentKills marks unjustified statuses and describes each kill`() {
        val f = fixture()
        f.player.playerName = "Hero"
        f.tasks.callbackOnStore = true
        f.tasks.result = FakeQueryResult(
            listOf(
                mapOf(
                    "time" to 1,
                    "killed_by" to "Hero",
                    "mostdamage_by" to "",
                    "unjustified" to 1,
                    "mostdamage_unjustified" to 0,
                    "name" to "Victim1",
                    "entries" to 3,
                ),
                mapOf(
                    "time" to 2,
                    "killed_by" to "Other",
                    "mostdamage_by" to "Hero",
                    "unjustified" to 0,
                    "mostdamage_unjustified" to 1,
                    "name" to "Victim2",
                    "entries" to 3,
                ),
                mapOf(
                    "time" to 3,
                    "killed_by" to "Other",
                    "mostdamage_by" to "Hero",
                    "unjustified" to 0,
                    "mostdamage_unjustified" to 0,
                    "name" to "Victim3",
                    "entries" to 3,
                ),
            ),
        )

        f.cyclopedia.loadRecentKills(1, 2)

        assertThat(f.player.pvpKillSends).containsExactly(
            PvpKillSend(
                page = 1,
                pages = 2,
                entries = listOf(
                    RecentPvPKillEntry("Killed Victim1.", 1, RecentKillStatus.UNJUSTIFIED),
                    RecentPvPKillEntry("Killed Victim2.", 2, RecentKillStatus.UNJUSTIFIED),
                    RecentPvPKillEntry("Killed Victim3.", 3, RecentKillStatus.JUSTIFIED),
                ),
            ),
        )
    }

    @Test
    fun `loadRecentKills escapes the player name in the query`() {
        val f = fixture()
        f.player.playerName = "O'Hero"

        f.cyclopedia.loadRecentKills(1, 10)

        assertThat(f.database.escapeCalls).containsExactly("O'Hero")
        assertThat(f.tasks.queries.single())
            .contains("`killed_by` = O\\'Hero")
            .contains("LIMIT 0, 10")
        assertThat(f.player.addedAsyncTasks).containsExactly(PlayerAsyncTask.RecentPvPKills)
    }

    // ---- fixture ---------------------------------------------------------------

    private class Fixture {
        val player = FakeCyclopediaPlayer()
        val kv = InMemoryKV()
        val database = FakeDatabase()
        val tasks = RecordingDatabaseTasks()
        val errorLogs = mutableListOf<String>()
        val warnLogs = mutableListOf<String>()
        var playersByID: (Int) -> CyclopediaPlayer? = { player }

        val cyclopedia = PlayerCyclopedia(
            player = player,
            database = database,
            databaseTasks = tasks,
            playersByID = { playersByID(it) },
            logError = { errorLogs.add(it) },
            logWarn = { warnLogs.add(it) },
        )

        init {
            player.kv = kv
        }
    }

    private fun fixture(): Fixture = Fixture()

    private class FakeCyclopediaPlayer : CyclopediaPlayer {
        var playerName = "Knight"
        var kv: KV = InMemoryKV()
        val deathSends = mutableListOf<DeathSend>()
        val pvpKillSends = mutableListOf<PvpKillSend>()
        val addedAsyncTasks = mutableListOf<PlayerAsyncTask>()
        val resetAsyncTasks = mutableListOf<PlayerAsyncTask>()

        override fun getGUID(): Long = 42

        override fun getID(): Int = 7

        override fun getName(): String = playerName

        override fun kv(): KV = kv

        override fun addAsyncOngoingTask(task: PlayerAsyncTask) {
            addedAsyncTasks.add(task)
        }

        override fun resetAsyncOngoingTask(task: PlayerAsyncTask) {
            resetAsyncTasks.add(task)
        }

        override fun sendCyclopediaCharacterRecentDeaths(page: Int, pages: Int, entries: List<RecentDeathEntry>) {
            deathSends.add(DeathSend(page, pages, entries))
        }

        override fun sendCyclopediaCharacterRecentPvPKills(page: Int, pages: Int, entries: List<RecentPvPKillEntry>) {
            pvpKillSends.add(PvpKillSend(page, pages, entries))
        }
    }

    private data class DeathSend(val page: Int, val pages: Int, val entries: List<RecentDeathEntry>)

    private data class PvpKillSend(val page: Int, val pages: Int, val entries: List<RecentPvPKillEntry>)

    private class FakeDatabase : CyclopediaDatabase {
        var storeQueryResult: CyclopediaQueryResult? = null
        val queries = mutableListOf<String>()
        val escapeCalls = mutableListOf<String>()

        override fun escapeString(value: String): String {
            escapeCalls.add(value)
            return value.replace("'", "\\'")
        }

        override fun storeQuery(query: String): CyclopediaQueryResult? {
            queries.add(query)
            return storeQueryResult
        }
    }

    private open class RecordingDatabaseTasks : CyclopediaDatabaseTasks {
        val queries = mutableListOf<String>()
        private var callback: ((CyclopediaQueryResult?, Boolean) -> Unit)? = null

        /** Invoke [store]'s callback inline, mirroring an immediate database answer. */
        var callbackOnStore: Boolean = false

        var result: CyclopediaQueryResult? = null

        override fun store(query: String, callback: (CyclopediaQueryResult?, Boolean) -> Unit) {
            queries.add(query)
            this.callback = callback
            if (callbackOnStore) {
                callback(result, true)
            }
        }

        fun invoke() {
            callback?.invoke(result, true)
        }
    }

    private class FakeQueryResult(
        private val rows: List<Map<String, Any>>,
    ) : CyclopediaQueryResult {
        private var cursor = 0

        override fun getNumber(name: String): Long = (rows[cursor].getValue(name) as Number).toLong()

        override fun getString(name: String): String = rows[cursor].getValue(name) as String

        override fun countResults(): Int = rows.size

        override fun next(): Boolean {
            cursor++
            return cursor < rows.size
        }
    }
}
