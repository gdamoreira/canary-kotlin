package org.opentibia.canary.map.house

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.BedItem
import org.opentibia.canary.items.Cylinder
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.ReturnValue

/** `RentPeriod_t` (src/map/map_definitions.hpp:25-31). */
enum class RentPeriod_t {
    RENTPERIOD_DAILY,
    RENTPERIOD_WEEKLY,
    RENTPERIOD_MONTHLY,
    RENTPERIOD_YEARLY,
    RENTPERIOD_NEVER,
}

/** `TradeEvents_t` (src/items/items_definitions.hpp:197-200). */
enum class TradeEvents_t {
    ON_TRADE_TRANSFER,
    ON_TRADE_CANCEL,
}

/**
 * Injected view of the `g_game()` / `g_configManager()` / `g_saveManager()` /
 * `Database` / `IOLoginData` calls the `House` and `Houses` members make in
 * src/map/house/house.cpp. Every member transcribes one C++ call so the rent,
 * transfer and auction logic stays testable without a live world
 * (AGENTS.md 1.1).
 */
interface HouseWorld {
    /** `g_game().map.getTile(position)` (house.cpp:100, 322, 106; the C++ house tiles are `Tile` objects). */
    fun getTile(position: Position): Tile?

    /** `g_game().getPlayerByGUID(guid, allowOffline)` (house.cpp:271, 340, 639, 971). */
    fun getPlayerByGuid(guid: Long, allowOffline: Boolean = false): Player?

    /** `IOLoginData::loadPlayerById(tmpPlayer, guid)` (house.cpp:276). */
    fun loadPlayerById(guid: Long): Player?

    /** `g_saveManager().savePlayer(player)` (house.cpp:393, 989, 1061). */
    fun savePlayer(player: Player)

    /** `Player::getInbox()` (house.cpp:356, 1053). */
    fun getInbox(player: Player): Cylinder?

    /** `g_game().internalTeleport(target, position)` (house.cpp:228). */
    fun internalTeleport(target: Player, position: Position): ReturnValue

    /** `g_game().addMagicEffect(position, effect)` (house.cpp:229-230). */
    fun addMagicEffect(position: Position, effect: Int)

    /** `g_game().internalMoveItem(from, to, index, item, count, movedItem, flags, actor)` (house.cpp:356). */
    fun internalMoveItem(
        from: Cylinder?,
        to: Cylinder?,
        index: Int,
        item: Item,
        count: Int,
        actor: Creature?,
        flags: Int,
    ): ReturnValue

    /** `g_game().internalAddItem(to, item, index, flags)` (house.cpp:1054). */
    fun internalAddItem(to: Cylinder?, item: Item?, index: Int, flags: Int): ReturnValue

    /** `g_game().internalRemoveItem(item, count)` (house.cpp:646). */
    fun internalRemoveItem(item: Item, count: Int): ReturnValue

    /** `Item::CreateItem(id)` (house.cpp:1026). */
    fun createItem(id: Int): Item?

    /** `g_game().removeMoney(player, amount, subType, useBank)` (house.cpp:1000). */
    fun removeMoney(player: Player, amount: Long, subType: Int, useBank: Boolean): Boolean

    /** `Player::getLastLoginSaved()` (house.cpp:981). */
    fun getLastLoginSaved(player: Player): Long

    /** `Player::isVip()` (house.cpp:982). */
    fun isVip(player: Player): Boolean

    /** `Player::getBankBalance()` (house.cpp:999). */
    fun getBankBalance(player: Player): Long

    /** `g_game().map.towns.getTown(townId)` (house.cpp:966); `payHouses` only reads the non-null check. */
    fun hasTown(townId: Int): Boolean

    /** `Item::isWrapable()` (house.cpp:325, 410). */
    fun isWrapable(item: Item): Boolean

    /** `Item::isWrapContainer()` (house.cpp:364, 445). */
    fun isWrapContainer(item: Item): Boolean

    /** `item->getCustomAttribute("unWrapId")` (house.cpp:365). */
    fun getUnWrapId(item: Item): Int

    /** `g_game().wrapItem(item, house)` (house.cpp:450). */
    fun wrapItem(item: Item, house: House): Item

    /** `Item::items[unWrapId]` (house.cpp:370), used only by the transfer log line. */
    fun getItemType(itemId: Int): ItemType?

    /** `BedItem::wakeUp(nullptr)` (house.cpp:54). */
    fun wakeUpBed(bed: BedItem)

    /** `g_game().map.markNavigationTopologyChanged(position)` (house.cpp:28). */
    fun markNavigationTopologyChanged(position: Position)

    /** `Database::executeQuery` of the `UPDATE houses SET owner = ..., new_owner = -1, paid = 0, ...` statement (house.cpp:99-100). */
    fun updateOwnerRow(house: House, guid: Long)

