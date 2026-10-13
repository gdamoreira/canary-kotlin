package org.opentibia.canary.lua.bindings.map

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.BedItem
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.map.Town
import org.opentibia.canary.map.Towns
import org.opentibia.canary.map.house.Door
import org.opentibia.canary.map.house.GUEST_LIST
import org.opentibia.canary.map.house.House
import org.opentibia.canary.map.house.HouseOwnerInfo
import org.opentibia.canary.map.house.HouseWorld
import org.opentibia.canary.map.house.Houses
import org.opentibia.canary.map.house.NoHouseWorld
import org.opentibia.canary.map.house.SUBOWNER_LIST
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript-style tests for the `House` Lua binding library, mirroring
 * `src/lua/functions/map/house_functions.cpp`.
 *
 * The engine seam is fake, but every method is invoked through real Lua chunks with the
 * exact Canary names (`House(1)`, `house:getTown()`, `house:startTrade(...)`, ...).
 */
class HouseFunctionsTest {

    private class FakeHouseOperations(
        val houses: Houses = Houses(
            ownerInfoLookup = { guid -> HouseOwnerInfo(name = "Owner$guid", accountId = 1) },
        ),
        val towns: Towns = Towns(),
        private val map: GameMap? = null,
    ) : HouseOperations {
        var transferOnRestart: Boolean = false
        val messages: MutableList<String> = mutableListOf()
        val traded: MutableList<Item> = mutableListOf()
        var playerLookup: (Long) -> Player? = { null }
        var tradeStarter: (Player, Player, Item) -> Boolean = { _, _, _ -> true }

        override fun getHouse(id: Int): House? = houses.getHouse(id)

        override fun getTown(townId: Int): Town? = towns.getTown(townId)

        override fun getPlayerByGuid(guid: Long): Player? = playerLookup(guid)

        override fun isTransferOnServerRestart(house: House): Boolean = transferOnRestart

        override fun sendTextMessage(player: Player, message: String) {
            messages += message
        }

        override fun getHouseByPlayerId(playerId: Long): House? = houses.getHouseByPlayerId(playerId)

        override fun startTrade(player: Player, tradePartner: Player, item: Item): Boolean {
            traded += item
            return tradeStarter(player, tradePartner, item)
        }

        override fun getTile(position: Position): Tile? = map?.getTile(position)
    }

    private fun environment(operations: HouseOperations): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also { HouseFunctions.install(it, operations) }

    private fun player(id: Long, name: String = "Player$id", position: Position = Position(0, 0, 7)): Player =
        Player(id = id, name = name, maxHealth = 100, health = 100, position = position)

    @Test
    fun `House constructor resolves the registry entry and returns nil for an unknown id`() {
        val operations = FakeHouseOperations()
        operations.houses.addHouse(1).name = "Central Plaza"
        val environment = environment(operations)

        environment.loadChunk("house = House(1)")
        environment.loadChunk("missing = House(99)")
        environment.loadChunk("name = house:getName()")

        assertThat(environment.getGlobal("house").isuserdata()).isTrue()
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Central Plaza")
    }

