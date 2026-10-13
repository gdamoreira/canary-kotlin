package org.opentibia.canary.io

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.items.ATTR_CONTAINER_ITEMS
import org.opentibia.canary.items.BedItem
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Cylinder
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.map.house.CyclopediaHouseState
import org.opentibia.canary.map.house.GUEST_LIST
import org.opentibia.canary.map.house.SUBOWNER_LIST
import java.sql.Connection
import java.time.Instant
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/** `ITEM_BATHTUB_FILLED` (src/utils/utils_definitions.hpp:705). */
private const val ITEM_BATHTUB_FILLED = 26077

/** `ITEM_BATHTUB_FILLED_NOTMOVABLE` (src/utils/utils_definitions.hpp:706). */
private const val ITEM_BATHTUB_FILLED_NOTMOVABLE = 26100

/**
 * Port of `IOMapSerialize` (src/io/iomapserialize.hpp:14-31,
 * src/io/iomapserialize.cpp) over JDBC. The C++ statics and `Database`
 * singleton become constructor-injected dependencies (AGENTS.md 1.1); the
 * `g_game()`/`IOLoginData` side effects of loading are explicit seams so the
 * persistence logic stays testable.
 */
class IOMapSerialize(
    private val dataSource: DataSource,
    private val map: GameMap,
    private val itemTypes: ItemTypeRegistry = ItemTypeRegistry(),
    private val isTransferOnServerRestart: Boolean = false,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
    private val setTransferPlayerHouseItems: (houseId: Int, playerId: Long) -> Unit = { _, _ -> },
    private val increaseBankBalance: (playerId: Long, amount: Long) -> Unit = { _, _ -> },
    /** `g_game().transformItem(item, id)` seam (iomapserialize.cpp:216). */
    private val transformItem: (item: Item, id: Int) -> Unit = { _, _ -> },
    /** `g_game().removeBedSleeper(guid)` seam (iomapserialize.cpp:233). */
    private val removeBedSleeper: (sleeperGuid: Long) -> Unit = {},
    /** `BedItem::getNextBedItem()` seam; needs map position wiring (PARITY GAP). */
    private val nextBedItem: (bed: BedItem) -> BedItem? = { null },
    /** `g_decay().startDecay(item)` seam (src/items/item.cpp:3493-3495). */
    private val startDecaying: (item: Item) -> Unit = {},
) {

    /** `IOMapSerialize::bedsToCheck` (iomapserialize.hpp:30). */
    private val bedsToCheck = mutableListOf<BedItem>()

    /** `IOMapSerialize::loadHouseItems` (iomapserialize.cpp:17-80). */
    fun loadHouseItems() {
        val blobs = try {
            dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT data FROM tile_store").use { stmt ->
                    stmt.executeQuery().use { rs ->
                        val collected = mutableListOf<ByteArray>()
                        while (rs.next()) {
                            collected.add(rs.getBytes("data") ?: ByteArray(0))
                        }
                        collected
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "[loadHouseItems] Error occurred loading house items" }
            return
        }

        for (blob in blobs) {
            val propStream = PropStream(blob)
            val x = propStream.readU16() ?: continue
            val y = propStream.readU16() ?: continue
            val z = propStream.readU8() ?: continue

            val tile = map.getTile(Position(x, y, z)) ?: continue

            val itemCount = propStream.readU32() ?: continue
            var remaining = itemCount
            while (remaining-- > 0L) {
                if (tile.isHouse()) {
                    val house = map.houses.getHouse(tile.houseId)
                    if (house != null && !isTransferOnServerRestart && house.owner == 0L) {
                        logger.trace {
                            "Skipping load item from house id: ${house.id}, position: " +
                                "(${tile.position.x}, ${tile.position.y}, ${tile.position.z}), house does not have owner"
                        }
                        house.clearHouseInfo(false)
                        continue
                    }
                }

                loadItem(propStream, tile, true)
            }
        }

        for (bed in bedsToCheck) {
            val next = nextBedItem(bed)
            val isComplete = next != null && (bed.isBedComplete(next) || next.isBedComplete(bed))
            if (!isComplete) {
                logger.warn { "[BedFix] House bug detected, correcting bed ${bed.id}." }
                bed.forceClearSleeper()
                if (next != null && nextBedItem(next) == bed) {
                    next.forceClearSleeper()
                }
            }
        }

        bedsToCheck.clear()
    }

    /** `IOMapSerialize::saveHouseItems` (iomapserialize.cpp:82-92). */
    fun saveHouseItems(): Boolean {
        val success = executeWithinTransaction { connection ->
            saveHouseItemsGuard(connection)
        }

        if (!success) {
            logger.error { "[saveHouseItems] Error occurred saving houses" }
        }
        return success
    }

    /** `IOMapSerialize::SaveHouseItemsGuard` (iomapserialize.cpp:94-128). */
    private fun saveHouseItemsGuard(connection: Connection): Boolean {
        connection.prepareStatement("DELETE FROM tile_store").use { stmt ->
            stmt.executeUpdate()
        }

        connection.prepareStatement("INSERT INTO tile_store (house_id, data) VALUES (?, ?)").use { stmt ->
            val stream = PropWriteStream()
            for (house in map.houses.getHouses()) {
                for (houseTile in house.tiles) {
                    val tile = map.getTile(houseTile.position) ?: continue
                    saveTile(stream, tile)

                    val attributes = stream.getStream()
                    if (attributes.isNotEmpty()) {
                        stmt.setInt(1, house.id)
                        stmt.setBytes(2, attributes)
                        stmt.addBatch()
                    }
                    stream.clear()
                }
            }
            stmt.executeBatch()
        }

        return true
    }

    /** `IOMapSerialize::loadContainer` (iomapserialize.cpp:130-145). */
    private fun loadContainer(propStream: PropStream, container: Container): Boolean {
        while (container.serializationCount > 0) {
            if (!loadItem(propStream, container)) {
                logger.warn { "Deserialization error for container item: ${container.id}" }
                return false
            }
            container.serializationCount--
        }

        val endAttr = propStream.readU8()
        if (endAttr == null || endAttr != 0) {
            logger.warn { "Deserialization error for container item: ${container.id}" }
            return false
        }
        return true
    }

    /** `IOMapSerialize::loadItem` (iomapserialize.cpp:147-240). */
    private fun loadItem(propStream: PropStream, parent: Cylinder, isHouseItem: Boolean = false): Boolean {
        val id = propStream.readU16() ?: return false

        val tile = parent as? Tile

        val itemType = itemTypes.get(id)
        val isBed = itemType?.isBed == true
        val movable = itemType?.isMovable == true
        val transformOnUse = itemType?.transformOnUse ?: 0

        if (isBed || movable || tile == null || itemType?.isCarpet == true || itemType?.isTrashHolder == true) {
            // create a new item
            val item = createItem(id)
            if (item != null) {
                if (item.unserializeAttr(propStream)) {
                    // Remove only not movable and not sleeper bed
                    val bed = item.getBed()
                    if (isHouseItem && isBed && bed != null && bed.sleeperGUID == 0L && !movable) {
                        return false
                    }
                    val container = item.getContainer()
                    if (container != null && !loadContainer(propStream, container)) {
                        return false
                    }

                    parent.internalAddThing(item)
                    startDecaying(item)
                } else {
                    logger.warn { "Deserialization error in $id" }
                    return false
                }

                val bed = item.getBed()
                if (bed != null && bed.sleeperGUID != 0L) {
                    bedsToCheck.add(bed)
                }
            }
        } else {
            // Stationary items like doors/beds/blackboards/bookcases
            var item: Item? = null
            for (findItem in tile.items) {
                if (findItem.id == id) {
                    item = findItem
                    break
                } else if (transformOnUse != 0 && findItem.id == transformOnUse) {
                    item = findItem
                    break
                } else if (itemType?.isDoor == true && findItem.getDoor() != null) {
                    item = findItem
                    break
                } else if (itemType?.isBed == true && findItem.getBed() != null) {
                    item = findItem
                    break
                }
            }

            if (item != null) {
                if (item.unserializeAttr(propStream)) {
                    val container = item.getContainer()
                    if (container != null && !loadContainer(propStream, container)) {
                        return false
                    }

                    transformItem(item, id)
                } else {
                    logger.warn { "Deserialization error in $id" }
                }
            } else {
                // The map changed since the last save, just read the attributes
                val dummy = createItem(id)
                if (dummy != null) {
                    dummy.unserializeAttr(propStream)
                    val container = dummy.getContainer()
                    if (container != null) {
                        if (!loadContainer(propStream, container)) {
                            return false
                        }
                    } else {
                        val bedItem = dummy as? BedItem
                        if (bedItem != null) {
                            val sleeperGuid = bedItem.getSleeper()
                            if (sleeperGuid != 0L) {
                                removeBedSleeper(sleeperGuid)
                            }
                        }
                    }
                }
            }
        }
        return true
    }

    /** `IOMapSerialize::saveItem` (iomapserialize.cpp:242-259). */
    private fun saveItem(stream: PropWriteStream, item: Item) {
        val container = item.getContainer()

        // Write ID & props
        stream.writeU16(item.id)
        item.serializeAttr(stream)

        if (container != null) {
            // Hack our way into the attributes
            stream.writeU8(ATTR_CONTAINER_ITEMS)
            stream.writeU32(container.size().toLong())
            for (child in container.getReversedItems()) {
                saveItem(stream, child)
            }
        }

        stream.writeU8(0x00) // attr end
    }

    /** `IOMapSerialize::saveTile` (iomapserialize.cpp:261-294). */
    private fun saveTile(stream: PropWriteStream, tile: Tile) {
        val items = ArrayDeque<Item>()
        var count = 0
        for (item in tile.items) {
            if (item.id == ITEM_BATHTUB_FILLED_NOTMOVABLE) {
                val tub = createItem(ITEM_BATHTUB_FILLED) ?: Item(ITEM_BATHTUB_FILLED)
                items.addFirst(tub)
                count++
                continue
            } else if (!item.isSavedToHouses()) {
                continue
            }

            items.addFirst(item)
            count++
        }

        if (items.isNotEmpty()) {
            stream.writeU16(tile.position.x)
            stream.writeU16(tile.position.y)
            stream.writeU8(tile.position.z)

            stream.writeU32(count.toLong())
            for (item in items) {
                saveItem(stream, item)
            }
        }
    }

    /** `Item::CreateItem` (src/items/item.cpp:393), null for an unregistered id. */
    private fun createItem(id: Int): Item? {
        if (itemTypes.get(id) == null) {
            return null
        }
        return Item.create(id, registry = itemTypes)
    }

    /** `IOMapSerialize::loadHouseInfo` (iomapserialize.cpp:296-389). */
    fun loadHouseInfo(): Boolean {
        val rows = try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT id, owner, new_owner, bidder, bidder_name, highest_bid, internal_bid, bid_end_date, " +
                        "state, transfer_status FROM houses",
                ).use { stmt ->
                    stmt.executeQuery().use { rs ->
                        val collected = mutableListOf<HouseInfoRow>()
                        while (rs.next()) {
                            collected.add(
                                HouseInfoRow(
                                    id = rs.getInt("id"),
                                    owner = rs.getLong("owner"),
                                    newOwner = rs.getLong("new_owner"),
                                    bidder = rs.getLong("bidder"),
                                    bidderName = rs.getString("bidder_name") ?: "",
                                    highestBid = rs.getLong("highest_bid"),
                                    internalBid = rs.getLong("internal_bid"),
                                    bidEndDate = rs.getLong("bid_end_date"),
                                    state = CyclopediaHouseState.fromValue(rs.getInt("state")),
                                    transferStatus = rs.getInt("transfer_status") != 0,
                                ),
                            )
                        }
                        collected
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "[loadHouseInfo] Error occurred loading houses info" }
            return false
        }

        if (rows.isEmpty()) {
            return false
        }

        for (row in rows) {
            val houseId = row.id
            val house = map.houses.getHouse(houseId) ?: continue

            var owner = row.owner
            var newOwner = row.newOwner
            var bidder = row.bidder
            var bidderName = row.bidderName
            var highestBid = row.highestBid
            var internalBid = row.internalBid
            var bidEndDate = row.bidEndDate
            var state = row.state
            var transferStatus = row.transferStatus
            val timeNow = now()

            if (isTransferOnServerRestart && newOwner >= 0) {
                setTransferPlayerHouseItems(houseId, owner)
                if (newOwner == 0L) {
                    logger.debug { "Removing house id '$houseId' owner" }
                    house.setOwner(0)
                } else {
                    logger.debug { "Setting house id '$houseId' owner to player GUID '$newOwner'" }
                    house.setOwner(newOwner)
                }
            } else if (state == CyclopediaHouseState.Available && timeNow > bidEndDate && bidder > 0) {
                logger.debug { "[BID] - Setting house id '$houseId' owner to player GUID '$bidder'" }
                if (highestBid < internalBid) {
                    val diff = internalBid - highestBid
                    increaseBankBalance(bidder, diff)
                }
                house.setOwner(bidder)
                bidder = 0
                bidderName = ""
                highestBid = 0
                internalBid = 0
                bidEndDate = 0
            } else if (state == CyclopediaHouseState.Transfer && timeNow > bidEndDate && bidder > 0) {
                logger.debug {
                    "[TRANSFER] - Removing house id '$houseId' from owner GUID '$owner' and transfering to new owner GUID '$bidder'"
                }
                if (transferStatus) {
                    setTransferPlayerHouseItems(houseId, owner)
                    house.setOwner(bidder)
                    increaseBankBalance(owner, internalBid)
                } else {
                    house.setOwner(owner)
                }
                bidder = 0
                bidderName = ""
                internalBid = 0
                bidEndDate = 0
                transferStatus = false
            } else if (state == CyclopediaHouseState.MoveOut && timeNow > bidEndDate) {
                logger.debug { "[MOVE OUT] - Removing house id '$houseId' owner" }
                setTransferPlayerHouseItems(houseId, owner)
                house.setOwner(0)
                bidEndDate = 0
            } else {
                house.setOwner(owner, false)
                house.state = state
            }

            house.bidder = bidder
            house.bidderName = bidderName
            house.highestBid = highestBid
            house.internalBid = internalBid
            house.bidHolderLimit = internalBid
            house.bidEndDate = bidEndDate
            house.transferStatus = transferStatus
        }

        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT house_id, listid, list FROM house_lists").use { stmt ->
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val house = map.houses.getHouse(rs.getInt("house_id")) ?: continue
                            val listId = rs.getInt("listid")
                            house.setAccessList(listId, rs.getString("list") ?: "")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "[loadHouseInfo] Error occurred loading house lists" }
        }
        return true
    }

    /** `IOMapSerialize::saveHouseInfo` (iomapserialize.cpp:391-401). */
    fun saveHouseInfo(): Boolean {
        val success = executeWithinTransaction { connection ->
            saveHouseInfoGuard(connection)
        }

        if (!success) {
            logger.error { "[saveHouseInfo] Error occurred saving houses info" }
        }
        return success
    }

    /** `IOMapSerialize::SaveHouseInfoGuard` (iomapserialize.cpp:403-468). */
    private fun saveHouseInfoGuard(connection: Connection): Boolean {
        // Canary uses MySQL `ON DUPLICATE KEY UPDATE` (DBInsert::execute,
        // database.cpp:453-481). PostgreSQL 16 gets the equivalent
        // `ON CONFLICT ... DO UPDATE`; H2's PostgreSQL mode does not parse
        // `ON CONFLICT DO UPDATE`, so local runs use H2's `MERGE ... KEY`.
        val h2 = !connection.metaData.databaseProductName.contains("PostgreSQL", ignoreCase = true)
        val houseUpdate = if (h2) {
            """
            MERGE INTO houses (id, owner, paid, warnings, name, town_id, rent, size, beds, bidder,
                bidder_name, highest_bid, internal_bid, bid_end_date, state, transfer_status)
            KEY (id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        } else {
            """
            INSERT INTO houses (id, owner, paid, warnings, name, town_id, rent, size, beds, bidder,
                bidder_name, highest_bid, internal_bid, bid_end_date, state, transfer_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                owner = EXCLUDED.owner,
                paid = EXCLUDED.paid,
                warnings = EXCLUDED.warnings,
                name = EXCLUDED.name,
                town_id = EXCLUDED.town_id,
                rent = EXCLUDED.rent,
                size = EXCLUDED.size,
                beds = EXCLUDED.beds,
                bidder = EXCLUDED.bidder,
                bidder_name = EXCLUDED.bidder_name,
                highest_bid = EXCLUDED.highest_bid,
                internal_bid = EXCLUDED.internal_bid,
                bid_end_date = EXCLUDED.bid_end_date,
                state = EXCLUDED.state,
                transfer_status = EXCLUDED.transfer_status
            """.trimIndent()
        }

        connection.prepareStatement(houseUpdate).use { stmt ->
            for (house in map.houses.getHouses()) {
                stmt.setInt(1, house.id)
                stmt.setLong(2, house.owner)
                stmt.setLong(3, house.paidUntil)
                stmt.setInt(4, house.rentWarnings)
                stmt.setString(5, house.name)
                stmt.setInt(6, house.townId)
                stmt.setLong(7, house.rent)
                stmt.setInt(8, house.size)
                stmt.setInt(9, house.bedsCount)
                stmt.setLong(10, house.bidder)
                stmt.setString(11, house.bidderName)
                stmt.setLong(12, house.highestBid)
                stmt.setLong(13, house.internalBid)
                stmt.setLong(14, house.bidEndDate)
                stmt.setInt(15, house.state.value)
                stmt.setInt(16, if (house.transferStatus) 1 else 0)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }

        val listUpdate = if (h2) {
            """
            MERGE INTO house_lists (house_id, listid, list, version)
            KEY (house_id, listid)
            VALUES (?, ?, ?, ?)
            """.trimIndent()
        } else {
            """
            INSERT INTO house_lists (house_id, listid, list, version)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (house_id, listid) DO UPDATE SET
                list = EXCLUDED.list,
                version = EXCLUDED.version
            """.trimIndent()
        }

        val version = getTimeUsNow()
        connection.prepareStatement(listUpdate).use { stmt ->
            for (house in map.houses.getHouses()) {
                val guestList = house.getAccessList(GUEST_LIST) ?: ""
                if (guestList.isNotEmpty()) {
                    stmt.setInt(1, house.id)
                    stmt.setInt(2, GUEST_LIST)
                    stmt.setString(3, guestList)
                    stmt.setLong(4, version)
                    stmt.addBatch()
                }

                val subOwnerList = house.getAccessList(SUBOWNER_LIST) ?: ""
                if (subOwnerList.isNotEmpty()) {
                    stmt.setInt(1, house.id)
                    stmt.setInt(2, SUBOWNER_LIST)
                    stmt.setString(3, subOwnerList)
                    stmt.setLong(4, version)
                    stmt.addBatch()
                }

                for (door in house.getDoors()) {
                    val doorList = door.getAccessList() ?: ""
                    if (doorList.isNotEmpty()) {
                        stmt.setInt(1, house.id)
                        stmt.setInt(2, door.getDoorId())
                        stmt.setString(3, doorList)
                        stmt.setLong(4, version)
                        stmt.addBatch()
                    }
                }
            }
            stmt.executeBatch()
        }

        connection.prepareStatement("DELETE FROM house_lists WHERE version < ?").use { stmt ->
            stmt.setLong(1, version)
            stmt.executeUpdate()
        }

        return true
    }

    /**
     * `DBTransaction::executeWithinTransaction` (src/database/database.hpp:233-256):
     * the callback runs inside one transaction; a false callback result or a
     * thrown exception rolls the transaction back.
     */
    private fun executeWithinTransaction(action: (Connection) -> Boolean): Boolean {
        dataSource.connection.use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            return try {
                val result = action(connection)
                if (result) {
                    connection.commit()
                } else {
                    connection.rollback()
                }
                result
            } catch (e: Exception) {
                connection.rollback()
                logger.error(e) { "[executeWithinTransaction] Error occurred during transaction" }
                false
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    /** `getTimeUsNow` (src/utils/tools.cpp:542-545). */
    private fun getTimeUsNow(): Long {
        val instant = Instant.now()
        return instant.epochSecond * 1_000_000 + instant.nano / 1000
    }
}

/** One buffered `houses` row read by [IOMapSerialize.loadHouseInfo]. */
private data class HouseInfoRow(
    val id: Int,
    val owner: Long,
    val newOwner: Long,
    val bidder: Long,
    val bidderName: String,
    val highestBid: Long,
    val internalBid: Long,
    val bidEndDate: Long,
    val state: CyclopediaHouseState,
    val transferStatus: Boolean,
)
