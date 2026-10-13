package org.opentibia.canary.lua.bindings.map

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.movement.areInRange
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.bindings.core.PositionFunctions
import org.opentibia.canary.lua.bindings.core.TileFunctions
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.luaToPosition
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.bindings.core.wrapItemUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.map.Town
import org.opentibia.canary.map.house.House
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Engine seam for the `House` metatable. Mirrors the `g_game()` /
 * `g_configManager()` lookups of src/lua/functions/map/house_functions.cpp so the
 * binding can stay free of globals (AGENTS.md 1.1).
 */
interface HouseOperations {
    /** `g_game().map.houses.getHouse(id)` (house_functions.cpp:61). */
    fun getHouse(id: Int): House?

    /** `g_game().map.towns.getTown(townId)` (house_functions.cpp:98). */
    fun getTown(townId: Int): Town?

    /** `g_game().getPlayerByGUID(guid)` (house_functions.cpp:171). */
    fun getPlayerByGuid(guid: Long): Player?

    /** `g_configManager().getBoolean(TOGGLE_HOUSE_TRANSFER_ON_SERVER_RESTART)` (house_functions.cpp:169). */
    fun isTransferOnServerRestart(house: House): Boolean

    /** `Player::sendTextMessage(MESSAGE_EVENT_ADVANCE, message)` (house_functions.cpp:173). */
    fun sendTextMessage(player: Player, message: String)

    /** `g_game().map.houses.getHouseByPlayerId(playerId)` (house_functions.cpp:241). */
    fun getHouseByPlayerId(playerId: Long): House?

    /** `g_game().internalStartTrade(player, tradePartner, item)` (house_functions.cpp:266). */
    fun startTrade(player: Player, tradePartner: Player, item: Item): Boolean

    /** `g_game().map.getTile(position)` backing `house:getTiles()`/`house:getItems()` (house_functions.cpp:359-393). */
    fun getTile(position: Position): Tile?
}

/**
 * Registers the Canary `House` userdata methods:
 *
 *   House(id)
 *   house:getId()
 *   house:getName()
 *   house:getTown()
 *   house:getExitPosition()
 *   house:getRent()
 *   house:getPrice()
 *   house:getOwnerGuid()
 *   house:setHouseOwner(guid[, updateDatabase = true])
 *   house:setNewOwnerGuid(guid[, updateDatabase = true])
 *   house:hasItemOnTile()
 *   house:hasNewOwnership(guid)
 *   house:startTrade(player, tradePartner)
 *   house:getBeds()
 *   house:getBedCount()
 *   house:getDoors()
 *   house:getDoorCount()
 *   house:getDoorIdByPosition(position)
 *   house:getTiles()
 *   house:getItems()
 *   house:getTileCount()
 *   house:canEditAccessList(listId, player)
 *   house:getAccessList(listId)
 *   house:setAccessList(listId, list)
 *   house:kickPlayer(player, targetPlayer)
 *   house:isInvited(player)
 *
 * It also registers the global `House(id)` class function and the `__eq` metamethod
 * mirroring HouseFunctions::luaHouseCreate and `Lua::luaUserdataCompare`.
 *
 * PARITY GAPS (documented, no invented state):
 * - `House(id)` resolves through [HouseOperations.getHouse]; Canary's shared userdata
 *   carries a `__gc` collector that is not needed by the LuaJ port.
 * - `setNewOwnerGuid` ignores the `updateDatabase` argument exactly like Canary's
 *   `house_functions.cpp:179-180` (the parameter is documented but never read).
 * - `startTrade` cannot reparent the transfer container to the Player cylinder
 *   (`transferItem->getParent()->setParent(player)`) because the port's `Player` is not
 *   a `Cylinder`; the trade still starts through [HouseOperations.startTrade].
 * - `getTiles`/`getItems` resolve each `HouseTile` through [HouseOperations.getTile];
 *   unresolved tiles are skipped because the port's `House` stores positions, not the
 *   `Tile` objects Canary pushes.
 * - `getItems` walks the Kotlin tile's full item list; Canary's `TileItemVector` excludes
 *   the ground item.
 */
object HouseFunctions {
    const val HOUSE_TYPE = "House"