    @Test
    fun `House accessors mirror the domain values`() {
        val operations = FakeHouseOperations()
        operations.towns.addTown(
            3,
            Town(3).apply {
                setName("Thais")
                setTemplePos(Position(100, 100, 7))
            },
        )
        operations.houses.addHouse(5).apply {
            name = "Villa"
            owner = 42
            rent = 100
            townId = 3
            size = 25
            entryPos = Position(10, 20, 7)
        }
        val environment = environment(operations)

        environment.loadChunk(
            """
            house = House(5)
            id = house:getId()
            name = house:getName()
            townName = house:getTown():getName()
            exitText = house:getExitPosition():toString()
            rent = house:getRent()
            price = house:getPrice()
            owner = house:getOwnerGuid()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("id").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Villa")
        assertThat(environment.getGlobal("townName").tojstring()).isEqualTo("Thais")
        assertThat(environment.getGlobal("exitText").tojstring()).isEqualTo("( 10, 20, 7 )")
        assertThat(environment.getGlobal("rent").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("price").toint()).isEqualTo(25_100)
        assertThat(environment.getGlobal("owner").toint()).isEqualTo(42)
    }

    @Test
    fun `House getPrice returns zero when the userdata is missing`() {
        val operations = FakeHouseOperations()
        val environment = environment(operations)

        environment.loadChunk("price = rawgetmetatable('House').getPrice(123)")

        assertThat(environment.getGlobal("price").toint()).isEqualTo(0)
    }

    @Test
    fun `House setHouseOwner delegates to setOwner with the updateDatabase default`() {
        val operations = FakeHouseOperations()
        val house = operations.houses.addHouse(1)
        val environment = environment(operations)

        environment.loadChunk("updated = House(1):setHouseOwner(77)")
        environment.loadChunk("guid = House(1):getOwnerGuid()")

        assertThat(environment.getGlobal("updated").toboolean()).isTrue()
        assertThat(environment.getGlobal("guid").toint()).isEqualTo(77)
        assertThat(house.owner).isEqualTo(77L)
    }

    @Test
    fun `House setNewOwnerGuid applies the guid when no restart transfer is scheduled`() {
        val operations = FakeHouseOperations()
        val house = operations.houses.addHouse(1)
        val environment = environment(operations)

        environment.loadChunk("scheduled = House(1):setNewOwnerGuid(55)")

        assertThat(environment.getGlobal("scheduled").toboolean()).isTrue()
        assertThat(house.owner).isEqualTo(55L)
    }

    @Test
    fun `House setNewOwnerGuid refuses when a restart transfer is pending and messages the owner`() {
        val operations = FakeHouseOperations()
        val house = operations.houses.addHouse(1).apply {
            owner = 7
            setNewOwnership()
        }
        operations.transferOnRestart = true
        val ownerPlayer = player(7)
        operations.playerLookup = { guid -> if (guid == 7L) ownerPlayer else null }
        val environment = environment(operations)

        environment.loadChunk("scheduled = House(1):setNewOwnerGuid(55)")

        assertThat(environment.getGlobal("scheduled").isnil()).isTrue()
        assertThat(house.owner).isEqualTo(7L)
        assertThat(operations.messages).containsExactly(
            "You cannot leave this house. Ownership is already scheduled to be transferred upon the next server restart.",
        )
    }

    @Test
    fun `House hasItemOnTile reports wrapable or pickupable items`() {
        val map = GameMap()
        val world = object : HouseWorld by NoHouseWorld {
            override fun getTile(position: Position): Tile? = map.getTile(position)
        }
        val operations = FakeHouseOperations(
            houses = Houses(
                ownerInfoLookup = { guid -> HouseOwnerInfo(name = "Owner$guid", accountId = 1) },
                world = world,
            ),
            map = map,
        )
        val tile = map.getOrCreateTile(Position(10, 20, 7))
        operations.houses.addHouse(1).addTile(Position(10, 20, 7))
        val environment = environment(operations)

        environment.loadChunk("empty = House(1):hasItemOnTile()")

        tile.addItem(Item(100, 1, ItemType(100, "wrapped parcel", isWrappable = true, wrapableTo = 200)))
        environment.loadChunk("withItem = House(1):hasItemOnTile()")

        assertThat(environment.getGlobal("empty").toboolean()).isFalse()
        assertThat(environment.getGlobal("withItem").toboolean()).isTrue()
    }

    @Test
    fun `House hasNewOwnership is gated by the restart transfer config`() {
        val operations = FakeHouseOperations()
        operations.houses.addHouse(1).setNewOwnership()
        val environment = environment(operations)

        environment.loadChunk("disabled = House(1):hasNewOwnership()")

        operations.transferOnRestart = true
        environment.loadChunk("enabled = House(1):hasNewOwnership()")

        assertThat(environment.getGlobal("disabled").toboolean()).isFalse()
        assertThat(environment.getGlobal("enabled").toboolean()).isTrue()
    }

    @Test
    fun `House bed and door lists render their userdata and counts`() {
        val operations = FakeHouseOperations()
        val house = operations.houses.addHouse(1)
        house.addTile(Position(10, 20, 7))
        house.addTile(Position(11, 20, 7))
        house.addBed(BedItem(1000))
        house.addBed(BedItem(1001))
        val door = Door(1234).apply { setDoorId(9) }
        val doorTile = Tile(Position(11, 20, 7))
        door.setParent(doorTile)
        house.addDoor(door)
        val environment = environment(operations)

        environment.loadChunk(
            """
            house = House(1)
            bedCount = #house:getBeds()
            beds = house:getBedCount()
            doorCount = #house:getDoors()
            doors = house:getDoorCount()
            tiles = house:getTileCount()
            doorId = house:getDoorIdByPosition({ x = 11, y = 20, z = 7 })
            missingDoor = house:getDoorIdByPosition({ x = 0, y = 0, z = 0 })
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("bedCount").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("beds").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("doorCount").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("doors").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("tiles").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("doorId").toint()).isEqualTo(9)
        assertThat(environment.getGlobal("missingDoor").isnil()).isTrue()
    }

