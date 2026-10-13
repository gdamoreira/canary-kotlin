package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.npcs.NpcInfo
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.luaToPosition
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.npcshop.ShopBlock

/**
 * Engine seam for the `Npc` metatable. Mirrors the primitives of
 * src/lua/functions/creatures/npc/npc_functions.cpp.
 *
 * Every method returns `null` when the receiver is not an npc, matching Canary's
 * `Lua::getUserdataShared<Npc>(L, 1, "Npc")` nil results.
 */
interface NpcOperations {
    /** `Npc(id)` resolving `Game::getNpcByID`; null when absent. */
    fun createById(id: Long): Any?

    /** `Npc(name)` resolving `Game::getNpcByName`; null when absent. */
    fun createByName(name: String): Any?

    fun isNpc(value: Any?): Boolean
    fun setMasterPos(npc: Any, position: Position): Boolean?
    fun getCurrency(npc: Any): Int?
    fun setCurrency(npc: Any, currency: Int): Boolean?
    fun getSpeechBubble(npc: Any): Int?
    fun setSpeechBubble(npc: Any, speechBubble: Int): Boolean?
    fun getId(npc: Any): Long?
    fun getName(npc: Any): String?
    fun setName(npc: Any, name: String): Boolean?
    fun place(npc: Any, position: Position, extended: Boolean, force: Boolean): Any?
    fun say(npc: Any, text: String, type: Int, ghost: Boolean, target: Any?, position: Position?): Boolean?
    fun turnToCreature(npc: Any, creature: Any?, stopEventWalk: Boolean): Boolean?
    fun setPlayerInteraction(npc: Any, creature: Any?, topicId: Int): Boolean?
    fun removePlayerInteraction(npc: Any, creature: Any?): Boolean?
    fun isInteractingWithPlayer(npc: Any, creature: Any?): Boolean?
    fun isInTalkRange(npc: Any, position: Position, range: Int): Boolean?
    fun isPlayerInteractingOnTopic(npc: Any, creature: Any?, topicId: Int): Boolean?
    fun openShopWindow(npc: Any, player: Any?): Boolean?
    fun openShopWindowTable(npc: Any, player: Any?, items: List<ShopData>): Boolean?
    fun closeShopWindow(npc: Any, player: Any?): Boolean?
    fun getShopItem(npc: Any, playerGuid: Long): List<ShopData>?
    fun isMerchant(npc: Any, playerGuid: Long): Boolean?
    fun move(npc: Any, direction: Int): Boolean?
    fun turn(npc: Any, direction: Int): Boolean?
    fun follow(npc: Any, player: Any?): Boolean?
    fun sellItem(
        npc: Any,
        player: Any?,
        itemId: Int,
        amount: Double,
        subType: Int,
        actionId: Int,
        ignoreCap: Boolean,
        inBackpacks: Boolean,
    ): Boolean?

    fun getDistanceTo(npc: Any, uid: Int): Int?
}

/**
 * Registers the Canary `Npc` methods from
 * src/lua/functions/creatures/npc/npc_functions.cpp:
 *
 *   Npc([id or name or userdata])
 *   npc:isNpc()
 *   npc:setMasterPos(pos)
 *   npc:getCurrency()
 *   npc:setCurrency(currency)
 *   npc:getSpeechBubble()
 *   npc:setSpeechBubble(speechBubble)
 *   npc:getId()
 *   npc:getName()
 *   npc:setName(name)
 *   npc:place(position[, extended = false[, force = true]])
 *   npc:say(text[, type = TALKTYPE_PRIVATE_NP[, ghost = false[, target[, position]]]])
 *   npc:turnToCreature(creature[, stopEventWalk = true])
 *   npc:setPlayerInteraction(creature[, topic = 0])
 *   npc:removePlayerInteraction(creature)
 *   npc:isInteractingWithPlayer(creature)
 *   npc:isInTalkRange(position[, range = 4])
 *   npc:isPlayerInteractingOnTopic(creature[, topicId = 0])
 *   npc:openShopWindow(player)
 *   npc:openShopWindowTable(player, items)
 *   npc:closeShopWindow(player)
 *   npc:getShopItem([playerGUID = 0])
 *   npc:isMerchant([playerGUID = 0])
 *   npc:move(direction)
 *   npc:turn(direction)
 *   npc:follow(player)
 *   npc:sellItem(player, itemId, amount[, subtype = 1[, actionid = 0[, ignoreCap = false[, inBackpacks = false]]]])
 *   npc:getDistanceTo(uid)
 *
 * Fidelity quirks kept from the C++ source: `setCurrency`/`setName`/`setSpeechBubble`
 * push no result on their success path and therefore evaluate to `nil`; `move`/`turn`
 * return no values; `getShopItem` answers `true` (the C++ field writes target the
 * receiver stack slot, which this port cannot reproduce; PARITY GAP).
 */
