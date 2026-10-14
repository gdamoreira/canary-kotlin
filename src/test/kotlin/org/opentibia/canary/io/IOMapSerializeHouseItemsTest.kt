package org.opentibia.canary.io

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.items.BedItem
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.house.Door
import org.opentibia.canary.map.house.GUEST_LIST
import org.opentibia.canary.map.house.Houses
import org.opentibia.canary.systems.imbuement.Imbuements

/**
 * H2 (Flyway V1-V11) round-trip tests for the house-item half of
 * `IOMapSerialize` (src/io/iomapserialize.cpp:17-294), transcribing
 * `loadHouseItems`, `saveHouseItems`, `SaveHouseItemsGuard`, `loadContainer`,
 * `loadItem`, `saveItem`, and `saveTile` including the bathtub substitution,
 * `isSavedToHouses` guard, owner guard, and bed completeness fix.
 */
class IOMapSerializeHouseItemsTest {

    private lateinit var dataSource: HikariDataSource

    private val itemTypes = ItemTypeRegistry().apply {
        register(ItemType(1001, "movable", isMovable = true))
        register(ItemType(1002, "fixed"))
        register(ItemType(2148, "coin", isStackable = true, isMovable = true))
        register(ItemType(1987, "bag", isContainer = true, capacity = 8, isMovable = true))
        register(ItemType(26077, "bathtub filled", isMovable = true))
        register(ItemType(26100, "bathtub filled notmovable"))
        register(ItemType(1750, "bed", isBed = true, isMovable = false, bedPartOf = 1751))
        register(ItemType(1751, "bed partner", isBed = true, isMovable = false, bedPartOf = 1750))
        register(ItemType(1210, "door", isDoor = true, isMovable = false))
        register(ItemType(2400, "sword", isMovable = true, imbuementSlot = 2))
        register(ItemType(1300, "imbueable door", isDoor = true, isMovable = false, imbuementSlot = 2))
    }

    private val imbuements = Imbuements().apply {
        assertThat(loadFromXml(TEST_XML)).isTrue()
    }

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_house_items_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(10)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun newMap(): GameMap = GameMap(houses = Houses())

    private fun newSerializer(
        map: GameMap,
        isTransferOnServerRestart: Boolean = false,
        transformItem: (Item, Int) -> Unit = { _, _ -> },
        removeBedSleeper: (Long) -> Unit = {},
        nextBedItem: (BedItem) -> BedItem? = { null },
        imbuements: Imbuements? = null,
    ): IOMapSerialize = IOMapSerialize(
        dataSource = dataSource,
        map = map,
        itemTypes = itemTypes,
        isTransferOnServerRestart = isTransferOnServerRestart,
        transformItem = transformItem,
        removeBedSleeper = removeBedSleeper,
        nextBedItem = nextBedItem,
        imbuements = imbuements,
    )

    /** `Map::save` (src/map/map.cpp:262-273): info first, then items. */
    private fun saveHouse(map: GameMap): Boolean {
        val serializer = newSerializer(map)
        assertThat(serializer.saveHouseInfo()).isTrue()
        return serializer.saveHouseItems()
    }

    private fun houseTile(map: GameMap, houseId: Int, position: Position) {
        map.houses.addHouse(houseId).addTile(position)
        map.getOrCreateTile(position).houseId = houseId
    }