    @Test
    fun `House getTiles resolves map tiles and getItems renders their item lists`() {
        val map = GameMap()
        map.getOrCreateTile(Position(10, 20, 7)).addItem(Item(2160, 5))
        map.getOrCreateTile(Position(11, 20, 7))
        val operations = FakeHouseOperations(map = map)
        val house = operations.houses.addHouse(1)
        house.addTile(Position(10, 20, 7))
        house.addTile(Position(11, 20, 7))
        val environment = environment(operations)

        environment.loadChunk(
            """
            house = House(1)
            tiles = house:getTiles()
            tileCount = #tiles
            firstText = tiles[1]:getPosition():toString()
            items = house:getItems()
            itemCount = #items
            firstId = items[1]:getId()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("tileCount").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("firstText").tojstring()).isEqualTo("( 10, 20, 7 )")
        assertThat(environment.getGlobal("itemCount").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("firstId").toint()).isEqualTo(2160)
    }

    @Test
    fun `House access list methods mirror the Canary list ids`() {
        val operations = FakeHouseOperations()
        val house = operations.houses.addHouse(1).apply { owner = 7 }
        house.setAccessList(GUEST_LIST, "Guest")
        val environment = environment(operations)
        environment.globals.set("owner", environment.userdata.wrap("Player", player(7, "Owner")))
        environment.globals.set("guest", environment.userdata.wrap("Player", player(8, "Guest")))
        environment.globals.set("stranger", environment.userdata.wrap("Player", player(9, "Stranger")))

        environment.loadChunk(
            """
            house = House(1)
            canOwnerEdit = house:canEditAccessList(0x100, owner)
            canGuestEdit = house:canEditAccessList(0x100, guest)
            canOwnerEditSub = house:canEditAccessList(0x101, owner)
            list = house:getAccessList(0x100)
            missing = house:getAccessList(99)
            stored = house:setAccessList(0x101, "Sub")
            guestInvited = house:isInvited(guest)
            strangerInvited = house:isInvited(stranger)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("canOwnerEdit").toboolean()).isTrue()
        assertThat(environment.getGlobal("canGuestEdit").toboolean()).isFalse()
        assertThat(environment.getGlobal("canOwnerEditSub").toboolean()).isTrue()
        assertThat(environment.getGlobal("list").tojstring()).isEqualTo("Guest\n")
        assertThat(environment.getGlobal("missing").toboolean()).isFalse()
        assertThat(environment.getGlobal("stored").toboolean()).isTrue()
        assertThat(environment.getGlobal("guestInvited").toboolean()).isTrue()
        assertThat(environment.getGlobal("strangerInvited").toboolean()).isFalse()
    }

