package org.opentibia.canary.map.house

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream
import org.opentibia.canary.items.ATTR_HOUSEDOORID
import org.opentibia.canary.items.AttrReadValue
import org.opentibia.canary.items.BedItem
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.INDEX_WHEREEVER
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.systems.rewardchest.ReturnValue
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private val logger = KotlinLogging.logger {}

/** `GUEST_LIST` (`src/map/map_definitions.hpp:14`). */
const val GUEST_LIST: Int = 0x100

/** `SUBOWNER_LIST` (`src/map/map_definitions.hpp:15`). */
const val SUBOWNER_LIST: Int = 0x101

/** `CONST_ME_POFF` (src/utils/utils_definitions.hpp:63). */
internal const val CONST_ME_POFF = 3

/** `CONST_ME_TELEPORT` (src/utils/utils_definitions.hpp:71). */
internal const val CONST_ME_TELEPORT = 11

/** `ITEM_DOCUMENT_RO` (src/utils/utils_definitions.hpp:555). */
internal const val ITEM_DOCUMENT_RO = 2834

/** `ITEM_LOCKER` (src/utils/utils_definitions.hpp:620). */
internal const val ITEM_LOCKER = 3497

/** `ITEM_LETTER_STAMPED` (src/utils/utils_definitions.hpp:635). */
internal const val ITEM_LETTER_STAMPED = 3506

/** `AccessHouseLevel_t` (`src/map/map_definitions.hpp:18-23`). */
enum class AccessHouseLevel(val value: Int) {
    HOUSE_NOT_INVITED(0),
    HOUSE_GUEST(1),
    HOUSE_SUBOWNER(2),
    HOUSE_OWNER(3),
}

/** `NETWORKMESSAGE_PLAYERNAME_MAXLENGTH` (src/utils/const.hpp:15). */
private const val PLAYERNAME_MAX_LENGTH = 30

/** `CyclopediaHouseState` (`src/enums/player_cyclopedia.hpp:64-69`). */
enum class CyclopediaHouseState(val value: Int) {
    Available(0),
    Rented(2),
    Transfer(3),
    MoveOut(4);

    companion object {
        fun fromValue(value: Int): CyclopediaHouseState =
            entries.firstOrNull { it.value == value } ?: Available
    }
}

data class HouseTile(
    val position: Position,
    val houseId: Int
)

/** `Houses::getHouseByPlayerId` companion lookup result (`house.cpp:137-151`). */
data class HouseOwnerInfo(val name: String, val accountId: Long)

/**
 * `formatNumber` (src/utils/tools.cpp:2162-2172): the decimal representation
 * with a comma every three digits.
 */
internal fun formatNumber(number: Long): String {
    val builder = StringBuilder(number.toString())
    var pos = builder.length - 3
    while (pos > 0) {
        builder.insert(pos, ',')
        pos -= 3
    }
    return builder.toString()
}

/**
 * Port of `class AccessList` (src/map/house/house.hpp:23-40,
 * src/map/house/house.cpp:673-796).
 *
 * The C++ list keeps player GUIDs and guild rank ids resolved through
 * `g_game()`/`IOLoginData`; the instance-owned port stores the canonical name
 * keys instead and leaves guild resolution to the caller (PARITY GAP).
 */
class AccessList {
    private var list: String = ""
    private val playerList = mutableSetOf<String>()
    private val guildRankList = mutableSetOf<String>()
    private var allowEveryone: Boolean = false

    /** `AccessList::parseList` (house.cpp:673-722). */
    fun parseList(list: String) {
        val validList = INVALID_CHARS.replace(list, "")
        val withoutEmptyLines = buildString {
            for (line in validList.split('\n')) {
                if (line.isNotEmpty()) {
                    append(line).append('\n')
                }
            }
        }

        playerList.clear()
        guildRankList.clear()
        allowEveryone = false
        this.list = withoutEmptyLines
        if (list.isEmpty()) {
            return
        }

        val lines = withoutEmptyLines.split('\n').take(MAX_LIST_LINES)
        for (rawLine in lines) {
            val line = rawLine.trim().trim('\t').trim()
            if (line.isEmpty() || line.first() == '#' || line.length > MAX_LIST_LINE_LENGTH) {
                continue
            }

            val lower = line.lowercase()
            val atPos = lower.indexOf('@')
            if (atPos >= 0) {
                if (atPos == 0) {
                    guildRankList.add(lower.substring(1))
                } else {
                    guildRankList.add(lower.substring(0, atPos) + "@" + lower.substring(atPos + 1))
                }
            } else if (lower == "*") {
                allowEveryone = true
            } else if (lower.any { it == '!' || it == '*' || it == '?' }) {
                // Remove regular expressions since they don't make much sense in houses
                continue
            } else if (lower.length <= PLAYERNAME_MAX_LENGTH) {
                playerList.add(lower)
            }
        }
    }

    /** The `AccessList::getList` accessor (house.cpp:787-789). */
    fun getList(): String = list

    /**
     * `AccessList::isInList` (house.cpp:774-785) over the canonical name keys.
     * Guild-rank membership needs the guild registry and is not resolved here.
     */
    fun isInList(playerName: String): Boolean {
        if (allowEveryone) {
            return true
        }
        return playerList.contains(playerName.lowercase())
    }

