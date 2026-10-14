package org.opentibia.canary.map

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionInvisible
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.game.movement.Teleport
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.items.BedItem
import org.opentibia.canary.items.INDEX_WHEREEVER
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemProperty
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.items.ItemTypes_t
import org.opentibia.canary.items.MagicField
import org.opentibia.canary.items.TrashHolder
import org.opentibia.canary.map.house.Door
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcripts of `Tile` (src/items/tile.hpp:30-290, src/items/tile.cpp).
 * The player/monster query and spectator/move-event side effects that need
 * `g_game()`/`g_moveEvents()` are documented PARITY GAPS inside the port.
 */
class TileTest {

    private val types = ItemTypeRegistry().apply {
        register(ItemType(100, "ground", isGroundTile = true))
        register(ItemType(101, "plain"))
        register(ItemType(102, "top 2", alwaysOnTopOrder = 2))
        register(ItemType(103, "top 4", alwaysOnTopOrder = 4))
        register(ItemType(104, "top 3", alwaysOnTopOrder = 3))
        register(ItemType(105, "splash", isSplash = true, alwaysOnTopOrder = 1))
        register(ItemType(106, "magic field", type = ItemTypes_t.ITEM_TYPE_MAGICFIELD))
        register(ItemType(107, "wall", isGroundTile = true, blockSolid = true, isPickupable = false))
        register(ItemType(108, "coin", isStackable = true))
        register(ItemType(109, "look through", lookThrough = true))
        register(ItemType(110, "hangable", isHangable = true))
        register(ItemType(111, "vertical", isVertical = true))
        register(ItemType(112, "high", hasHeight = true))
        register(ItemType(113, "door", isDoor = true))
        register(ItemType(114, "bed", isBed = true, isPickupable = false))
        register(ItemType(115, "trash holder", isTrashHolder = true))
        register(ItemType(116, "movable", isMovable = true))
        register(ItemType(117, "projectile blocker", blockProjectile = true))
        register(ItemType(118, "path blocker", blockPathFind = true))
        register(ItemType(119, "horizontal", isHorizontal = true))
        register(ItemType(120, "solid item", blockSolid = true))
    }

    private val pos = Position(100, 100, 7)

    private fun item(id: Int, count: Int = 1): Item = Item(id, count = count, type = types.get(id))

    private fun tileWithGround(): Tile {
        val tile = Tile(pos)
        tile.internalAddThing(item(100))
        return tile
    }

    private fun creature(id: Long, name: String = "C$id"): Player =
        Player(id = id, name = name, maxHealth = 100, health = 100, position = pos)

    // --- flags and protection zones -----------------------------------------

