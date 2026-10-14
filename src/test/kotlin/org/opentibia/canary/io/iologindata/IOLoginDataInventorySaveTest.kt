package org.opentibia.canary.io.iologindata

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
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
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the live inventory save walk of
 * `IOLoginDataSave::saveItems` / `savePlayerItem`
 * (src/io/functions/iologindata_save_player.cpp:34-188, 534-564):
 * the rows are built from `player->inventory[slot]` with the BFS `sid`
 * assignment from 101 and the `OPENCONTAINER` re-arming performed from
 * `Player::getOpenContainers` (`player.hpp:1671`), then round-tripped through
 * `IOLoginDataLoad::loadPlayerInventoryItems`
 * (src/io/functions/iologindata_load_player.cpp:543-632).
 */
class IOLoginDataInventorySaveTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var loginData: IOLoginData
    private lateinit var itemTypes: ItemTypeRegistry

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_iologin_save_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(dataSource)
        insertPlayer(101L, "Save Hero")

        itemTypes = ItemTypeRegistry().apply {
            register(ItemType(2376, "backpack", isContainer = true, capacity = 20, isMovable = true))
            register(ItemType(1987, "bag", isContainer = true, capacity = 8, isMovable = true))
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
        name = "Save Hero",
        maxHealth = 100,
        health = 100,
        capacity = 100_000.0,
        position = Position(1000, 1000, 7),
    )

    private fun item(id: Int, count: Int = 1): Item = Item.create(id, count, itemTypes)

    @Test
    fun `live inventory walk assigns BFS sids from 101 across slots and nested bags`() {
        val player = newPlayer()
        val backpack = item(2376) as Container
        val bag = item(1987) as Container
        val nestedBag = item(1987) as Container
        bag.addItem(item(2160, count = 50))
        nestedBag.addItem(item(2400))
        backpack.addItem(bag)
        backpack.addItem(nestedBag)
        assertThat(player.addItem(item(2160), Slots_t.CONST_SLOT_RING)).isEqualTo(ReturnValue.NOERROR)
        assertThat(player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)).isEqualTo(ReturnValue.NOERROR)

        assertThat(loginData.savePlayerItem(player)).isTrue()

        // Rows in Canary's descending sid load order. The BFS gives
        // backpack=101 (slot 3), ring=102 (slot 9), then the backpack's
        // children bag=103/nestedBag=104 before the grandchildren coin=105
        // (under bag) and sword=106 (under nestedBag): a depth-first walk
        // would visit nestedBag's subtree before bag's.
        val rows = loginData.loadPlayerInventoryItems(101L)
        assertThat(rows.map { it.sid }).containsExactly(106, 105, 104, 103, 102, 101)
        assertThat(rows.map { it.pid }).containsExactly(104, 103, 101, 101, 9, 3)
        assertThat(rows.map { it.itemId }).containsExactly(2400, 2160, 1987, 1987, 2160, 2376)
        assertThat(rows.single { it.sid == 105 }.count).isEqualTo(50)
    }

    @Test
    fun `live inventory with nested bags saves and reloads identically`() {
        val player = newPlayer()
        val backpack = item(2376) as Container
        val bag = item(1987) as Container
        val coins = item(2160, count = 50)
        val sword = item(2400)
        sword.setAttribute(ItemAttribute.TEXT, "Excalibur")
        bag.addItem(coins)
        bag.addItem(sword)
        backpack.addItem(bag)
        assertThat(player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)).isEqualTo(ReturnValue.NOERROR)

        assertThat(loginData.savePlayerItem(player)).isTrue()

        val reloaded = newPlayer()
        val slots = sortedMapOf<Int, Item>()
        loginData.loadPlayerInventoryItems(reloaded, loginData.loadPlayerInventoryItems(101L)) { slot, entry ->
            slots[slot] = entry
        }

        assertThat(slots.keys).containsExactly(3)
        val reloadedBackpack = slots.getValue(3) as Container
        assertThat(reloadedBackpack.id).isEqualTo(2376)
        val reloadedBag = reloadedBackpack.items.single() as Container
        assertThat(reloadedBag.items.map { it.id }).containsExactly(2160, 2400)
        assertThat(reloadedBag.items[0].count).isEqualTo(50)
        assertThat(reloadedBag.items[1].getAttribute(ItemAttribute.TEXT)).isEqualTo("Excalibur")
    }

    @Test
    fun `savePlayerItem re-arms OPENCONTAINER from the open container registry`() {
        val player = newPlayer()
        val backpack = item(2376) as Container
        val bag = item(1987) as Container
        backpack.addItem(bag)
        assertThat(player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)).isEqualTo(ReturnValue.NOERROR)
        player.addContainer(5, backpack)
        player.addContainer(9, bag)

        assertThat(loginData.savePlayerItem(player)).isTrue()

        assertThat(backpack.getAttribute(ItemAttribute.OPENCONTAINER)).isEqualTo(6L)
        assertThat(bag.getAttribute(ItemAttribute.OPENCONTAINER)).isEqualTo(10L)

        val rows = loginData.loadPlayerInventoryItems(101L)
        assertThat(rows.single { it.sid == 101 }.attributes[ItemAttribute.OPENCONTAINER]).isEqualTo(6L)
        assertThat(rows.single { it.sid == 102 }.attributes[ItemAttribute.OPENCONTAINER]).isEqualTo(10L)
    }

    @Test
    fun `savePlayerItem clears a stale OPENCONTAINER when the container is not open`() {
        val player = newPlayer()
        val backpack = item(2376) as Container
        backpack.setAttribute(ItemAttribute.OPENCONTAINER, 7L)
        assertThat(player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)).isEqualTo(ReturnValue.NOERROR)

        assertThat(loginData.savePlayerItem(player)).isTrue()

        assertThat(backpack.getAttribute(ItemAttribute.OPENCONTAINER)).isEqualTo(0L)
        val row = loginData.loadPlayerInventoryItems(101L).single()
        assertThat(row.attributes[ItemAttribute.OPENCONTAINER]).isEqualTo(0L)
    }
}