    private companion object {
        const val MAX_LIST_LINE_LENGTH = 100
        const val MAX_LIST_LINES = 100
        val INVALID_CHARS = Regex("[^a-zA-Z' \n*!@#]+")
    }
}

/**
 * Port of `class Door` (src/map/house/house.hpp:42-76,
 * src/map/house/house.cpp:793-887). The door item is an `Item` whose
 * `doorId` travels through `ATTR_HOUSEDOORID` and whose `serializeAttr` is
 * deliberately empty, exactly like the C++ override.
 */
class Door(
    id: Int,
    type: ItemType? = null,
) : Item(id, type = type) {
    private var doorId: Int = 0
    private var house: House? = null
    private val accessList = AccessList()

    override fun getDoor(): Door = this

    /** `Door::serializeAttr` (house.hpp:57) is an empty override. */
    override fun serializeAttr(propWriteStream: PropWriteStream) {
    }

    /** `Door::readAttr` (house.cpp:793-804). */
    override fun readAttr(attr: Int, propStream: PropStream): AttrReadValue {
        if (attr == ATTR_HOUSEDOORID) {
            val doorId = propStream.readU8() ?: return AttrReadValue.ATTR_READ_ERROR
            setDoorId(doorId)
            return AttrReadValue.ATTR_READ_CONTINUE
        }
        return super.readAttr(attr, propStream)
    }

    /** `Door::setDoorId` / `Door::getDoorId` (house.hpp:59-65). */
    fun setDoorId(doorId: Int) {
        this.doorId = doorId
    }

    fun getDoorId(): Int = doorId

    /** `Door::getHouse` (house.hpp:51-53). */
    fun getHouse(): House? = house

    internal fun setHouse(newHouse: House) {
        if (house != null) {
            return
        }
        house = newHouse
    }

    /** `Door::canUse` (house.cpp:819-829). */
    fun canUse(player: Player?): Boolean {
        val house = house ?: return true
        if (house.getHouseAccessLevel(player).value >= AccessHouseLevel.HOUSE_SUBOWNER.value) {
            return true
        }
        return accessList.isInList(player?.name ?: "")
    }

    /** `Door::setAccessList` (house.cpp:831-837). */
    fun setAccessList(textlist: String) {
        accessList.parseList(textlist)
    }

    /** `Door::getAccessList` (house.cpp:839-845), `null` when the door has no house. */
    fun getAccessList(): String? = if (house == null) null else accessList.getList()

    /** `Door::onRemoved` (house.cpp:848-854). */
    override fun onRemoved() {
        super.onRemoved()
        house?.removeDoor(this)
    }
}

/**
 * Port of `class HouseTransferItem` (src/map/house/house.hpp:87-101,
 * src/map/house/house.cpp:619-652): the read-only document traded for a house.
 */
class HouseTransferItem internal constructor(
    private val house: House?,
    private val world: HouseWorld,
) : Item(ITEM_DOCUMENT_RO) {

    /** `HouseTransferItem::canTransform` (house.hpp:95-97). */
    fun canTransform(): Boolean = false

    /** `HouseTransferItem::onTradeEvent` (house.cpp:629-652). */
    fun onTradeEvent(event: TradeEvents_t, owner: Player) {
        if (event == TradeEvents_t.ON_TRADE_TRANSFER) {
            val house = this.house
            if (house != null) {
                val isTransferOnRestart = world.toggleHouseTransferOnServerRestart
                val ownershipTransferMessage = " The ownership will be transferred upon server restart."
                val boughtMessage = "You have successfully bought the house." +
                    if (isTransferOnRestart) ownershipTransferMessage else ""
                val soldMessage = "You have successfully sold your house." +
                    if (isTransferOnRestart) ownershipTransferMessage else ""

                owner.sendTextMessageTyped(MessageClass.MESSAGE_EVENT_ADVANCE, boughtMessage)

                val oldOwner = world.getPlayerByGuid(house.owner)
                oldOwner?.sendTextMessageTyped(MessageClass.MESSAGE_EVENT_ADVANCE, soldMessage)

                house.executeTransfer(this, owner)
            }

            world.internalRemoveItem(this, 1)
        } else if (event == TradeEvents_t.ON_TRADE_CANCEL) {
            house?.resetTransferItem()
        }
    }

    companion object {
        /** `HouseTransferItem::createHouseTransferItem` (house.cpp:619-627). */
        fun createHouseTransferItem(house: House): HouseTransferItem {
            val transferItem = HouseTransferItem(house, house.world)
            transferItem.setID(ITEM_DOCUMENT_RO)
            transferItem.setSubType(1)
            transferItem.setAttribute(
                ItemAttribute.DESCRIPTION,
                "It is a house transfer document for '${house.name}'.",
            )
            return transferItem
        }
    }
}

/**
 * Port of `class House` (src/map/house/house.hpp:96-377,
 * src/map/house/house.cpp:22-260 and 432-1073). The `g_game()`,
 * `g_configManager()`, `g_saveManager()`, `Database` and `IOLoginData` calls
 * are injected through [world] (AGENTS.md 1.1); the remaining PARITY GAPS are
 * listed on each member.
 */
