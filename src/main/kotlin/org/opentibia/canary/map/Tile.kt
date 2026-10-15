package org.opentibia.canary.map

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.game.movement.Teleport
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.game.zones.Zone
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.game.zones.getZoneType as zoneTypeOf
import org.opentibia.canary.items.BedItem
import org.opentibia.canary.items.Cylinder
import org.opentibia.canary.items.CylinderLink
import org.opentibia.canary.items.INDEX_WHEREEVER
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemProperty
import org.opentibia.canary.items.MagicField
import org.opentibia.canary.items.QueryDestinationResult
import org.opentibia.canary.items.QueryMaxCountResult
import org.opentibia.canary.items.Thing
import org.opentibia.canary.items.TrashHolder
import org.opentibia.canary.map.house.House
import org.opentibia.canary.systems.rewardchest.ReturnValue
import kotlin.math.max

/**
 * Port of `class Tile` (src/items/tile.hpp:30-290, src/items/tile.cpp). The
 * single Kotlin class merges the C++ `DynamicTile`/`StaticTile`/`HouseTile`
 * storage variants (the map sector always materializes the vectors) and exposes
 * `houseId`/`house` for the house-tile link the C++ `HouseTile` overrides.
 *
 * Faithful surface: item/creature storage, top/down ordering, tile flags and
 * protection zones, fields, house door registration, thing indexes, cylinder
 * queries, zone membership.
 *
 * PARITY GAPS (need `g_game()`/spectators/`g_moveEvents()`/dispatcher):
 * `onAddTileItem`/`onUpdateTileItem`/`onRemoveTileItem`/`onUpdateTile` client
 * sends, `browseFields` remaps, automatic carpet propagation, clean-tile
 * scheduling, movement-script calls, `Zone::itemAdded`/`thingAdded` callbacks,
 * the floor-change `ItemType::floorChange` bit, mailbox/depot tile flags,
 * `safeCall` and the monster/player branches of `queryAdd`.
 */
open class Tile(override val position: Position) : Cylinder {
    /** `Tile::ground` (src/items/tile.hpp:286). */
    var ground: Item? = null

    /**
     * `TileItemVector items` (src/items/tile.hpp:30-102): down items occupy
     * `[0, downItemCount)` in reverse insertion order, top items follow in
     * ascending `alwaysOnTopOrder`.
     */
    private val _items = mutableListOf<Item>()
    val items: List<Item> get() = _items

    private var downItemCount: Int = 0

    /** `CreatureVector creatures` (src/items/tile.hpp:27). */
    private val _creatures = mutableListOf<Creature>()
    fun getCreatures(): List<Creature> = _creatures

    /** `Tile::flags` (`TileFlags_t` bits, src/items/items_definitions.hpp:443-477). */
    var flags: Int = 0

    /** `HouseTile` marker; `0` for a plain tile. */
    var houseId: Int = 0

    /** `HouseTile::house` (src/map/house/housetile.hpp:40); base `Tile::getHouse` returns null. */
    var house: House? = null

    /** `Tile::zones` (src/items/tile.hpp:289). */
    private val zones = linkedSetOf<Zone>()

    /**
     * The owning map, used by `queryDestination`/`getSurroundingTiles` in place
     * of the C++ `g_game().map` global. `GameMap` wires it on tile creation.
     */
    var map: GameMap? = null

    // --- thing metadata -------------------------------------------------------

    /** `Tile::getDescription` (src/items/tile.cpp:241-243). */
    override fun getDescription(lookDistance: Int): String =
        "You dont know why, but you cant see anything!"

    /** `Tile::getThrowRange` (src/items/tile.hpp:126-128). */
    override fun getThrowRange(): Int = 0

    /** `Tile::isPushable` (src/items/tile.hpp:129-131). */
    override fun isPushable(): Boolean = false

    /** `Tile::getTile` (src/items/tile.hpp:133-135). */
    override fun getTile(): Tile = this

    /** `Tile::getCylinder` (src/items/tile.hpp:137-139). */
    override fun getCylinder(): Cylinder = this

    /** `Tile::isRemoved` (src/items/tile.hpp:249-251). */
    override fun isRemoved(): Boolean = false

    /** `HouseTile::getHouse` (src/map/house/housetile.hpp:33-35). */
    @JvmName("getTileHouse")
    fun getHouse(): House? = house

    /** `Tile::isHouse` (src/items/tile.hpp). */
    fun isHouse(): Boolean = houseId != 0

    // --- flags and properties -------------------------------------------------

    /** `Tile::hasFlag` (src/items/tile.cpp:182-184). */
    fun hasFlag(flag: Int): Boolean = (flags and flag) != 0

    /** `Tile::setFlag` (src/items/tile.hpp:175-177). */
    fun setFlag(flag: Int) {
        flags = flags or flag
    }

    /** `Tile::resetFlag` (src/items/tile.hpp:178-180). */
    fun resetFlag(flag: Int) {
        flags = flags and flag.inv()
    }

    /** `Tile::getZoneType` (src/items/tile.hpp:188-199). */
    fun getZoneType(): ZoneType = zoneTypeOf(flags)