    @Test
    fun `House kickPlayer teleports an uninvited target standing on a house tile`() {
        val map = GameMap()
        val operations = FakeHouseOperations(map = map)
        val house = operations.houses.addHouse(1).apply {
            owner = 7
            entryPos = Position(30, 30, 7)
        }
        house.addTile(Position(10, 20, 7))
        val tile = map.getOrCreateTile(Position(10, 20, 7))
        tile.houseId = house.id
        tile.house = house
        val target = player(8, "Target", Position(10, 20, 7)).apply { setParent(tile) }
        val environment = environment(operations)
        environment.globals.set("owner", environment.userdata.wrap("Player", player(7)))
        environment.globals.set("target", environment.userdata.wrap("Player", target))

        environment.loadChunk("kicked = House(1):kickPlayer(owner, target)")

        assertThat(environment.getGlobal("kicked").toboolean()).isTrue()
    }

    @Test
    fun `House startTrade rejects an out of range partner`() {
        val operations = FakeHouseOperations()
        operations.houses.addHouse(1).owner = 7
        val environment = environment(operations)
        environment.globals.set("owner", environment.userdata.wrap("Player", player(7, position = Position(100, 100, 7))))
        environment.globals.set("partner", environment.userdata.wrap("Player", player(8, position = Position(110, 100, 7))))

        environment.loadChunk("result = House(1):startTrade(owner, partner)")

        assertThat(environment.getGlobal("result").toint()).isEqualTo(ReturnValue.TRADEPLAYERFARAWAY.ordinal)
    }

    @Test
    fun `House startTrade rejects a player who does not own the house`() {
        val operations = FakeHouseOperations()
        operations.houses.addHouse(1).owner = 7
        val environment = environment(operations)
        environment.globals.set("owner", environment.userdata.wrap("Player", player(8, position = Position(100, 100, 7))))
        environment.globals.set("partner", environment.userdata.wrap("Player", player(9, position = Position(101, 100, 7))))

        environment.loadChunk("result = House(1):startTrade(owner, partner)")

        assertThat(environment.getGlobal("result").toint()).isEqualTo(ReturnValue.YOUDONTOWNTHISHOUSE.ordinal)
    }

    @Test
    fun `House startTrade rejects a partner who already owns a house`() {
        val operations = FakeHouseOperations()
        operations.houses.addHouse(1).owner = 7
        operations.houses.addHouse(2).owner = 8
        val environment = environment(operations)
        environment.globals.set("owner", environment.userdata.wrap("Player", player(7, position = Position(100, 100, 7))))
        environment.globals.set("partner", environment.userdata.wrap("Player", player(8, position = Position(101, 100, 7))))

        environment.loadChunk("result = House(1):startTrade(owner, partner)")

        assertThat(environment.getGlobal("result").toint()).isEqualTo(ReturnValue.TRADEPLAYERALREADYOWNSAHOUSE.ordinal)
    }

    @Test
    fun `House startTrade rejects a partner holding the highest bid`() {
        val operations = FakeHouseOperations()
        operations.houses.addHouse(1).owner = 7
        val partner = player(8, position = Position(101, 100, 7)).apply {
            account = Account(id = 1, houseBidId = 3)
        }
        val environment = environment(operations)
        environment.globals.set("owner", environment.userdata.wrap("Player", player(7, position = Position(100, 100, 7))))
        environment.globals.set("partner", environment.userdata.wrap("Player", partner))

        environment.loadChunk("result = House(1):startTrade(owner, partner)")

        assertThat(environment.getGlobal("result").toint()).isEqualTo(ReturnValue.TRADEPLAYERHIGHESTBIDDER.ordinal)
    }