    fun install(env: LuaEnvironment, operations: HouseOperations = UnwiredHouseOperations) {
        val registry = env.userdata

        env.globals.set(HOUSE_TYPE, luaFunction { args ->
            val house = operations.getHouse(args.arg(1).optint(0)) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap(HOUSE_TYPE, house)
        })

        registry.registerMethod(HOUSE_TYPE, "__eq", luaFunction { args ->
            val house = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val other = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(house == other)
        })

        registry.registerMethod(HOUSE_TYPE, "getId", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(house.id)
        })

        registry.registerMethod(HOUSE_TYPE, "getName", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(house.name)
        })

        registry.registerMethod(HOUSE_TYPE, "getTown", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            val town = operations.getTown(house.townId) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap(TownFunctions.TOWN_TYPE, town)
        })

        registry.registerMethod(HOUSE_TYPE, "getExitPosition", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap(PositionFunctions.POSITION_TYPE, house.getEntryPosition())
        })

        registry.registerMethod(HOUSE_TYPE, "getRent", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(house.getRent().toDouble())
        })

        registry.registerMethod(HOUSE_TYPE, "getPrice", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.valueOf(0)
            LuaValue.valueOf(house.getPrice().toDouble())
        })

        registry.registerMethod(HOUSE_TYPE, "getOwnerGuid", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(house.owner.toDouble())
        })

        registry.registerMethod(HOUSE_TYPE, "setHouseOwner", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            val guid = args.arg(2).toint().toLong() and 0xFFFFFFFFL
            val updateDatabase = args.arg(3).optboolean(true)
            house.setOwner(guid, updateDatabase)
            LuaValue.TRUE
        })

        registry.registerMethod(HOUSE_TYPE, "setNewOwnerGuid", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            if (operations.isTransferOnServerRestart(house) && house.hasNewOwnership()) {
                val player = operations.getPlayerByGuid(house.owner)
                if (player != null) {
                    operations.sendTextMessage(
                        player,
                        "You cannot leave this house. Ownership is already scheduled to be transferred upon the next server restart.",
                    )
                }
                return@luaFunction LuaValue.NIL
            }

            house.setNewOwnerGuid(args.arg(2).optint(0), false)
            LuaValue.TRUE
        })

        registry.registerMethod(HOUSE_TYPE, "hasItemOnTile", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(house.hasItemOnTile())
        })

        registry.registerMethod(HOUSE_TYPE, "hasNewOwnership", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.isTransferOnServerRestart(house) && house.hasNewOwnership())
        })

        registry.registerMethod(HOUSE_TYPE, "startTrade", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            val player = retrieveUserdata(args, 2) as? Player ?: return@luaFunction LuaValue.NIL
            val tradePartner = retrieveUserdata(args, 3) as? Player ?: return@luaFunction LuaValue.NIL

            if (!areInRange(tradePartner.position, player.position, 2, 2, 0)) {
                return@luaFunction LuaValue.valueOf(ReturnValue.TRADEPLAYERFARAWAY.ordinal)
            }

            if (house.owner != player.getGUID()) {
                return@luaFunction LuaValue.valueOf(ReturnValue.YOUDONTOWNTHISHOUSE.ordinal)
            }

            if (operations.getHouseByPlayerId(tradePartner.getGUID()) != null) {
                return@luaFunction LuaValue.valueOf(ReturnValue.TRADEPLAYERALREADYOWNSAHOUSE.ordinal)
            }

            if ((tradePartner.account?.getHouseBidId() ?: 0) != 0) {
                return@luaFunction LuaValue.valueOf(ReturnValue.TRADEPLAYERHIGHESTBIDDER.ordinal)
            }

            val transferItem = house.getTransferItem()
                ?: return@luaFunction LuaValue.valueOf(ReturnValue.YOUCANNOTTRADETHISHOUSE.ordinal)

            if (operations.isTransferOnServerRestart(house) && house.hasNewOwnership()) {
                operations.sendTextMessage(
                    tradePartner,
                    "You cannot buy this house. Ownership is already scheduled to be transferred upon the next server restart.",
                )
                operations.sendTextMessage(
                    player,
                    "You cannot sell this house. Ownership is already scheduled to be transferred upon the next server restart.",
                )
                return@luaFunction LuaValue.valueOf(ReturnValue.YOUCANNOTTRADETHISHOUSE.ordinal)
            }

            if (!operations.startTrade(player, tradePartner, transferItem)) {
                house.resetTransferItem()
            }

            LuaValue.valueOf(ReturnValue.NOERROR.ordinal)
        })

        registry.registerMethod(HOUSE_TYPE, "getBeds", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                house.getBeds().forEachIndexed { index, bed -> table.set(index + 1, wrapItemUserdata(env, bed)) }
            }
        })

        registry.registerMethod(HOUSE_TYPE, "getBedCount", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(house.getBedCount().toDouble())
        })

        registry.registerMethod(HOUSE_TYPE, "getDoors", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                house.getDoors().forEachIndexed { index, door -> table.set(index + 1, wrapItemUserdata(env, door)) }
            }
        })

        registry.registerMethod(HOUSE_TYPE, "getDoorCount", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(house.getDoors().size)
        })

        registry.registerMethod(HOUSE_TYPE, "getDoorIdByPosition", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            val position = luaToPosition(args.arg(2)) ?: return@luaFunction LuaValue.NIL
            val door = house.getDoorByPosition(position) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(door.getDoorId())
        })

        registry.registerMethod(HOUSE_TYPE, "getTiles", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                var index = 0
                for (houseTile in house.tiles) {
                    val tile = operations.getTile(houseTile.position) ?: continue
                    table.set(++index, env.userdata.wrap(TileFunctions.TILE_TYPE, tile))
                }
            }
        })

        registry.registerMethod(HOUSE_TYPE, "getItems", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                var index = 0
                for (houseTile in house.tiles) {
                    val tile = operations.getTile(houseTile.position) ?: continue
                    for (item in tile.items) {
                        table.set(++index, wrapItemUserdata(env, item))
                    }
                }
            }
        })

        registry.registerMethod(HOUSE_TYPE, "getTileCount", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(house.tiles.size)
        })

        registry.registerMethod(HOUSE_TYPE, "canEditAccessList", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            val player = retrieveUserdata(args, 3) as? Player ?: return@luaFunction LuaValue.NIL
            val listId = args.arg(2).toint()
            LuaValue.valueOf(house.canEditAccessList(listId, player))
        })

        registry.registerMethod(HOUSE_TYPE, "getAccessList", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            val listId = args.arg(2).toint()
            val list = house.getAccessList(listId) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(list)
        })

        registry.registerMethod(HOUSE_TYPE, "setAccessList", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            val listId = args.arg(2).toint()
            house.setAccessList(listId, args.arg(3).optjstring(""))
            LuaValue.TRUE
        })

        registry.registerMethod(HOUSE_TYPE, "kickPlayer", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            val player = retrieveUserdata(args, 2) as? Player ?: return@luaFunction LuaValue.NIL
            val targetPlayer = retrieveUserdata(args, 3) as? Player ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(house.kickPlayer(player, targetPlayer))
        })

        registry.registerMethod(HOUSE_TYPE, "isInvited", luaFunction { args ->
            val house = retrieveUserdata(args, 1) as? House ?: return@luaFunction LuaValue.NIL
            val player = retrieveUserdata(args, 2) as? Player ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(house.isInvited(player))
        })
    }
}

internal object UnwiredHouseOperations : HouseOperations {
    override fun getHouse(id: Int): House? =
        throw NotImplementedError("HouseFunctions.getHouse seam is not wired")

    override fun getTown(townId: Int): Town? =
        throw NotImplementedError("HouseFunctions.getTown seam is not wired")

    override fun getPlayerByGuid(guid: Long): Player? =
        throw NotImplementedError("HouseFunctions.getPlayerByGuid seam is not wired")

    override fun isTransferOnServerRestart(house: House): Boolean =
        throw NotImplementedError("HouseFunctions.isTransferOnServerRestart seam is not wired")

    override fun sendTextMessage(player: Player, message: String): Unit =
        throw NotImplementedError("HouseFunctions.sendTextMessage seam is not wired")

    override fun getHouseByPlayerId(playerId: Long): House? =
        throw NotImplementedError("HouseFunctions.getHouseByPlayerId seam is not wired")

    override fun startTrade(player: Player, tradePartner: Player, item: Item): Boolean =
        throw NotImplementedError("HouseFunctions.startTrade seam is not wired")

    override fun getTile(position: Position): Tile? =
        throw NotImplementedError("HouseFunctions.getTile seam is not wired")
}