    /** `Tile::hasProperty(ItemProperty)` (src/items/tile.cpp:124-153). */
    fun hasProperty(prop: ItemProperty): Boolean = when (prop) {
        ItemProperty.CONST_PROP_BLOCKSOLID -> hasFlag(TileFlags.TILESTATE_BLOCKSOLID.value)
        ItemProperty.CONST_PROP_HASHEIGHT -> hasFlag(TileFlags.TILESTATE_HASHEIGHT.value)
        ItemProperty.CONST_PROP_BLOCKPROJECTILE -> hasFlag(TileFlags.TILESTATE_BLOCKPROJECTILE.value)
        ItemProperty.CONST_PROP_BLOCKPATH -> hasFlag(TileFlags.TILESTATE_BLOCKPATH.value)
        ItemProperty.CONST_PROP_ISVERTICAL -> hasFlag(TileFlags.TILESTATE_ISVERTICAL.value)
        ItemProperty.CONST_PROP_ISHORIZONTAL -> hasFlag(TileFlags.TILESTATE_ISHORIZONTAL.value)
        ItemProperty.CONST_PROP_MOVABLE -> hasFlag(TileFlags.TILESTATE_MOVABLE.value)
        ItemProperty.CONST_PROP_IMMOVABLEBLOCKSOLID -> hasFlag(TileFlags.TILESTATE_IMMOVABLEBLOCKSOLID.value)
        ItemProperty.CONST_PROP_IMMOVABLEBLOCKPATH -> hasFlag(TileFlags.TILESTATE_IMMOVABLEBLOCKPATH.value)
        ItemProperty.CONST_PROP_IMMOVABLENOFIELDBLOCKPATH ->
            hasFlag(TileFlags.TILESTATE_IMMOVABLENOFIELDBLOCKPATH.value)

        ItemProperty.CONST_PROP_NOFIELDBLOCKPATH -> hasFlag(TileFlags.TILESTATE_NOFIELDBLOCKPATH.value)
        ItemProperty.CONST_PROP_SUPPORTHANGABLE -> hasFlag(TileFlags.TILESTATE_SUPPORTS_HANGABLE.value)
    }

    /** `Tile::hasProperty(exclude, prop)` (src/items/tile.cpp:155-180). */
    fun hasProperty(exclude: Item?, prop: ItemProperty): Boolean {
        if (exclude == null) {
            return false
        }

        if (ground != null && ground !== exclude && ground!!.hasProperty(prop)) {
            return true
        }

        for (item in _items) {
            if (item !== exclude && item.hasProperty(prop)) {
                return true
            }
        }
        return false
    }

    /** `Tile::hasHeight` (src/items/tile.cpp:186-211). */
    fun hasHeight(n: Int): Boolean {
        var height = 0

        val currentGround = ground
        if (currentGround != null) {
            if (currentGround.hasProperty(ItemProperty.CONST_PROP_HASHEIGHT)) {
                ++height
            }
            if (n == height) {
                return true
            }
        }

        for (item in _items) {
            if (item.hasProperty(ItemProperty.CONST_PROP_HASHEIGHT)) {
                ++height
            }
            if (n == height) {
                return true
            }
        }
        return false
    }

    /** `Tile::isMovableBlocking` (src/items/tile.cpp:2004-2006). */
    fun isMovableBlocking(): Boolean = ground == null || hasFlag(TileFlags.TILESTATE_BLOCKSOLID.value)

    // --- counts ---------------------------------------------------------------

    /** `Tile::getCreatureCount` (src/items/tile.cpp:213-218). */
    fun getCreatureCount(): Int = _creatures.size

    /** `Tile::getItemCount` (src/items/tile.cpp:220-225). */
    fun getItemCount(): Int = _items.size

    /** `Tile::getTopItemCount` (src/items/tile.cpp:227-232). */
    fun getTopItemCount(): Int = _items.size - downItemCount

    /** `Tile::getDownItemCount` (src/items/tile.cpp:234-239). */
    fun getDownItemCount(): Int = downItemCount

    /** `Tile::getThingCount` (src/items/tile.hpp:158-164). */
    fun getThingCount(): Int = getCreatureCount() + getItemCount() + if (ground != null) 1 else 0

    // --- special item lookups -------------------------------------------------

    /** `Tile::getTeleportItem` (src/items/tile.cpp:245-258). */
    fun getTeleportItem(): Teleport? {
        if (!hasFlag(TileFlags.TILESTATE_TELEPORT.value)) {
            return null
        }

        for (index in _items.indices.reversed()) {
            val teleport = _items[index].getTeleport()
            if (teleport != null) {
                return teleport
            }
        }
        return null
    }

    /** `Tile::getFieldItem` (src/items/tile.cpp:260-277). */
    fun getFieldItem(): MagicField? {
        if (!hasFlag(TileFlags.TILESTATE_MAGICFIELD.value)) {
            return null
        }

        ground?.getMagicField()?.let { return it }

        for (index in _items.indices.reversed()) {
            val field = _items[index].getMagicField()
            if (field != null) {
                return field
            }
        }
        return null
    }

    /** `Tile::getTrashHolder` (src/items/tile.cpp:279-296). */
    fun getTrashHolder(): TrashHolder? {
        if (!hasFlag(TileFlags.TILESTATE_TRASHHOLDER.value)) {
            return null
        }

        ground?.getTrashHolder()?.let { return it }

        for (index in _items.indices.reversed()) {
            val trashHolder = _items[index].getTrashHolder()
            if (trashHolder != null) {
                return trashHolder
            }
        }
        return null
    }

    /** `Tile::getBedItem` (src/items/tile.cpp:317-334). */
    fun getBedItem(): BedItem? {
        if (!hasFlag(TileFlags.TILESTATE_BED.value)) {
            return null
        }

        ground?.getBed()?.let { return it }

        for (index in _items.indices.reversed()) {
            val bed = _items[index].getBed()
            if (bed != null) {
                return bed
            }
        }
        return null
    }

    /** `Tile::hasHarmfulField` (src/items/tile.cpp:974-976). */
    fun hasHarmfulField(): Boolean {
        if (!hasFlag(TileFlags.TILESTATE_MAGICFIELD.value)) {
            return false
        }
        val field = getFieldItem() ?: return false
        return !field.isBlocking() && field.getDamage() > 0
    }

    /** `Tile::checkNpcCanWalkIntoTile` (src/items/tile.cpp:966-972). */
    fun checkNpcCanWalkIntoTile(): ReturnValue =
        if (hasHarmfulField()) ReturnValue.NOTPOSSIBLE else ReturnValue.NOERROR

    // --- creature lookups -----------------------------------------------------

    /** `Tile::getTopCreature` (src/items/tile.cpp:336-343). */
    fun getTopCreature(): Creature? = _creatures.firstOrNull()

    /** `Tile::getBottomCreature` (src/items/tile.cpp:345-352). */
    fun getBottomCreature(): Creature? = _creatures.lastOrNull()