    /** `Database::executeQuery` of `UPDATE houses SET new_owner = ...` (house.cpp:41-44). */
    fun updateNewOwnerRow(house: House, newOwnerGuid: Int)

    /** `time(nullptr)` (house.cpp:115, 958). */
    fun timeNow(): Long

    /** `g_configManager().getFloat(HOUSE_RENT_RATE)` (house.cpp:1066). */
    val houseRentRate: Float

    /** `g_configManager().getNumber(HOUSE_PRICE_PER_SQM)` (house.cpp:1070). */
    val housePricePerSqm: Int

    /** `g_configManager().getFloat(HOUSE_PRICE_RENT_MULTIPLIER)` (house.cpp:1071). */
    val housePriceRentMultiplier: Float

    /** `g_configManager().getBoolean(HOUSE_PURSHASED_SHOW_PRICE)` (house.cpp:168). */
    val housePurchasedShowPrice: Boolean

    /** `g_configManager().getBoolean(CYCLOPEDIA_HOUSE_AUCTION)` (house.cpp:164). */
    val cyclopediaHouseAuction: Boolean

    /** `g_configManager().getNumber(HOUSE_LOSE_AFTER_INACTIVITY)` (house.cpp:979). */
    val houseLoseAfterInactivity: Int

    /** `g_configManager().getBoolean(VIP_KEEP_HOUSE)` (house.cpp:982). */
    val vipKeepHouse: Boolean

    /** `g_configManager().getBoolean(TOGGLE_HOUSE_TRANSFER_ON_SERVER_RESTART)` (house.cpp:34, 632). */
    val toggleHouseTransferOnServerRestart: Boolean

    /** `g_configManager().getString(GLOBAL_SERVER_SAVE_TIME)` (house.cpp:600). */
    val serverSaveTime: String
}

/**
 * Inert [HouseWorld] used when no game world is wired: the config values keep
 * the Canary defaults from `src/config/configmanager.cpp` and every world
 * operation is a no-op. `timeNow` stays on the system clock so the existing
 * `setOwner` paid-until behavior is unchanged when no world is injected.
 */
object NoHouseWorld : HouseWorld {
    override fun getTile(position: Position): Tile? = null
    override fun getPlayerByGuid(guid: Long, allowOffline: Boolean): Player? = null
    override fun loadPlayerById(guid: Long): Player? = null
    override fun savePlayer(player: Player) = Unit
    override fun getInbox(player: Player): Cylinder? = null
    override fun internalTeleport(target: Player, position: Position): ReturnValue = ReturnValue.NOTPOSSIBLE
    override fun addMagicEffect(position: Position, effect: Int) = Unit
    override fun internalMoveItem(
        from: Cylinder?,
        to: Cylinder?,
        index: Int,
        item: Item,
        count: Int,
        actor: Creature?,
        flags: Int,
    ): ReturnValue = ReturnValue.NOTPOSSIBLE
    override fun internalAddItem(to: Cylinder?, item: Item?, index: Int, flags: Int): ReturnValue = ReturnValue.NOTPOSSIBLE
    override fun internalRemoveItem(item: Item, count: Int): ReturnValue = ReturnValue.NOTPOSSIBLE
    override fun createItem(id: Int): Item? = null
    override fun removeMoney(player: Player, amount: Long, subType: Int, useBank: Boolean): Boolean = false
    override fun getLastLoginSaved(player: Player): Long = 0
    override fun isVip(player: Player): Boolean = false
    override fun getBankBalance(player: Player): Long = 0
    override fun hasTown(townId: Int): Boolean = false
    override fun isWrapable(item: Item): Boolean {
        val type = item.type ?: return false
        return type.isWrappable && type.wrapableTo > 0
    }
    override fun isWrapContainer(item: Item): Boolean = false
    override fun getUnWrapId(item: Item): Int = 0
    override fun wrapItem(item: Item, house: House): Item = item
    override fun getItemType(itemId: Int): ItemType? = null
    override fun wakeUpBed(bed: BedItem) = Unit
    override fun markNavigationTopologyChanged(position: Position) = Unit
    override fun updateOwnerRow(house: House, guid: Long) = Unit
    override fun updateNewOwnerRow(house: House, newOwnerGuid: Int) = Unit
    override fun timeNow(): Long = System.currentTimeMillis() / 1000
    override val houseRentRate: Float = 1.0f
    override val housePricePerSqm: Int = 1000
    override val housePriceRentMultiplier: Float = 1.0f
    override val housePurchasedShowPrice: Boolean = false
    override val cyclopediaHouseAuction: Boolean = true
    override val houseLoseAfterInactivity: Int = 0
    override val vipKeepHouse: Boolean = false
    override val toggleHouseTransferOnServerRestart: Boolean = false
    override val serverSaveTime: String = "06:00"
}