    @Test
    fun `House startTrade rejects a house whose transfer item is already taken`() {
        val operations = FakeHouseOperations()
        val house = operations.houses.addHouse(1).apply { owner = 7 }
        house.getTransferItem()
        val environment = environment(operations)
        environment.globals.set("owner", environment.userdata.wrap("Player", player(7, position = Position(100, 100, 7))))
        environment.globals.set("partner", environment.userdata.wrap("Player", player(8, position = Position(101, 100, 7))))

        environment.loadChunk("result = House(1):startTrade(owner, partner)")

        assertThat(environment.getGlobal("result").toint()).isEqualTo(ReturnValue.YOUCANNOTTRADETHISHOUSE.ordinal)
    }

    @Test
    fun `House startTrade refuses a pending restart transfer and messages both players`() {
        val operations = FakeHouseOperations()
        operations.houses.addHouse(1).apply {
            owner = 7
            setNewOwnership()
        }
        operations.transferOnRestart = true
        val environment = environment(operations)
        environment.globals.set("owner", environment.userdata.wrap("Player", player(7, position = Position(100, 100, 7))))
        environment.globals.set("partner", environment.userdata.wrap("Player", player(8, position = Position(101, 100, 7))))

        environment.loadChunk("result = House(1):startTrade(owner, partner)")

        assertThat(environment.getGlobal("result").toint()).isEqualTo(ReturnValue.YOUCANNOTTRADETHISHOUSE.ordinal)
        assertThat(operations.messages).containsExactly(
            "You cannot buy this house. Ownership is already scheduled to be transferred upon the next server restart.",
            "You cannot sell this house. Ownership is already scheduled to be transferred upon the next server restart.",
        )
        assertThat(operations.traded).isEmpty()
    }

    @Test
    fun `House startTrade hands the transfer item to internalStartTrade on success`() {
        val operations = FakeHouseOperations()
        operations.houses.addHouse(1).owner = 7
        val environment = environment(operations)
        environment.globals.set("owner", environment.userdata.wrap("Player", player(7, position = Position(100, 100, 7))))
        environment.globals.set("partner", environment.userdata.wrap("Player", player(8, position = Position(101, 100, 7))))

        environment.loadChunk("result = House(1):startTrade(owner, partner)")

        assertThat(environment.getGlobal("result").toint()).isEqualTo(ReturnValue.NOERROR.ordinal)
        assertThat(operations.traded).hasSize(1)
    }

    @Test
    fun `House startTrade resets the transfer item when internalStartTrade fails`() {
        val operations = FakeHouseOperations()
        operations.houses.addHouse(1).owner = 7
        operations.tradeStarter = { _, _, _ -> false }
        val environment = environment(operations)
        environment.globals.set("owner", environment.userdata.wrap("Player", player(7, position = Position(100, 100, 7))))
        environment.globals.set("partner", environment.userdata.wrap("Player", player(8, position = Position(101, 100, 7))))

        environment.loadChunk("result = House(1):startTrade(owner, partner)")
        environment.loadChunk("second = House(1):startTrade(owner, partner)")

        assertThat(environment.getGlobal("result").toint()).isEqualTo(ReturnValue.NOERROR.ordinal)
        assertThat(environment.getGlobal("second").toint()).isEqualTo(ReturnValue.NOERROR.ordinal)
        assertThat(operations.traded).hasSize(2)
    }

    @Test
    fun `House methods are registered on the House metatable`() {
        val operations = FakeHouseOperations()
        val environment = environment(operations)

        environment.loadChunk(
            "checks = (rawgetmetatable('House').getId ~= nil) and " +
                "(rawgetmetatable('House').startTrade ~= nil) and " +
                "(rawgetmetatable('House').isInvited ~= nil) and " +
                "(rawgetmetatable('House').__eq ~= nil) and " +
                "(House ~= nil)",
        )

        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
    }
}