    /** `Tile::getTopVisibleCreature` (src/items/tile.cpp:354-379). */
    fun getTopVisibleCreature(creature: Creature?): Creature? {
        if (creature != null) {
            val player = creature as? Player
            if (player != null && player.isAccessPlayer()) {
                return getTopCreature()
            }

            for (tileCreature in _creatures) {
                if (creature.canSeeCreature(tileCreature)) {
                    return tileCreature
                }
            }
        } else {
            for (tileCreature in _creatures) {
                // tile.cpp:362-366: only non-ghost creatures are returned.
                if (!tileCreature.isInvisible() && !tileCreature.isInGhostMode()) {
                    return tileCreature
                }
            }
        }
        return null
    }

    /** `Tile::getBottomVisibleCreature` (src/items/tile.cpp:381-406). */
    fun getBottomVisibleCreature(creature: Creature?): Creature? {
        if (creature != null) {
            val player = creature as? Player
            if (player != null && player.isAccessPlayer()) {
                return getBottomCreature()
            }

            for (index in _creatures.indices.reversed()) {
                val tileCreature = _creatures[index]
                if (creature.canSeeCreature(tileCreature)) {
                    return tileCreature
                }
            }
        } else {
            for (index in _creatures.indices.reversed()) {
                val tileCreature = _creatures[index]
                // tile.cpp:389-393: only non-ghost creatures are returned.
                if (!tileCreature.isInvisible() && !tileCreature.isInGhostMode()) {
                    return tileCreature
                }
            }
        }
        return null
    }

    // --- item lookups ---------------------------------------------------------

    /** `Tile::getTopDownItem` (src/items/tile.cpp:408-413). */
    fun getTopDownItem(): Item? = if (downItemCount == 0) null else _items[0]

    /** `Tile::getTopTopItem` (src/items/tile.cpp:415-420). */
    fun getTopTopItem(): Item? = if (getTopItemCount() == 0) null else _items.last()

    /** `Tile::getItemByTopOrder` (src/items/tile.cpp:422-436). */
    fun getItemByTopOrder(topOrder: Int): Item? {
        for (index in _items.indices.reversed()) {
            if (index < downItemCount) {
                break
            }
            if ((_items[index].type?.alwaysOnTopOrder ?: 0) == topOrder) {
                return _items[index]
            }
        }
        return null
    }

    /** `Tile::getTopVisibleThing` (src/items/tile.cpp:438-462). */
    fun getTopVisibleThing(creature: Creature?): Thing? {
        getTopVisibleCreature(creature)?.let { return it }

        for (index in 0 until downItemCount) {
            val item = _items[index]
            if (item.type?.lookThrough != true) {
                return item
            }
        }

        for (index in _items.indices.reversed()) {
            if (index < downItemCount) {
                break
            }
            val item = _items[index]
            if (item.type?.lookThrough != true) {
                return item
            }
        }

        return ground
    }

    /** `Tile::getUseItem` (src/items/tile.cpp:2008-2019). */
    fun getUseItem(index: Int): Item? {
        if (_items.isEmpty()) {
            return ground
        }
        return getThing(index)?.getItem()
    }

    /** `Tile::getDoorItem` (src/items/tile.cpp:2021-2037). */
    fun getDoorItem(): Item? {
        if (_items.isEmpty()) {
            return ground
        }
        return _items.firstOrNull { it.type?.isDoor == true }
    }

    // --- thing indexes --------------------------------------------------------

    /** `Tile::getThingIndex` (src/items/tile.cpp:1443-1496). */
    override fun getThingIndex(thing: Thing?): Int {
        if (thing == null) {
            return -1
        }

        var n = -1
        if (ground != null) {
            if (ground === thing) {
                return 0
            }
            ++n
        }

        val item = thing.getItem()
        if (item != null && item.isAlwaysOnTop()) {
            for (index in downItemCount until _items.size) {
                ++n
                if (_items[index] === item) {
                    return n
                }
            }
        } else {
            n += getTopItemCount()
        }

        val creature = thing.getCreature()
        if (creature != null) {
            for (tileCreature in _creatures) {
                ++n
                if (tileCreature === creature) {
                    return n
                }
            }
        } else {
            n += _creatures.size
        }

        if (item != null && !item.isAlwaysOnTop()) {
            for (index in 0 until downItemCount) {
                ++n
                if (_items[index] === item) {
                    return n
                }
            }
        }
        return -1
    }

    /** `Tile::getClientIndexOfCreature` (src/items/tile.cpp:1498-1525). */
    fun getClientIndexOfCreature(player: Player?, creature: Creature?): Int {
        if (player == null || creature == null) {
            return -1
        }

        var n = if (ground != null) 1 else 0
        n += getTopItemCount()

        for (index in _creatures.indices.reversed()) {
            val tileCreature = _creatures[index]
            if (tileCreature === creature) {
                return n
            }
            if (player.canSeeCreature(tileCreature)) {
                ++n
            }
        }
        return -1
    }

    /** `Tile::getStackposOfCreature` (src/items/tile.cpp:1531-1559). */
    fun getStackposOfCreature(player: Player?, creature: Creature?): Int {
        if (player == null || creature == null) {
            return -1
        }

        var n = if (ground != null) 1 else 0
        n += getTopItemCount()
        if (n >= 10) {
            return -1
        }

        for (index in _creatures.indices.reversed()) {
            val tileCreature = _creatures[index]
            if (tileCreature === creature) {
                return n
            }
            if (player.canSeeCreature(tileCreature)) {
                if (++n >= 10) {
                    return -1
                }
            }
        }
        return -1
    }

    /** `Tile::getStackposOfItem` (src/items/tile.cpp:1561-1608). */
    fun getStackposOfItem(player: Player?, item: Item?): Int {
        if (player == null || item == null) {
            return -1
        }

        var n = 0
        if (ground != null) {
            if (ground === item) {
                return n
            }
            ++n
        }

        if (item.isAlwaysOnTop()) {
            for (index in downItemCount until _items.size) {
                if (_items[index] === item) {
                    return n
                } else if (++n == 10) {
                    return -1
                }
            }
        } else {
            n += getTopItemCount()
            if (n >= 10) {
                return -1
            }
        }

        for (tileCreature in _creatures) {
            if (player.canSeeCreature(tileCreature)) {
                if (++n >= 10) {
                    return -1
                }
            }
        }

        if (!item.isAlwaysOnTop()) {
            for (index in 0 until downItemCount) {
                if (_items[index] === item) {
                    return n
                } else if (++n >= 10) {
                    return -1
                }
            }
        }
        return -1
    }

