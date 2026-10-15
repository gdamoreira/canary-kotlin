package org.opentibia.canary.io.iologindata

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.creatures.Player
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
 * Transcript tests for `IOLoginDataSave::savePlayerExivaRestrictions`
 * (src/io/functions/iologindata_save_player.cpp:878-907) wired into
 * `IOLoginDataSave::savePlayerFirst` (:201-203):
 *
 *  - the six flags are written as `ValueWrapper(bool)` exactly like the C++
 *    implicit `ValueWrapper(bool)` constructor;
 *  - both whitelists are written as arrays of `static_cast<int>(guid)`
 *    elements;
 *  - the write happens before the `players` record write, so a failed record
 *    write still leaves the scope in `Player::kv()`;
 *  - `IOLoginData::savePlayer` + the write-behind flush persist the scope, so
 *    the `exiva-restrictions` keys survive a reload through
 *    `IOLoginDataLoad::loadPlayerExivaRestrictions`
 *    (iologindata_load_player.cpp:1042-1086).
 *
 * The round trip pins the documented Canary `ValueWrapper::getNumber()`
 * boolean behavior (value_wrapper.hpp:51-58): the persisted `true` flags reload
 * as `false`, while the whitelist arrays survive exactly.
 */
class IOLoginDataExivaSaveTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var kv: KVSQL

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_exiva_save_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(dataSource)
        insertPlayer(101L, "Exiva Saver")
        kv = KVSQL(dataSource)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun newPlayer(): Player = Player(
        id = 101L,
        name = "Exiva Saver",
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
    fun `savePlayerFirst writes the canonical exiva flags and whitelists into the player kv`() {
        val player = newPlayer()
        player.kv = kv.scoped("player").scoped("101")
        val restrictions = player.getExivaRestrictions()
        restrictions.allowAll = true
        restrictions.allowOwnGuild = false
        restrictions.allowOwnParty = false
        restrictions.allowVipList = true
        restrictions.allowPlayerWhitelist = false
        restrictions.allowGuildWhitelist = true
        restrictions.playerWhitelist.addAll(listOf(42L, 43L))
        restrictions.guildWhitelist.add(7L)

        assertThat(newLoginData().savePlayerFirst(player)).isTrue()

        val scope = player.kv.scoped("exiva-restrictions")
        assertThat(scope.get("allowAll")).isEqualTo(ValueWrapper(true))
        assertThat(scope.get("allowOwnGuild")).isEqualTo(ValueWrapper(false))
        assertThat(scope.get("allowOwnParty")).isEqualTo(ValueWrapper(false))
        assertThat(scope.get("allowVipList")).isEqualTo(ValueWrapper(true))
        assertThat(scope.get("allowPlayerWhitelist")).isEqualTo(ValueWrapper(false))
        assertThat(scope.get("allowGuildWhitelist")).isEqualTo(ValueWrapper(true))
        assertThat(scope.get("playerWhitelist")!!.getArray().map { it.getInt() }).containsExactly(42, 43)
        assertThat(scope.get("guildWhitelist")!!.getArray().map { it.getInt() }).containsExactly(7)
    }

    @Test
    fun `savePlayerFirst casts a whitelist guid above Int MAX like static_cast int`() {
        val player = newPlayer()
        player.kv = kv.scoped("player").scoped("101")
        val restrictions = player.getExivaRestrictions()
        restrictions.playerWhitelist.add(4_294_967_295L)
        restrictions.guildWhitelist.add(4_294_967_296L)

        assertThat(newLoginData().savePlayerFirst(player)).isTrue()

        val scope = player.kv.scoped("exiva-restrictions")
        assertThat(scope.get("playerWhitelist")!!.getArray().single().getInt()).isEqualTo(-1)
        assertThat(scope.get("guildWhitelist")!!.getArray().single().getInt()).isEqualTo(0)
    }

    @Test
    fun `savePlayerFirst writes the exiva scope before the player record write`() {
        val player = newPlayer()
        player.kv = kv.scoped("player").scoped("101")
        player.getExivaRestrictions().allowAll = true

        val failing = newLoginData(players = FailingPlayerRepository)

        assertThat(failing.savePlayerFirst(player)).isFalse()
        assertThat(player.kv.scoped("exiva-restrictions").get("allowAll")).isEqualTo(ValueWrapper(true))
    }

    @Test
    fun `savePlayer persists the exiva scope and the reload restores the whitelists`() {
        val player = newPlayer()
        player.kv = kv.scoped("player").scoped("101")
        val restrictions = player.getExivaRestrictions()
        restrictions.allowAll = true
        restrictions.allowOwnParty = false
        restrictions.playerWhitelist.addAll(listOf(42L, 43L))
        restrictions.guildWhitelist.add(7L)

        val loginData = newLoginData()
        assertThat(loginData.savePlayer(player)).isTrue()
        assertThat(kv.saveAll()).isTrue()

        // A fresh store can only resolve the keys through `kv_store` (V16).
        val reloaded = newPlayer()
        reloaded.kv = KVSQL(dataSource).scoped("player").scoped("101")
        assertThat(loginData.loadPlayerById(reloaded, 101L, disableIrrelevantInfo = false)).isTrue()

        val persistedScope = reloaded.kv.scoped("exiva-restrictions")
        assertThat(persistedScope.get("allowAll")).isEqualTo(ValueWrapper(true))
        assertThat(persistedScope.get("allowOwnParty")).isEqualTo(ValueWrapper(false))
        assertThat(persistedScope.get("playerWhitelist")!!.getArray().map { it.getInt() }).containsExactly(42, 43)
        assertThat(persistedScope.get("guildWhitelist")!!.getArray().map { it.getInt() }).containsExactly(7)

        val loaded = reloaded.getExivaRestrictions()
        assertThat(loaded.playerWhitelist).containsExactly(42L, 43L)
        assertThat(loaded.guildWhitelist).containsExactly(7L)
        // `ValueWrapper::getNumber()` (value_wrapper.hpp:51-58) returns 0.0 for
        // the boolean variant the saver writes, so every persisted flag reloads
        // as `false` exactly like the C++ loader.
        assertThat(loaded.allowAll).isFalse()
        assertThat(loaded.allowOwnGuild).isFalse()
        assertThat(loaded.allowOwnParty).isFalse()
        assertThat(loaded.allowVipList).isFalse()
        assertThat(loaded.allowPlayerWhitelist).isFalse()
        assertThat(loaded.allowGuildWhitelist).isFalse()
    }

    @Test
    fun `savePlayer persists a whitelist guid above Int MAX and the reload wraps it back to uint32`() {
        val player = newPlayer()
        player.kv = kv.scoped("player").scoped("101")
        val restrictions = player.getExivaRestrictions()
        restrictions.playerWhitelist.add(4_294_967_295L)
        restrictions.guildWhitelist.add(4_294_967_295L)

        val loginData = newLoginData()
        assertThat(loginData.savePlayer(player)).isTrue()
        assertThat(kv.saveAll()).isTrue()

        val reloaded = newPlayer()
        reloaded.kv = KVSQL(dataSource).scoped("player").scoped("101")
        assertThat(loginData.loadPlayerById(reloaded, 101L, disableIrrelevantInfo = false)).isTrue()

        val loaded = reloaded.getExivaRestrictions()
        // The persisted `static_cast<int>(guid)` is `-1`; the C++ loader pushes
        // it into `std::vector<uint32_t>`, so the value wraps back to
        // 4294967295 (uint32 semantics) instead of sign-extending to -1.
        assertThat(loaded.playerWhitelist).containsExactly(4_294_967_295L)
        assertThat(loaded.guildWhitelist).containsExactly(4_294_967_295L)
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
