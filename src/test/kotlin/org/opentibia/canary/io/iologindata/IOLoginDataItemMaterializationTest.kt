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
import org.opentibia.canary.database.PlayerItemState
import org.opentibia.canary.database.serializeItemAttributes
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.items.ObjectCategory_t
import org.opentibia.canary.items.containers.Inbox
import org.opentibia.canary.items.containers.depot.DepotChest
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.imbuement.ImbuementItem

/**
 * Transcript tests for the `IOLoginDataLoad` item materialization port
 * (src/io/functions/iologindata_load_player.cpp:34-65, 543-758): the stored
 * rows are rebuilt into `Item`/`Container` trees following Canary's exact
 * `sid` descending order and guards, including the `ATTR_CUSTOM` custom
 * attribute store that carries imbuements
 * (src/items/item.cpp:485, 912-1366, 1521-1536).
 */
class IOLoginDataItemMaterializationTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var loginData: IOLoginData
    private lateinit var player: Player
    private val decayingItems = mutableListOf<Item>()
    private val imbuementDecayItems = mutableListOf<ImbuementItem>()

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_iologin_items_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(dataSource)
        insertPlayer(101L, "Login Hero")

        val itemTypes = ItemTypeRegistry().apply {
            register(ItemType(2376, "backpack", isContainer = true, capacity = 20, isMovable = true))
            register(ItemType(1987, "bag", isContainer = true, capacity = 8, isMovable = true))
            register(ItemType(2400, "magic sword", isMovable = true, imbuementSlot = 2))
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
            startDecaying = { decayingItems.add(it) },
            startImbuementDecay = { imbuementDecayItems.add(it) },
        )

        player = Player(
            id = 101L,
            name = "Login Hero",
            maxHealth = 100,
            health = 100,
            position = Position(1000, 1000, 7),
        )
        player.premium = true
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

    @Test
    fun `loadItems materializes items keyed by sid with their parent and attributes`() {
        val itemsMap = sortedMapOf<Int, Pair<Item, Int>>()
        val rows = listOf(
            PlayerContainerItemState(pid = 101, sid = 102, itemId = 2160, count = 50),
            PlayerContainerItemState(
                pid = 1,
                sid = 101,
                itemId = 2376,
                attributes = mapOf(ItemAttribute.ACTIONID to 7L),
            ),
        )

        assertThat(loginData.loadItems(itemsMap, rows, player)).isSameAs(itemsMap)

        assertThat(itemsMap.keys).containsExactly(101, 102)
        assertThat(itemsMap[101]!!.first.id).isEqualTo(2376)
        assertThat(itemsMap[101]!!.first.getActionId()).isEqualTo(7)
        assertThat(itemsMap[101]!!.second).isEqualTo(1)
        assertThat(itemsMap[102]!!.first.count).isEqualTo(50)
        assertThat(itemsMap[102]!!.second).isEqualTo(101)
    }

    @Test
    fun `loadItems skips an item type that is not registered like Canary's null CreateItem`() {
        val itemsMap = sortedMapOf<Int, Pair<Item, Int>>()

        loginData.loadItems(
            itemsMap,
            listOf(PlayerContainerItemState(pid = 0, sid = 101, itemId = 999_999)),
            player,
        )

        assertThat(itemsMap).isEmpty()
    }

    @Test
    fun `loadItems skips a row whose attributes cannot be materialized`() {
        val itemsMap = sortedMapOf<Int, Pair<Item, Int>>()

        loginData.loadItems(
            itemsMap,
            listOf(
                PlayerContainerItemState(
                    pid = 0,
                    sid = 101,
                    itemId = 2376,
                    attributes = mapOf(ItemAttribute.CUSTOM to mapOf("bad" to Any())),
                ),
            ),
            player,
        )

        assertThat(itemsMap).isEmpty()
    }

    @Test
    fun `loadPlayerInventoryItems rebuilds slot items with imbuements and managed loot containers`() {
        loginData.savePlayerItem(
            101L,
            listOf(
                PlayerItemState(pid = 10, sid = 110, itemId = 2160, count = 100),
                PlayerItemState(
                    pid = 6,
                    sid = 106,
                    itemId = 2400,
                    attributes = mapOf(
                        ItemAttribute.CUSTOM to mapOf("500" to ((72000L shl 8) or 300L)),
                    ),
                ),
                PlayerItemState(
                    pid = 3,
                    sid = 103,
                    itemId = 2376,
                    attributes = mapOf(
                        ItemAttribute.QUICKLOOTCONTAINER to (1L shl ObjectCategory_t.OBJECTCATEGORY_ARMORS.value),
                    ),
                ),
            ),
        )
        val rows = loginData.loadPlayerInventoryItems(101L)
        val slots = sortedMapOf<Int, Item>()

        val materialized = loginData.loadPlayerInventoryItems(player, rows) { slot, item -> slots[slot] = item }

        assertThat(materialized.keys).containsExactly(103, 106, 110)
        assertThat(slots.keys).containsExactly(3, 6, 10)
        assertThat(slots[10]!!.count).isEqualTo(100)
        assertThat(slots[6]!!.getImbuementAttribute(0)).isEqualTo((72000L shl 8) or 300L)
        assertThat(slots[6]!!.hasImbuements()).isTrue()

        val backpack = slots[3] as Container
        assertThat(player.getManagedContainer(ObjectCategory_t.OBJECTCATEGORY_ARMORS, true)).isSameAs(backpack)

        assertThat(decayingItems.map { it.id }).containsExactly(2160, 2400, 2376)
        assertThat(imbuementDecayItems.map { it.id }).containsExactly(2400)
    }

    @Test
    fun `backpack with a nested bag round-trips through inventory persistence`() {
        loginData.savePlayerItem(
            101L,
            listOf(
                PlayerItemState(pid = 3, sid = 101, itemId = 2376),
                PlayerItemState(pid = 101, sid = 102, itemId = 1987),
                PlayerItemState(pid = 102, sid = 103, itemId = 2160, count = 50),
                PlayerItemState(pid = 102, sid = 104, itemId = 2400),
            ),
        )
        val rows = loginData.loadPlayerInventoryItems(101L)
        val slots = sortedMapOf<Int, Item>()

        val materialized = loginData.loadPlayerInventoryItems(player, rows) { slot, item -> slots[slot] = item }

        assertThat(materialized.keys).containsExactly(101, 102, 103, 104)
        assertThat(slots.keys).containsExactly(3)

        val backpack = slots.getValue(3) as Container
        assertThat(backpack.items.map { it.id }).containsExactly(1987)

        val bag = backpack.items.single() as Container
        assertThat(bag.items.map { it.id }).containsExactly(2160, 2400)

        // The direct slot item decays immediately; the nested items decay only
        // after the whole parent chain is attached, in descending sid order.
        assertThat(decayingItems.map { it.id }).containsExactly(2376, 2400, 2160, 1987)
    }

    @Test
    fun `loadPlayerInventoryItems skips a nested row whose parent sid is missing`() {
        loginData.savePlayerItem(
            101L,
            listOf(
                PlayerItemState(pid = 3, sid = 101, itemId = 2376),
                PlayerItemState(pid = 999, sid = 102, itemId = 2160, count = 50),
            ),
        )
        val rows = loginData.loadPlayerInventoryItems(101L)
        val slots = sortedMapOf<Int, Item>()

        val materialized = loginData.loadPlayerInventoryItems(player, rows) { slot, item -> slots[slot] = item }

        assertThat(materialized.keys).containsExactly(101, 102)
        assertThat(slots.keys).containsExactly(3)
        assertThat(decayingItems.map { it.id }).containsExactly(2376)
    }

    @Test
    fun `loadPlayerDepotItems rebuilds the nested container tree in ascending sid order`() {
        loginData.savePlayerDepotItems(
            101L,
            listOf(
                PlayerContainerItemState(pid = 1, sid = 101, itemId = 2376),
                PlayerContainerItemState(pid = 101, sid = 102, itemId = 1987),
                PlayerContainerItemState(pid = 102, sid = 103, itemId = 2160, count = 50),
                PlayerContainerItemState(pid = 102, sid = 104, itemId = 2400),
            ),
        )
        val rows = loginData.loadPlayerDepotItems(101L)
        val depotChest = DepotChest(DepotChest.ITEM_DEPOT_NULL + 1)
        val depotChests = mapOf(1 to depotChest)

        val materialized = loginData.loadPlayerDepotItems(player, rows) { depotId -> depotChests[depotId] }

        assertThat(materialized.keys).containsExactly(101, 102, 103, 104)
        assertThat(depotChest.items.map { it.id }).containsExactly(2376)

        val backpack = depotChest.items[0] as Container
        assertThat(backpack.items.map { it.id }).containsExactly(1987)

        val bag = backpack.items[0] as Container
        assertThat(bag.items.map { it.id }).containsExactly(2160, 2400)

        // The direct depot-chest children decay immediately; the nested items
        // decay only after the parent chain is established.
        assertThat(decayingItems.map { it.id }).containsExactly(2376, 2400, 2160, 1987)
    }

    @Test
    fun `loadPlayerInboxItems rebuilds the inbox tree and skips a missing inbox`() {
        loginData.savePlayerInbox(
            101L,
            listOf(
                PlayerContainerItemState(pid = 0, sid = 101, itemId = 2376),
                PlayerContainerItemState(pid = 101, sid = 102, itemId = 2160, count = 10),
            ),
        )
        val rows = loginData.loadPlayerInboxItems(101L)
        val inbox = Inbox(12902)

        val materialized = loginData.loadPlayerInboxItems(player, rows) { inbox }

        assertThat(materialized.keys).containsExactly(101, 102)
        assertThat(inbox.items.map { it.id }).containsExactly(2376)
        assertThat((inbox.items[0] as Container).items.map { it.id }).containsExactly(2160)
        assertThat(decayingItems.map { it.id }).containsExactly(2376, 2160)

        decayingItems.clear()
        val missingInbox = loginData.loadPlayerInboxItems(player, rows) { null }

        assertThat(missingInbox.keys).containsExactly(101, 102)
        assertThat(decayingItems).isEmpty()
    }

    @Test
    fun `loadItems applies the decoded attribute map through readAttr semantics`() {
        insertRawDepotRow(
            playerId = 101L,
            pid = 1,
            sid = 101,
            itemId = 2400,
            count = 1,
            blob = serializeItemAttributes(
                count = 1,
                attributes = mapOf(
                    ItemAttribute.DURATION to 3600L,
                    ItemAttribute.DECAYSTATE to 1L,
                    ItemAttribute.CUSTOM to mapOf("500" to ((72000L shl 8) or 300L)),
                ),
            ),
        )
        val rows = loginData.loadPlayerDepotItems(101L)
        val depotChest = DepotChest(DepotChest.ITEM_DEPOT_NULL + 1)

        val materialized = loginData.loadPlayerDepotItems(player, rows) { depotChest }

        val item = materialized.getValue(101).first
        assertThat(item.getDecaying()).isEqualTo(org.opentibia.canary.items.ItemDecayState.DECAYING_PENDING)
        assertThat(item.getDuration()).isEqualTo(3600)
        assertThat(item.getImbuementAttribute(0)).isEqualTo((72000L shl 8) or 300L)
    }

    private fun insertRawDepotRow(playerId: Long, pid: Int, sid: Int, itemId: Int, count: Int, blob: ByteArray) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO player_depotitems (player_id, pid, sid, itemtype, count, attributes) VALUES (?, ?, ?, ?, ?, ?)",
            ).use { stmt ->
                stmt.setLong(1, playerId)
                stmt.setInt(2, pid)
                stmt.setInt(3, sid)
                stmt.setInt(4, itemId)
                stmt.setInt(5, count)
                stmt.setBytes(6, blob)
                stmt.executeUpdate()
            }
        }
    }
}