    /** `Tile::getFirstIndex` (src/items/tile.cpp:1610-1612). */
    override fun getFirstIndex(): Int = 0

    /** `Tile::getLastIndex` (src/items/tile.cpp:1614-1616). */
    override fun getLastIndex(): Int = getThingCount()

    /** `Tile::getItemTypeCount` (src/items/tile.cpp:1618-1633). */
    override fun getItemTypeCount(itemId: Int, subType: Int): Int {
        var count = 0
        if (ground?.id == itemId) {
            count += Item.countByType(ground, subType)
        }
        for (item in _items) {
            if (item.id == itemId) {
                count += Item.countByType(item, subType)
            }
        }
        return count
    }

    /** `Tile::getThing` (src/items/tile.cpp:1635-1664). */
    override fun getThing(index: Int): Thing? {
        var remaining = index
        if (ground != null) {
            if (remaining == 0) {
                return ground
            }
            --remaining
        }

        val topItemSize = getTopItemCount()
        if (remaining < topItemSize) {
            return _items[downItemCount + remaining]
        }
        remaining -= topItemSize

        if (remaining < _creatures.size) {
            return _creatures[remaining]
        }
        remaining -= _creatures.size

        if (remaining < downItemCount) {
            return _items[remaining]
        }
        return null
    }

    // --- legacy item storage seam --------------------------------------------

    /**
     * Legacy Kotlin storage helper, not part of the C++ `Tile` API: appends an
     * item without classification. New code should use [internalAddThing] or
     * [addThing] so the top/down ordering matches Canary.
     */
    fun addItem(item: Item) {
        _items.add(item)
    }

    /** Legacy Kotlin removal helper; keeps the down-item counter consistent. */
    fun removeItem(item: Item): Boolean {
        val index = _items.indexOfFirst { it === item }
        if (index < 0) {
            return false
        }
        _items.removeAt(index)
        if (index < downItemCount) {
            --downItemCount
        }
        return true
    }

    // --- item list mutation ---------------------------------------------------

    /** `Tile::onAddTileItem` (src/items/tile.cpp:464-541); the client sends, browse-field remap and carpet propagation are PARITY GAPS. */
    private fun onAddTileItem(item: Item) {
        setTileFlags(item)
    }

    /** `Tile::onUpdateTileItem` (src/items/tile.cpp:543-591); navigation/spectator updates are PARITY GAPS. */
    private fun onUpdateTileItem(oldItem: Item, newItem: Item) {
        if (oldItem === newItem) {
            return
        }
    }

    /** `Tile::onRemoveTileItem` (src/items/tile.cpp:593-680); spectator sends, zone callbacks and carpet cleanup are PARITY GAPS. */
    private fun onRemoveTileItem(item: Item) {
        resetTileFlags(item)
    }

    /** `Tile::updateTileFlags` (src/items/tile.cpp:1819-1823). */
    fun updateTileFlags(item: Item) {
        resetTileFlags(item)
        setTileFlags(item)
    }

    /** `Tile::setGround` (src/items/tile.cpp:1825-1834). */
    @JvmName("setGroundItem")
    fun setGround(item: Item) {
        ground?.let { resetTileFlags(it) }
        ground = item
        setTileFlags(item)
    }

    /** `Tile::setTileFlags` (src/items/tile.cpp:1836-1921); the floor-change bit, mailbox and depot flags are PARITY GAPS. */
    fun setTileFlags(item: Item) {
        if (item.hasProperty(ItemProperty.CONST_PROP_IMMOVABLEBLOCKSOLID)) {
            setFlag(TileFlags.TILESTATE_IMMOVABLEBLOCKSOLID.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_BLOCKPATH)) {
            setFlag(TileFlags.TILESTATE_BLOCKPATH.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_NOFIELDBLOCKPATH)) {
            setFlag(TileFlags.TILESTATE_NOFIELDBLOCKPATH.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_IMMOVABLENOFIELDBLOCKPATH)) {
            setFlag(TileFlags.TILESTATE_IMMOVABLENOFIELDBLOCKPATH.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_SUPPORTHANGABLE)) {
            setFlag(TileFlags.TILESTATE_SUPPORTS_HANGABLE.value)
        }

        if (item.getTeleport() != null) {
            setFlag(TileFlags.TILESTATE_TELEPORT.value)
        }

        if (item.getMagicField() != null) {
            setFlag(TileFlags.TILESTATE_MAGICFIELD.value)
        }

        if (item.getTrashHolder() != null) {
            setFlag(TileFlags.TILESTATE_TRASHHOLDER.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_BLOCKSOLID)) {
            setFlag(TileFlags.TILESTATE_BLOCKSOLID.value)
        }

        if (item.getBed() != null) {
            setFlag(TileFlags.TILESTATE_BED.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_IMMOVABLEBLOCKPATH)) {
            setFlag(TileFlags.TILESTATE_IMMOVABLEBLOCKPATH.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_MOVABLE)) {
            setFlag(TileFlags.TILESTATE_MOVABLE.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_ISHORIZONTAL)) {
            setFlag(TileFlags.TILESTATE_ISHORIZONTAL.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_ISVERTICAL)) {
            setFlag(TileFlags.TILESTATE_ISVERTICAL.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_BLOCKPROJECTILE)) {
            setFlag(TileFlags.TILESTATE_BLOCKPROJECTILE.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_HASHEIGHT)) {
            setFlag(TileFlags.TILESTATE_HASHEIGHT.value)
        }
    }

