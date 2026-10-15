package org.opentibia.canary.io.iologindata

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.Group
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
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the `group_id` load of
 * `IOLoginDataLoad::loadPlayerBasicInfo`
 * (src/io/functions/iologindata_load_player.cpp:140-145) over the injected
 * `g_game().groups.getGroup` registry seam (`Groups::getGroup`,
 * groups.cpp:78-169): the persisted `group_id` reaches `Player::setGroup`
 * (player.hpp:519-521), so group-gated logic (`Player::isAccessPlayer`,
 * player.cpp:7524-7526) sees the account's real group. Group 1
 * (`GROUP_TYPE_NORMAL`, src/enums/account_group_type.hpp:17) and an id the
 * registry does not know keep the port's default group.
 */
class IOLoginDataGroupLoadTest {

    private lateinit var dataSource: HikariDataSource

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_group_load_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(dataSource)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun loginData(groups: (Int) -> Group?): IOLoginData = IOLoginData(
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
        groups = groups,
    )

    private fun insertPlayer(id: Long, name: String, groupId: Int) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO players (
                    id, name, account_id, group_id, level, vocation, health, healthmax,
                    mana, manamax, experience, posx, posy, posz, cap, sex, pronoun, town_id
                ) VALUES (?, ?, 1, ?, 10, 4, 100, 100, 50, 50, 1, 1000, 1000, 7, 350, 1, 3, 1)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.setString(2, name)
                stmt.setInt(3, groupId)
                stmt.executeUpdate()
            }
        }
    }

    private fun newPlayer(id: Long = 101L, name: String = "Group Hero"): Player = Player(
        id = id,
        name = name,
        maxHealth = 1,
        health = 1,
        position = Position(1, 1, 7),
    )

    @Test
    fun `loadPlayerById sets the persisted group on the player`() {
        insertPlayer(101L, "Group Hero", groupId = GAMEMASTER.id)
        val player = newPlayer()

        val loaded = loginData(groups = { id -> GAMEMASTER.takeIf { it.id == id } })
            .loadPlayerById(player, 101L)

        assertThat(loaded).isTrue()
        assertThat(player.group).isEqualTo(GAMEMASTER)
        assertThat(player.isAccessPlayer()).isTrue()
    }

    @Test
    fun `loadPlayerByName sets the persisted group on the player`() {
        insertPlayer(102L, "Named Hero", groupId = GAMEMASTER.id)
        val player = newPlayer(id = 102L, name = "Named Hero")

        val loaded = loginData(groups = { id -> GAMEMASTER.takeIf { it.id == id } })
            .loadPlayerByName(player, "Named Hero")

        assertThat(loaded).isTrue()
        assertThat(player.group).isEqualTo(GAMEMASTER)
    }

    @Test
    fun `group 1 keeps the default group`() {
        insertPlayer(101L, "Group Hero", groupId = GROUP_TYPE_NORMAL)
        val player = newPlayer()

        val loaded = loginData(groups = { id -> GAMEMASTER.takeIf { it.id == id } })
            .loadPlayerById(player, 101L)

        assertThat(loaded).isTrue()
        assertThat(player.group).isNull()
        assertThat(player.isAccessPlayer()).isFalse()
    }

    @Test
    fun `an unregistered group id keeps the default group`() {
        insertPlayer(101L, "Group Hero", groupId = 9)
        val player = newPlayer()

        val loaded = loginData(groups = { id -> GAMEMASTER.takeIf { it.id == id } })
            .loadPlayerById(player, 101L)

        assertThat(loaded).isTrue()
        assertThat(player.group).isNull()
    }

    private companion object {
        /** `GroupType::GROUP_TYPE_NORMAL` (src/enums/account_group_type.hpp:17). */
        const val GROUP_TYPE_NORMAL = 1

        val GAMEMASTER = Group(name = "gamemaster", id = 4, access = true, maxVipEntries = 200)
    }
}