    @Test
    fun `set and reset flags`() {
        val tile = Tile(pos)

        assertThat(tile.hasFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)).isFalse()
        tile.setFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
        assertThat(tile.hasFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)).isTrue()
        tile.resetFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
        assertThat(tile.hasFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)).isFalse()
    }

    @Test
    fun `zone type follows the canary precedence`() {
        val tile = Tile(pos)

        assertThat(tile.getZoneType()).isEqualTo(ZoneType.ZONE_NORMAL)

        tile.setFlag(TileFlags.TILESTATE_NOPVPZONE.value)
        assertThat(tile.getZoneType()).isEqualTo(ZoneType.ZONE_NOPVP)

        tile.setFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
        assertThat(tile.getZoneType()).isEqualTo(ZoneType.ZONE_PROTECTION)

        tile.resetFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
        tile.setFlag(TileFlags.TILESTATE_PVPZONE.value)
        assertThat(tile.getZoneType()).isEqualTo(ZoneType.ZONE_NOPVP)

        tile.resetFlag(TileFlags.TILESTATE_NOPVPZONE.value)
        assertThat(tile.getZoneType()).isEqualTo(ZoneType.ZONE_PVP)

        tile.resetFlag(TileFlags.TILESTATE_PVPZONE.value)
        tile.setFlag(TileFlags.TILESTATE_NOLOGOUT.value)
        assertThat(tile.getZoneType()).isEqualTo(ZoneType.ZONE_NOLOGOUT)
    }

    @Test
    fun `hasProperty maps solid and path flags`() {
        val tile = Tile(pos)

        tile.setFlag(TileFlags.TILESTATE_BLOCKSOLID.value)
        tile.setFlag(TileFlags.TILESTATE_BLOCKPATH.value)
        tile.setFlag(TileFlags.TILESTATE_BLOCKPROJECTILE.value)
        tile.setFlag(TileFlags.TILESTATE_ISVERTICAL.value)
        tile.setFlag(TileFlags.TILESTATE_ISHORIZONTAL.value)
        tile.setFlag(TileFlags.TILESTATE_HASHEIGHT.value)

        assertThat(tile.hasProperty(ItemProperty.CONST_PROP_BLOCKSOLID)).isTrue()
        assertThat(tile.hasProperty(ItemProperty.CONST_PROP_BLOCKPATH)).isTrue()
        assertThat(tile.hasProperty(ItemProperty.CONST_PROP_BLOCKPROJECTILE)).isTrue()
        assertThat(tile.hasProperty(ItemProperty.CONST_PROP_ISVERTICAL)).isTrue()
        assertThat(tile.hasProperty(ItemProperty.CONST_PROP_ISHORIZONTAL)).isTrue()
        assertThat(tile.hasProperty(ItemProperty.CONST_PROP_HASHEIGHT)).isTrue()
    }

    @Test
    fun `hasProperty maps immovable and hangable flags`() {
        val tile = Tile(pos)

        tile.setFlag(TileFlags.TILESTATE_MOVABLE.value)
        tile.setFlag(TileFlags.TILESTATE_IMMOVABLEBLOCKSOLID.value)
        tile.setFlag(TileFlags.TILESTATE_IMMOVABLEBLOCKPATH.value)
        tile.setFlag(TileFlags.TILESTATE_IMMOVABLENOFIELDBLOCKPATH.value)
        tile.setFlag(TileFlags.TILESTATE_NOFIELDBLOCKPATH.value)
        tile.setFlag(TileFlags.TILESTATE_SUPPORTS_HANGABLE.value)

        assertThat(tile.hasProperty(ItemProperty.CONST_PROP_MOVABLE)).isTrue()
        assertThat(tile.hasProperty(ItemProperty.CONST_PROP_IMMOVABLEBLOCKSOLID)).isTrue()
        assertThat(tile.hasProperty(ItemProperty.CONST_PROP_IMMOVABLEBLOCKPATH)).isTrue()
        assertThat(tile.hasProperty(ItemProperty.CONST_PROP_IMMOVABLENOFIELDBLOCKPATH)).isTrue()
        assertThat(tile.hasProperty(ItemProperty.CONST_PROP_NOFIELDBLOCKPATH)).isTrue()
        assertThat(tile.hasProperty(ItemProperty.CONST_PROP_SUPPORTHANGABLE)).isTrue()
    }

    @Test
    fun `hasProperty with exclude ignores the excluded item and the ground`() {
        val tile = tileWithGround()
        val wall = item(120)
        tile.internalAddThing(wall)

        assertThat(tile.hasProperty(ItemProperty.CONST_PROP_BLOCKSOLID)).isTrue()
        assertThat(tile.hasProperty(wall, ItemProperty.CONST_PROP_BLOCKSOLID)).isFalse()

        val ground = tile.ground!!
        assertThat(tile.hasProperty(ground, ItemProperty.CONST_PROP_BLOCKSOLID)).isTrue()
        assertThat(tile.hasProperty(null, ItemProperty.CONST_PROP_BLOCKSOLID)).isFalse()
    }

    @Test
    fun `hasHeight counts the ground and stacked height items`() {
        val tile = Tile(pos)
        tile.internalAddThing(item(112))

        assertThat(tile.hasHeight(1)).isTrue()
        assertThat(tile.hasHeight(2)).isFalse()

        tile.internalAddThing(item(112))
        assertThat(tile.hasHeight(2)).isTrue()
    }

    @Test
    fun `setGround swaps flags to the new ground type`() {
        val tile = Tile(pos)

        tile.setGround(item(118))
        assertThat(tile.hasFlag(TileFlags.TILESTATE_BLOCKPATH.value)).isTrue()
        assertThat(tile.hasFlag(TileFlags.TILESTATE_NOFIELDBLOCKPATH.value)).isTrue()

        tile.setGround(item(101))
        assertThat(tile.hasFlag(TileFlags.TILESTATE_BLOCKPATH.value)).isFalse()
        assertThat(tile.hasFlag(TileFlags.TILESTATE_NOFIELDBLOCKPATH.value)).isFalse()
    }

    @Test
    fun `adding and removing a blocking item updates the solid flags`() {
        val tile = tileWithGround()
        val wall = item(120)

        tile.internalAddThing(wall)
        assertThat(tile.hasFlag(TileFlags.TILESTATE_BLOCKSOLID.value)).isTrue()
        assertThat(tile.hasFlag(TileFlags.TILESTATE_IMMOVABLEBLOCKSOLID.value)).isTrue()

        tile.removeThing(wall, 1)
        assertThat(tile.hasFlag(TileFlags.TILESTATE_BLOCKSOLID.value)).isFalse()
        assertThat(tile.hasFlag(TileFlags.TILESTATE_IMMOVABLEBLOCKSOLID.value)).isFalse()
    }

    @Test
    fun `isMovableBlocking reports tiles without ground or with solid flags`() {
        val tile = Tile(pos)
        assertThat(tile.isMovableBlocking()).isTrue()

        tile.setFlag(TileFlags.TILESTATE_BLOCKSOLID.value)
        assertThat(tile.isMovableBlocking()).isTrue()

        tile.resetFlag(TileFlags.TILESTATE_BLOCKSOLID.value)
        tile.internalAddThing(item(100))
        assertThat(tile.isMovableBlocking()).isFalse()
    }

    // --- creatures -----------------------------------------------------------

    @Test
    fun `addThing inserts creatures at the top of the creature vector`() {
        val tile = tileWithGround()
        val first = creature(1)
        val second = creature(2)

        tile.addThing(first)
        tile.addThing(second)

        assertThat(tile.getCreatures()).containsExactly(second, first)
        assertThat(tile.getTopCreature()).isSameAs(second)
        assertThat(tile.getBottomCreature()).isSameAs(first)
        assertThat(tile.getCreatureCount()).isEqualTo(2)
    }

    @Test
    fun `adding a creature ties its parent to the tile`() {
        val tile = tileWithGround()
        val moved = creature(1)

        tile.addThing(moved)

        assertThat(moved.getTile()).isSameAs(tile)
        assertThat(moved.getParent()).isSameAs(tile)
        assertThat(moved.position).isEqualTo(pos)
    }

    @Test
    fun `removeThing erases the creature`() {
        val tile = tileWithGround()
        val first = creature(1)
        val second = creature(2)
        tile.addThing(first)
        tile.addThing(second)

        tile.removeThing(first, 0)

        assertThat(tile.getCreatures()).containsExactly(second)
    }

    @Test
    fun `removeCreature erases the creature`() {
        val tile = tileWithGround()
        val only = creature(1)
        tile.addThing(only)

        tile.removeCreature(only)

        assertThat(tile.getCreatures()).isEmpty()
    }

    @Test
    fun `thing count adds ground, items and creatures`() {
        val tile = tileWithGround()
        tile.internalAddThing(item(101))
        tile.addThing(creature(1))

        assertThat(tile.getThingCount()).isEqualTo(3)
        assertThat(tile.getItemCount()).isEqualTo(1)
        assertThat(tile.getCreatureCount()).isEqualTo(1)
    }

    @Test
    fun `top visible creature honours an access player and invisibility`() {
        val tile = tileWithGround()
        // `Player::canSeeCreature` skips the invisibility check for players
        // (player.cpp:1418-1420), so the hidden creature is a monster here.
        val hidden = Monster(id = 1, name = "Hidden", maxHealth = 100, health = 100, position = pos).apply {
            addCondition(
                ConditionInvisible(
                    ConditionId.CONDITIONID_COMBAT,
                    ConditionType.CONDITION_INVISIBLE,
                    ticks = 10000,
                ),
            )
        }
        tile.addThing(hidden)

        val normal = creature(2, "Viewer")
        assertThat(tile.getTopVisibleCreature(normal)).isNull()

        val access = creature(3, "Gamemaster").apply { group = Group(name = "GM", access = true) }
        assertThat(tile.getTopVisibleCreature(access)).isSameAs(hidden)
        assertThat(tile.getBottomVisibleCreature(access)).isSameAs(hidden)
    }

    @Test
    fun `top visible creature without a viewer skips invisibles`() {
        val tile = tileWithGround()
        val hidden = creature(1, "Hidden").apply {
            addCondition(
                ConditionInvisible(
                    ConditionId.CONDITIONID_COMBAT,
                    ConditionType.CONDITION_INVISIBLE,
                    ticks = 10000,
                ),
            )
        }
        val visible = creature(2, "Visible")
        tile.addThing(hidden)
        tile.addThing(visible)

        assertThat(tile.getTopVisibleCreature(null)).isSameAs(visible)
        assertThat(tile.getBottomVisibleCreature(null)).isSameAs(visible)
    }

    @Test
    fun `stack position and client index count ground tops creatures and down items`() {
        val tile = tileWithGround()
        val top = item(102)
        val down = item(101)
        tile.internalAddThing(down)
        tile.internalAddThing(top)
        val only = creature(1)
        tile.addThing(only)
        val player = creature(2)

        assertThat(tile.getStackposOfItem(player, tile.ground!!)).isZero()
        assertThat(tile.getStackposOfItem(player, top)).isEqualTo(1)
        assertThat(tile.getStackposOfItem(player, down)).isEqualTo(3)
        assertThat(tile.getStackposOfCreature(player, only)).isEqualTo(2)
        assertThat(tile.getClientIndexOfCreature(player, only)).isEqualTo(2)
    }

    // --- top/down ordering ---------------------------------------------------

    @Test
    fun `addThing orders always-on-top items by their order`() {
        val tile = tileWithGround()

        tile.addThing(item(102))
        tile.addThing(item(103))
        tile.addThing(item(104))

        assertThat(tile.items.map { it.id }).containsExactly(102, 104, 103)
        assertThat(tile.getTopItemCount()).isEqualTo(3)
        assertThat(tile.getDownItemCount()).isZero()
        assertThat(tile.getTopTopItem()?.id).isEqualTo(103)
        assertThat(tile.getTopDownItem()).isNull()
    }

    @Test
    fun `addThing keeps equal always-on-top order stable`() {
        val tile = tileWithGround()
        val first = item(102)
        val second = item(102)

        tile.addThing(first)
        tile.addThing(second)

        assertThat(tile.items).containsExactly(second, first)
    }

    @Test
    fun `internalAddThing appends equal always-on-top order`() {
        val tile = tileWithGround()
        val first = item(102, count = 1)
        val second = item(102, count = 2)

        tile.internalAddThing(first)
        tile.internalAddThing(second)

        assertThat(tile.items).containsExactly(first, second)
    }

    @Test
    fun `addThing inserts down items at the top-down position`() {
        val tile = tileWithGround()
        val first = item(101)
        val second = item(101)

        tile.addThing(first)
        tile.addThing(second)

        assertThat(tile.items).containsExactly(second, first)
        assertThat(tile.getTopDownItem()).isSameAs(second)
        assertThat(tile.getTopTopItem()).isNull()
        assertThat(tile.getDownItemCount()).isEqualTo(2)
    }

    @Test
    fun `addThing replaces an existing splash`() {
        val tile = tileWithGround()
        val first = item(105)
        val second = item(105)
        tile.addThing(first)

        tile.addThing(second)

        assertThat(tile.items).containsExactly(second)
    }

    @Test
    fun `addThing replaces a replaceable magic field`() {
        val tile = tileWithGround()
        val first = MagicField(106, damage = 10, type = types.get(106))
        val second = MagicField(106, damage = 20, type = types.get(106))

        tile.addThing(first)
        tile.addThing(second)

        assertThat(tile.items).containsExactly(second)
        assertThat(tile.getFieldItem()).isSameAs(second)
    }

    @Test
    fun `addThing refuses to replace a permanent magic field`() {
        val tile = tileWithGround()
        val permanent = MagicField(106, damage = 10, replaceable = false, type = types.get(106))
        val next = MagicField(106, damage = 20, type = types.get(106))
        tile.addThing(permanent)

        tile.addThing(next)

        assertThat(tile.items).containsExactly(permanent)
        assertThat(next.getParent()).isNull()
    }

    @Test
    fun `internalAddThing keeps the first ground`() {
        val tile = Tile(pos)
        val ground = item(100)
        val other = item(100)

        tile.internalAddThing(ground)
        tile.internalAddThing(other)

        assertThat(tile.ground).isSameAs(ground)
        assertThat(tile.items).isEmpty()
    }

    @Test
    fun `addThing replaces the ground`() {
        val tile = tileWithGround()
        val next = item(100)

        tile.addThing(next)

        assertThat(tile.ground).isSameAs(next)
        assertThat(tile.items).isEmpty()
    }

    // --- thing vector and indexes --------------------------------------------

    @Test
    fun `getThingIndex follows ground tops creatures then down items`() {
        val tile = tileWithGround()
        val down1 = item(101)
        val down2 = item(101)
        val top2 = item(102)
        val top3 = item(104)
        val top4 = item(103)
        tile.internalAddThing(down1)
        tile.internalAddThing(down2)
        tile.internalAddThing(top2)
        tile.internalAddThing(top4)
        tile.internalAddThing(top3)
        val only = creature(1)
        tile.addThing(only)

        assertThat(tile.getThingIndex(tile.ground)).isZero()
        assertThat(tile.getThingIndex(top2)).isEqualTo(1)
        assertThat(tile.getThingIndex(top3)).isEqualTo(2)
        assertThat(tile.getThingIndex(top4)).isEqualTo(3)
        assertThat(tile.getThingIndex(only)).isEqualTo(4)
        assertThat(tile.getThingIndex(down2)).isEqualTo(5)
        assertThat(tile.getThingIndex(down1)).isEqualTo(6)
        assertThat(tile.getThingIndex(item(101))).isEqualTo(-1)
        assertThat(tile.getThingIndex(null)).isEqualTo(-1)
    }

    @Test
    fun `getThing mirrors the thing index layout`() {
        val tile = tileWithGround()
        val down = item(101)
        val top = item(102)
        tile.internalAddThing(down)
        tile.internalAddThing(top)
        val only = creature(1)
        tile.addThing(only)

        assertThat(tile.getThing(0)).isSameAs(tile.ground)
        assertThat(tile.getThing(1)).isSameAs(top)
        assertThat(tile.getThing(2)).isSameAs(only)
        assertThat(tile.getThing(3)).isSameAs(down)
        assertThat(tile.getThing(4)).isNull()
        assertThat(tile.getFirstIndex()).isZero()
        assertThat(tile.getLastIndex()).isEqualTo(4)
    }

    @Test
    fun `replaceThing swaps the ground and stacked positions`() {
        val tile = tileWithGround()
        val top = item(102)
        val down = item(101)
        tile.internalAddThing(down)
        tile.internalAddThing(top)
        val newGround = item(100)
        val newTop = item(104)
        val newDown = item(101, count = 3)

        tile.replaceThing(0, newGround)
        tile.replaceThing(1, newTop)
        tile.replaceThing(2, newDown)

        assertThat(tile.ground).isSameAs(newGround)
        assertThat(tile.items).containsExactly(newDown, newTop)
        assertThat(tile.getThingIndex(newTop)).isEqualTo(1)
        assertThat(tile.getThingIndex(newDown)).isEqualTo(2)
    }

    @Test
    fun `updateThing changes the id and subtype`() {
        val tile = tileWithGround()
        val coin = item(108, count = 3)
        tile.internalAddThing(coin)

        tile.updateThing(coin, 108, 50)

        assertThat(coin.id).isEqualTo(108)
        assertThat(coin.getSubType()).isEqualTo(50)
    }

    @Test
    fun `removeThing decrements a stackable item`() {
        val tile = tileWithGround()
        val coin = item(108, count = 10)
        tile.internalAddThing(coin)

        tile.removeThing(coin, 4)

        assertThat(tile.items).containsExactly(coin)
        assertThat(coin.count).isEqualTo(6)
    }

    @Test
    fun `removeThing drops a stackable item when the full count is removed`() {
        val tile = tileWithGround()
        val coin = item(108, count = 10)
        tile.internalAddThing(coin)

        tile.removeThing(coin, 10)

        assertThat(tile.items).isEmpty()
        assertThat(tile.getDownItemCount()).isZero()
    }

    @Test
    fun `removeThing clears the ground and its flags`() {
        val tile = Tile(pos)
        val ground = item(118)
        tile.internalAddThing(ground)
        assertThat(tile.hasFlag(TileFlags.TILESTATE_BLOCKPATH.value)).isTrue()

        tile.removeThing(ground, 1)

        assertThat(tile.ground).isNull()
        assertThat(tile.hasFlag(TileFlags.TILESTATE_BLOCKPATH.value)).isFalse()
    }

    @Test
    fun `getItemByTopOrder finds top items by order`() {
        val tile = tileWithGround()
        val top3 = item(104)
        tile.internalAddThing(item(102))
        tile.internalAddThing(top3)

        assertThat(tile.getItemByTopOrder(3)).isSameAs(top3)
        assertThat(tile.getItemByTopOrder(4)).isNull()
    }

    @Test
    fun `top visible thing skips look-through items`() {
        val tile = tileWithGround()
        val visible = item(101)
        val through = item(109)
        tile.internalAddThing(visible)
        tile.internalAddThing(through)

        assertThat(tile.getTopVisibleThing(null)).isSameAs(visible)

        tile.removeThing(visible, 1)
        assertThat(tile.getTopVisibleThing(null)).isSameAs(tile.ground)
    }

    @Test
    fun `getUseItem falls back to the ground when there are no items`() {
        val tile = tileWithGround()
        val ground = tile.ground!!

        assertThat(tile.getUseItem(0)).isSameAs(ground)

        val item = item(101)
        tile.internalAddThing(item)
        assertThat(tile.getUseItem(1)).isSameAs(item)
    }

    @Test
    fun `getDoorItem finds the door on the tile`() {
        val tile = tileWithGround()
        assertThat(tile.getDoorItem()).isSameAs(tile.ground)

        val door = Door(113, type = types.get(113))
        tile.internalAddThing(door)
        assertThat(tile.getDoorItem()).isSameAs(door)
    }

    @Test
    fun `getItemTypeCount sums matching ids and subtypes`() {
        val tile = tileWithGround()
        tile.internalAddThing(item(108, count = 3))
        tile.internalAddThing(item(108, count = 5))

        assertThat(tile.getItemTypeCount(108)).isEqualTo(8)
        assertThat(tile.getItemTypeCount(108, -1)).isEqualTo(8)
        assertThat(tile.getItemTypeCount(108, 0)).isZero()
        assertThat(tile.getItemTypeCount(999)).isZero()
    }

    // --- fields, teleports, beds and trash holders ---------------------------

    @Test
    fun `getFieldItem finds magic fields on the ground and in the item list`() {
        val tile = tileWithGround()
        assertThat(tile.getFieldItem()).isNull()

        val field = MagicField(106, damage = 20, type = types.get(106))
        tile.internalAddThing(field)

        assertThat(tile.getFieldItem()).isSameAs(field)
        assertThat(tile.hasHarmfulField()).isTrue()
    }

    @Test
    fun `hasHarmfulField requires a damaging non-blocking field`() {
        val tile = tileWithGround()
        val harmless = MagicField(106, damage = 0, type = types.get(106))
        tile.internalAddThing(harmless)

        assertThat(tile.hasHarmfulField()).isFalse()
    }

    @Test
    fun `getTeleportItem finds the teleport item`() {
        val tile = tileWithGround()
        assertThat(tile.getTeleportItem()).isNull()

        val teleport = Teleport(108, destPos = Position(1, 1, 1))
        tile.internalAddThing(teleport)

        assertThat(tile.getTeleportItem()).isSameAs(teleport)
        assertThat(tile.hasFlag(TileFlags.TILESTATE_TELEPORT.value)).isTrue()
    }

    @Test
    fun `getBedItem finds the bed item`() {
        val tile = tileWithGround()
        val bed = BedItem(114, type = types.get(114))
        tile.internalAddThing(bed)

        assertThat(tile.getBedItem()).isSameAs(bed)
        assertThat(tile.hasFlag(TileFlags.TILESTATE_BED.value)).isTrue()
    }

    @Test
    fun `getTrashHolder finds the trash holder item`() {
        val tile = tileWithGround()
        val holder = TrashHolder(115, type = types.get(115))
        tile.internalAddThing(holder)

        assertThat(tile.getTrashHolder()).isSameAs(holder)
        assertThat(tile.hasFlag(TileFlags.TILESTATE_TRASHHOLDER.value)).isTrue()
    }

    // --- house tiles ---------------------------------------------------------

    @Test
    fun `isHouse follows the house id`() {
        val tile = Tile(pos)
        assertThat(tile.isHouse()).isFalse()
        assertThat(tile.getHouse()).isNull()

        tile.houseId = 7
        assertThat(tile.isHouse()).isTrue()
    }

    @Test
    fun `internalAddThing registers a numbered door with the tile house`() {
        val map = GameMap()
        val house = map.houses.addHouse(1)
        val tile = map.getOrCreateTile(pos)
        tile.houseId = 1
        tile.house = house
        val door = Door(113, type = types.get(113)).apply { setDoorId(9) }

        tile.internalAddThing(door)

        assertThat(tile.getHouse()).isSameAs(house)
        assertThat(house.getDoors()).containsExactly(door)
        assertThat(door.getHouse()).isSameAs(house)
    }

    @Test
    fun `internalAddThing ignores doors without a door id`() {
        val map = GameMap()
        val house = map.houses.addHouse(1)
        val tile = map.getOrCreateTile(pos)
        tile.house = house
        val door = Door(113, type = types.get(113))

        tile.internalAddThing(door)

        assertThat(house.getDoors()).isEmpty()
    }

    // --- cylinder queries ----------------------------------------------------

    @Test
    fun `queryAdd rejects an item without ground and accepts hangables`() {
        val tile = Tile(pos)

        assertThat(tile.queryAdd(INDEX_WHEREEVER, item(101), 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(tile.queryAdd(INDEX_WHEREEVER, item(110), 1, 0)).isEqualTo(ReturnValue.NOERROR)

        tile.internalAddThing(item(100))
        assertThat(tile.queryAdd(INDEX_WHEREEVER, item(101), 1, 0)).isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `queryAdd honours the nolimit flag`() {
        val tile = Tile(pos)

        assertThat(tile.queryAdd(INDEX_WHEREEVER, item(101), 1, CylinderFlags.FLAG_NOLIMIT))
            .isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `queryAdd rejects non-pickupable items on a low wall`() {
        val tile = Tile(pos)
        tile.internalAddThing(item(107))

        assertThat(tile.queryAdd(INDEX_WHEREEVER, item(101), 1, 0)).isEqualTo(ReturnValue.NOTENOUGHROOM)
    }

    @Test
    fun `queryAdd with ignore block item rejects immovable solids`() {
        val tile = Tile(pos)
        tile.internalAddThing(item(107))
        val walker = creature(1)

        assertThat(tile.queryAdd(INDEX_WHEREEVER, walker, 1, CylinderFlags.FLAG_IGNOREBLOCKITEM))
            .isEqualTo(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `queryAdd rejects a blocking item on a tile with a visible creature`() {
        val tile = tileWithGround()
        tile.addThing(creature(1))
        val blocking = item(120)

        assertThat(tile.queryAdd(INDEX_WHEREEVER, blocking, 1, 0)).isEqualTo(ReturnValue.NOTENOUGHROOM)
        assertThat(tile.queryAdd(INDEX_WHEREEVER, blocking, 1, CylinderFlags.FLAG_IGNOREBLOCKCREATURE))
            .isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `queryMaxCount clamps to at least one`() {
        val tile = Tile(pos)

        assertThat(tile.queryMaxCount(INDEX_WHEREEVER, item(101), 0, 0, 0))
            .isEqualTo(org.opentibia.canary.items.QueryMaxCountResult(ReturnValue.NOERROR, 1))
        assertThat(tile.queryMaxCount(INDEX_WHEREEVER, item(101), 9, 0, 0))
            .isEqualTo(org.opentibia.canary.items.QueryMaxCountResult(ReturnValue.NOERROR, 9))
    }

    @Test
    fun `queryRemove validates the item, the count and movability`() {
        val tile = tileWithGround()
        val movable = item(116)
        val fixed = item(101)
        val coin = item(108, count = 5)
        tile.internalAddThing(movable)
        tile.internalAddThing(fixed)
        tile.internalAddThing(coin)

        assertThat(tile.queryRemove(movable, 1, 0)).isEqualTo(ReturnValue.NOERROR)
        assertThat(tile.queryRemove(movable, 0, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(tile.queryRemove(fixed, 1, 0)).isEqualTo(ReturnValue.NOTMOVABLE)
        assertThat(tile.queryRemove(fixed, 1, CylinderFlags.FLAG_IGNORENOTMOVABLE)).isEqualTo(ReturnValue.NOERROR)
        assertThat(tile.queryRemove(coin, 6, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(tile.queryRemove(item(101), 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(tile.queryRemove(null, 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `queryDestination returns the tile itself without a floor change`() {
        val tile = tileWithGround()
        val item = item(101)

        val result = tile.queryDestination(INDEX_WHEREEVER, item, null, 0)

        assertThat(result.cylinder).isSameAs(tile)
        assertThat(result.index).isEqualTo(INDEX_WHEREEVER)
        assertThat(result.destItem).isNull()
        assertThat(result.flags).isZero()
    }

    @Test
    fun `queryDestination follows a down floor change`() {
        val map = GameMap()
        val upper = map.getOrCreateTile(Position(100, 100, 7))
        val shaft = map.getOrCreateTile(Position(100, 100, 8))
        val destination = map.getOrCreateTile(Position(100, 101, 8))
        upper.setFlag(TileFlags.TILESTATE_FLOORCHANGE_DOWN.value)
        shaft.setFlag(TileFlags.TILESTATE_FLOORCHANGE_NORTH.value)

        val result = upper.queryDestination(INDEX_WHEREEVER, null, null, 0)

        assertThat(result.cylinder).isSameAs(destination)
        assertThat(result.flags and CylinderFlags.FLAG_NOLIMIT).isNotZero()
    }

    @Test
    fun `getSurroundingTiles returns the eight neighbours in canary order`() {
        val map = GameMap()
        val center = map.getOrCreateTile(pos)
        val north = map.getOrCreateTile(Position(pos.x, pos.y - 1, pos.z))
        val south = map.getOrCreateTile(Position(pos.x, pos.y + 1, pos.z))

        val surrounding = center.getSurroundingTiles()

        assertThat(surrounding).hasSize(8)
        assertThat(surrounding[0]).isNull()
        assertThat(surrounding[1]).isNull()
        assertThat(surrounding[2]).isSameAs(north)
        assertThat(surrounding[3]).isSameAs(south)
    }

    @Test
    fun `tile description matches canary`() {
        val tile = Tile(pos)

        assertThat(tile.getDescription(0)).isEqualTo("You dont know why, but you cant see anything!")
        assertThat(tile.getThrowRange()).isZero()
        assertThat(tile.isPushable()).isFalse()
        assertThat(tile.getTile()).isSameAs(tile)
        assertThat(tile.getCylinder()).isSameAs(tile)
        assertThat(tile.isRemoved()).isFalse()
    }

    @Test
    fun `legacy addItem appends without touching the down count`() {
        val tile = tileWithGround()
        val first = item(101)
        val second = item(101)

        tile.addItem(first)
        tile.addItem(second)

        assertThat(tile.items).containsExactly(first, second)
        assertThat(tile.getDownItemCount()).isZero()
    }

    @Test
    fun `removeItem adjusts the down count`() {
        val tile = tileWithGround()
        val down = item(101)
        tile.internalAddThing(down)

        assertThat(tile.removeItem(down)).isTrue()

        assertThat(tile.items).isEmpty()
        assertThat(tile.getDownItemCount()).isZero()
        assertThat(tile.removeItem(down)).isFalse()
    }
}