    /** `Tile::resetTileFlags` (src/items/tile.cpp:1923-2002); the floor-change bit, mailbox and depot flags are PARITY GAPS. */
    fun resetTileFlags(item: Item) {
        if (item.hasProperty(ItemProperty.CONST_PROP_BLOCKSOLID) &&
            !hasProperty(item, ItemProperty.CONST_PROP_BLOCKSOLID)
        ) {
            resetFlag(TileFlags.TILESTATE_BLOCKSOLID.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_IMMOVABLEBLOCKSOLID) &&
            !hasProperty(item, ItemProperty.CONST_PROP_IMMOVABLEBLOCKSOLID)
        ) {
            resetFlag(TileFlags.TILESTATE_IMMOVABLEBLOCKSOLID.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_BLOCKPATH) &&
            !hasProperty(item, ItemProperty.CONST_PROP_BLOCKPATH)
        ) {
            resetFlag(TileFlags.TILESTATE_BLOCKPATH.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_NOFIELDBLOCKPATH) &&
            !hasProperty(item, ItemProperty.CONST_PROP_NOFIELDBLOCKPATH)
        ) {
            resetFlag(TileFlags.TILESTATE_NOFIELDBLOCKPATH.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_IMMOVABLEBLOCKPATH) &&
            !hasProperty(item, ItemProperty.CONST_PROP_IMMOVABLEBLOCKPATH)
        ) {
            resetFlag(TileFlags.TILESTATE_IMMOVABLEBLOCKPATH.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_IMMOVABLENOFIELDBLOCKPATH) &&
            !hasProperty(item, ItemProperty.CONST_PROP_IMMOVABLENOFIELDBLOCKPATH)
        ) {
            resetFlag(TileFlags.TILESTATE_IMMOVABLENOFIELDBLOCKPATH.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_MOVABLE) &&
            !hasProperty(item, ItemProperty.CONST_PROP_MOVABLE)
        ) {
            resetFlag(TileFlags.TILESTATE_MOVABLE.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_ISHORIZONTAL) &&
            !hasProperty(item, ItemProperty.CONST_PROP_ISHORIZONTAL)
        ) {
            resetFlag(TileFlags.TILESTATE_ISHORIZONTAL.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_ISVERTICAL) &&
            !hasProperty(item, ItemProperty.CONST_PROP_ISVERTICAL)
        ) {
            resetFlag(TileFlags.TILESTATE_ISVERTICAL.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_BLOCKPROJECTILE) &&
            !hasProperty(item, ItemProperty.CONST_PROP_BLOCKPROJECTILE)
        ) {
            resetFlag(TileFlags.TILESTATE_BLOCKPROJECTILE.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_HASHEIGHT) &&
            !hasProperty(item, ItemProperty.CONST_PROP_HASHEIGHT)
        ) {
            resetFlag(TileFlags.TILESTATE_HASHEIGHT.value)
        }

        if (item.getTeleport() != null) {
            resetFlag(TileFlags.TILESTATE_TELEPORT.value)
        }

        if (item.getMagicField() != null) {
            resetFlag(TileFlags.TILESTATE_MAGICFIELD.value)
        }

        if (item.getTrashHolder() != null) {
            resetFlag(TileFlags.TILESTATE_TRASHHOLDER.value)
        }

        if (item.getBed() != null) {
            resetFlag(TileFlags.TILESTATE_BED.value)
        }

        if (item.hasProperty(ItemProperty.CONST_PROP_SUPPORTHANGABLE)) {
            resetFlag(TileFlags.TILESTATE_SUPPORTS_HANGABLE.value)
        }
    }

    // --- cylinder queries -----------------------------------------------------

    /**
     * `Tile::queryAdd` (src/items/tile.cpp:691-964). The item branch is
     * faithful; the creature branch ports the npc/pathfinding/occupancy and
     * ignore-block-item rules, while the monster push/field rules, player
     * account/PZ-lock rules and the expert-pvp field step need `g_game()`,
     * `g_configManager()` and `ExpertPvp` (PARITY GAP).
     */
    override fun queryAdd(index: Int, thing: Thing?, count: Int, flags: Int, actor: Creature?): ReturnValue {
        if (thing == null) {
            return ReturnValue.NOTPOSSIBLE
        }

        if (flags and CylinderFlags.FLAG_NOLIMIT != 0) {
            return ReturnValue.NOERROR
        }

        val creature = thing.getCreature()
        if (creature != null) {
            if (creature is Npc) {
                val returnValue = checkNpcCanWalkIntoTile()
                if (returnValue != ReturnValue.NOERROR) {
                    return returnValue
                }
            }

            if (flags and CylinderFlags.FLAG_PATHFINDING != 0 &&
                hasFlag(TileFlags.TILESTATE_FLOORCHANGE.value or TileFlags.TILESTATE_TELEPORT.value)
            ) {
                return ReturnValue.NOTPOSSIBLE
            }

            // Creature::isMoveLocked() is not modelled (PARITY GAP).
            if (ground == null) {
                return ReturnValue.NOTPOSSIBLE
            }

            if (_creatures.isNotEmpty() && flags and CylinderFlags.FLAG_IGNOREBLOCKCREATURE == 0) {
                for (tileCreature in _creatures) {
                    // tile.cpp:850: only non-ghost creatures occupy the tile.
                    if (!tileCreature.isInGhostMode()) {
                        return ReturnValue.NOTENOUGHROOM
                    }
                }
            }

            if (flags and CylinderFlags.FLAG_IGNOREBLOCKITEM == 0) {
                if (hasFlag(TileFlags.TILESTATE_BLOCKSOLID.value)) {
                    // ExpertPvp field step and safe magic-wall removal are
                    // PARITY GAPS.
                    return ReturnValue.NOTENOUGHROOM
                }
            } else {
                val currentGround = ground
                if (currentGround != null) {
                    val groundType = currentGround.type
                    if (groundType?.blockSolid == true &&
                        (groundType.isMovable != true || currentGround.hasAttribute(ItemAttribute.UNIQUEID))
                    ) {
                        return ReturnValue.NOTPOSSIBLE
                    }
                }

                for (item in _items) {
                    val itemType = item.type
                    if (itemType?.blockSolid == true &&
                        (itemType.isMovable != true || item.hasAttribute(ItemAttribute.UNIQUEID))
                    ) {
                        return ReturnValue.NOTPOSSIBLE
                    }
                }
            }
        } else if (thing.getItem() != null) {
            val item = thing.getItem()!!
            if (_items.size >= 0x3E8) {
                return ReturnValue.NOTPOSSIBLE
            }

            val itemIsHangable = item.isHangable()
            if (ground == null && !itemIsHangable) {
                return ReturnValue.NOTPOSSIBLE
            }

            if (_creatures.isNotEmpty() && item.isBlocking() && flags and CylinderFlags.FLAG_IGNOREBLOCKCREATURE == 0) {
                for (tileCreature in _creatures) {
                    // tile.cpp:911: only non-ghost creatures occupy the tile.
                    if (!tileCreature.isInGhostMode()) {
                        return ReturnValue.NOTENOUGHROOM
                    }
                }
            }

            if (itemIsHangable && hasFlag(TileFlags.TILESTATE_SUPPORTS_HANGABLE.value)) {
                for (tileItem in _items) {
                    if (tileItem.isHangable()) {
                        return ReturnValue.NEEDEXCHANGE
                    }
                }
            } else {
                val currentGround = ground
                if (currentGround != null) {
                    val groundType = currentGround.type
                    if (groundType?.blockSolid == true) {
                        if ((groundType.isPickupable != true && groundType.isTrashHolder != true) ||
                            item.isMagicField() ||
                            item.isBlocking()
                        ) {
                            if (!item.isPickupable() && !item.isCarpet()) {
                                return ReturnValue.NOTENOUGHROOM
                            }

                            if (groundType.hasHeight != true) {
                                return ReturnValue.NOTENOUGHROOM
                            }
                        }
                    }
                }

                for (tileItem in _items) {
                    val itemType = tileItem.type
                    if (itemType?.blockSolid != true || itemType.isTrashHolder == true) {
                        continue
                    }

                    if (itemType.isPickupable == true && !item.isMagicField() && !item.isBlocking()) {
                        continue
                    }

                    if (!item.isPickupable()) {
                        return ReturnValue.NOTENOUGHROOM
                    }

                    if (itemType.hasHeight != true || itemType.isPickupable == true) {
                        return ReturnValue.NOTENOUGHROOM
                    }
                }
            }
        }
        return ReturnValue.NOERROR
    }

