package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcripts of `TrashHolder` (src/items/trashholder.hpp:15-44,
 * src/items/trashholder.cpp:14-96).
 */
class TrashHolderTest {

    private val holderType = ItemType(
        115,
        "trash holder",
        isTrashHolder = true,
        isGroundTile = true,
        magicEffect = 5,
    )

    private val movableType = ItemType(116, "movable", isMovable = true)

    private class FakeWorld : TrashHolderWorld {
        val removed = mutableListOf<Item>()
        val effects = mutableListOf<Pair<Position, Int>>()

        override fun internalRemoveItem(item: Item, count: Int): ReturnValue {
            removed.add(item)
            return ReturnValue.NOERROR
        }

        override fun addMagicEffect(position: Position, effect: Int) {
            effects.add(position to effect)
        }
    }

    private class RecordingCylinder : Cylinder {
        var addThing: Thing? = null
        var oldParent: Cylinder? = null
        var index: Int = -1
        var link: CylinderLink? = null
        var removeThing: Thing? = null
        var newParent: Cylinder? = null

        override fun getDescription(lookDistance: Int): String = ""
        override fun getThrowRange(): Int = 0
        override fun isPushable(): Boolean = false
        override fun queryAdd(index: Int, thing: Thing?, count: Int, flags: Int, actor: Creature?): ReturnValue =
            ReturnValue.NOTPOSSIBLE

        override fun queryMaxCount(
            index: Int,
            thing: Thing?,
            count: Int,
            maxQueryCount: Int,
            flags: Int,
        ): QueryMaxCountResult = QueryMaxCountResult(ReturnValue.NOTPOSSIBLE, maxQueryCount)

        override fun queryRemove(thing: Thing?, count: Int, flags: Int, actor: Creature?): ReturnValue =
            ReturnValue.NOTPOSSIBLE

        override fun queryDestination(index: Int, thing: Thing?, destItem: Item?, flags: Int): QueryDestinationResult =
            QueryDestinationResult(null, index, destItem, flags)

        override fun addThing(thing: Thing?) {}
        override fun addThing(index: Int, thing: Thing?) {}
        override fun updateThing(thing: Thing?, itemId: Int, count: Int) {}
        override fun replaceThing(index: Int, thing: Thing?) {}
        override fun removeThing(thing: Thing?, count: Int) {}

        override fun postAddNotification(thing: Thing?, oldParent: Cylinder?, index: Int, link: CylinderLink) {
            this.addThing = thing
            this.oldParent = oldParent
            this.index = index
            this.link = link
        }

        override fun postRemoveNotification(thing: Thing?, newParent: Cylinder?, index: Int, link: CylinderLink) {
            this.removeThing = thing
            this.newParent = newParent
            this.index = index
            this.link = link
        }
    }

    @Test
    fun `getTrashHolder and getCylinder return itself`() {
        val holder = TrashHolder(115, type = holderType)

        assertThat(holder.getTrashHolder()).isSameAs(holder)
        assertThat(holder.getCylinder()).isSameAs(holder)
        assertThat(holder.getContainer()).isNull()
    }

