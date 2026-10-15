package org.opentibia.canary.io.iologindata

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Virtue_t
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.GuidNameEx
import org.opentibia.canary.database.JdbcAccountRepository
import org.opentibia.canary.database.JdbcFeatureStateRepository
import org.opentibia.canary.database.JdbcPlayerDepotRepository
import org.opentibia.canary.database.JdbcPlayerItemsRepository
import org.opentibia.canary.database.JdbcPlayerKillsRepository
import org.opentibia.canary.database.JdbcPlayerRepository
import org.opentibia.canary.database.JdbcPlayerRewardsRepository
import org.opentibia.canary.database.JdbcPlayerSpellsRepository
import org.opentibia.canary.database.JdbcPlayerStashRepository
import org.opentibia.canary.database.JdbcPlayerStorageRepository
import org.opentibia.canary.database.JdbcVipRepository
import org.opentibia.canary.database.PlayerLoginRepository
import org.opentibia.canary.database.PlayerRecord
import org.opentibia.canary.kv.KVSQL
import org.opentibia.canary.kv.ValueWrapper
import org.opentibia.canary.map.Position

/**
 * Transcript tests for `IOLoginDataSave::savePlayerSystems`
 * (src/io/functions/iologindata_save_player.cpp:861-876) wired into
 * `IOLoginDataSave::savePlayerFirst` (:201):
 *
 *  - a virtue above `Virtue_t::None` is written as
 *    `ValueWrapper(static_cast<uint8_t>(virtue))` into the `spells` scope of
 *    `Player::kv()`, and a harmony above 0 as `ValueWrapper(uint8_t)` (:869,
 *    :874);
 *  - the C++ `>` guards leave the scope untouched for a cleared
 *    virtue/harmony (neither branch removes a previously persisted value);
 *  - the write happens before the `players` record write exactly like the C++
 *    call order (:201-203);
 *  - `IOLoginData::savePlayer` + the KV write-behind flush persist the scope,
 *    so the value survives a reload through the
 *    `IOLoginDataLoad::loadPlayerUpdateSystem` tail
 *    (iologindata_load_player.cpp:1020-1028).
 */
class IOLoginDataSystemsSaveTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var kv: KVSQL

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_systems_save_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(dataSource)
        insertPlayer(101L, "Systems Saver")
        kv = KVSQL(dataSource)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun newPlayer(): Player = Player(
        id = 101L,
        name = "Systems Saver",
        maxHealth = 1,
        health = 1,
        position = Position(1, 1, 7),
    ).also { it.account = Account(id = 1L) }

    private fun newLoginData(players: PlayerLoginRepository? = null): IOLoginData = IOLoginData(
        players = players ?: JdbcPlayerRepository(dataSource),
        accounts = JdbcAccountRepository(dataSource),
        vip = JdbcVipRepository(dataSource),
        storageRepository = JdbcPlayerStorageRepository(dataSource),
        spells = JdbcPlayerSpellsRepository(dataSource),
        kills = JdbcPlayerKillsRepository(dataSource),
        stash = JdbcPlayerStashRepository(dataSource),
        rewards = JdbcPlayerRewardsRepository(dataSource),
        features = JdbcFeatureStateRepository(dataSource),
        items = JdbcPlayerItemsRepository(dataSource),
        depots = JdbcPlayerDepotRepository(dataSource),
        subloads = IOLoginDataSubloads(vip = JdbcVipRepository(dataSource)),
    )

    @Test
    fun `savePlayerSystems writes the virtue and harmony into the spells scope`() {
        val player = newPlayer()
        player.kv = kv.scoped("player").scoped("101")
        player.setVirtue(Virtue_t.Justice, notifyClient = false)
        player.setHarmony(4)

        newLoginData().savePlayerSystems(player)

        val scope = player.kv.scoped("spells")
        assertThat(scope.get("virtue")).isEqualTo(ValueWrapper(2))
        assertThat(scope.get("harmony")).isEqualTo(ValueWrapper(4))
    }

    @Test
    fun `savePlayerSystems keeps a cleared virtue and harmony out of the scope`() {
        val player = newPlayer()
        player.kv = kv.scoped("player").scoped("101")
        val scope = player.kv.scoped("spells")
        scope.set("virtue", ValueWrapper(2))
        scope.set("harmony", ValueWrapper(4))

        newLoginData().savePlayerSystems(player)

        // `Virtue_t::None` and `harmony == 0` fail both C++ `>` guards, so the
        // previously persisted values stay untouched.
        assertThat(scope.get("virtue")).isEqualTo(ValueWrapper(2))
        assertThat(scope.get("harmony")).isEqualTo(ValueWrapper(4))
    }

    @Test
    fun `savePlayerFirst saves the systems before the player record write`() {
        val player = newPlayer()
        player.kv = kv.scoped("player").scoped("101")
        player.setVirtue(Virtue_t.Harmony, notifyClient = false)
        player.setHarmony(1)

        assertThat(newLoginData(players = FailingPlayerRepository).savePlayerFirst(player)).isFalse()

        val scope = player.kv.scoped("spells")
        assertThat(scope.get("virtue")).isEqualTo(ValueWrapper(1))
        assertThat(scope.get("harmony")).isEqualTo(ValueWrapper(1))
    }

    @Test
    fun `savePlayer persists the systems and the login load restores the virtue and harmony`() {
        val player = newPlayer()
        player.kv = kv.scoped("player").scoped("101")
        player.setVirtue(Virtue_t.Justice, notifyClient = false)
        player.setHarmony(4)

        val loginData = newLoginData()
        assertThat(loginData.savePlayer(player)).isTrue()
        assertThat(kv.saveAll()).isTrue()

        // A fresh store can only resolve the keys through `kv_store` (V16).
        val reloaded = newPlayer()
        reloaded.kv = KVSQL(dataSource).scoped("player").scoped("101")
        assertThat(loginData.loadPlayerById(reloaded, 101L, disableIrrelevantInfo = false)).isTrue()

        assertThat(player.kv.scoped("spells").get("virtue")).isEqualTo(ValueWrapper(2))
        assertThat(player.kv.scoped("spells").get("harmony")).isEqualTo(ValueWrapper(4))

        assertThat(reloaded.getVirtue()).isEqualTo(Virtue_t.Justice)
        assertThat(reloaded.getHarmony()).isEqualTo(4)
    }

    @Test
    fun `a login without a persisted system state keeps the defaults`() {
        val player = newPlayer()
        player.kv = kv.scoped("player").scoped("101")

        assertThat(newLoginData().loadPlayerById(player, 101L, disableIrrelevantInfo = false)).isTrue()

        assertThat(player.getVirtue()).isEqualTo(Virtue_t.None)
        assertThat(player.getHarmony()).isZero()
    }

    private fun insertPlayer(id: Long, name: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO players (id, name, account_id) VALUES (?, ?, 1)",
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.setString(2, name)
                stmt.executeUpdate()
            }
        }
    }

    /** `PlayerLoginRepository` double whose record write fails like a broken `players` update. */
    private object FailingPlayerRepository : PlayerLoginRepository {
        override fun loadRecordById(id: Long): PlayerRecord? = null

        override fun loadRecordByName(name: String): PlayerRecord? = null

        override fun getNameByGuid(guid: Long): String = ""

        override fun getGuidByName(name: String): Long = 0L

        override fun getGuidByNameEx(name: String): GuidNameEx? = null

        override fun formatPlayerName(name: String): String? = null

        override fun increaseBankBalance(guid: Long, amount: Long): Boolean = false

        override fun savePlayerRecord(player: Player): Boolean = false
    }
}
