package org.opentibia.canary.io

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.house.CyclopediaHouseState
import org.opentibia.canary.map.house.GUEST_LIST
import org.opentibia.canary.map.house.HouseOwnerInfo
import org.opentibia.canary.map.house.Houses
import org.opentibia.canary.map.house.SUBOWNER_LIST

/**
 * H2 (Flyway V1-V11) round-trip tests for the house-info half of
 * `IOMapSerialize` (src/io/iomapserialize.cpp:296-468), transcribing
 * `loadHouseInfo`, `saveHouseInfo`, and `SaveHouseInfoGuard` including the
 * `houses`/`house_lists` upserts and the `version <` pruning.
 */
class IOMapSerializeHouseInfoTest {

    private lateinit var dataSource: HikariDataSource

    private val ownerLookup: (Long) -> HouseOwnerInfo? = { guid ->
        HouseOwnerInfo("Player $guid", guid * 10)
    }

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_house_info_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        // V10 belongs to a sibling workstream; this branch applies V1-V9 plus V11.
        assertThat(applied).isGreaterThanOrEqualTo(10)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun newSerializer(
        map: GameMap,
        isTransferOnServerRestart: Boolean = false,
        now: Long = 1000L,
        setTransferPlayerHouseItems: (Int, Long) -> Unit = { _, _ -> },
        increaseBankBalance: (Long, Long) -> Unit = { _, _ -> },
    ): IOMapSerialize = IOMapSerialize(
        dataSource = dataSource,
        map = map,
        isTransferOnServerRestart = isTransferOnServerRestart,
        now = { now },
        setTransferPlayerHouseItems = setTransferPlayerHouseItems,
        increaseBankBalance = increaseBankBalance,
    )

    private fun newMap(): GameMap = GameMap(houses = Houses(ownerInfoLookup = ownerLookup))

