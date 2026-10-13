package org.opentibia.canary.map.house

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream
import org.opentibia.canary.items.ATTR_HOUSEDOORID
import org.opentibia.canary.items.AttrReadValue
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
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

    /** `Door::setAccessList` (house.cpp:831-837). */
    fun setAccessList(textlist: String) {
        accessList.parseList(textlist)
    }

    /** `Door::getAccessList` (house.cpp:839-845), `null` when the door has no house. */
    fun getAccessList(): String? = if (house == null) null else accessList.getList()
}

/**
 * Port of the state and access-list half of `class House`
 * (src/map/house/house.hpp:96-373, src/map/house/house.cpp:22-260). World
 * mutation (`tryTransferOwnership`, `kickPlayer`, door descriptions), the
 * legacy `UPDATE houses` write inside `setOwner` and the bed/item containers
 * need the game engine and are intentionally not ported (PARITY GAP).
 */
class House internal constructor(
    val id: Int,
    var name: String = "",
    var owner: Long = 0,
    var rent: Long = 0,
    var townId: Int = 0,
    private val ownerInfoLookup: (Long) -> HouseOwnerInfo? = { null },
    private val rentPeriod: () -> String = { "never" },
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

    /** `House::addTile` (house.cpp:26-31); flags/navigation updates are not ported. */
    fun addTile(position: Position): HouseTile {
        val tile = HouseTile(position, houseId = id)
        _tiles.add(tile)
        return tile
    }

    /** `House::setGuildhall` / `House::isGuildhall` (house.hpp:310-314). */
    fun setGuildhall(isGuildhall: Boolean) {
        guildhall = isGuildhall
    }

    fun isGuildhall(): Boolean = guildhall

    /** `House::setOwner` (house.cpp:94-153), state-only (no live `UPDATE houses`). */
    fun setOwner(guid: Long, updateDatabase: Boolean = true, player: Player? = null) {
        if (isLoaded && owner == guid) {
            return
        }
        isLoaded = true

        if (owner != 0L) {
            // `House::tryTransferOwnership(player, false)` needs the container
            // transfer engine and is a PARITY GAP.
        }

        if (guid != 0L) {
            paidUntil = when (rentPeriod().lowercase()) {
                "yearly" -> now() + 24 * 60 * 60 * 365
                "monthly" -> now() + 24 * 60 * 60 * 30
                "weekly" -> now() + 24 * 60 * 60 * 7
                "daily" -> now() + 24 * 60 * 60
                else -> 0
            }
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
    }

    /** `House::addDoor` (house.cpp:503-507). */
    fun addDoor(door: Door) {
        doorList.add(door)
        door.setHouse(this)
    }

    /** `House::getDoors` (`house.hpp:211-214`). */
    fun getDoors(): List<Door> = doorList

    /** `House::setAccessList` (house.cpp:235-263), without the kick side effect. */
    fun setAccessList(listId: Int, textlist: String) {
        when (listId) {
            GUEST_LIST -> guestList.parseList(textlist)
            SUBOWNER_LIST -> subOwnerList.parseList(textlist)
            else -> {
                val door = getDoorByNumber(listId)
                if (door != null) {
                    door.setAccessList(textlist)
                } else {
                    // The door list is only matched after the house items are
                    // loaded, so cache it for `getAccessList` to flush.
                    doorListCache[listId] = textlist
                }
            }
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

    /** `House::getDoorByNumber` (house.cpp:515-523). */
    fun getDoorByNumber(lookId: Int): Door? = doorList.firstOrNull { it.getDoorId() == lookId }

    /** `House::clearHouseInfo` (house.cpp:50-70); bed wake-up is a PARITY GAP. */
    fun clearHouseInfo(preventOwnerDeletion: Boolean) {
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

    /** Existing engine helper: house permission check over the access lists. */
    fun canEnter(player: Player): Boolean {
        if (owner != 0L && player.id == owner) return true
        val name = player.name.lowercase()
        return subOwnerList.isInList(name) || guestList.isInList(name)
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

    private fun now(): Long = System.currentTimeMillis() / 1000
}

/**
 * Port of `class Houses` (src/map/house/house.hpp:391-438,
 * src/map/house/house.cpp:860-938): the id and client-id registries plus the
 * house XML loader. The C++ statics act as global ownership; the Kotlin
 * registry is instance-owned (AGENTS.md 1.1).
 */
class Houses(
    private val ownerInfoLookup: (Long) -> HouseOwnerInfo? = { null },
    private val rentPeriod: () -> String = { "never" },
) {
    /** `Houses::houseMap` (`house.hpp:434`), ordered by id like `std::map`. */
    private val houseMap = sortedMapOf<Int, House>()

    /** `Houses::houseMapClientId` (`house.hpp:435`). */
    private val houseMapClientId = linkedMapOf<Int, House>()

    /** `Houses::addHouse` (house.hpp:396-403). */
    fun addHouse(id: Int): House = houseMap.getOrPut(id) {
        House(id, ownerInfoLookup = ownerInfoLookup, rentPeriod = rentPeriod)
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

    /** `Houses::getHouseByPlayerId` (house.cpp:860-868). */
    fun getHouseByPlayerId(playerId: Long): House? =
        houseMap.values.firstOrNull { it.owner == playerId }

    /** `Houses::getHouses` (house.hpp:429-431). */
    fun getHouses(): List<House> = houseMap.values.toList()

    /** `Houses::loadHousesXML` (house.cpp:894-938). */
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