object NpcFunctions {
    const val NPC_TYPE = "Npc"

    /** `TALKTYPE_PRIVATE_NP` (src/utils/utils_definitions.hpp:356). */
    private const val TALKTYPE_PRIVATE_NP = 10

    fun install(env: LuaEnvironment, operations: NpcOperations = WorldNpcOperations()) {
        val registry = env.userdata

        env.globals.set(NPC_TYPE, luaFunction { args ->
            val argument = args.arg(1)
            val npc = when {
                argument.isnumber() -> operations.createById(argument.tolong())
                argument.isstring() -> operations.createByName(argument.tojstring())
                argument.isuserdata() -> argument.touserdata()?.takeIf { operations.isNpc(it) }
                else -> null
            }
            npc?.let { env.userdata.wrap(NPC_TYPE, it) } ?: LuaValue.NIL
        })

        registry.registerMethod(NPC_TYPE, "isNpc", luaFunction { args ->
            LuaValue.valueOf(operations.isNpc(retrieveUserdata(args, 1)))
        })

        registry.registerMethod(NPC_TYPE, "setMasterPos", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            val position = luaToPosition(args.arg(2)) ?: Position(0, 0, 0)
            operations.setMasterPos(npc, position)?.let { LuaValue.valueOf(it) } ?: LuaValue.FALSE
        })

        registry.registerMethod(NPC_TYPE, "getCurrency", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getCurrency(npc)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(NPC_TYPE, "setCurrency", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            operations.setCurrency(npc, args.arg(2).toint())
            LuaValue.NIL
        })

        registry.registerMethod(NPC_TYPE, "getSpeechBubble", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getSpeechBubble(npc)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(NPC_TYPE, "setSpeechBubble", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setSpeechBubble(npc, args.arg(2).toint())
            LuaValue.NIL
        })