class House internal constructor(
    val id: Int,
    var name: String = "",
    var owner: Long = 0,
    @get:JvmName("getRentValue")
    var rent: Long = 0,
    var townId: Int = 0,
    private val ownerInfoLookup: (Long) -> HouseOwnerInfo? = { null },
    private val rentPeriod: () -> String = { "never" },
    internal val world: HouseWorld = NoHouseWorld,
) {
    var entryPos: Position = Position(0, 0, 0)
    var size: Int = 0
    var clientId: Int = 0
    var maxBeds: Int = 4
    var bedsCount: Int = -1
    var paidUntil: Long = 0
    var rentWarnings: Int = 0
    var bidder: Long = 0
    var bidderName: String = ""
    var highestBid: Long = 0
    var internalBid: Long = 0
    var bidHolderLimit: Long = 0
    var bidEndDate: Long = 0
    var state: CyclopediaHouseState = CyclopediaHouseState.Available
    var transferStatus: Boolean = false
    var ownerAccountId: Long = 0
    var ownerName: String = ""
    private var guildhall: Boolean = false
    private var isLoaded: Boolean = false

    private val _tiles = mutableListOf<HouseTile>()
    val tiles: List<HouseTile> get() = _tiles

    private val guestList = AccessList()
    private val subOwnerList = AccessList()
    private val doorList = mutableListOf<Door>()
    private val doorListCache = mutableMapOf<Int, String>()
    private val bedsList = mutableListOf<BedItem>()

    /** `House::transfer_container` (house.hpp:323): the locker the transfer document lives in. */
    private val transferContainer = Container(ITEM_LOCKER, capacity = 8)

    /** `House::transferItem` (house.hpp:335). */
    private var transferItem: HouseTransferItem? = null

    /** `House::hasNewOwnerOnStartup` (house.hpp:333). */
    private var hasNewOwnerOnStartup: Boolean = false

    private val failedItemTransfers = mutableListOf<String>()
    private val movedItemsLog = mutableListOf<String>()
    private var itemsFound: Long = 0
    private var itemsMoved: Long = 0
    private var totalItemsFound: Long = 0
    private var totalItemsMoved: Long = 0

    /** `House::addTile` (house.cpp:26-31). */
    fun addTile(position: Position): HouseTile {
        val tile = HouseTile(position, houseId = id)
        _tiles.add(tile)
        world.getTile(position)?.setFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
        world.markNavigationTopologyChanged(position)
        updateDoorDescription()
        return tile
    }

    /** `House::setGuildhall` / `House::isGuildhall` (house.hpp:310-314). */
    fun setGuildhall(isGuildhall: Boolean) {
        guildhall = isGuildhall
    }

    fun isGuildhall(): Boolean = guildhall

    /** `House::getEntryPosition` (house.hpp:127-129). */
    fun getEntryPosition(): Position = entryPos

    /** `House::isInvited` (house.hpp:117-119). */
    fun isInvited(player: Player?): Boolean =
        getHouseAccessLevel(player) != AccessHouseLevel.HOUSE_NOT_INVITED

    /** `House::canEditAccessList` (house.cpp:552-563). */
    fun canEditAccessList(listId: Int, player: Player?): Boolean =
        when (getHouseAccessLevel(player)) {
            AccessHouseLevel.HOUSE_OWNER -> true
            AccessHouseLevel.HOUSE_SUBOWNER -> listId == GUEST_LIST
            else -> false
        }

    /** `House::setNewOwnerGuid` (house.cpp:33-48). */
    fun setNewOwnerGuid(newOwnerGuid: Int, serverStartup: Boolean) {
        if (!world.toggleHouseTransferOnServerRestart) {
            setOwner(newOwnerGuid.toLong() and 0xFFFFFFFFL, true)
            return
        }

        world.updateNewOwnerRow(this, newOwnerGuid)
        if (!serverStartup) {
            setNewOwnership()
        }
    }

    /** `House::hasNewOwnership` / `House::setNewOwnership` (house.cpp:432-438). */
    fun hasNewOwnership(): Boolean = hasNewOwnerOnStartup

    fun setNewOwnership() {
        hasNewOwnerOnStartup = true
    }

    /** `House::clearHouseInfo` (house.cpp:50-70). */
    fun clearHouseInfo(preventOwnerDeletion: Boolean) {
        // Remove players from beds
        for (bed in bedsList) {
            if (bed.getSleeper() != 0L) {
                world.wakeUpBed(bed)
            }
        }

        // Clean access lists
        if (!preventOwnerDeletion) {
            owner = 0
            ownerAccountId = 0
        }
        setAccessList(SUBOWNER_LIST, "")
        setAccessList(GUEST_LIST, "")

        for (door in doorList) {
            door.setAccessList("")
        }
    }

    /** `House::tryTransferOwnership` (house.cpp:72-92). */
    fun tryTransferOwnership(player: Player?, serverStartup: Boolean): Boolean {
        val transferSuccess = if (player != null) {
            transferToDepot(player)
        } else {
            transferToDepot()
        }

        for (tile in _tiles) {
            val creatures = world.getTile(tile.position)?.getCreatures() ?: continue
            for (index in creatures.indices.reversed()) {
                // `Creature::getPlayer` is not overridden by the port's Player yet (PARITY GAP).
                kickPlayer(null, creatures[index] as? Player)
            }
        }

        clearHouseInfo(serverStartup)

        return transferSuccess
    }

    /** `House::setOwner` (house.cpp:94-154). */
    fun setOwner(guid: Long, updateDatabase: Boolean = true, player: Player? = null) {
        if (updateDatabase && owner != guid) {
            world.updateOwnerRow(this, guid)
        }

        if (isLoaded && owner == guid) {
            return
        }
        isLoaded = true

        if (owner != 0L) {
            tryTransferOwnership(player, false)
        }

        if (guid != 0L) {
            val currentTime = world.timeNow() + when (rentPeriod().lowercase()) {
                "yearly" -> 24 * 60 * 60 * 365
                "monthly" -> 24 * 60 * 60 * 30
                "weekly" -> 24 * 60 * 60 * 7
                "daily" -> 24 * 60 * 60
                else -> 0
            }
            paidUntil = currentTime
        } else {
            paidUntil = 0
        }

        rentWarnings = 0

        if (guid != 0L) {
            val info = ownerInfoLookup(guid) ?: return
            if (info.name.isNotEmpty()) {
                owner = guid
                ownerName = info.name
                ownerAccountId = info.accountId
                state = CyclopediaHouseState.Rented
            }
        }

        updateDoorDescription()
    }

    /** `House::updateDoorDescription` (house.cpp:156-181). */
    fun updateDoorDescription() {
        val description = buildString {
            if (owner != 0L) {
                append("It belongs to house '").append(name).append("'. ")
                append(ownerName).append(" owns this house.")
            } else {
                append("It belongs to house '").append(name).append("'. Nobody owns this house.")
            }

            if (!world.cyclopediaHouseAuction) {
                append(" It is ").append(size).append(" square meters.")
                val housePrice = getPrice()
                // C++ compares the uint32 price against `-1` after an int32 conversion.
                if (housePrice != 0xFFFFFFFFL) {
                    if (world.housePurchasedShowPrice || owner == 0L) {
                        append(" It costs ").append(formatNumber(housePrice)).append(" gold coins.")
                    }
                    val strRentPeriod = rentPeriod().lowercase()
                    if (strRentPeriod != "never") {
                        append(" The rent cost is ").append(formatNumber(getRent()))
                            .append(" gold coins and it is billed ").append(strRentPeriod).append(".")
                    }
                }
            }
        }

        for (door in doorList) {
            door.setAttribute(ItemAttribute.DESCRIPTION, description)
        }
    }

    /**
     * `House::getHouseAccessLevel` (src/map/house/house.cpp:183-210). The
     * `HOUSE_OWNED_BY_ACCOUNT` config lookup of `house.cpp:189` is an injected
     * parameter (AGENTS.md 1.1), defaulting to the disabled config value.
     */
    fun getHouseAccessLevel(player: Player?, houseOwnedByAccount: Boolean = false): AccessHouseLevel {
        if (player == null) {
            return AccessHouseLevel.HOUSE_OWNER
        }

        if (houseOwnedByAccount && ownerAccountId == (player.account?.id ?: 0L)) {
            return AccessHouseLevel.HOUSE_OWNER
        }

        if (player.hasFlag(PlayerFlags_t.CanEditHouses)) {
            return AccessHouseLevel.HOUSE_OWNER
        }

        if (player.getGUID() == owner) {
            return AccessHouseLevel.HOUSE_OWNER
        }

        if (subOwnerList.isInList(player.name)) {
            return AccessHouseLevel.HOUSE_SUBOWNER
        }

        if (guestList.isInList(player.name)) {
            return AccessHouseLevel.HOUSE_GUEST
        }

        return AccessHouseLevel.HOUSE_NOT_INVITED
    }

    /** `House::kickPlayer` (house.cpp:213-233). */
    fun kickPlayer(player: Player?, target: Player?): Boolean {
        if (target == null) {
            return false
        }

        val houseTile = target.getTile() ?: return false
        if (!houseTile.isHouse() || houseTile.getHouse() !== this) {
            return false
        }

        if (getHouseAccessLevel(player).value < getHouseAccessLevel(target).value ||
            target.hasFlag(PlayerFlags_t.CanEditHouses)
        ) {
            return false
        }

        val oldPosition = target.position
        if (world.internalTeleport(target, getEntryPosition()) == ReturnValue.NOERROR) {
            world.addMagicEffect(oldPosition, CONST_ME_POFF)
            world.addMagicEffect(getEntryPosition(), CONST_ME_TELEPORT)
        }
        return true
    }

    /** `House::setAccessList` (house.cpp:235-264). */
    fun setAccessList(listId: Int, textlist: String) {
        if (listId == GUEST_LIST) {
            guestList.parseList(textlist)
        } else if (listId == SUBOWNER_LIST) {
            subOwnerList.parseList(textlist)
        } else {
            val door = getDoorByNumber(listId)
            if (door != null) {
                door.setAccessList(textlist)
            } else {
                // The door list is only loaded after the house items are loaded, so we need to save it here for later use
                doorListCache[listId] = textlist
            }

            // We dont have kick anyone
            return
        }

        // kick uninvited players
        for (tile in _tiles) {
            val creatures = world.getTile(tile.position)?.getCreatures() ?: continue
            for (index in creatures.indices.reversed()) {
                // `Creature::getPlayer` is not overridden by the port's Player yet (PARITY GAP).
                val player = creatures[index] as? Player ?: continue
                if (!isInvited(player)) {
                    kickPlayer(null, player)
                }
            }
        }
    }

    /** `House::transferToDepot` (house.cpp:266-283). */
    fun transferToDepot(): Boolean {
        if (townId == 0) {
            return false
        }

        val player = world.getPlayerByGuid(owner)
        if (player != null) {
            transferToDepot(player)
        } else {
            val tmpPlayer = world.loadPlayerById(owner) ?: return false
            transferToDepot(tmpPlayer)
        }
        return true
    }

    /** `House::transferToDepot(player)` (house.cpp:285-306). */
    fun transferToDepot(player: Player?): Boolean {
        if (townId == 0 || player == null) {
            return false
        }

        itemsFound = 0
        itemsMoved = 0
        totalItemsFound = 0
        totalItemsMoved = 0

        for (tile in _tiles) {
            if (!transferToDepot(player, tile)) {
                return false
            }
        }

        val totalItemsMissing = if (totalItemsFound > totalItemsMoved) totalItemsFound - totalItemsMoved else 0
        logger.info {
            "[House::transferToDepot] Transfer finished for player '${player.name}'. " +
                "Total items processed: $totalItemsFound, moved: $totalItemsMoved, missing: $totalItemsMissing"
        }
        return true
    }

    /** `House::transferToDepot(player, tile)` (house.cpp:308-399). */
    fun transferToDepot(player: Player, tile: HouseTile): Boolean {
        if (townId == 0) {
            return false
        }
        val mapTile = world.getTile(tile.position)
        if (mapTile?.getHouse() !== this) {
            logger.debug { "[transferToDepot] tile house is not this house" }
            return false
        }

        failedItemTransfers.clear()
        movedItemsLog.clear()
        itemsFound = 0
        itemsMoved = 0

        val moveItemList = mutableListOf<Item>()
        for (item in mapTile.items) {
            if (world.isWrapable(item)) {
                handleWrapableItem(moveItemList, item, player, tile)
            } else if (item.isPickupable()) {
                moveItemList.add(item)
            } else {
                val container = item.getContainer()
                if (container != null) {
                    collectMovableItemsFromContainer(moveItemList, container, player, tile)
                }
            }
        }

        val playersToSave = linkedSetOf(player)
        for (item in moveItemList) {
            var targetPlayer = player

            if (item.hasOwner() && !item.isOwner(targetPlayer)) {
                val itemOwner = world.getPlayerByGuid(item.getOwnerId(), true)
                if (itemOwner != null) {
                    targetPlayer = itemOwner
                    playersToSave.add(targetPlayer)
                } else {
                    logger.warn {
                        "[transferToDepot] owner of item '${item.getName()}' (GUID: ${item.getOwnerId()}) " +
                            "not found, skipping transfer"
                    }
                    continue
                }
            }

            val itemParent = item.getParent()
            if (itemParent == null) {
                logger.warn {
                    "[transferToDepot] item '${item.getName()}' (id: ${item.id}) has no parent " +
                        "(already wrapped or removed), skipping move to avoid duplication"
                }
                continue
            }

            val result = world.internalMoveItem(
                itemParent,
                world.getInbox(targetPlayer),
                INDEX_WHEREEVER,
                item,
                item.getItemCount(),
                targetPlayer,
                CylinderFlags.FLAG_NOLIMIT,
            )
            if (result != ReturnValue.NOERROR) {
                logger.error {
                    "[transferToDepot] Failed to transfer item '${item.getName()}' (id: ${item.id}, " +
                        "pos: ${tile.position}) to '${targetPlayer.name}', error: $result"
                }
                failedItemTransfers.add("${item.getName()} (id: ${item.id}) at ${tile.position}")
                continue
            }

            ++itemsMoved
            if (world.isWrapContainer(item)) {
                val unWrapId = world.getUnWrapId(item)
                val itemType = world.getItemType(unWrapId)
                movedItemsLog.add(
                    "${item.getName()} (wrapped from ${itemType?.name ?: ""} id: ${itemType?.id ?: 0}) at ${tile.position}",
                )
                continue
            }
            movedItemsLog.add("${item.getName()} (id: ${item.id}) at ${tile.position}")
        }

        itemsFound = itemsMoved + failedItemTransfers.size

        if (itemsFound > 0 || itemsMoved > 0) {
            logger.info {
                "[House::transferToDepot] $itemsFound items found, " +
                    "$itemsMoved items successfully transferred for player '${player.name}'"
            }

            for (moved in movedItemsLog) {
                logger.info { "[House::transferToDepot] Moved: $moved" }
            }

            if (failedItemTransfers.isNotEmpty()) {
                for (entry in failedItemTransfers) {
                    logger.warn { "[House::transferToDepot] Failed to transfer: $entry" }
                }
            }
        }
        for (playerToSave in playersToSave) {
            world.savePlayer(playerToSave)
        }

        totalItemsFound += itemsFound
        totalItemsMoved += itemsMoved
        return true
    }

    /** `House::hasItemOnTile` (house.cpp:401-430). */
    fun hasItemOnTile(): Boolean {
        var foundItem = false
        for (tile in _tiles) {
            val items = world.getTile(tile.position)?.items ?: continue
            for (item in items) {
                if (world.isWrapable(item)) {
                    foundItem = true
                    logger.error {
                        "It is not possible to purchase a house with wrap item inside: " +
                            "id '${item.id}', name '${item.getName()}'"
                    }
                    break
                } else if (item.isPickupable()) {
                    foundItem = true
                    logger.error {
                        "It is not possible to purchase a house with pickupable item inside: " +
                            "id '${item.id}', name '${item.getName()}'"
                    }
                    break
                } else {
                    if (item.getContainer() != null && (item.isPickupable() || world.isWrapable(item))) {
                        foundItem = true
                        logger.error {
                            "It is not possible to purchase a house with container item inside: " +
                                "id '${item.id}', name '${item.getName()}'"
                        }
                        break
                    }
                }
            }
        }

        return foundItem
    }

    /** `House::handleWrapableItem` (house.cpp:440-457). */
    private fun handleWrapableItem(
        moveItemList: MutableList<Item>,
        item: Item,
        player: Player,
        houseTile: HouseTile,
    ) {
        if (world.isWrapContainer(item)) {
            logger.debug { "[handleWrapableItem] found wrapable container '${item.getName()}'" }
            collectMovableItemsFromContainer(moveItemList, item.getContainer(), player, houseTile)
        }

        val newItem = world.wrapItem(item, this)
        if (newItem.isRemoved() && newItem.getParent() == null) {
            logger.warn {
                "[handleWrapableItem] item removed during wrapping - check ground type - player name: " +
                    "${player.name} item id: ${item.id} position: ${houseTile.position}"
            }
            return
        }

        moveItemList.add(newItem)
    }

    /** `House::collectMovableItemsFromContainer` (house.cpp:459-481). */
    private fun collectMovableItemsFromContainer(
        moveItemList: MutableList<Item>,
        container: Container?,
        player: Player,
        houseTile: HouseTile,
    ) {
        if (container == null) {
            return
        }

        for (item in container.getItemList()) {
            if (world.isWrapable(item)) {
                handleWrapableItem(moveItemList, item, player, houseTile)
            } else if (item.isPickupable()) {
                moveItemList.add(item)
            } else {
                val innerContainer = item.getContainer()
                if (innerContainer != null) {
                    collectMovableItemsFromContainer(moveItemList, innerContainer, player, houseTile)
                }
            }
        }

        if (container.isPickupable() && !world.isWrapContainer(container)) {
            moveItemList.add(container)
        }
    }

    /** `House::getAccessList` (house.cpp:483-509). */
    fun getAccessList(listId: Int): String? {
        when (listId) {
            GUEST_LIST -> return guestList.getList()
            SUBOWNER_LIST -> return subOwnerList.getList()
        }

        val door = getDoorByNumber(listId) ?: return null

        val cached = doorListCache.remove(listId)
        if (cached != null) {
            door.setAccessList(cached)
        }
        return door.getAccessList()
    }

    /** `House::addDoor` (house.cpp:511-515). */
    fun addDoor(door: Door) {
        doorList.add(door)
        door.setHouse(this)
        updateDoorDescription()
    }

    /** `House::removeDoor` (house.cpp:517-522). */
    fun removeDoor(door: Door) {
        doorList.remove(door)
    }

    /** `House::addBed` (house.cpp:524-527). */
    fun addBed(bed: BedItem) {
        bedsList.add(bed)
        bed.setHouse(this)
    }

    /**
     * `House::removeBed` (house.cpp:529-532): the `bed->setHouse(nullptr)`
     * half cannot run because the port's `BedItem::setHouse` only accepts a
     * non-null house (PARITY GAP).
     */
    fun removeBed(bed: BedItem) {
        bedsList.remove(bed)
    }

    /** `House::getBeds` (house.hpp:217-219). */
    fun getBeds(): List<BedItem> = bedsList

    /** `House::getBedCount` (house.hpp:220-222). */
    fun getBedCount(): Long = (bedsList.size / 2).toLong()

    /** `House::getDoorByNumber` (house.cpp:534-541). */
    fun getDoorByNumber(doorId: Int): Door? = doorList.firstOrNull { it.getDoorId() == doorId }

    /** `House::getDoorByPosition` (house.cpp:543-550). */
    fun getDoorByPosition(pos: Position): Door? = doorList.firstOrNull { it.position == pos }

    /** `House::getDoors` (`house.hpp:211-214`). */
    fun getDoors(): List<Door> = doorList

    /** `House::getTransferItem` (house.cpp:565-574). */
    fun getTransferItem(): HouseTransferItem? {
        if (transferItem != null) {
            return null
        }

        transferContainer.resetParent()
        val item = HouseTransferItem.createHouseTransferItem(this)
        transferItem = item
        transferContainer.addThing(item)
        return item
    }

    /** `House::resetTransferItem` (house.cpp:576-583). */
    fun resetTransferItem() {
        val item = transferItem ?: return
        transferItem = null
        transferContainer.resetParent()
        transferContainer.removeThing(item, item.getItemCount())
    }

    /** `House::executeTransfer` (house.cpp:654-671). */
    fun executeTransfer(item: HouseTransferItem, newOwner: Player): Boolean {
        if (transferItem !== item) {
            return false
        }

        if (world.toggleHouseTransferOnServerRestart) {
            if (hasNewOwnerOnStartup) {
                return false
            }

            setNewOwnerGuid(newOwner.getGUID().toInt(), false)
        } else {
            setOwner(newOwner.getGUID())
        }
        transferItem = null
        return true
    }

    /** `House::calculateBidEndDate` (house.cpp:585-617). */
    fun calculateBidEndDate(daysToEnd: Int) {
        val daysSinceEpoch = world.timeNow() / SECONDS_PER_DAY
        val todayMidnight = daysSinceEpoch * SECONDS_PER_DAY
        val targetDay = todayMidnight + daysToEnd.toLong() * SECONDS_PER_DAY

        val params = world.serverSaveTime.split(":")
        val hour = params.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
        val minute = params.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        val second = params.getOrNull(2)?.trim()?.toIntOrNull() ?: 0

        bidEndDate = targetDay + hour * 3600L + minute * 60L + second
    }

    /** `House::getPayRentWarnings` / `House::setPayRentWarnings` (house.hpp:180-185). */
    fun getPayRentWarnings(): Long = rentWarnings.toLong()

    fun setPayRentWarnings(warnings: Long) {
        rentWarnings = warnings.toInt()
    }

    /** `House::getRent` (house.cpp:1065-1067). */
    fun getRent(): Long = (world.houseRentRate * rent.toFloat()).toLong() and 0xFFFFFFFFL

    /** `House::getPrice` (house.cpp:1069-1073). */
    fun getPrice(): Long {
        val sqmPrice = world.housePricePerSqm.toLong() * size
        val rentPrice = (getRent().toFloat() * world.housePriceRentMultiplier).toLong()
        return (sqmPrice + rentPrice) and 0xFFFFFFFFL
    }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}