    /** `Tile::queryMaxCount` (src/items/tile.cpp:978-981). */
    override fun queryMaxCount(
        index: Int,
        thing: Thing?,
        count: Int,
        maxQueryCount: Int,
        flags: Int,
    ): QueryMaxCountResult = QueryMaxCountResult(ReturnValue.NOERROR, max(1, count))

    /** `Tile::queryRemove` (src/items/tile.cpp:983-1007). */
    override fun queryRemove(thing: Thing?, count: Int, flags: Int, actor: Creature?): ReturnValue {
        if (thing == null) {
            return ReturnValue.NOTPOSSIBLE
        }

        if (getThingIndex(thing) == -1) {
            return ReturnValue.NOTPOSSIBLE
        }

        val item = thing.getItem() ?: return ReturnValue.NOTPOSSIBLE
        if (count == 0 || (item.isStackable() && count > item.getItemCount())) {
            return ReturnValue.NOTPOSSIBLE
        }

        if (!item.isMovable() && flags and CylinderFlags.FLAG_IGNORENOTMOVABLE == 0) {
            return ReturnValue.NOTMOVABLE
        }

        return ReturnValue.NOERROR
    }

    /** `Tile::queryDestination` (src/items/tile.cpp:1009-1111); the mailbox equality check is a PARITY GAP. */
    override fun queryDestination(index: Int, thing: Thing?, destItem: Item?, flags: Int): QueryDestinationResult {
        var destTile: Tile? = null
        var destItemResult: Item? = null
        var outFlags = flags
        val map = this.map

        if (hasFlag(TileFlags.TILESTATE_FLOORCHANGE_DOWN.value)) {
            var dx = position.x
            var dy = position.y
            val dz = position.z + 1

            val southDownTile = map?.getTile(Position(dx, dy - 1, dz))
            if (southDownTile != null && southDownTile.hasFlag(TileFlags.TILESTATE_FLOORCHANGE_SOUTH_ALT.value)) {
                dy -= 2
                destTile = map.getTile(Position(dx, dy, dz))
            } else {
                val eastDownTile = map?.getTile(Position(dx - 1, dy, dz))
                if (eastDownTile != null && eastDownTile.hasFlag(TileFlags.TILESTATE_FLOORCHANGE_EAST_ALT.value)) {
                    dx -= 2
                    destTile = map.getTile(Position(dx, dy, dz))
                } else {
                    val downTile = map?.getTile(Position(dx, dy, dz))
                    if (downTile != null) {
                        if (downTile.hasFlag(TileFlags.TILESTATE_FLOORCHANGE_NORTH.value)) {
                            ++dy
                        }
                        if (downTile.hasFlag(TileFlags.TILESTATE_FLOORCHANGE_SOUTH.value)) {
                            --dy
                        }
                        if (downTile.hasFlag(TileFlags.TILESTATE_FLOORCHANGE_SOUTH_ALT.value)) {
                            dy -= 2
                        }
                        if (downTile.hasFlag(TileFlags.TILESTATE_FLOORCHANGE_EAST.value)) {
                            --dx
                        }
                        if (downTile.hasFlag(TileFlags.TILESTATE_FLOORCHANGE_EAST_ALT.value)) {
                            dx -= 2
                        }
                        if (downTile.hasFlag(TileFlags.TILESTATE_FLOORCHANGE_WEST.value)) {
                            ++dx
                        }
                        destTile = map.getTile(Position(dx, dy, dz))
                    }
                }
            }
        } else if (hasFlag(TileFlags.TILESTATE_FLOORCHANGE.value)) {
            var dx = position.x
            var dy = position.y
            val dz = position.z - 1

            if (hasFlag(TileFlags.TILESTATE_FLOORCHANGE_NORTH.value)) {
                --dy
            }
            if (hasFlag(TileFlags.TILESTATE_FLOORCHANGE_SOUTH.value)) {
                ++dy
            }
            if (hasFlag(TileFlags.TILESTATE_FLOORCHANGE_EAST.value)) {
                ++dx
            }
            if (hasFlag(TileFlags.TILESTATE_FLOORCHANGE_WEST.value)) {
                --dx
            }
            if (hasFlag(TileFlags.TILESTATE_FLOORCHANGE_SOUTH_ALT.value)) {
                dy += 2
            }
            if (hasFlag(TileFlags.TILESTATE_FLOORCHANGE_EAST_ALT.value)) {
                dx += 2
            }

            destTile = map?.getTile(Position(dx, dy, dz))
        }

        if (destTile == null) {
            destTile = this
        } else {
            outFlags = outFlags or CylinderFlags.FLAG_NOLIMIT
        }

        val destThing = destTile.getTopDownItem()
        if (destThing != null) {
            destItemResult = destThing.getItem()
            val thingItem = thing?.getItem()
            // `thingItem.getMailbox() != destItem.getMailbox()` is always false
            // in the Kotlin model (no Mailbox type), so only the null check remains.
            if (thingItem == null) {
                return QueryDestinationResult(destTile, index, destItemResult, outFlags)
            }
            val destCylinder = destThing.getCylinder()
            if (destCylinder != null && destCylinder.getContainer() == null) {
                return QueryDestinationResult(destCylinder, index, destItemResult, outFlags)
            }
        }
        return QueryDestinationResult(destTile, index, destItemResult, outFlags)
    }