        registry.registerMethod(NPC_TYPE, "getId", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getId(npc)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
        })

        registry.registerMethod(NPC_TYPE, "getName", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getName(npc)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(NPC_TYPE, "setName", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setName(npc, args.arg(2).tojstring())
            LuaValue.NIL
        })

        registry.registerMethod(NPC_TYPE, "place", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val position = luaToPosition(args.arg(2)) ?: Position(0, 0, 0)
            val extended = args.arg(3).optboolean(false)
            val force = args.arg(4).optboolean(true)
            operations.place(npc, position, extended, force)?.let { env.userdata.wrap(NPC_TYPE, it) } ?: LuaValue.NIL
        })

        registry.registerMethod(NPC_TYPE, "say", luaFunction { args ->
            val parameters = args.narg()
            var position: Position? = null
            if (parameters >= 6) {
                val parsed = luaToPosition(args.arg(6)) ?: Position(0, 0, 0)
                if (parsed.x == 0 || parsed.y == 0) {
                    return@luaFunction LuaValue.FALSE
                }
                position = parsed
            }
            val target = if (parameters >= 5) retrieveUserdata(args, 5) else null
            val ghost = args.arg(4).optboolean(false)
            val type = args.arg(3).optint(TALKTYPE_PRIVATE_NP)
            val text = args.arg(2).tojstring()
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.say(npc, text, type, ghost, target, position)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(NPC_TYPE, "turnToCreature", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val creature = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.NIL
            val stopEventWalk = args.arg(3).optboolean(true)
            operations.turnToCreature(npc, creature, stopEventWalk)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(NPC_TYPE, "setPlayerInteraction", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val creature = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.NIL
            operations.setPlayerInteraction(npc, creature, args.arg(3).optint(0))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        registry.registerMethod(NPC_TYPE, "removePlayerInteraction", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val creature = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.NIL
            operations.removePlayerInteraction(npc, creature)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(NPC_TYPE, "isInteractingWithPlayer", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val creature = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.NIL
            operations.isInteractingWithPlayer(npc, creature)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(NPC_TYPE, "isInTalkRange", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val position = luaToPosition(args.arg(2)) ?: Position(0, 0, 0)
            val range = args.arg(3).optint(4)
            operations.isInTalkRange(npc, position, range)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(NPC_TYPE, "isPlayerInteractingOnTopic", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val creature = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.NIL
            operations.isPlayerInteractingOnTopic(npc, creature, args.arg(3).optint(0))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        registry.registerMethod(NPC_TYPE, "openShopWindow", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            val player = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.FALSE
            operations.openShopWindow(npc, player)?.let { LuaValue.valueOf(it) } ?: LuaValue.FALSE
        })

        registry.registerMethod(NPC_TYPE, "openShopWindowTable", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            val player = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.NIL
            if (!args.arg(3).istable()) {
                return@luaFunction LuaValue.FALSE
            }
            val items = shopDataList(args.arg(3).checktable())
            operations.openShopWindowTable(npc, player, items)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(NPC_TYPE, "closeShopWindow", luaFunction { args ->
            val player = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.FALSE
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            operations.closeShopWindow(npc, player)?.let { LuaValue.valueOf(it) } ?: LuaValue.FALSE
        })

        registry.registerMethod(NPC_TYPE, "getShopItem", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            operations.getShopItem(npc, args.arg(2).optlong(0L))
            LuaValue.TRUE
        })

        registry.registerMethod(NPC_TYPE, "isMerchant", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            operations.isMerchant(npc, args.arg(2).optlong(0L))?.let { LuaValue.valueOf(it) } ?: LuaValue.FALSE
        })

        registry.registerMethod(NPC_TYPE, "move", luaFunction { args ->
            val npc = retrieveUserdata(args, 1)
            if (npc != null) {
                operations.move(npc, args.arg(2).toint())
            }
            LuaValue.NONE
        })

        registry.registerMethod(NPC_TYPE, "turn", luaFunction { args ->
            val npc = retrieveUserdata(args, 1)
            if (npc != null) {
                operations.turn(npc, args.arg(2).toint())
            }
            LuaValue.NONE
        })

        registry.registerMethod(NPC_TYPE, "follow", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            val player = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.NIL
            operations.follow(npc, player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(NPC_TYPE, "sellItem", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            val player = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.FALSE
            operations.sellItem(
                npc,
                player,
                args.arg(3).toint(),
                args.arg(4).todouble(),
                args.arg(5).optint(1),
                args.arg(6).optint(0),
                args.arg(7).optboolean(false),
                args.arg(8).optboolean(false),
            )?.let { LuaValue.valueOf(it) } ?: LuaValue.FALSE
        })

        registry.registerMethod(NPC_TYPE, "getDistanceTo", luaFunction { args ->
            val npc = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            val uid = args.arg(args.narg()).toint()
            operations.getDistanceTo(npc, uid)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })
    }

    /**
     * Mirrors the `luaNpcOpenShopWindowTable` table walk: every entry supplies
     * `clientId`, `subType` (falling back to `subtype` when zero), `buy`, `sell`,
     * `storageKey`, `storageValue` and the optional `itemName`.
     */
    private fun shopDataList(table: LuaTable): List<ShopData> =
        (1..table.length()).map { index ->
            val row = table.get(index)
            var subType = row.get("subType").optint(0)
            if (subType == 0) {
                subType = row.get("subtype").optint(0)
            }
            ShopData(
                itemId = row.get("clientId").optint(0),
                itemName = row.get("itemName").optjstring(""),
                itemSubType = subType,
                itemBuyPrice = row.get("buy").optlong(0L),
                itemSellPrice = row.get("sell").optlong(0L),
                itemStorageKey = row.get("storageKey").optint(0),
                itemStorageValue = row.get("storageValue").optint(0),
            )
        }
}

/**
 * [NpcOperations] seam backed by the Kotlin [Npc]/[NpcType] domain objects. Shop
 * windows are tracked per player id because the Kotlin [Player] carries no
 * `shopOwner` field yet.
 *
 * Parity gaps, all documented approximations:
 * - `createById`/`createByName` return null: no live NPC registry is wired into this
 *   operations object (`Game::getNpcByID`/`getNpcByName`);
 * - `place` returns null: no placement engine is wired;
 * - `say` returns false: no spectator/protocol sink is wired for
 *   `Game::internalCreatureSay`;
 * - `follow` returns false: `Creature::setFollowCreature` is not modeled;
 * - `sellItem` performs only the item-type validity gate and answers true; granting
 *   the items, removing the currency and sending `MESSAGE_TRADE` stay with the trade
 *   pipeline;
 * - `getDistanceTo` returns null: no script-environment UID registry is wired.
 */
class WorldNpcOperations(
    private val itemTypes: ItemTypeRegistry? = null,
) : NpcOperations {

    /** `Player::shopOwner` (player.hpp:739) bookkeeping keyed by player id. */
    private val shopOwners = mutableMapOf<Long, Any>()

    /** `Npc::setName` bookkeeping; the Kotlin [Creature.name] is immutable. */
    private val nameOverrides = mutableMapOf<Long, String>()

    override fun createById(id: Long): Any? = null

    override fun createByName(name: String): Any? = null

    override fun isNpc(value: Any?): Boolean = value is Npc

    override fun setMasterPos(npc: Any, position: Position): Boolean? {
        val target = npc as? Npc ?: return null
        target.setMasterPos(position)
        return true
    }

    override fun getCurrency(npc: Any): Int? = (npc as? Npc)?.getCurrency()

    override fun setCurrency(npc: Any, currency: Int): Boolean? {
        val target = npc as? Npc ?: return null
        target.setCurrency(currency)
        return true
    }

    override fun getSpeechBubble(npc: Any): Int? {
        val target = npc as? Npc ?: return null
        return target.npcType?.info?.speechBubble ?: NpcInfo.SPEECHBUBBLE_NORMAL
    }

    override fun setSpeechBubble(npc: Any, speechBubble: Int): Boolean? {
        val target = npc as? Npc ?: return null
        target.npcType?.info?.speechBubble = speechBubble
        return true
    }

    override fun getId(npc: Any): Long? = (npc as? Creature)?.id

    override fun getName(npc: Any): String? {
        val target = npc as? Creature ?: return null
        return nameOverrides[target.id] ?: target.name
    }

    override fun setName(npc: Any, name: String): Boolean? {
        val target = npc as? Creature ?: return null
        nameOverrides[target.id] = name
        return true
    }

    /** No placement engine is wired; `Game::placeCreature` is a PARITY GAP. */
    override fun place(npc: Any, position: Position, extended: Boolean, force: Boolean): Any? = null

    /** No spectator/protocol sink is wired; `Game::internalCreatureSay` is a PARITY GAP. */
    override fun say(npc: Any, text: String, type: Int, ghost: Boolean, target: Any?, position: Position?): Boolean? =
        false

    override fun turnToCreature(npc: Any, creature: Any?, stopEventWalk: Boolean): Boolean? {
        val target = npc as? Npc ?: return null
        val candidate = creature as? Creature ?: return null
        if (stopEventWalk) {
            // `Creature::stopEventWalk` clears the pending walk event.
            target.eventWalk = 0
        }
        target.turnToCreature(candidate)
        return true
    }

    override fun setPlayerInteraction(npc: Any, creature: Any?, topicId: Int): Boolean? {
        val target = npc as? Npc ?: return null
        val player = creature as? Creature ?: return false
        target.setPlayerInteraction(player.id, topicId)
        return true
    }

    override fun removePlayerInteraction(npc: Any, creature: Any?): Boolean? {
        val target = npc as? Npc ?: return null
        val player = creature as? Player ?: return false
        target.removePlayerInteraction(player)
        return true
    }

    override fun isInteractingWithPlayer(npc: Any, creature: Any?): Boolean? {
        val target = npc as? Npc ?: return null
        val player = creature as? Creature ?: return false
        return target.isInteractingWithPlayer(player.id)
    }

    override fun isInTalkRange(npc: Any, position: Position, range: Int): Boolean? {
        val target = npc as? Npc ?: return null
        return target.canInteract(position, range)
    }

    override fun isPlayerInteractingOnTopic(npc: Any, creature: Any?, topicId: Int): Boolean? {
        val target = npc as? Npc ?: return null
        val player = creature as? Creature ?: return false
        return target.isPlayerInteractingOnTopic(player.id, topicId)
    }

    override fun openShopWindow(npc: Any, player: Any?): Boolean? {
        val target = npc as? Npc ?: return null
        val customer = player as? Player ?: return false
        shopOwners[customer.id] = target
        return true
    }

    override fun openShopWindowTable(npc: Any, player: Any?, items: List<ShopData>): Boolean? {
        val target = npc as? Npc ?: return null
        val customer = player as? Player ?: return false
        target.addShopPlayer(customer.getGUID(), items.map { toShopBlock(it) })
        shopOwners[customer.id] = target
        return true
    }

    override fun closeShopWindow(npc: Any, player: Any?): Boolean? {
        val target = npc as? Npc ?: return null
        val customer = player as? Player ?: return false
        if (shopOwners[customer.id] === target) {
            shopOwners.remove(customer.id)
        }
        target.removeShopPlayer(customer.getGUID())
        return true
    }

    override fun getShopItem(npc: Any, playerGuid: Long): List<ShopData>? {
        val target = npc as? Npc ?: return null
        return target.getShopItemVector(playerGuid).map { fromShopBlock(it) }
    }

    override fun isMerchant(npc: Any, playerGuid: Long): Boolean? {
        val target = npc as? Npc ?: return null
        return target.getShopItemVector(playerGuid).isNotEmpty()
    }

    /** No move engine is wired; `Game::internalMoveCreature` is a PARITY GAP (no-op). */
    override fun move(npc: Any, direction: Int): Boolean? = npc is Npc

    override fun turn(npc: Any, direction: Int): Boolean? {
        val target = npc as? Npc ?: return null
        val resolved = Direction.entries.getOrNull(direction) ?: return false
        target.direction = resolved
        return true
    }

    /** `Creature::setFollowCreature` is not modeled; answers false (PARITY GAP). */
    override fun follow(npc: Any, player: Any?): Boolean? {
        if (npc !is Npc || player !is Player) {
            return null
        }
        return false
    }

    /**
     * Validity gate only: an unknown item id is rejected when the item catalog is
     * wired; granting the purchased items, removing the currency and the
     * `MESSAGE_TRADE` feedback are not ported (PARITY GAP).
     */
    override fun sellItem(
        npc: Any,
        player: Any?,
        itemId: Int,
        amount: Double,
        subType: Int,
        actionId: Int,
        ignoreCap: Boolean,
        inBackpacks: Boolean,
    ): Boolean? {
        if (npc !is Npc || player !is Player) {
            return null
        }
        if (itemTypes != null && itemTypes.get(itemId) == null) {
            return false
        }
        return true
    }

    /** No script-environment UID registry is wired; `getThingByUID` is a PARITY GAP. */
    override fun getDistanceTo(npc: Any, uid: Int): Int? = null

    private fun toShopBlock(data: ShopData): ShopBlock = ShopBlock(
        itemId = data.itemId,
        itemName = data.itemName.ifEmpty { itemTypes?.get(data.itemId)?.name ?: "" },
        itemSubType = data.itemSubType,
        itemBuyPrice = data.itemBuyPrice,
        itemSellPrice = data.itemSellPrice,
        itemStorageKey = data.itemStorageKey,
        itemStorageValue = data.itemStorageValue,
    )

    private fun fromShopBlock(block: ShopBlock): ShopData = ShopData(
        itemId = block.itemId,
        itemName = block.itemName,
        itemSubType = block.itemSubType,
        itemBuyPrice = block.itemBuyPrice,
        itemSellPrice = block.itemSellPrice,
        itemStorageKey = block.itemStorageKey,
        itemStorageValue = block.itemStorageValue,
    )
}