/**
 * Port of `class Houses` (src/map/house/house.hpp:391-438,
 * src/map/house/house.cpp:856-1063): the id and client-id registries, the
 * house XML loader and the rent cycle. The C++ statics act as global
 * ownership; the Kotlin registry is instance-owned (AGENTS.md 1.1).
 */
class Houses(
    private val ownerInfoLookup: (Long) -> HouseOwnerInfo? = { null },
    private val rentPeriod: () -> String = { "never" },
    private val world: HouseWorld = NoHouseWorld,
) {
    /** `Houses::houseMap` (`house.hpp:434`), ordered by id like `std::map`. */
    private val houseMap = sortedMapOf<Int, House>()

    /** `Houses::houseMapClientId` (`house.hpp:435`). */
    private val houseMapClientId = linkedMapOf<Int, House>()

    /** `Houses::addHouse` (house.hpp:396-403). */
    fun addHouse(id: Int): House = houseMap.getOrPut(id) {
        House(
            id,
            ownerInfoLookup = ownerInfoLookup,
            rentPeriod = rentPeriod,
            world = world,
        )
    }

    /** `Houses::getHouse` (house.hpp:405-412). */
    fun getHouse(houseId: Int): House? = houseMap[houseId]

    /** `Houses::addHouseClientId` (house.hpp:406-412). */
    fun addHouseClientId(clientId: Int, house: House) {
        if (houseMapClientId.containsKey(clientId)) {
            return
        }
        houseMapClientId[clientId] = house
    }

    /** `Houses::getHouseByClientId` (house.hpp:414-420). */
    fun getHouseByClientId(clientId: Int): House? = houseMapClientId[clientId]

    /** `Houses::getHouseByPlayerId` (house.cpp:856-863). */
    fun getHouseByPlayerId(playerId: Long): House? =
        houseMap.values.firstOrNull { it.owner == playerId }

    /** `Houses::getAllHousesByPlayerId` (house.cpp:865-873). */
    fun getAllHousesByPlayerId(playerId: Long): List<House> =
        houseMap.values.filter { it.owner == playerId }

    /** `Houses::getHouseByBidderName` (house.cpp:875-882). */
    fun getHouseByBidderName(bidderName: String): House? =
        houseMap.values.firstOrNull { it.bidderName == bidderName }

    /** `Houses::getHouseCountByAccount` (house.cpp:884-892). */
    fun getHouseCountByAccount(accountId: Long): Int =
        houseMap.values.count { it.ownerAccountId == accountId }

    /** `Houses::getHouses` (house.hpp:429-431). */
    fun getHouses(): List<House> = houseMap.values.toList()

    /** `Houses::loadHousesXML` (house.cpp:894-951). */
    fun loadHousesXML(filename: String): Boolean {
        val doc = try {
            DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(ByteArrayInputStream(File(filename).readBytes()))
        } catch (e: Exception) {
            logger.error(e) { "[Houses::loadHousesXML] - Failed to load $filename" }
            return false
        }

        val root = doc.documentElement ?: return true
        if (!root.tagName.equals("houses", ignoreCase = true)) {
            return true
        }

        for (houseNode in elementChildren(root)) {
            if (!houseNode.hasAttribute("houseid")) {
                return false
            }

            val houseId = houseNode.getAttribute("houseid").toIntOrNull() ?: 0
            val house = getHouse(houseId)
            if (house == null) {
                logger.error { "[Houses::loadHousesXML] - Unknown house, id: $houseId" }
                return false
            }

            house.name = houseNode.getAttribute("name")

            val entryPos = Position(
                houseNode.getAttribute("entryx").toIntOrNull() ?: 0,
                houseNode.getAttribute("entryy").toIntOrNull() ?: 0,
                houseNode.getAttribute("entryz").toIntOrNull() ?: 0,
            )
            if (entryPos.x == 0 && entryPos.y == 0 && entryPos.z == 0) {
                logger.warn {
                    "[Houses::loadHousesXML] - Entry not set for house name: ${house.name} with id: $houseId"
                }
            }
            house.entryPos = entryPos

            house.rent = houseNode.getAttribute("rent").toLongOrNull() ?: 0L
            house.size = houseNode.getAttribute("size").toIntOrNull() ?: 0
            house.townId = houseNode.getAttribute("townid").toIntOrNull() ?: 0
            house.clientId = houseNode.getAttribute("clientid").toIntOrNull() ?: 0

            if (houseNode.hasAttribute("guildhall")) {
                // pugixml `as_bool` accepts 1/true/yes/on (house.cpp:930).
                val guildhall = houseNode.getAttribute("guildhall").lowercase()
                house.setGuildhall(guildhall == "1" || guildhall == "true" || guildhall == "yes" || guildhall == "on")
            }

            val maxBeds = if (houseNode.hasAttribute("beds")) {
                houseNode.getAttribute("beds").toIntOrNull() ?: 0
            } else {
                -1
            }
            house.maxBeds = maxBeds

            house.setOwner(0, false)
            addHouseClientId(house.clientId, house)
        }
        return true
    }

    /**
     * `Houses::payHouses` (house.cpp:953-1063). The C++ letter/item and money
     * side effects go through [world]; the `RentPeriod_t` decision itself is
     * `CanaryServer::setupHousesRent` (src/canary_server.cpp:353-370).
     */
    fun payHouses(rentPeriod: RentPeriod_t) {
        if (rentPeriod == RentPeriod_t.RENTPERIOD_NEVER) {
            return
        }

        val currentTime = world.timeNow()
        for (house in houseMap.values) {
            if (house.owner == 0L) {
                continue
            }

            val ownerId = house.owner
            if (!world.hasTown(house.townId)) {
                continue
            }

            val player = world.getPlayerByGuid(ownerId, true)
            if (player == null) {
                // Player doesn't exist, reset house owner
                house.tryTransferOwnership(null, true)
                continue
            }

            // Player hasn't logged in for a while, reset house owner
            val daysToReset = world.houseLoseAfterInactivity
            if (daysToReset > 0) {
                val daysSinceLastLogin = (currentTime - world.getLastLoginSaved(player)) / (60 * 60 * 24)
                val vipKeep = world.vipKeepHouse && world.isVip(player)
                val activityKeep = daysSinceLastLogin < daysToReset
                if (vipKeep && !activityKeep) {
                    logger.info {
                        "Player ${player.name} has not logged in for $daysToReset days, " +
                            "but is a VIP, so the house will not be reset."
                    }
                } else if (!vipKeep && !activityKeep) {
                    logger.info {
                        "Player ${player.name} has not logged in for $daysToReset days, so the house will be reset."
                    }
                    house.setOwner(0, true, player)
                    world.savePlayer(player)
                    continue
                }
            }

            val rent = house.getRent()
            if (rent == 0L || house.paidUntil > currentTime) {
                continue
            }

            if (world.getBankBalance(player) >= rent) {
                world.removeMoney(player, rent, 0, true)

                var paidUntil = currentTime
                when (rentPeriod) {
                    RentPeriod_t.RENTPERIOD_DAILY -> paidUntil += 24 * 60 * 60
                    RentPeriod_t.RENTPERIOD_WEEKLY -> paidUntil += 24 * 60 * 60 * 7
                    RentPeriod_t.RENTPERIOD_MONTHLY -> paidUntil += 24 * 60 * 60 * 30
                    RentPeriod_t.RENTPERIOD_YEARLY -> paidUntil += 24 * 60 * 60 * 365
                    RentPeriod_t.RENTPERIOD_NEVER -> Unit
                }

                house.paidUntil = paidUntil
            } else {
                if (house.getPayRentWarnings() < 7) {
                    val daysLeft = 7 - house.getPayRentWarnings()

                    val letter = world.createItem(ITEM_LETTER_STAMPED)
                    val period = when (rentPeriod) {
                        RentPeriod_t.RENTPERIOD_DAILY -> "daily"
                        RentPeriod_t.RENTPERIOD_WEEKLY -> "weekly"
                        RentPeriod_t.RENTPERIOD_MONTHLY -> "monthly"
                        RentPeriod_t.RENTPERIOD_YEARLY -> "annual"
                        RentPeriod_t.RENTPERIOD_NEVER -> ""
                    }

                    letter?.setAttribute(
                        ItemAttribute.TEXT,
                        "Warning! \nThe $period rent of ${house.getRent()} gold for your house " +
                            "\"${house.name}\" is payable. Have it within $daysLeft days or you will lose this house.",
                    )
                    val playerInbox = world.getInbox(player)
                    world.internalAddItem(playerInbox, letter, INDEX_WHEREEVER, CylinderFlags.FLAG_NOLIMIT)
                    house.setPayRentWarnings(house.getPayRentWarnings() + 1)
                } else {
                    house.setOwner(0, true, player)
                }
            }

            world.savePlayer(player)
        }
    }

    private fun elementChildren(node: Element): List<Element> {
        val children = mutableListOf<Element>()
        val childNodes = node.childNodes
        for (index in 0 until childNodes.length) {
            val child = childNodes.item(index)
            if (child.nodeType == Node.ELEMENT_NODE) {
                children.add(child as Element)
            }
        }
        return children
    }
}
