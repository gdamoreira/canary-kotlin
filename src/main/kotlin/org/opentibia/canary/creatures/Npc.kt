package org.opentibia.canary.creatures

import org.opentibia.canary.creatures.npcs.NpcInfo
import org.opentibia.canary.creatures.npcs.NpcType
import org.opentibia.canary.creatures.npcs.NpcWorld
import org.opentibia.canary.creatures.spawns.SpawnsNpc
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.npcshop.ShopBlock
import org.opentibia.canary.systems.rewardchest.ReturnValue
import kotlin.math.abs

data class ShopItem(
    val itemId: Int,
    val name: String,
    val price: Int
)

class Npc(
    id: Long,
    name: String,
    speed: Int = 100,
    position: Position,
    /**
     * `std::shared_ptr<NpcType> npcType` (`npc.hpp:154`). When present it seeds
     * health, base speed, outfit and light (`npc.cpp:420-439`). The
     * `RATE_NPC_HEALTH` multiplier and `registerCreatureEvent` stay with their
     * config/Lua owners (PARITY GAPS).
     */
    val npcType: NpcType? = null,
    /**
     * Injected replacement for the `g_game()` lookups of `npc.cpp`
     * (AGENTS.md 1.1). `null` keeps the legacy port path for the XML pipeline.
     */
    private val world: NpcWorld? = null
) : Creature(
    id,
    name,
    maxHealth = npcType?.info?.healthMax ?: 100,
    health = npcType?.info?.health ?: 100,
    maxMana = 0,
    mana = 0,
    speed = npcType?.info?.baseSpeed ?: speed,
    position = position
) {

    init {
        val type = npcType
        if (type != null) {
            setDefaultOutfit(type.info.outfit)
            setCurrentOutfit(type.info.outfit)
            setCreatureLight(type.info.light)
        }
    }

    private val shopItems = mutableListOf<ShopItem>()

    /** `Npc::playerInteractionsOrder` (src/creatures/npcs/npc.hpp:148). */
    private val playerInteractionsOrder = mutableListOf<Long>()

    /** `Npc::playerInteractions` (src/creatures/npcs/npc.hpp:150) — a `std::map`, key ordered. */
    private val playerInteractions: MutableMap<Long, Int> = sortedMapOf()

    /** `Npc::shopPlayers` (src/creatures/npcs/npc.hpp:152). */
    private val shopPlayers: MutableMap<Long, List<ShopBlock>> = sortedMapOf()

    /** `Npc::masterPos` (src/creatures/npcs/npc.hpp:166). */
    private var masterPosValue: Position = position

    /** `Creature::listWalkDir` (`src/creatures/creature.hpp:912`). */
    internal val listWalkDir = mutableListOf<Direction>()

    /** `Creature::eventWalk` (`src/creatures/creature.hpp:911`) as a pending-step latch. */
    override var eventWalk: Long = 0

    /** `Npc::walkTicks` (src/creatures/npcs/npc.hpp:160). */
    private var walkTicks: Int = 0

    private val walkRadius: Int get() = npcType?.info?.walkRadius ?: DEFAULT_WALK_RADIUS

    private val walkInterval: Int get() = npcType?.info?.walkInterval ?: DEFAULT_WALK_INTERVAL

    private val floorChange: Boolean get() = npcType?.info?.floorChange == true

    fun addShopItem(itemId: Int, name: String, price: Int) {
        shopItems.add(ShopItem(itemId, name, price))
    }

    fun getShopItems(): List<ShopItem> = shopItems

    /** `Npc::getMasterPos` (src/creatures/npcs/npc.cpp:488-490). */
    fun getMasterPos(): Position = masterPosValue

    /** `Npc::setMasterPos` (src/creatures/npcs/npc.cpp:492-494). */
    fun setMasterPos(pos: Position) {
        masterPosValue = pos
    }

    /**
     * `Npc::setPlayerInteraction` (src/creatures/npcs/npc.cpp:1156-1168). The
     * `g_game().getCreatureByID` lookup is the injected [world]; without one the
     * legacy port path records the interaction without focusing (PARITY GAP).
     */
    fun setPlayerInteraction(playerId: Long, topicId: Int = 0) {
        val creature = world?.getCreatureByID(playerId)
        if (world != null && creature == null) {
            return
        }

        if (playerInteractionsOrder.isEmpty() || playerId !in playerInteractionsOrder) {
            playerInteractionsOrder.add(playerId)
            if (creature != null) {
                turnToCreature(creature)
            }
        }

        playerInteractions[playerId] = topicId
    }

    /** Legacy signature kept for the XML pipeline and existing call sites. */
    fun removePlayerInteraction(playerId: Long) {
        playerInteractionsOrder.removeAll { it == playerId }
        playerInteractions.remove(playerId)
        refocusLastInteraction()
    }

    /** `Npc::removePlayerInteraction` (src/creatures/npcs/npc.cpp:1170-1183). */
    fun removePlayerInteraction(player: Player) {
        playerInteractionsOrder.removeAll { it == player.id }
        if (playerInteractions.containsKey(player.id)) {
            playerInteractions.remove(player.id)
            world?.closeShopWindow(player)
        }

        refocusLastInteraction()
    }

    private fun refocusLastInteraction() {
        val lastPlayerId = playerInteractionsOrder.lastOrNull() ?: return
        val creature = world?.getCreatureByID(lastPlayerId) ?: return
        turnToCreature(creature)
    }

    /** `Npc::resetPlayerInteractions` (src/creatures/npcs/npc.cpp:1185-1187). */
    fun resetPlayerInteractions() {
        playerInteractions.clear()
    }

    /** `Npc::isInteractingWithPlayer` (src/creatures/npcs/npc.cpp:554-563). */
    fun isInteractingWithPlayer(playerId: Long): Boolean = playerInteractions.containsKey(playerId)

    /** `Npc::isPlayerInteractingOnTopic` (src/creatures/npcs/npc.cpp:565-575). */
    fun isPlayerInteractingOnTopic(playerId: Long, topicId: Int): Boolean =
        playerInteractions[playerId] == topicId

    /**
     * `Creature::turnToCreature` (`src/creatures/creature.cpp:1882-1910`): the
     * tan-based quadrant choice, kept on `Npc` until `Creature` grows the C++
     * member in this port. Canary broadcasts through `Game::internalCreatureTurn`;
     * the port only mutates [direction] (protocol PARITY GAP).
     */
    fun turnToCreature(creature: Creature) {
        val creaturePos = creature.position
        val dx = position.x - creaturePos.x
        val dy = position.y - creaturePos.y

        val tan = if (dx != 0) dy.toFloat() / dx else 10f

        direction = if (abs(tan) < 1) {
            if (dx > 0) Direction.West else Direction.East
        } else {
            if (dy > 0) Direction.North else Direction.South
        }
    }

    /** `Npc::canInteract` (src/creatures/npcs/npc.cpp:535-540). */
    fun canInteract(pos: Position, range: Int = 4): Boolean {
        if (pos.z != position.z) {
            return false
        }
        return Creature.canSee(position, pos, range, range)
    }

    /** `Npc::canSeeInvisibility` (src/creatures/npcs/npc.cpp:542-544). */
    override fun canSeeInvisibility(): Boolean = true

    /** `Npc::ignoreHeight` (src/creatures/npcs/npc.hpp:163) — never assigned in Canary. */
    private var ignoreHeight = false

    /** `Npc::canWalkTo` (src/creatures/npcs/npc.cpp:1189-1213). */
    fun canWalkTo(fromPos: Position, dir: Direction): Boolean {
        if (walkRadius == 0) {
            return false
        }

        val toPos = dir.offset(fromPos)
        if (!SpawnsNpc.isInZone(masterPosValue, walkRadius, toPos)) {
            return false
        }

        val world = world ?: return false
        if (!world.canQueryAddNpc(this, toPos)) {
            return false
        }

        if (!floorChange && world.isFloorChangeOrTeleportTile(toPos)) {
            return false
        }

        if (!ignoreHeight && world.tileHasHeight(toPos)) {
            return false
        }

        return true
    }

    /** `Npc::getRandomStep` (src/creatures/npcs/npc.cpp:1219-1236). */
    fun getRandomStep(
        shuffleDirections: (MutableList<Direction>) -> Unit = { it.shuffle() },
    ): Direction? {
        val directionVector = mutableListOf(Direction.North, Direction.West, Direction.East, Direction.South)
        shuffleDirections(directionVector)

        for (direction in directionVector) {
            if (canWalkTo(position, direction)) {
                return direction
            }
        }
        return null
    }

    /**
     * `Npc::onThinkWalk` (src/creatures/npcs/npc.cpp:1080-1104). The queued
     * direction is returned instead of the C++ out-parameter; [eventWalk] plays
     * the role of the scheduled walk event as a pending-step latch.
     */
    fun onThinkWalk(
        interval: Int,
        shuffleDirections: (MutableList<Direction>) -> Unit = { it.shuffle() },
    ): Direction? {
        if (walkInterval == 0 || getBaseSpeed() == 0) {
            return null
        }

        // If talking, no walking
        if (playerInteractions.isNotEmpty()) {
            walkTicks = 0
            eventWalk = 0
            return null
        }

        walkTicks += interval
        if (walkTicks < walkInterval) {
            return null
        }

        val newDirection = getRandomStep(shuffleDirections)
        if (newDirection != null) {
            listWalkDir.add(newDirection)
            addEventWalk()
        }

        walkTicks = 0
        return newDirection
    }

    /** `Creature::addEventWalk` (`src/creatures/creature.cpp:307-333`). */
    private fun addEventWalk() {
        eventWalk = 1
    }

    /** `Npc::getCurrency` (src/creatures/npcs/npc.cpp:504-506). */
    fun getCurrency(): Int = npcType?.info?.currencyId ?: NpcInfo.ITEM_GOLD_COIN

    /**
     * `Npc::setCurrency` (src/creatures/npcs/npc.cpp:508-510) writes the type
     * currency; without an [npcType] there is no stored currency to update
     * (legacy port path, PARITY GAP).
     */
    fun setCurrency(currency: Int) {
        npcType?.info?.currencyId = currency
    }

    /** `Npc::getShopItemVector` (src/creatures/npcs/npc.cpp:512-521). */
    fun getShopItemVector(playerGUID: Long): List<ShopBlock> {
        if (playerGUID != 0L) {
            val playerShopItems = shopPlayers[playerGUID]
            if (playerShopItems != null && playerShopItems.isNotEmpty()) {
                return playerShopItems
            }
        }

        return npcType?.info?.shopItemVector ?: emptyList()
    }

    /** `Npc::isShopPlayer` (src/creatures/npcs/npc.cpp:1242-1244). */
    fun isShopPlayer(playerGUID: Long): Boolean = shopPlayers.containsKey(playerGUID)

    /** `Npc::addShopPlayer` (src/creatures/npcs/npc.cpp:1246-1248) — `try_emplace` keeps an existing entry. */
    fun addShopPlayer(playerGUID: Long, shopItems: List<ShopBlock>) {
        if (!shopPlayers.containsKey(playerGUID)) {
            shopPlayers[playerGUID] = shopItems
        }
    }

    /** `Npc::removeShopPlayer` (src/creatures/npcs/npc.cpp:1250-1252). */
    fun removeShopPlayer(playerGUID: Long) {
        shopPlayers.remove(playerGUID)
    }

    /** `Npc::closeAllShopWindows` (src/creatures/npcs/npc.cpp:1254-1268). */
    fun closeAllShopWindows() {
        val world = world
        if (world != null) {
            for (playerGuid in shopPlayers.keys.toList()) {
                val player = world.getPlayerByGUID(playerGuid) ?: continue
                world.closeShopWindow(player)
            }
        }

        shopPlayers.clear()
    }

    private val onPlayerBuyHandlers =
        mutableListOf<(Npc, Player, Int, Int, Int, Boolean, Boolean, Long) -> Unit>()

    /** Registers a listener for [onPlayerBuyItem] engine dispatch. */
    fun registerOnPlayerBuyHandler(handler: (Npc, Player, Int, Int, Int, Boolean, Boolean, Long) -> Unit) {
        onPlayerBuyHandlers.add(handler)
    }

    /** Removes a listener registered with [registerOnPlayerBuyHandler]. */
    fun unregisterOnPlayerBuyHandler(handler: (Npc, Player, Int, Int, Int, Boolean, Boolean, Long) -> Unit) {
        onPlayerBuyHandlers.remove(handler)
    }

    /**
     * `Npc::onPlayerBuyItem` (src/creatures/npcs/npc.cpp:738-791): capacity and
     * funds checks, then the `playerBuyEvent` dispatch carrying `totalCost`.
     */
    fun onPlayerBuyItem(
        player: Player?,
        itemId: Int,
        subType: Int,
        amount: Int,
        ignore: Boolean,
        inBackpacks: Boolean,
    ) {
        if (player == null) {
            return
        }

        val world = world
        if (world != null) {
            if (isBackpackSlotUnavailable(player, itemId, ignore)) {
                world.sendCancelMessage(player, ReturnValue.NOTENOUGHROOM)
                return
            }

            if (exceedsTileLimit(player, itemId, amount, inBackpacks, ignore)) {
                world.sendCancelMessage(player, ReturnValue.NOTENOUGHROOM)
                return
            }
        }

        var buyPrice = 0L
        for (shopBlock in getShopItemVector(player.getGUID())) {
            if (itemId == shopBlock.itemId && shopBlock.itemBuyPrice != 0L) {
                buyPrice = shopBlock.itemBuyPrice
            }
        }

        val totalCost = buyPrice * amount
        val bagsCost = calculateBagsCost(itemId, amount, inBackpacks)
        if (hasInsufficientFunds(player, totalCost, bagsCost)) {
            return
        }

        onPlayerBuyHandlers.toList().forEach {
            it(this, player, itemId, subType, amount, ignore, inBackpacks, totalCost)
        }
    }

    /** Anonymous-namespace `isBackpackSlotUnavailable` (`src/creatures/npcs/npc.cpp:32-43`). */
    private fun isBackpackSlotUnavailable(player: Player, itemId: Int, ignore: Boolean): Boolean {
        val world = world ?: return false
        if (ignore || world.getFreeBackpackSlots(player) != 0) {
            return false
        }

        if (world.hasBackpackEquipped(player)) {
            return true
        }

        return !world.itemTypeIsBackpack(itemId)
    }

    /** Anonymous-namespace `exceedsTileLimit` (`src/creatures/npcs/npc.cpp:54-68`). */
    private fun exceedsTileLimit(
        player: Player,
        itemId: Int,
        amount: Int,
        inBackpacks: Boolean,
        ignore: Boolean,
    ): Boolean {
        if (!ignore) {
            return false
        }

        val world = world ?: return false
        if (!world.playerHasTile(player)) {
            return false
        }

        val slotsNeeded = world.calculateSlotsNeeded(itemId, amount, inBackpacks)
        val itemCount = world.getTileItemCount(player).toDouble()
        return (itemCount + (slotsNeeded - world.getFreeBackpackSlots(player))) > TILE_ITEM_LIMIT
    }

    /** Anonymous-namespace `calculateBagsCost` (`src/creatures/npcs/npc.cpp:70-77`). */
    private fun calculateBagsCost(itemId: Int, amount: Int, inBackpacks: Boolean): Long {
        if (!inBackpacks) {
            return 0
        }

        val world = world ?: return 0
        val slotsNeeded = world.calculateSlotsNeeded(itemId, amount, true)
        return SHOPPING_BAG_PRICE * slotsNeeded.toLong()
    }

    /** Anonymous-namespace `hasInsufficientFunds` (`src/creatures/npcs/npc.cpp:87-108`). */
    private fun hasInsufficientFunds(player: Player, totalCost: Long, bagsCost: Long): Boolean {
        val world = world ?: return false
        val currency = getCurrency()
        if (currency == NpcInfo.ITEM_GOLD_COIN) {
            val totalRequired = totalCost + bagsCost
            return (world.getMoney(player) + world.getBankBalance(player)) < totalRequired
        }

        val availableCurrency = world.getItemTypeCount(player, currency)
        return availableCurrency < totalCost ||
            (world.getMoney(player) + world.getBankBalance(player)) < bagsCost
    }

    private val onPlayerSellHandlers =
        mutableListOf<(Npc, Player, Int, Int, Int, Boolean, String, Long) -> Unit>()

    /** Registers a listener for [onPlayerSellItem] engine dispatch. */
    fun registerOnPlayerSellHandler(handler: (Npc, Player, Int, Int, Int, Boolean, String, Long) -> Unit) {
        onPlayerSellHandlers.add(handler)
    }

    /** Removes a listener registered with [registerOnPlayerSellHandler]. */
    fun unregisterOnPlayerSellHandler(handler: (Npc, Player, Int, Int, Int, Boolean, String, Long) -> Unit) {
        onPlayerSellHandlers.remove(handler)
    }

    /**
     * `Npc::onPlayerSellItem` (src/creatures/npcs/npc.cpp:910-1017) without the
     * loot-pouch/sale-context overload: gold sales remove the items and pay the
     * player, custom currencies count, pay and remove, then the
     * `playerSellEvent` dispatch carries the sold amount and total price.
     */
    fun onPlayerSellItem(
        player: Player?,
        itemId: Int,
        subType: Int,
        amount: Int,
        ignore: Boolean,
    ) {
        if (player == null) {
            return
        }

        val sellPrice = getSellPriceForItem(getShopItemVector(player.getGUID()), itemId)
        if (sellPrice == 0L) {
            return
        }

        val currency = getCurrency()
        val soldAmount: Int
        val totalCost: Long
        if (currency == NpcInfo.ITEM_GOLD_COIN) {
            soldAmount = removeItemsFromInventory(player, itemId, ignore, amount)
            if (soldAmount == 0) {
                world?.sendTextMessage(player, "You have no items to sell.")
                return
            }

            totalCost = sellPrice * soldAmount
            applyGoldSaleProceeds(player, totalCost)
        } else {
            soldAmount = countSellableItemsFromInventory(player, itemId, ignore, amount)
            if (soldAmount == 0) {
                world?.sendTextMessage(player, "You have no items to sell.")
                return
            }

            totalCost = sellPrice * soldAmount
            if (!applyCustomSaleProceeds(player, currency, totalCost)) {
                return
            }

            val removed = removeItemsFromInventory(player, itemId, ignore, soldAmount)
            if (removed != soldAmount) {
                // Canary logs the mismatch (`npc.cpp:992-994`); logging is a PARITY GAP.
            }
        }

        val itemName = world?.getItemTypeName(itemId) ?: ""
        onPlayerSellHandlers.toList().forEach {
            it(this, player, itemId, subType, soldAmount, ignore, itemName, totalCost)
        }
    }

    /** Anonymous-namespace `getSellPriceForItem` (`src/creatures/npcs/npc.cpp:144-152`). */
    private fun getSellPriceForItem(shopVector: List<ShopBlock>, itemId: Int): Long {
        for (shopBlock in shopVector) {
            if (itemId == shopBlock.itemId && shopBlock.itemSellPrice != 0L) {
                return shopBlock.itemSellPrice
            }
        }

        return 0
    }

    /** Anonymous-namespace `removeItemsFromInventory` (`src/creatures/npcs/npc.cpp:236-266`). */
    private fun removeItemsFromInventory(player: Player, itemId: Int, ignore: Boolean, amount: Int): Int {
        val world = world ?: return 0
        var removed = 0
        var toRemove = amount
        for (item in world.getInventoryItemsFromId(player, itemId, ignore)) {
            if (toRemove == 0) {
                break
            }

            if (isInvalidItemForNpcSell(item, itemId)) {
                continue
            }

            val removeCount = minOf(toRemove, item.count)
            if (world.removeItem(player, item, removeCount) != ReturnValue.NOERROR) {
                continue
            }

            toRemove -= removeCount
            removed += removeCount
        }

        return removed
    }

    /** Anonymous-namespace `countSellableItemsFromInventory` (`src/creatures/npcs/npc.cpp:175-207`). */
    private fun countSellableItemsFromInventory(player: Player, itemId: Int, ignore: Boolean, amount: Int): Int {
        val world = world ?: return 0
        var found = 0
        var remaining = amount
        for (item in world.getInventoryItemsFromId(player, itemId, ignore)) {
            if (remaining == 0) {
                break
            }

            if (isInvalidItemForNpcSell(item, itemId)) {
                continue
            }

            val count = minOf(remaining, item.count)
            if (!world.canRemoveFromInventory(player, item, count)) {
                continue
            }

            remaining -= count
            found += count
        }

        return found
    }

    /**
     * Anonymous-namespace `isInvalidItemForNpcSell` (`src/creatures/npcs/npc.cpp:134-136`).
     * `Item::hasImbuements` has no counterpart on the port's `Item` (PARITY GAP),
     * so only the id and tier checks are transcribed.
     */
    private fun isInvalidItemForNpcSell(item: Item, itemId: Int): Boolean {
        if (item.id != itemId) {
            return true
        }

        val tier = item.getAttribute(ItemAttribute.TIER) as? Long ?: 0L
        return tier > 0
    }

    /**
     * Anonymous-namespace `applyGoldSaleProceeds` (`src/creatures/npcs/npc.cpp:268-308`).
     * The `sendResourceBalance`/bank-transfer text and the metrics counters are
     * not ported (PARITY GAP).
     */
    private fun applyGoldSaleProceeds(player: Player, totalCost: Long) {
        val world = world ?: return
        if (world.getAutoBank()) {
            world.setBankBalance(player, world.getBankBalance(player) + totalCost)
            return
        }

        val (addedMoney, _) = world.addMoney(player, totalCost, 0)
        if (addedMoney < totalCost) {
            val refund = totalCost - addedMoney
            world.setBankBalance(player, world.getBankBalance(player) + refund)
        }
    }

    /** Anonymous-namespace `applyCustomSaleProceeds` (`src/creatures/npcs/npc.cpp:317-332`). */
    private fun applyCustomSaleProceeds(player: Player, currency: Int, totalCost: Long): Boolean {
        val world = world ?: return false
        if (world.addItemToPlayer(player, currency, totalCost)) {
            return true
        }

        world.sendTextMessage(player, "An error occurred while completing the sale. Your items were not exchanged.")
        return false
    }

    private val onPlayerCheckItemHandlers = mutableListOf<(Npc, Player, Int, Int) -> Unit>()

    /** Registers a listener for [onPlayerCheckItem] engine dispatch. */
    fun registerOnPlayerCheckItemHandler(handler: (Npc, Player, Int, Int) -> Unit) {
        onPlayerCheckItemHandlers.add(handler)
    }

    /** Removes a listener registered with [registerOnPlayerCheckItemHandler]. */
    fun unregisterOnPlayerCheckItemHandler(handler: (Npc, Player, Int, Int) -> Unit) {
        onPlayerCheckItemHandlers.remove(handler)
    }

    /** `Npc::onPlayerCheckItem` (src/creatures/npcs/npc.cpp:1019-1036). */
    fun onPlayerCheckItem(player: Player?, itemId: Int, subType: Int) {
        if (player == null) {
            return
        }

        onPlayerCheckItemHandlers.toList().forEach { it(this, player, itemId, subType) }
    }

    private val onPlayerCloseChannelHandlers = mutableListOf<(Npc, Player) -> Unit>()

    /** Registers a listener for [onPlayerCloseChannel] engine dispatch. */
    fun registerOnPlayerCloseChannelHandler(handler: (Npc, Player) -> Unit) {
        onPlayerCloseChannelHandlers.add(handler)
    }

    /** Removes a listener registered with [registerOnPlayerCloseChannelHandler]. */
    fun unregisterOnPlayerCloseChannelHandler(handler: (Npc, Player) -> Unit) {
        onPlayerCloseChannelHandlers.remove(handler)
    }

    /**
     * `Npc::onPlayerCloseChannel` (src/creatures/npcs/npc.cpp:1038-1056). Canary
     * skips the interaction removal when the Lua script persisted a coroutine
     * state; the port always removes after the dispatch (PARITY GAP).
     */
    fun onPlayerCloseChannel(creature: Creature?) {
        val player = creature as? Player ?: return
        onPlayerCloseChannelHandlers.toList().forEach { it(this, player) }
        removePlayerInteraction(player)
    }

    private val onCreatureSayHandlers = mutableListOf<(Npc, Creature, Int, String) -> Unit>()

    /** Registers a listener for [onCreatureSay] engine dispatch. */
    fun registerOnCreatureSayHandler(handler: (Npc, Creature, Int, String) -> Unit) {
        onCreatureSayHandlers.add(handler)
    }

    /** Removes a listener registered with [registerOnCreatureSayHandler]. */
    fun unregisterOnCreatureSayHandler(handler: (Npc, Creature, Int, String) -> Unit) {
        onCreatureSayHandlers.remove(handler)
    }

    /**
     * `Npc::onCreatureSay` engine entry (src/creatures/npcs/npc.cpp:670-689):
     * non-player speakers are ignored, then the registered scripting handlers
     * receive the say event. The dialogue decisions themselves stay Lua
     * (AGENTS.md 1.4).
     */
    fun onCreatureSay(creature: Creature, type: Int, text: String) {
        val player = creature as? Player ?: return
        onCreatureSayHandlers.toList().forEach { it(this, player, type, text) }
    }

    /**
     * Mirrors Canary C++ Npc::onCreatureSay(creature, type, text)
     * src/creatures/npcs/npc.cpp:670
     */
    fun onCreatureSay(player: Player, type: Int, text: String): String {
        onCreatureSay(player as Creature, type, text)
        return when (text.lowercase().trim()) {
            "hi", "hello" -> {
                setPlayerInteraction(player.id)
                "Hello, ${player.name}!"
            }
            "bye", "farewell" -> {
                removePlayerInteraction(player.id)
                "Good bye, ${player.name}."
            }
            "trade" -> {
                if (isInteractingWithPlayer(player.id)) {
                    "Take a look at my wares."
                } else {
                    ""
                }
            }
            else -> {
                if (isInteractingWithPlayer(player.id)) "I don't understand." else ""
            }
        }
    }

    companion object {
        /** `NpcInfo::walkRadius` default (`src/creatures/npcs/npcs.hpp:59`). */
        const val DEFAULT_WALK_RADIUS: Int = 10

        /** `NpcInfo::walkInterval` default (`src/creatures/npcs/npcs.hpp:44`). */
        const val DEFAULT_WALK_INTERVAL: Int = 2000

        /** Anonymous-namespace `kShoppingBagPrice` (`src/creatures/npcs/npc.cpp:29`). */
        const val SHOPPING_BAG_PRICE: Int = 20

        /** Anonymous-namespace `exceedsTileLimit` item cap (`src/creatures/npcs/npc.cpp:67`). */
        const val TILE_ITEM_LIMIT: Int = 30
    }
}