    private fun queryHouses(): Map<String, String?> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, owner, paid, warnings, name, town_id, rent, size, beds, bidder, bidder_name, " +
                    "highest_bid, internal_bid, bid_end_date, state, transfer_status FROM houses ORDER BY id",
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) {
                        return emptyMap()
                    }
                    return linkedMapOf(
                        "id" to rs.getString("id"),
                        "owner" to rs.getString("owner"),
                        "paid" to rs.getString("paid"),
                        "warnings" to rs.getString("warnings"),
                        "name" to rs.getString("name"),
                        "town_id" to rs.getString("town_id"),
                        "rent" to rs.getString("rent"),
                        "size" to rs.getString("size"),
                        "beds" to rs.getString("beds"),
                        "bidder" to rs.getString("bidder"),
                        "bidder_name" to rs.getString("bidder_name"),
                        "highest_bid" to rs.getString("highest_bid"),
                        "internal_bid" to rs.getString("internal_bid"),
                        "bid_end_date" to rs.getString("bid_end_date"),
                        "state" to rs.getString("state"),
                        "transfer_status" to rs.getString("transfer_status"),
                    )
                }
            }
        }
    }

    private fun execute(sql: String) {
        dataSource.connection.use { conn ->
            conn.createStatement().use { it.execute(sql) }
        }
    }

    private fun countRows(table: String): Int {
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
    fun `saveHouseInfo upserts the exact houses column set and updates in place`() {
        val map = newMap()
        val house = map.houses.addHouse(1).apply {
            name = "Central Flat 1"
            owner = 10
            paidUntil = 111
            rentWarnings = 2
            townId = 3
            rent = 500
            size = 17
            bedsCount = 4
            bidder = 11
            bidderName = "Bidder"
            highestBid = 100
            internalBid = 90
            bidEndDate = 999
            state = CyclopediaHouseState.Rented
            transferStatus = true
        }

        assertThat(newSerializer(map).saveHouseInfo()).isTrue()
        assertThat(queryHouses()).containsExactlyEntriesOf(
            linkedMapOf(
                "id" to "1",
                "owner" to "10",
                "paid" to "111",
                "warnings" to "2",
                "name" to "Central Flat 1",
                "town_id" to "3",
                "rent" to "500",
                "size" to "17",
                "beds" to "4",
                "bidder" to "11",
                "bidder_name" to "Bidder",
                "highest_bid" to "100",
                "internal_bid" to "90",
                "bid_end_date" to "999",
                "state" to "2",
                "transfer_status" to "1",
            ),
        )

        house.name = "Renamed"
        house.owner = 12
        assertThat(newSerializer(map).saveHouseInfo()).isTrue()
        assertThat(countRows("houses")).isEqualTo(1)
        assertThat(queryHouses()["owner"]).isEqualTo("12")
        assertThat(queryHouses()["name"]).isEqualTo("Renamed")
    }

    @Test
    fun `saveHouseInfo writes guest, subowner, and door lists and prunes stale versions`() {
        val map = newMap()
        val house = map.houses.addHouse(1).apply {
            name = "Central Flat 1"
        }
        house.setAccessList(GUEST_LIST, "Alice")
        house.setAccessList(SUBOWNER_LIST, "Bob")
        val door = org.opentibia.canary.map.house.Door(id = 1210).apply { setDoorId(9) }
        house.addDoor(door)
        door.setAccessList("Carol")

        val serializer = newSerializer(map)
        assertThat(serializer.saveHouseInfo()).isTrue()
        execute(
            "INSERT INTO house_lists (house_id, listid, list, version) VALUES (1, 77, 'Stale', 1)",
        )

        assertThat(serializer.saveHouseInfo()).isTrue()
        assertThat(countRows("house_lists")).isEqualTo(3)

        val rows = mutableMapOf<Int, String>()
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT listid, list, version FROM house_lists WHERE house_id = 1 ORDER BY listid")
                .use { stmt ->
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            rows[rs.getInt("listid")] = rs.getString("list")
                            assertThat(rs.getLong("version")).isGreaterThan(1L)
                        }
                    }
                }
        }
        assertThat(rows).containsExactlyInAnyOrderEntriesOf(
            mapOf(GUEST_LIST to "Alice\n", SUBOWNER_LIST to "Bob\n", 9 to "Carol\n"),
        )
    }

    @Test
    fun `saveHouseInfo skips empty access lists`() {
        val map = newMap()
        map.houses.addHouse(5).name = "Empty"

        assertThat(newSerializer(map).saveHouseInfo()).isTrue()
        assertThat(countRows("houses")).isEqualTo(1)
        assertThat(countRows("house_lists")).isZero()
    }

    @Test
    fun `loadHouseInfo reads owner, state, and access lists when no transfer is due`() {
        val map = newMap()
        map.houses.addHouse(1)
        execute(
            "INSERT INTO houses (id, owner, new_owner, paid, warnings, name, town_id, rent, size, beds, " +
                "bidder, bidder_name, highest_bid, internal_bid, bid_end_date, state, transfer_status) " +
                "VALUES (1, 10, -1, 555, 0, 'Central Flat 1', 3, 500, 17, 4, 0, '', 0, 0, 0, 2, 0)",
        )
        execute("INSERT INTO house_lists (house_id, listid, list, version) VALUES (1, 256, 'Alice', 1)")

        assertThat(newSerializer(map).loadHouseInfo()).isTrue()

        val house = map.houses.getHouse(1)
        assertThat(house?.owner).isEqualTo(10L)
        assertThat(house?.ownerAccountId).isEqualTo(100L)
        assertThat(house?.state).isEqualTo(CyclopediaHouseState.Rented)
        assertThat(house?.getAccessList(GUEST_LIST)).isEqualTo("Alice\n")
    }

    @Test
    fun `loadHouseInfo transfers ownership on restart when new_owner is set`() {
        val map = newMap()
        map.houses.addHouse(1)
        execute(
            "INSERT INTO houses (id, owner, new_owner, paid, warnings, name, town_id, rent, size, beds, " +
                "bidder, bidder_name, highest_bid, internal_bid, bid_end_date, state, transfer_status) " +
                "VALUES (1, 10, 20, 0, 0, 'House', 1, 0, 1, 0, 0, '', 0, 0, 0, 2, 0)",
        )

        val transfers = mutableListOf<Pair<Int, Long>>()
        val serializer = newSerializer(
            map,
            isTransferOnServerRestart = true,
            setTransferPlayerHouseItems = { houseId, playerId -> transfers.add(houseId to playerId) },
        )

        assertThat(serializer.loadHouseInfo()).isTrue()
        assertThat(map.houses.getHouse(1)?.owner).isEqualTo(20L)
        assertThat(transfers).containsExactly(1 to 10L)
    }

    @Test
    fun `loadHouseInfo removes the owner on restart when new_owner is zero`() {
        val map = newMap()
        map.houses.addHouse(1)
        execute(
            "INSERT INTO houses (id, owner, new_owner, paid, warnings, name, town_id, rent, size, beds, " +
                "bidder, bidder_name, highest_bid, internal_bid, bid_end_date, state, transfer_status) " +
                "VALUES (1, 10, 0, 0, 0, 'House', 1, 0, 1, 0, 0, '', 0, 0, 0, 2, 0)",
        )

        val serializer = newSerializer(map, isTransferOnServerRestart = true)
        assertThat(serializer.loadHouseInfo()).isTrue()
        assertThat(map.houses.getHouse(1)?.owner).isEqualTo(0L)
    }

    @Test
    fun `loadHouseInfo awards an expired auction to the bidder and refunds the difference`() {
        val map = newMap()
        map.houses.addHouse(1)
        execute(
            "INSERT INTO houses (id, owner, new_owner, paid, warnings, name, town_id, rent, size, beds, " +
                "bidder, bidder_name, highest_bid, internal_bid, bid_end_date, state, transfer_status) " +
                "VALUES (1, 0, -1, 0, 0, 'House', 1, 0, 1, 0, 20, 'Bidder', 100, 150, 999, 0, 0)",
        )

        val refunds = mutableListOf<Pair<Long, Long>>()
        val serializer = newSerializer(
            map,
            now = 1000L,
            increaseBankBalance = { playerId, amount -> refunds.add(playerId to amount) },
        )

        assertThat(serializer.loadHouseInfo()).isTrue()

        val house = map.houses.getHouse(1)
        assertThat(house?.owner).isEqualTo(20L)
        assertThat(house?.bidder).isEqualTo(0L)
        assertThat(house?.bidderName).isEmpty()
        assertThat(house?.highestBid).isEqualTo(0L)
        assertThat(house?.internalBid).isEqualTo(0L)
        assertThat(house?.bidEndDate).isEqualTo(0L)
        assertThat(house?.bidHolderLimit).isEqualTo(0L)
        assertThat(refunds).containsExactly(20L to 50L)
    }

    @Test
    fun `loadHouseInfo completes an expired transfer only when transfer_status is set`() {
        val map = newMap()
        map.houses.addHouse(1)
        execute(
            "INSERT INTO houses (id, owner, new_owner, paid, warnings, name, town_id, rent, size, beds, " +
                "bidder, bidder_name, highest_bid, internal_bid, bid_end_date, state, transfer_status) " +
                "VALUES (1, 10, -1, 0, 0, 'House', 1, 0, 1, 0, 20, 'Bidder', 0, 70, 999, 3, 1)",
        )

        val transfers = mutableListOf<Pair<Int, Long>>()
        val refunds = mutableListOf<Pair<Long, Long>>()
        val serializer = newSerializer(
            map,
            now = 1000L,
            setTransferPlayerHouseItems = { houseId, playerId -> transfers.add(houseId to playerId) },
            increaseBankBalance = { playerId, amount -> refunds.add(playerId to amount) },
        )

        assertThat(serializer.loadHouseInfo()).isTrue()

        val house = map.houses.getHouse(1)
        assertThat(house?.owner).isEqualTo(20L)
        assertThat(house?.transferStatus).isFalse()
        assertThat(house?.internalBid).isEqualTo(0L)
        assertThat(transfers).containsExactly(1 to 10L)
        assertThat(refunds).containsExactly(10L to 70L)
    }

    @Test
    fun `loadHouseInfo keeps the owner on an expired transfer without transfer_status`() {
        val map = newMap()
        map.houses.addHouse(1)
        execute(
            "INSERT INTO houses (id, owner, new_owner, paid, warnings, name, town_id, rent, size, beds, " +
                "bidder, bidder_name, highest_bid, internal_bid, bid_end_date, state, transfer_status) " +
                "VALUES (1, 10, -1, 0, 0, 'House', 1, 0, 1, 0, 20, 'Bidder', 0, 70, 999, 3, 0)",
        )

        val serializer = newSerializer(map, now = 1000L)
        assertThat(serializer.loadHouseInfo()).isTrue()

        val house = map.houses.getHouse(1)
        assertThat(house?.owner).isEqualTo(10L)
        assertThat(house?.bidder).isEqualTo(0L)
    }

    @Test
    fun `loadHouseInfo moves the owner out on an expired move-out`() {
        val map = newMap()
        map.houses.addHouse(1)
        execute(
            "INSERT INTO houses (id, owner, new_owner, paid, warnings, name, town_id, rent, size, beds, " +
                "bidder, bidder_name, highest_bid, internal_bid, bid_end_date, state, transfer_status) " +
                "VALUES (1, 10, -1, 0, 0, 'House', 1, 0, 1, 0, 0, '', 0, 0, 999, 4, 0)",
        )

        val transfers = mutableListOf<Pair<Int, Long>>()
        val serializer = newSerializer(
            map,
            now = 1000L,
            setTransferPlayerHouseItems = { houseId, playerId -> transfers.add(houseId to playerId) },
        )

        assertThat(serializer.loadHouseInfo()).isTrue()
        assertThat(map.houses.getHouse(1)?.owner).isEqualTo(0L)
        assertThat(map.houses.getHouse(1)?.bidEndDate).isEqualTo(0L)
        assertThat(transfers).containsExactly(1 to 10L)
    }

    @Test
    fun `loadHouseInfo keeps a pending auction when the bid has not expired`() {
        val map = newMap()
        map.houses.addHouse(1)
        execute(
            "INSERT INTO houses (id, owner, new_owner, paid, warnings, name, town_id, rent, size, beds, " +
                "bidder, bidder_name, highest_bid, internal_bid, bid_end_date, state, transfer_status) " +
                "VALUES (1, 10, -1, 0, 0, 'House', 1, 0, 1, 0, 20, 'Bidder', 100, 100, 1001, 0, 0)",
        )

        val serializer = newSerializer(map, now = 1000L)
        assertThat(serializer.loadHouseInfo()).isTrue()

        val house = map.houses.getHouse(1)
        assertThat(house?.owner).isEqualTo(10L)
        assertThat(house?.bidder).isEqualTo(20L)
        assertThat(house?.bidderName).isEqualTo("Bidder")
        assertThat(house?.bidEndDate).isEqualTo(1001L)
    }
}
