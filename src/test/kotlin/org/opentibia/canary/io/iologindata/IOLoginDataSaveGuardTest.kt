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
import org.opentibia.canary.database.PlayerContainerItemState
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.items.containers.depot.DepotChest
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.Reward
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for `IOLoginData::savePlayerGuard`
 * (src/io/iologindata.cpp:213-267) calling the live item walks through
 * `IOLoginData::savePlayer` (:195-211):
 *  - `IOLoginDataSave::savePlayerItem` / `saveItems` inventory walk
 *    (src/io/functions/iologindata_save_player.cpp:534-564);
 *  - `IOLoginDataSave::savePlayerDepotItems` (:566-597) over
 *    `Player::depotChests` (player.hpp:1673);
 *  - `IOLoginDataSave::saveRewardItems` (:599-632) over
 *    `Player::getRewardList` / `Player::getReward` (player.cpp:2067-2092) with
 *    the seven-day age window;
 *  - `IOLoginDataSave::savePlayerInbox` (:634-659) over
 *    `Player::getInbox()->getItemList`.
 */
class IOLoginDataSaveGuardTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var loginData: IOLoginData
    private lateinit var itemTypes: ItemTypeRegistry
    private val depotChests = mutableMapOf<Int, Container>()

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_iologin_guard_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(dataSource)
        insertPlayer(101L, "Save Guard Hero")

        itemTypes = ItemTypeRegistry().apply {
            register(ItemType(2376, "backpack", isContainer = true, capacity = 20, isMovable = true))
            register(ItemType(1987, "bag", isContainer = true, capacity = 8, isMovable = true))
            register(ItemType(2400, "magic sword", isMovable = true))
            register(ItemType(2160, "crystal coin", isStackable = true))
        }

        loginData = newLoginData(depotChests = { depotChests })
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun newLoginData(depotChests: ((Player) -> Map<Int, Container>)? = null): IOLoginData = IOLoginData(
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
        depotChests = depotChests,
    )

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
        name = "Save Guard Hero",
        maxHealth = 100,
        health = 100,
        capacity = 100_000.0,
        position = Position(1000, 1000, 7),
    )

    private fun item(id: Int, count: Int = 1): Item = Item.create(id, count, itemTypes)

    @Test
    fun `savePlayer writes the live inventory depot reward and inbox rows`() {
        val player = newPlayer()

        val backpack = item(2376) as Container
        val bag = item(1987) as Container
        bag.addItem(item(2160, count = 50))
        backpack.addItem(bag)
        assertThat(player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)).isEqualTo(ReturnValue.NOERROR)

        player.setLastDepotId(1)
        val depotChest = DepotChest(DepotChest.depotChestItemId(1))
        depotChest.addItem(item(2400))
        depotChests[1] = depotChest

        val rewardId = System.currentTimeMillis() - 1_000L
        val reward = player.getRewardChest().getReward(rewardId, autoCreate = true)!!
        val rewardBag = item(1987) as Container
        rewardBag.addItem(item(2160, count = 7))
        reward.addItem(rewardBag)

        player.inbox.addItem(item(2400))

        assertThat(loginData.savePlayer(player)).isTrue()

        val inventoryRows = loginData.loadPlayerInventoryItems(101L)
        assertThat(inventoryRows.map { it.sid }).containsExactly(103, 102, 101)
        assertThat(inventoryRows.map { it.pid }).containsExactly(102, 101, 3)
        assertThat(inventoryRows.map { it.itemId }).containsExactly(2160, 1987, 2376)
        assertThat(inventoryRows.single { it.sid == 103 }.count).isEqualTo(50)

        val depotRows = loginData.loadPlayerDepotItems(101L)
        assertThat(depotRows).containsExactly(
            PlayerContainerItemState(pid = 1, sid = 101, itemId = 2400),
        )

        val rewardRows = loginData.loadRewardItems(101L)
        assertThat(rewardRows.map { it.sid }).containsExactly(101, 102, 103)
        assertThat(rewardRows.map { it.pid }).containsExactly(0, 101, 102)
        assertThat(rewardRows.map { it.itemId }).containsExactly(Reward.ITEM_REWARD_CONTAINER, 1987, 2160)
        assertThat(rewardRows.single { it.sid == 101 }.attributes[ItemAttribute.DATE]).isEqualTo(rewardId)

        val inboxRows = loginData.loadPlayerInboxItems(101L)
        assertThat(inboxRows).containsExactly(
            PlayerContainerItemState(pid = 0, sid = 101, itemId = 2400),
        )
    }

    @Test
    fun `savePlayer keeps fresh non-empty rewards and drops expired or empty ones`() {
        val player = newPlayer()
        val now = System.currentTimeMillis()
        val chest = player.getRewardChest()

        val fresh = chest.getReward(now - 1_000L, autoCreate = true)!!
        fresh.addItem(item(2400))
        chest.getReward(now - 2_000L, autoCreate = true)
        val expired = chest.getReward(now - 8 * 24 * 60 * 60 * 1_000L, autoCreate = true)!!
        expired.addItem(item(2160))

        assertThat(loginData.savePlayer(player)).isTrue()

        val rewardRows = loginData.loadRewardItems(101L)
        assertThat(rewardRows.map { it.sid }).containsExactly(101, 102)
        assertThat(rewardRows.map { it.itemId }).containsExactly(Reward.ITEM_REWARD_CONTAINER, 2400)
        assertThat(rewardRows.single { it.sid == 101 }.attributes[ItemAttribute.DATE]).isEqualTo(now - 1_000L)
    }

    @Test
    fun `savePlayer skips the depot save when the player never opened a depot`() {
        loginData.savePlayerDepotItems(101L, listOf(PlayerContainerItemState(pid = 1, sid = 101, itemId = 2400)))
        val player = newPlayer()
        val depotChest = DepotChest(DepotChest.depotChestItemId(1))
        depotChest.addItem(item(1987))
        depotChests[1] = depotChest

        assertThat(loginData.savePlayer(player)).isTrue()

        assertThat(loginData.loadPlayerDepotItems(101L)).containsExactly(
            PlayerContainerItemState(pid = 1, sid = 101, itemId = 2400),
        )
    }

    @Test
    fun `savePlayer leaves depot rows untouched when the depot chest sink is unwired`() {
        loginData.savePlayerDepotItems(101L, listOf(PlayerContainerItemState(pid = 1, sid = 101, itemId = 2400)))
        val unwired = newLoginData()
        val player = newPlayer()
        player.setLastDepotId(1)

        assertThat(unwired.savePlayer(player)).isTrue()

        assertThat(loginData.loadPlayerDepotItems(101L)).containsExactly(
            PlayerContainerItemState(pid = 1, sid = 101, itemId = 2400),
        )
    }

    @Test
    fun `savePlayer clears the persisted inventory depot reward and inbox rows of an empty player`() {
        loginData.savePlayerItem(
            101L,
            listOf(org.opentibia.canary.database.PlayerItemState(pid = 3, sid = 101, itemId = 2376)),
        )
        loginData.savePlayerDepotItems(101L, listOf(PlayerContainerItemState(pid = 1, sid = 101, itemId = 2400)))
        loginData.saveRewardItems(101L, listOf(PlayerContainerItemState(pid = 0, sid = 101, itemId = 19202)))
        loginData.savePlayerInbox(101L, listOf(PlayerContainerItemState(pid = 0, sid = 101, itemId = 2400)))
        val player = newPlayer()
        player.setLastDepotId(1)
        depotChests[1] = DepotChest(DepotChest.depotChestItemId(1))

        assertThat(loginData.savePlayer(player)).isTrue()

        assertThat(loginData.loadPlayerInventoryItems(101L)).isEmpty()
        assertThat(loginData.loadPlayerDepotItems(101L)).isEmpty()
        assertThat(loginData.loadRewardItems(101L)).isEmpty()
        assertThat(loginData.loadPlayerInboxItems(101L)).isEmpty()
    }
}