    /** `Tile::getSurroundingTiles` (src/items/tile.cpp:1113-1125). */
    fun getSurroundingTiles(): List<Tile?> {
        val map = this.map
        return listOf(
            map?.getTile(Position(position.x - 1, position.y, position.z)),
            map?.getTile(Position(position.x + 1, position.y, position.z)),
            map?.getTile(Position(position.x, position.y - 1, position.z)),
            map?.getTile(Position(position.x, position.y + 1, position.z)),
            map?.getTile(Position(position.x - 1, position.y - 1, position.z)),
            map?.getTile(Position(position.x + 1, position.y + 1, position.z)),
            map?.getTile(Position(position.x + 1, position.y - 1, position.z)),
            map?.getTile(Position(position.x - 1, position.y + 1, position.z)),
        )
    }

    // --- item/creature insertion ---------------------------------------------

    /** `Tile::addThing(thing)` (src/items/tile.cpp:1127-1129). */
    override fun addThing(thing: Thing?) {
        addThing(0, thing)
    }

    /** `Tile::addThing(index, thing)` (src/items/tile.cpp:1131-1243); the spectator notifications and carpet propagation are PARITY GAPS. */
    override fun addThing(index: Int, thing: Thing?) {
        if (thing == null) {
            return
        }

        val creature = thing.getCreature()
        if (creature != null) {
            Spectators.clearCache()
            creature.setParent(this)
            _creatures.add(0, creature)
            return
        }

        val item = thing.getItem() ?: return
        if (_items.size >= 0xFFFF) {
            return
        }

        item.setParent(this)

        if (item.isGroundTile()) {
            val currentGround = ground
            if (currentGround == null) {
                ground = item
                onAddTileItem(item)
            } else {
                currentGround.resetParent()
                ground = item
                resetTileFlags(currentGround)
                setTileFlags(item)
                onUpdateTileItem(currentGround, item)
            }
            return
        }

        if (item.isAlwaysOnTop()) {
            if (item.type?.isSplash == true) {
                for (position in downItemCount until _items.size) {
                    val oldSplash = _items[position]
                    if (oldSplash.type?.isSplash != true) {
                        continue
                    }
                    removeThing(oldSplash, 1)
                    oldSplash.resetParent()
                    break
                }
            }

            val newOrder = item.type?.alwaysOnTopOrder ?: 0
            var inserted = false
            for (position in downItemCount until _items.size) {
                val existingOrder = _items[position].type?.alwaysOnTopOrder ?: 0
                if (newOrder <= existingOrder) {
                    _items.add(position, item)
                    inserted = true
                    break
                }
            }
            if (!inserted) {
                _items.add(item)
            }

            onAddTileItem(item)
        } else {
            if (item.isMagicField()) {
                for (position in 0 until downItemCount) {
                    val oldField = _items[position].getMagicField() ?: continue
                    if (oldField.isReplaceable()) {
                        removeThing(oldField, 1)
                        oldField.resetParent()
                        break
                    } else {
                        item.resetParent()
                        return
                    }
                }
            }

            _items.add(0, item)
            ++downItemCount
            onAddTileItem(item)
        }
    }

    /** `Tile::updateThing` (src/items/tile.cpp:1245-1267). */
    override fun updateThing(thing: Thing?, itemId: Int, count: Int) {
        if (thing == null) {
            return
        }

        if (getThingIndex(thing) == -1) {
            return
        }

        val item = thing.getItem() ?: return
        resetTileFlags(item)
        item.setID(itemId)
        item.setSubType(count)
        setTileFlags(item)
        onUpdateTileItem(item, item)
    }

    /** `Tile::replaceThing` (src/items/tile.cpp:1269-1342). */
    override fun replaceThing(index: Int, thing: Thing?) {
        if (thing == null) {
            return
        }

        val item = thing.getItem() ?: return
        var pos = index
        var oldItem: Item? = null
        var isInserted = false

        val currentGround = ground
        if (currentGround != null) {
            if (pos == 0) {
                oldItem = currentGround
                ground = item
                isInserted = true
            }
            --pos
        }

        if (!isInserted) {
            val topItemSize = getTopItemCount()
            if (pos < topItemSize) {
                val absolute = downItemCount + pos
                oldItem = _items[absolute]
                _items[absolute] = item
                isInserted = true
            }
            pos -= topItemSize
        }

        if (!isInserted && pos < _creatures.size) {
            return
        }
        pos -= _creatures.size

        if (!isInserted && pos < downItemCount) {
            oldItem = _items[pos]
            _items[pos] = item
            isInserted = true
        }

        if (isInserted && oldItem != null) {
            item.setParent(this)
            resetTileFlags(oldItem)
            setTileFlags(item)
            onUpdateTileItem(oldItem, item)
            oldItem.resetParent()
        }
    }

