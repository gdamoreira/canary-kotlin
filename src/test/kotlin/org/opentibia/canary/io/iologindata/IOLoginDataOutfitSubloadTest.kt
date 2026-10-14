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
    ) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO players (
                    id, name, account_id, looktype, lookhead, lookbody, looklegs, lookfeet, lookaddons
                ) VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?)
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
                stmt.executeUpdate()
            }
        }
    }

    private fun newPlayer(): Player = Player(
        id = 101L,
        name = "Outfit Hero",
        maxHealth = 1,
        health = 1,
        position = Position(1, 1, 7),
    )
}
