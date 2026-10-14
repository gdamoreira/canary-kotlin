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
import org.opentibia.canary.database.PlayerContainerItemState
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the live `Player::depotChests` (player.hpp:1673) walk of
 * `IOLoginDataSave::savePlayerDepotItems`
 * (src/io/functions/iologindata_save_player.cpp:566-597) and
 * `IOLoginDataLoad::loadPlayerDepotItems`
 * (iologindata_load_player.cpp:663-708) over `Player::getDepotChest(depotId,
 * autoCreate)` (player.cpp:1998-2022): the default depot sink resolves the same
 * chest map the loader materializes, so a save/load round trip needs no caller
 * seam.
 */
class IOLoginDataDepotChestsTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var loginData: IOLoginData
    private lateinit var itemTypes: ItemTypeRegistry

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_iologin_depot_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(dataSource)
        insertPlayer(101L, "Depot Hero")

        itemTypes = ItemTypeRegistry().apply {
            register(ItemType(2400, "magic sword", isMovable = true))
            register(ItemType(2160, "crystal coin", isStackable = true))
        }

        loginData = IOLoginData(
            players = JdbcPlayerRepository(dataSource),
            accounts = JdbcAccountRepository(dataSource),
            vip = JdbcVipRepository(dataSource),
            storageRepository = JdbcPlayerStorageRepository(dataSource),
            spells = JdbcPlayerSpellsRepository(dataSource),
            kills = JdbcPlayerKillsRepository(dataSource),
            stash = JdbcPlayerStashRepository(dataSource),
            rewards = JdbcPlayerRewardsRepository(dataSource),
            items = JdbcPlayerItemsRepository(dataSource),
            depots = JdbcPlayerDepotRepository(dataSource),
            itemTypes = itemTypes,
        )
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun insertPlayer(id: Long, name: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO players (
                    id, name, account_id, level, vocation, health, healthmax, mana, manamax,
                    experience, posx, posy, posz, cap, sex, pronoun, town_id
                ) VALUES (?, ?, 1, 10, 4, 100, 100, 50, 50, 1, 1000, 1000, 7, 350, 1, 3, 1)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.setString(2, name)
                stmt.executeUpdate()
            }
        }
    }

    private fun newPlayer(): Player = Player(
        id = 101L,
        name = "Depot Hero",
        maxHealth = 100,
        health = 100,
        capacity = 100_000.0,
        position = Position(1000, 1000, 7),
    )

    private fun item(id: Int, count: Int = 1): Item = Item.create(id, count, itemTypes)

    @Test
    fun `save walks the live player depot chest map`() {
        val player = newPlayer()
        player.setLastDepotId(1)
        val chest = player.getDepotChest(1, autoCreate = true)!!
        chest.addItem(item(2400))
        chest.addItem(item(2160, count = 50))

        assertThat(loginData.savePlayerDepotItems(player)).isTrue()

        // The persisted rows come back in Canary's `ORDER BY sid DESC` order.
        assertThat(loginData.loadPlayerDepotItems(101L)).containsExactly(
            PlayerContainerItemState(pid = 1, sid = 102, itemId = 2160, count = 50),
            PlayerContainerItemState(pid = 1, sid = 101, itemId = 2400),
        )
    }

    @Test
    fun `load attaches the rows into the same live chests the save walks`() {
        loginData.savePlayerDepotItems(
            101L,
            listOf(
                PlayerContainerItemState(pid = 1, sid = 101, itemId = 2400),
                PlayerContainerItemState(pid = 1, sid = 102, itemId = 2160, count = 50),
            ),
        )
        val player = newPlayer()

        loginData.loadPlayerDepotItems(player, loginData.loadPlayerDepotItems(101L)) { depotId ->
            player.getDepotChest(depotId, autoCreate = true)
        }

        val chest = player.getDepotChest(1, autoCreate = false)
        assertThat(chest).isNotNull
        assertThat(chest!!.getItemList().map { it.id }).containsExactly(2400, 2160)
    }

    @Test
    fun `save clears the persisted rows when the live chest map is empty`() {
        loginData.savePlayerDepotItems(
            101L,
            listOf(PlayerContainerItemState(pid = 1, sid = 101, itemId = 2400)),
        )
        val player = newPlayer()
        player.setLastDepotId(1)
        player.getDepotChest(1, autoCreate = true)

        assertThat(loginData.savePlayerDepotItems(player)).isTrue()

        assertThat(loginData.loadPlayerDepotItems(101L)).isEmpty()
    }

    @Test
    fun `savePlayer writes the live depot chest rows through the default sink`() {
        val player = newPlayer()
        player.setLastDepotId(1)
        player.getDepotChest(1, autoCreate = true)!!.addItem(item(2400))

        assertThat(loginData.savePlayer(player)).isTrue()

        assertThat(loginData.loadPlayerDepotItems(101L)).containsExactly(
            PlayerContainerItemState(pid = 1, sid = 101, itemId = 2400),
        )
    }
}
