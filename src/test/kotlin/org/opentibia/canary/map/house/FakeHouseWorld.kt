package org.opentibia.canary.map.house

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.BedItem
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Cylinder
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.ReturnValue

/** One recorded `g_game().internalMoveItem` call. */
data class MovedItem(
    val from: Cylinder?,
    val to: Cylinder?,
    val index: Int,
    val item: Item,
    val count: Int,
    val actor: Creature?,
    val flags: Int,
)

/** One recorded `g_game().internalAddItem` call. */
data class AddedItem(
    val to: Cylinder?,
    val item: Item?,
    val index: Int,
    val flags: Int,
)

/**
 * Deterministic [HouseWorld] test double. Every world call is recorded so the
 * transcript tests can assert the exact Canary side effects.
 */
class FakeHouseWorld : HouseWorld {
    override var houseRentRate: Float = 1.0f
    override var housePricePerSqm: Int = 1000
    override var housePriceRentMultiplier: Float = 1.0f
    override var housePurchasedShowPrice: Boolean = false
    override var cyclopediaHouseAuction: Boolean = true
    override var houseLoseAfterInactivity: Int = 0
    override var vipKeepHouse: Boolean = false
    override var toggleHouseTransferOnServerRestart: Boolean = false
    override var serverSaveTime: String = "06:00"

    var now: Long = 1_700_000_000L

    val tiles = mutableMapOf<Position, Tile>()
    val onlinePlayers = mutableMapOf<Long, Player>()
    val offlinePlayers = mutableMapOf<Long, Player>()
    val bankBalances = mutableMapOf<Long, Long>()
    val lastLoginSaved = mutableMapOf<Long, Long>()
    val vipPlayers = mutableSetOf<Long>()
    val towns = mutableSetOf<Int>()
    var inbox: Cylinder? = Container(ITEM_LOCKER, capacity = 8)

    val teleports = mutableListOf<Pair<Player, Position>>()
    val magicEffects = mutableListOf<Pair<Position, Int>>()
    val savedPlayers = mutableListOf<Player>()
    val movedItems = mutableListOf<MovedItem>()
    val addedItems = mutableListOf<AddedItem>()
    val removedItems = mutableListOf<Pair<Item, Int>>()
    val ownerRows = mutableMapOf<Int, Long>()
    val newOwnerRows = mutableMapOf<Int, Int>()
    val navigationChanges = mutableListOf<Position>()
    val wokenBeds = mutableListOf<BedItem>()
    val moneyRemoved = mutableListOf<Pair<Player, Long>>()

    var teleportResult: ReturnValue = ReturnValue.NOERROR
    var moveItemResult: ReturnValue = ReturnValue.NOERROR
    var addItemResult: ReturnValue = ReturnValue.NOERROR
    var removeItemResult: ReturnValue = ReturnValue.NOERROR

    val wrapableItems = mutableSetOf<Int>()
    val wrapContainerItems = mutableSetOf<Int>()
    val unwrapIds = mutableMapOf<Int, Int>()
    val wrappedItems = mutableMapOf<Int, Item>()
    val itemTypes = mutableMapOf<Int, ItemType>()

    /** Registers a house tile at [position] with the `houseId`/`house` link already wired. */
    fun registerTile(house: House, position: Position): Tile {
        val tile = Tile(position).apply {
            houseId = house.id
            this.house = house
        }
        tiles[position] = tile
        if (house.tiles.none { it.position == position }) {
            house.addTile(position)
        }
        return tile
    }

    override fun getTile(position: Position): Tile? = tiles[position]

    override fun getPlayerByGuid(guid: Long, allowOffline: Boolean): Player? =
        onlinePlayers[guid] ?: if (allowOffline) offlinePlayers[guid] else null

    override fun loadPlayerById(guid: Long): Player? = offlinePlayers[guid]

    override fun savePlayer(player: Player) {
        savedPlayers.add(player)
    }

    override fun getInbox(player: Player): Cylinder? = inbox

    override fun internalTeleport(target: Player, position: Position): ReturnValue {
        if (teleportResult == ReturnValue.NOERROR) {
            teleports.add(target to position)
            // `Game::internalTeleport` moves the creature off its current tile.
            target.getTile()?.removeCreature(target)
            target.position = position
        }
        return teleportResult
    }

    override fun addMagicEffect(position: Position, effect: Int) {
        magicEffects.add(position to effect)
    }

    override fun internalMoveItem(
        from: Cylinder?,
        to: Cylinder?,
        index: Int,
        item: Item,
        count: Int,
        actor: Creature?,
        flags: Int,
    ): ReturnValue {
        if (moveItemResult == ReturnValue.NOERROR) {
            movedItems.add(MovedItem(from, to, index, item, count, actor, flags))
        }
        return moveItemResult
    }

    override fun internalAddItem(to: Cylinder?, item: Item?, index: Int, flags: Int): ReturnValue {
        if (addItemResult == ReturnValue.NOERROR) {
            addedItems.add(AddedItem(to, item, index, flags))
        }
        return addItemResult
    }

    override fun internalRemoveItem(item: Item, count: Int): ReturnValue {
        if (removeItemResult == ReturnValue.NOERROR) {
            removedItems.add(item to count)
        }
        return removeItemResult
    }

    override fun createItem(id: Int): Item = Item(id)

    override fun removeMoney(player: Player, amount: Long, subType: Int, useBank: Boolean): Boolean {
        moneyRemoved.add(player to amount)
        bankBalances[player.getGUID()] = (bankBalances[player.getGUID()] ?: 0L) - amount
        return true
    }

    override fun getLastLoginSaved(player: Player): Long = lastLoginSaved[player.getGUID()] ?: 0L

    override fun isVip(player: Player): Boolean = player.getGUID() in vipPlayers

    override fun getBankBalance(player: Player): Long = bankBalances[player.getGUID()] ?: 0L

    override fun hasTown(townId: Int): Boolean = townId in towns

    override fun isWrapable(item: Item): Boolean = item.id in wrapableItems

    override fun isWrapContainer(item: Item): Boolean = item.id in wrapContainerItems

    override fun getUnWrapId(item: Item): Int = unwrapIds[item.id] ?: 0

    override fun wrapItem(item: Item, house: House): Item {
        val wrapped = wrappedItems[item.id] ?: item
        // `Game::wrapItem` transforms the item in place, so the replacement
        // keeps the original parent and is not removed.
        wrapped.setParent(item.getParent())
        return wrapped
    }

    override fun getItemType(itemId: Int): ItemType? = itemTypes[itemId]

    override fun wakeUpBed(bed: BedItem) {
        wokenBeds.add(bed)
    }

    override fun markNavigationTopologyChanged(position: Position) {
        navigationChanges.add(position)
    }

    override fun updateOwnerRow(house: House, guid: Long) {
        ownerRows[house.id] = guid
    }

    override fun updateNewOwnerRow(house: House, newOwnerGuid: Int) {
        newOwnerRows[house.id] = newOwnerGuid
    }

    override fun timeNow(): Long = now
}