    /** `Tile::removeThing` (src/items/tile.cpp:1344-1432); the spectator stack positions and zone callbacks are PARITY GAPS. */
    override fun removeThing(thing: Thing?, count: Int) {
        if (thing == null) {
            return
        }

        val creature = thing.getCreature()
        if (creature != null) {
            val index = _creatures.indexOfFirst { it === creature }
            if (index >= 0) {
                Spectators.clearCache()
                _creatures.removeAt(index)
            }
            return
        }

        val item = thing.getItem() ?: return
        if (getThingIndex(item) == -1) {
            return
        }

        if (item === ground) {
            ground = null
            item.resetParent()
            onRemoveTileItem(item)
            return
        }

        if (item.isAlwaysOnTop()) {
            val index = (downItemCount until _items.size).firstOrNull { _items[it] === item } ?: return
            _items.removeAt(index)
            onRemoveTileItem(item)
            item.resetParent()
        } else {
            val index = (0 until downItemCount).firstOrNull { _items[it] === item }
                ?: _items.indexOfFirst { it === item }
            if (index < 0) {
                return
            }

            val itemType = item.type
            if (itemType?.isStackable == true && count != item.getItemCount()) {
                val newCount = max(0, item.getItemCount() - count)
                item.setItemCount(newCount)
                onUpdateTileItem(item, item)
            } else {
                item.resetParent()
                _items.removeAt(index)
                if (index < downItemCount) {
                    --downItemCount
                }
                onRemoveTileItem(item)
            }
        }
    }

    /** `Tile::removeCreature` (src/items/tile.cpp:1434-1441). */
    fun removeCreature(creature: Creature?) {
        if (creature == null) {
            return
        }
        map?.getMapSector(position.x, position.y)?.removeCreature(creature)
        removeThing(creature, 0)
    }

    // --- internal insertion ---------------------------------------------------

    /** `Tile::internalAddThing(thing)` (src/items/tile.cpp:1738-1756). */
    override fun internalAddThing(thing: Thing?) {
        internalAddThing(0, thing)
        if (thing == null || thing.getParent() == null) {
            return
        }

        val house = thing.getTile()?.getHouse() ?: return
        val item = thing.getItem() ?: return
        if (item.getParent() !== this) {
            return
        }

        val door = item.getDoor()
        if (door != null && door.getDoorId() != 0) {
            house.addDoor(door)
        }
    }

    /** `Tile::internalAddThing(index, thing)` (src/items/tile.cpp:1758-1817). */
    override fun internalAddThing(index: Int, thing: Thing?) {
        if (thing == null) {
            return
        }

        thing.setParent(this)

        val creature = thing.getCreature()
        if (creature != null) {
            Spectators.clearCache()
            _creatures.add(0, creature)
            return
        }

        val item = thing.getItem() ?: return

        if (item.isGroundTile()) {
            if (ground == null) {
                ground = item
                setTileFlags(item)
            }
            return
        }

        if (_items.size >= 0xFFFF) {
            return
        }

        if (item.isAlwaysOnTop()) {
            val newOrder = item.type?.alwaysOnTopOrder ?: 0
            var inserted = false
            for (position in downItemCount until _items.size) {
                val existingOrder = _items[position].type?.alwaysOnTopOrder ?: 0
                if (existingOrder > newOrder) {
                    _items.add(position, item)
                    inserted = true
                    break
                }
            }
            if (!inserted) {
                _items.add(item)
            }
        } else {
            _items.add(0, item)
            ++downItemCount
        }

        setTileFlags(item)
    }

    // --- notifications --------------------------------------------------------

    /** `Tile::postAddNotification` (src/items/tile.cpp:1666-1709); spectator sends and movement scripts are PARITY GAPS. */
    override fun postAddNotification(thing: Thing?, oldParent: Cylinder?, index: Int, link: CylinderLink) {
        if (thing == null) {
            return
        }

        if (link == CylinderLink.LINK_OWNER) {
            if (hasFlag(TileFlags.TILESTATE_TELEPORT.value)) {
                // Teleport::addThing needs the injected TeleportWorld; the
                // trash-holder branch below is the modelled path (PARITY GAP).
            } else if (hasFlag(TileFlags.TILESTATE_TRASHHOLDER.value)) {
                getTrashHolder()?.addThing(thing)
            }
        }
    }

    /** `Tile::postRemoveNotification` (src/items/tile.cpp:1711-1736); spectator sends and movement scripts are PARITY GAPS. */
    override fun postRemoveNotification(thing: Thing?, newParent: Cylinder?, index: Int, link: CylinderLink) {
        if (thing == null) {
            return
        }
    }

    // --- zones ----------------------------------------------------------------

    /** `Tile::addZone` (src/items/tile.cpp:2039-2057); the item/creature zone callbacks are PARITY GAPS. */
    fun addZone(zone: Zone?) {
        if (zone == null) {
            return
        }
        zones.add(zone)
    }

    /** `Tile::clearZones` (src/items/tile.cpp:2059-2082); the item/creature removal callbacks are PARITY GAPS. */
    fun clearZones() {
        val zonesToRemove = zones.filterNot { it.isStatic() }
        zones.removeAll(zonesToRemove.toSet())
    }

    /** `Tile::getZones` (src/items/tile.hpp:184-186). */
    fun getZones(): Set<Zone> = zones

    companion object {
        /**
         * `Tile::nullptr_tile` (src/items/tile.cpp:30-31): the `StaticTile` at
         * `(0xFFFF, 0xFFFF, 0xFF)` every detached thing reports as its position.
         */
        val nullptrTile = Tile(Position(0xFFFF, 0xFFFF, 0xFF))

        /** `Tile::nullptr_tile->getPosition()`. */
        val nullptrTilePosition: Position get() = nullptrTile.position
    }
}
