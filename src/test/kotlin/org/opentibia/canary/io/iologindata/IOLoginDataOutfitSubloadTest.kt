package org.opentibia.canary.io.iologindata

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.JdbcAccountRepository
import org.opentibia.canary.database.JdbcPlayerDepotRepository
import org.opentibia.canary.database.JdbcPlayerItemsRepository
import org.opentibia.canary.database.JdbcPlayerKillsRepository
import org.opentibia.canary.database.JdbcPlayerRepository
import org.opentibia.canary.database.JdbcPlayerRewardsRepository
import org.opentibia.canary.database.JdbcPlayerSpellsRepository
import org.opentibia.canary.database.JdbcPlayerStashRepository
import org.opentibia.canary.database.JdbcPlayerStorageRepository
import org.opentibia.canary.database.JdbcVipRepository
import org.opentibia.canary.map.Position

/**
 * Transcript tests for `IOLoginDataLoad::loadPlayerDefaultOutfit`
 * (src/io/functions/iologindata_load_player.cpp:291-317) driven through the full
 * `IOLoginData::loadPlayerById` path (src/io/iologindata.cpp:81-185).
 *
 * The persisted `players` look* columns are re-read by
 * [DbPlayerSubloadRepository] (the port's `PlayerRecord` does not carry them),
 * then applied to `player->defaultOutfit` and copied into `player->currentOutfit`
 * exactly like the C++ sub-load.
 */
class IOLoginDataOutfitSubloadTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var loginData: IOLoginData

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_outfit_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(dataSource)
        insertPlayer(
            id = 101L,
            name = "Outfit Hero",
            lookType = 137,
            lookHead = 1,
            lookBody = 2,
            lookLegs = 3,
            lookFeet = 4,
            lookAddons = 5,
        )

        loginData = newLoginData()
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `loadPlayerById applies the persisted outfit to the default and current outfit`() {
        val player = newPlayer()

        assertThat(loginData.loadPlayerById(player, 101L)).isTrue()

        val defaultOutfit = player.getDefaultOutfit()
        assertThat(defaultOutfit.lookType).isEqualTo(137)
        assertThat(defaultOutfit.lookHead).isEqualTo(1)
        assertThat(defaultOutfit.lookBody).isEqualTo(2)
        assertThat(defaultOutfit.lookLegs).isEqualTo(3)
        assertThat(defaultOutfit.lookFeet).isEqualTo(4)
        assertThat(defaultOutfit.lookAddons).isEqualTo(5)
        // `player->currentOutfit = player->defaultOutfit` (iologindata_load_player.cpp:316).
        assertThat(player.getCurrentOutfit()).isEqualTo(defaultOutfit)
    }

    @Test
    fun `loadPlayerById applies the persisted mount and familiar look columns`() {
        insertPlayer(
            id = 102L,
            name = "Mounted Hero",
            lookType = 137,
            lookHead = 1,
            lookBody = 2,
            lookLegs = 3,
            lookFeet = 4,
            lookAddons = 5,
            lookMountHead = 6,
            lookMountBody = 7,
            lookMountLegs = 8,
            lookMountFeet = 9,
            lookFamiliarsType = 410,
        )
        val player = newPlayer(id = 102L, name = "Mounted Hero")

        assertThat(loginData.loadPlayerById(player, 102L)).isTrue()

        val defaultOutfit = player.getDefaultOutfit()
        assertThat(defaultOutfit.lookMountHead).isEqualTo(6)
        assertThat(defaultOutfit.lookMountBody).isEqualTo(7)
        assertThat(defaultOutfit.lookMountLegs).isEqualTo(8)
        assertThat(defaultOutfit.lookMountFeet).isEqualTo(9)
        assertThat(defaultOutfit.lookFamiliarsType).isEqualTo(410)
        assertThat(player.getCurrentOutfit()).isEqualTo(defaultOutfit)
    }

    @Test
    fun `loadPlayerById blocks an unregistered familiar type before the current outfit copy`() {
        insertPlayer(
            id = 103L,
            name = "Familiar Hero",
            lookType = 137,
            lookHead = 1,
            lookBody = 2,
            lookLegs = 3,
            lookFeet = 4,
            lookAddons = 5,
            lookMountHead = 6,
            lookFamiliarsType = 999,
        )
        loginData = newLoginData(warnUnsafeScripts = true, isLookTypeRegistered = { it == 137 })
        val player = newPlayer(id = 103L, name = "Familiar Hero")

        assertThat(loginData.loadPlayerById(player, 103L)).isTrue()

        // The C++ loader mutates `defaultOutfit` in place and returns before
        // `player->currentOutfit = player->defaultOutfit`
        // (iologindata_load_player.cpp:312-317).
        val defaultOutfit = player.getDefaultOutfit()
        assertThat(defaultOutfit.lookFamiliarsType).isEqualTo(999)
        assertThat(defaultOutfit.lookMountHead).isEqualTo(6)
        assertThat(player.getCurrentOutfit().lookType).isZero()
    }

    @Test
    fun `loadPlayerById blocks an unregistered look type before the remaining look fields`() {
        loginData = newLoginData(warnUnsafeScripts = true, isLookTypeRegistered = { false })
        val player = newPlayer()

        assertThat(loginData.loadPlayerById(player, 101L)).isTrue()

        // The C++ assigns lookType before the guard and returns with only that
        // field committed (iologindata_load_player.cpp:296-301).
        val defaultOutfit = player.getDefaultOutfit()
        assertThat(defaultOutfit.lookType).isEqualTo(137)
        assertThat(defaultOutfit.lookHead).isZero()
        assertThat(defaultOutfit.lookBody).isZero()
        assertThat(player.getCurrentOutfit().lookType).isZero()
    }

    @Test
    fun `loadPlayerById applies the outfit when the look type is registered`() {
        loginData = newLoginData(warnUnsafeScripts = true, isLookTypeRegistered = { it == 137 })
        val player = newPlayer()

        assertThat(loginData.loadPlayerById(player, 101L)).isTrue()

        assertThat(player.getDefaultOutfit().lookType).isEqualTo(137)
        assertThat(player.getDefaultOutfit().lookHead).isEqualTo(1)
        assertThat(player.getCurrentOutfit().lookAddons).isEqualTo(5)
    }

    private fun newLoginData(
        warnUnsafeScripts: Boolean = false,
        isLookTypeRegistered: (Int) -> Boolean = { false },
    ): IOLoginData = IOLoginData(
        players = JdbcPlayerRepository(dataSource),
        accounts = JdbcAccountRepository(dataSource),
        vip = JdbcVipRepository(dataSource),
        storageRepository = JdbcPlayerStorageRepository(dataSource),
        items = JdbcPlayerItemsRepository(dataSource),
        depots = JdbcPlayerDepotRepository(dataSource),
        spells = JdbcPlayerSpellsRepository(dataSource),
        kills = JdbcPlayerKillsRepository(dataSource),
        stash = JdbcPlayerStashRepository(dataSource),
        rewards = JdbcPlayerRewardsRepository(dataSource),
        subloads = IOLoginDataSubloads(
            subloadRepository = DbPlayerSubloadRepository(dataSource),
            warnUnsafeScripts = warnUnsafeScripts,
            isLookTypeRegistered = isLookTypeRegistered,
        ),
    )

    private fun insertPlayer(
        id: Long,
        name: String,
        lookType: Int,
        lookHead: Int,
        lookBody: Int,
        lookLegs: Int,
        lookFeet: Int,
        lookAddons: Int,
        lookMountHead: Int = 0,
        lookMountBody: Int = 0,
        lookMountLegs: Int = 0,
        lookMountFeet: Int = 0,
        lookFamiliarsType: Int = 0,
    ) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO players (
                    id, name, account_id, looktype, lookhead, lookbody, looklegs, lookfeet, lookaddons,
                    lookmounthead, lookmountbody, lookmountlegs, lookmountfeet, lookfamiliarstype
                ) VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.setString(2, name)
                stmt.setInt(3, lookType)
                stmt.setInt(4, lookHead)
                stmt.setInt(5, lookBody)
                stmt.setInt(6, lookLegs)
                stmt.setInt(7, lookFeet)
                stmt.setInt(8, lookAddons)
                stmt.setInt(9, lookMountHead)
                stmt.setInt(10, lookMountBody)
                stmt.setInt(11, lookMountLegs)
                stmt.setInt(12, lookMountFeet)
                stmt.setInt(13, lookFamiliarsType)
                stmt.executeUpdate()
            }
        }
    }

    private fun newPlayer(id: Long = 101L, name: String = "Outfit Hero"): Player = Player(
        id = id,
        name = name,
        maxHealth = 1,
        health = 1,
        position = Position(1, 1, 7),
    )
}