    private fun tileStoreBlobs(): List<ByteArray> {
        val blobs = mutableListOf<ByteArray>()
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT data FROM tile_store").use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        blobs.add(rs.getBytes("data"))
                    }
                }
            }
        }
        return blobs
    }

    private fun rowCount(table: String): Int {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT COUNT(*) FROM $table").use { rs ->
                    rs.next()
                    return rs.getInt(1)
                }
            }
        }
    }

    @Test
    fun `saveHouseItems writes the position, count, item id and closing attribute`() {
        val map = newMap()
        val position = Position(100, 200, 7)
        houseTile(map, 1, position)
        map.getOrCreateTile(position).addItem(Item(1001, type = itemTypes.get(1001)))

        assertThat(saveHouse(map)).isTrue()

        assertThat(tileStoreBlobs()).hasSize(1)
        assertThat(tileStoreBlobs().first()).containsExactly(
            100, 0,
            200, 0,
            7,
            1, 0, 0, 0,
            0xE9, 0x03,
            0x00,
        )
    }

    @Test
    fun `saveTile reverses item order and keeps the bathtub substitution`() {
        val map = newMap()
        val position = Position(10, 20, 7)
        houseTile(map, 1, position)
        val tile = map.getOrCreateTile(position)
        tile.addItem(Item(1001, type = itemTypes.get(1001)))
        tile.addItem(Item(2148, count = 5, type = itemTypes.get(2148)))
        tile.addItem(Item(26100, type = itemTypes.get(26100)))
        tile.addItem(Item(1002, type = itemTypes.get(1002)))

        assertThat(saveHouse(map)).isTrue()

        assertThat(tileStoreBlobs().first()).containsExactly(
            10, 0,
            20, 0,
            7,
            3, 0, 0, 0,
            0xDD, 0x65,
            0x00,
            0x64, 0x08, 15, 5, 0x00,
            0xE9, 0x03, 0x00,
        )
    }

    @Test
    fun `saveHouseItems deletes previous rows and skips tiles without saved items`() {
        val map = newMap()
        val position = Position(1, 2, 7)
        houseTile(map, 1, position)
        val tile = map.getOrCreateTile(position)
        tile.addItem(Item(1002, type = itemTypes.get(1002)))

        val serializer = newSerializer(map)
        assertThat(serializer.saveHouseInfo()).isTrue()
        assertThat(serializer.saveHouseItems()).isTrue()
        assertThat(rowCount("tile_store")).isZero()

        val savedItem = Item(1001, type = itemTypes.get(1001))
        tile.addItem(savedItem)
        assertThat(serializer.saveHouseItems()).isTrue()
        assertThat(rowCount("tile_store")).isEqualTo(1)

        tile.removeItem(savedItem)
        assertThat(serializer.saveHouseItems()).isTrue()
        assertThat(rowCount("tile_store")).isZero()
    }

    @Test
    fun `loadHouseItems restores items, attributes and containers onto the map`() {
        val source = newMap()
        val position = Position(50, 60, 7)
        houseTile(source, 1, position)
        val sourceTile = source.getOrCreateTile(position)
        val sword = Item(1001, type = itemTypes.get(1001)).apply {
            setActionId(77)
            setAttribute(ItemAttribute.TEXT, "Hi")
        }
        val bag = Container(1987, capacity = 8, type = itemTypes.get(1987))
        bag.addItem(Item(2148, count = 5, type = itemTypes.get(2148)))
        sourceTile.addItem(sword)
        sourceTile.addItem(bag)

        assertThat(saveHouse(source)).isTrue()

        val target = newMap()
        houseTile(target, 1, position)
        target.houses.getHouse(1)?.owner = 5
        newSerializer(target).loadHouseItems()

        val loadedTile = target.getTile(position)
        assertThat(loadedTile?.items).hasSize(2)

        val loadedBag = loadedTile?.items?.filterIsInstance<Container>()?.single()
        assertThat(loadedBag).isNotNull
        assertThat(loadedBag?.items).hasSize(1)
        assertThat(loadedBag?.items?.first()?.id).isEqualTo(2148)
        assertThat(loadedBag?.items?.first()?.count).isEqualTo(5)

        val loadedSword = loadedTile?.items?.first { it.id == 1001 }
        assertThat(loadedSword?.getActionId()).isEqualTo(77)
        assertThat(loadedSword?.getAttribute(ItemAttribute.TEXT)).isEqualTo("Hi")
    }

    @Test
    fun `loadHouseItems skips an unowned house and clears its access lists`() {
        val source = newMap()
        val position = Position(5, 6, 7)
        houseTile(source, 1, position)
        source.getOrCreateTile(position).addItem(Item(1001, type = itemTypes.get(1001)))
        assertThat(saveHouse(source)).isTrue()

        val target = newMap()
        houseTile(target, 1, position)
        target.houses.getHouse(1)?.setAccessList(GUEST_LIST, "Alice")
        newSerializer(target).loadHouseItems()

        assertThat(target.getTile(position)?.items).isEmpty()
        assertThat(target.houses.getHouse(1)?.getAccessList(GUEST_LIST)).isEmpty()
    }

    @Test
    fun `loadHouseItems bypasses the owner guard when transfer on restart is enabled`() {
        val source = newMap()
        val position = Position(5, 6, 7)
        houseTile(source, 1, position)
        source.getOrCreateTile(position).addItem(Item(1001, type = itemTypes.get(1001)))
        assertThat(saveHouse(source)).isTrue()

        val target = newMap()
        houseTile(target, 1, position)
        newSerializer(target, isTransferOnServerRestart = true).loadHouseItems()

        assertThat(target.getTile(position)?.items?.map { it.id }).containsExactly(1001)
    }

    @Test
    fun `loadHouseItems drops a vacant house bed but loads a sleeping one`() {
        val source = newMap()
        val vacantPosition = Position(1, 1, 7)
        val sleepingPosition = Position(2, 2, 7)
        houseTile(source, 1, vacantPosition)
        houseTile(source, 1, sleepingPosition)
        source.getOrCreateTile(vacantPosition).addItem(BedItem(1750, type = itemTypes.get(1750)))
        source.getOrCreateTile(sleepingPosition).addItem(
            BedItem(1750, type = itemTypes.get(1750)).apply {
                sleeperGUID = 42
                sleepStart = 7
            },
        )
        assertThat(saveHouse(source)).isTrue()

        val target = newMap()
        houseTile(target, 1, vacantPosition)
        houseTile(target, 1, sleepingPosition)
        target.houses.getHouse(1)?.owner = 5
        newSerializer(target, nextBedItem = { null }).loadHouseItems()

        assertThat(target.getTile(vacantPosition)?.items).isEmpty()
        val sleeping = target.getTile(sleepingPosition)?.items?.filterIsInstance<BedItem>()?.single()
        assertThat(sleeping?.sleeperGUID).isEqualTo(0L)
        assertThat(sleeping?.getAttribute(ItemAttribute.DESCRIPTION)).isEqualTo("Nobody is sleeping there.")
    }

    @Test
    fun `loadHouseItems keeps a complete bed pair asleep`() {
        val source = newMap()
        val pillowPosition = Position(3, 3, 7)
        val blanketPosition = Position(4, 3, 7)
        houseTile(source, 1, pillowPosition)
        houseTile(source, 1, blanketPosition)
        source.getOrCreateTile(pillowPosition).addItem(
            BedItem(1750, type = itemTypes.get(1750)).apply { sleeperGUID = 42 },
        )
        source.getOrCreateTile(blanketPosition).addItem(BedItem(1751, type = itemTypes.get(1751)))
        assertThat(saveHouse(source)).isTrue()

        val target = newMap()
        houseTile(target, 1, pillowPosition)
        houseTile(target, 1, blanketPosition)
        target.houses.getHouse(1)?.owner = 5
        target.getOrCreateTile(blanketPosition).addItem(BedItem(1751, type = itemTypes.get(1751)))

        val blanket = target.getOrCreateTile(blanketPosition).items.filterIsInstance<BedItem>().single()
        val serializer = newSerializer(target, nextBedItem = { bed -> if (bed.id == 1750) blanket else null })
        serializer.loadHouseItems()

        val pillow = target.getTile(pillowPosition)?.items?.filterIsInstance<BedItem>()?.single()
        assertThat(pillow?.sleeperGUID).isEqualTo(42L)
    }

    @Test
    fun `loadItem applies saved attributes to an existing stationary door`() {
        val source = newMap()
        val position = Position(7, 7, 7)
        houseTile(source, 1, position)
        val door = Door(1210, type = itemTypes.get(1210)).apply { setDoorId(3) }
        source.getOrCreateTile(position).addItem(door)
        assertThat(saveHouse(source)).isTrue()

        val target = newMap()
        houseTile(target, 1, position)
        target.houses.getHouse(1)?.owner = 5
        val existingDoor = Door(1210, type = itemTypes.get(1210)).apply { setDoorId(3) }
        target.getOrCreateTile(position).addItem(existingDoor)

        val transformed = mutableListOf<Pair<Int, Int>>()
        val serializer = newSerializer(target, transformItem = { item, id -> transformed.add(item.id to id) })
        serializer.loadHouseItems()

        assertThat(target.getTile(position)?.items).containsExactly(existingDoor)
        assertThat(transformed).containsExactly(1210 to 1210)
    }

    @Test
    fun `loadHouseItems assigns the imbuement registry to restored movable items`() {
        val source = newMap()
        val position = Position(8, 8, 7)
        houseTile(source, 1, position)
        source.getOrCreateTile(position).addItem(
            Item(2400, type = itemTypes.get(2400)).apply { setImbuement(0, 200, 1000) },
        )
        assertThat(saveHouse(source)).isTrue()

        val target = newMap()
        houseTile(target, 1, position)
        target.houses.getHouse(1)?.owner = 5
        newSerializer(target, imbuements = imbuements).loadHouseItems()

        val restored = target.getTile(position)?.items?.first { it.id == 2400 }
        assertThat(restored?.imbuementRegistry).isSameAs(imbuements)
        assertThat(restored?.hasImbuements()).isFalse()
    }

    @Test
    fun `loadHouseItems assigns the imbuement registry to an existing stationary item`() {
        val source = newMap()
        val position = Position(9, 9, 7)
        houseTile(source, 1, position)
        source.getOrCreateTile(position).addItem(
            Door(1300, type = itemTypes.get(1300)).apply {
                setDoorId(3)
                setImbuement(0, 200, 1000)
            },
        )
        assertThat(saveHouse(source)).isTrue()

        val target = newMap()
        houseTile(target, 1, position)
        target.houses.getHouse(1)?.owner = 5
        val existingDoor = Door(1300, type = itemTypes.get(1300)).apply { setDoorId(3) }
        target.getOrCreateTile(position).addItem(existingDoor)
        newSerializer(target, imbuements = imbuements).loadHouseItems()

        assertThat(existingDoor.imbuementRegistry).isSameAs(imbuements)
        assertThat(existingDoor.hasImbuements()).isFalse()
    }

    @Test
    fun `loadHouseItems without a registry keeps the imbuement presence fallback`() {
        val source = newMap()
        val position = Position(8, 8, 7)
        houseTile(source, 1, position)
        source.getOrCreateTile(position).addItem(
            Item(2400, type = itemTypes.get(2400)).apply { setImbuement(0, 200, 1000) },
        )
        assertThat(saveHouse(source)).isTrue()

        val target = newMap()
        houseTile(target, 1, position)
        target.houses.getHouse(1)?.owner = 5
        newSerializer(target).loadHouseItems()

        val restored = target.getTile(position)?.items?.first { it.id == 2400 }
        assertThat(restored?.imbuementRegistry).isNull()
        assertThat(restored?.hasImbuements()).isTrue()
    }
}

private const val TEST_XML = """
<imbuements>
    <base id="1" name="Basic" price="5000" protectionPrice="10000" percent="90" removecost="15000" duration="72000"/>
    <category id="3" name="Critical Hit" agressive="1"/>
    <imbuement name="Strike" base="1" category="3" iconid="1">
        <attribute key="effect" type="skill" value="critical" bonus="1500" chance="1000"/>
    </imbuement>
</imbuements>
"""
