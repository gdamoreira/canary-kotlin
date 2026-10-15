package org.opentibia.canary.io.iologindata

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.database.DatabaseConnectionFactory
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
import org.opentibia.canary.kv.KVSQL
import org.opentibia.canary.kv.ValueWrapper
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the wave-64 composition of the login VIP / exiva
 * sub-loads:
 *
 *  - `PlayerVIP m_playerVIP` (player.hpp:1965, accessor `Player::vip()`
 *    player.hpp:1529-1530) is composed into the Kotlin `Player` through
 *    `PlayerFeatures.vip`, and `Player::kv()` (player.cpp:1398-1400, the
 *    `g_kv().scoped("player").scoped(guid)` root) is the full
 *    `org.opentibia.canary.kv.KV` `ValueWrapper` store.
 *  - `IOLoginDataLoad::loadPlayerVip` (iologindata_load_player.cpp:770-806)
 *    therefore resolves `player->vip()` and fills the account VIP list from
 *    `account_viplist`, `account_vipgroups` and `account_vipgrouplist` at
 *    login.
 *  - `IOLoginDataLoad::loadPlayerExivaRestrictions`
 *    (iologindata_load_player.cpp:1042-1086) resolves `player->kv()` and
 *    copies the six flags and both whitelists of the
 *    `player.<guid>.exiva-restrictions` scope into
 *    `Player::getExivaRestrictions()`.
 *
 * The fixture exercises `IOLoginData::loadPlayerById` over the real H2 schema
 * with the default sub-load seams, so only the composed `Player` state is
 * supplied.
 */
class IOLoginDataVipExivaCompositionTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var kv: KVSQL

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_vip_exiva_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(dataSource)
        insertPlayer(101L, "Composed Hero")
        kv = KVSQL(dataSource)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun newPlayer(): Player = Player(
        id = 101L,
        name = "Composed Hero",
        maxHealth = 1,
        health = 1,
        position = Position(1, 1, 7),
    ).also { it.account = Account(id = 1L) }

    private fun newLoginData(): IOLoginData = IOLoginData(
        players = JdbcPlayerRepository(dataSource),
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
    fun `loadPlayerById fills the composed vip list and its groups at login`() {
        insertPlayer(102L, "Composed Mate")
        val repository = JdbcVipRepository(dataSource)
        repository.addVipEntry(1L, 102L, "buddy nick", 3, true)
        repository.addVipGroupEntry(1, 1L, "Enemies", false)
        repository.addGuidVipGroupEntry(1, 1L, 102L)
        val player = newPlayer()

        assertThat(newLoginData().loadPlayerById(player, 101L, disableIrrelevantInfo = false)).isTrue()

        assertThat(player.features.vip.exists(102L)).isTrue()
        assertThat(player.features.vip.getVipEntries().map { it.guid }).containsExactly(102L)
        val group = player.features.vip.getGroupByID(1)
        assertThat(group).isNotNull
        assertThat(group!!.name).isEqualTo("Enemies")
        assertThat(group.vipGroupGuids).containsExactly(102L)
    }

    @Test
    fun `loadPlayerById keeps the persisted vip nick and name readable at login`() {
        insertPlayer(102L, "Composed Mate")
        val repository = JdbcVipRepository(dataSource)
        repository.addVipEntry(1L, 102L, "buddy nick", 3, true)
        val player = newPlayer()

        assertThat(newLoginData().loadPlayerById(player, 101L, disableIrrelevantInfo = false)).isTrue()

        val entry = newLoginData().getVipEntries(1L).single()
        assertThat(entry.guid).isEqualTo(102L)
        assertThat(entry.name).isEqualTo("Composed Mate")
        assertThat(entry.description).isEqualTo("buddy nick")
        assertThat(entry.icon).isEqualTo(3)
        assertThat(entry.notify).isTrue()
    }

    @Test
    fun `loadPlayerById loads the exiva restrictions from the player kv at login`() {
        val player = newPlayer()
        player.kv = kv.scoped("player").scoped("101")
        val scope = player.kv.scoped("exiva-restrictions")
        scope.set("allowAll", ValueWrapper(1))
        scope.set("allowOwnGuild", ValueWrapper(0))
        scope.set("allowOwnParty", ValueWrapper(1))
        scope.set("allowVipList", ValueWrapper(0))
        scope.set("allowPlayerWhitelist", ValueWrapper(1))
        scope.set("allowGuildWhitelist", ValueWrapper(0))
        scope.set("playerWhitelist", ValueWrapper(listOf(ValueWrapper(42), ValueWrapper(43))))
        scope.set("guildWhitelist", ValueWrapper(listOf(ValueWrapper(7))))

        assertThat(newLoginData().loadPlayerById(player, 101L, disableIrrelevantInfo = false)).isTrue()

        val restrictions = player.getExivaRestrictions()
        assertThat(restrictions.allowAll).isTrue()
        assertThat(restrictions.allowOwnGuild).isFalse()
        assertThat(restrictions.allowOwnParty).isTrue()
        assertThat(restrictions.allowVipList).isFalse()
        assertThat(restrictions.allowPlayerWhitelist).isTrue()
        assertThat(restrictions.allowGuildWhitelist).isFalse()
        assertThat(restrictions.playerWhitelist).containsExactly(42L, 43L)
        assertThat(restrictions.guildWhitelist).containsExactly(7L)
    }

    @Test
    fun `loadPlayerById mirrors the Canary getNumber boolean behavior for a persisted true`() {
        val player = newPlayer()
        player.kv = kv.scoped("player").scoped("101")
        player.kv.scoped("exiva-restrictions").set("allowAll", ValueWrapper(true))

        assertThat(newLoginData().loadPlayerById(player, 101L, disableIrrelevantInfo = false)).isTrue()

        // `ValueWrapper::getNumber()` (value_wrapper.hpp:51-58) returns 0.0 for
        // the boolean variant Canary's saver writes, so a persisted `true`
        // loads as `false` exactly like the C++ loader.
        assertThat(player.getExivaRestrictions().allowAll).isFalse()
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
}