    @Test
    fun `queryAdd accepts a plain item`() {
        val holder = TrashHolder(115, type = holderType)

        assertThat(holder.queryAdd(0, Item(116, type = movableType), 1, 0)).isEqualTo(ReturnValue.NOERROR)
        assertThat(holder.queryAdd(0, null, 1, 0)).isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `queryAdd accepts a non-item thing`() {
        val holder = TrashHolder(115, type = holderType)
        val creature = Player(1, "Walk", 100, 100, position = Position(0, 0, 0))

        assertThat(holder.queryAdd(0, creature, 1, 0)).isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `queryAdd rejects an item owned by another creature`() {
        val holder = TrashHolder(115, type = holderType)
        val owner = Player(1, "Owner", 100, 100, position = Position(0, 0, 0))
        val other = Player(2, "Other", 100, 100, position = Position(0, 0, 0))
        val item = Item(116, type = movableType).apply { setAttribute(ItemAttribute.OWNER, owner.id) }

        assertThat(holder.queryAdd(0, item, 1, 0, other)).isEqualTo(ReturnValue.ITEMISNOTYOURS)
        assertThat(holder.queryAdd(0, item, 1, 0, owner)).isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `queryMaxCount clamps to at least one`() {
        val holder = TrashHolder(115, type = holderType)

        assertThat(holder.queryMaxCount(0, null, 0, 0, 0))
            .isEqualTo(QueryMaxCountResult(ReturnValue.NOERROR, 1))
        assertThat(holder.queryMaxCount(0, null, 7, 0, 0))
            .isEqualTo(QueryMaxCountResult(ReturnValue.NOERROR, 7))
    }

    @Test
    fun `queryRemove is never possible`() {
        val holder = TrashHolder(115, type = holderType)

        assertThat(holder.queryRemove(Item(116, type = movableType), 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `queryDestination returns itself`() {
        val holder = TrashHolder(115, type = holderType)
        val result = holder.queryDestination(4, null, null, 0)

        assertThat(result.cylinder).isSameAs(holder)
        assertThat(result.index).isEqualTo(4)
        assertThat(result.destItem).isNull()
        assertThat(result.flags).isZero()
    }

    @Test
    fun `addThing removes a movable item and shows the holder magic effect`() {
        val world = FakeWorld()
        val holder = TrashHolder(115, type = holderType, world = world)
        val tile = Tile(Position(50, 60, 7))
        tile.internalAddThing(holder)
        val item = Item(116, type = movableType)

        holder.addThing(item)

        assertThat(world.removed).containsExactly(item)
        assertThat(world.effects).containsExactly(Position(50, 60, 7) to 5)
    }

    @Test
    fun `addThing ignores non-movable items`() {
        val world = FakeWorld()
        val holder = TrashHolder(115, type = holderType, world = world)
        val fixed = Item(117, type = ItemType(117, "fixed"))

        holder.addThing(fixed)

        assertThat(world.removed).isEmpty()
        assertThat(world.effects).isEmpty()
    }

    @Test
    fun `addThing ignores the holder itself`() {
        val world = FakeWorld()
        val holder = TrashHolder(115, type = holderType, world = world)

        holder.addThing(holder)

        assertThat(world.removed).isEmpty()
    }

    @Test
    fun `addThing ignores carpets`() {
        val world = FakeWorld()
        val holder = TrashHolder(115, type = holderType, world = world)
        val carpet = Item(118, type = ItemType(118, "carpet", isMovable = true, isCarpet = true))

        holder.addThing(carpet)

        assertThat(world.removed).isEmpty()
    }

    @Test
    fun `addThing ignores the decoration kit`() {
        val world = FakeWorld()
        val holder = TrashHolder(115, type = holderType, world = world)
        val kit = Item(TrashHolder.ITEM_DECORATION_KIT, type = movableType)

        holder.addThing(kit)

        assertThat(world.removed).isEmpty()
    }

    @Test
    fun `addThing keeps hangable items when the holder tile supports hangables`() {
        val world = FakeWorld()
        val holder = TrashHolder(115, type = holderType, world = world)
        val tile = Tile(Position(10, 10, 7))
        tile.setFlag(TileFlags.TILESTATE_SUPPORTS_HANGABLE.value)
        tile.internalAddThing(holder)
        val hangable = Item(119, type = ItemType(119, "hangable", isMovable = true, isHangable = true))

        holder.addThing(hangable)

        assertThat(world.removed).isEmpty()
    }

    @Test
    fun `addThing removes hangable items when the holder tile does not support them`() {
        val world = FakeWorld()
        val holder = TrashHolder(115, type = holderType, world = world)
        val tile = Tile(Position(10, 10, 7))
        tile.internalAddThing(holder)
        val hangable = Item(119, type = ItemType(119, "hangable", isMovable = true, isHangable = true))

        holder.addThing(hangable)

        assertThat(world.removed).containsExactly(hangable)
    }

    @Test
    fun `update replace and remove hooks do nothing`() {
        val holder = TrashHolder(115, type = holderType)

        holder.updateThing(Item(116), 117, 2)
        holder.replaceThing(0, Item(116))
        holder.removeThing(Item(116), 1)
    }

    @Test
    fun `post notifications delegate to the parent with link parent`() {
        val parent = RecordingCylinder()
        val holder = TrashHolder(115, type = holderType)
        holder.setParent(parent)
        val item = Item(116, type = movableType)

        holder.postAddNotification(item, null, 3)
        assertThat(parent.addThing).isSameAs(item)
        assertThat(parent.index).isEqualTo(3)
        assertThat(parent.link).isEqualTo(CylinderLink.LINK_PARENT)

        holder.postRemoveNotification(item, null, 4)
        assertThat(parent.removeThing).isSameAs(item)
        assertThat(parent.index).isEqualTo(4)
        assertThat(parent.link).isEqualTo(CylinderLink.LINK_PARENT)
    }

    @Test
    fun `Item create materializes a trash holder`() {
        val registry = ItemTypeRegistry().apply { register(holderType) }

        val item = Item.create(115, registry = registry)

        assertThat(item).isInstanceOf(TrashHolder::class.java)
        assertThat(item.getTrashHolder()).isSameAs(item)
    }
}
