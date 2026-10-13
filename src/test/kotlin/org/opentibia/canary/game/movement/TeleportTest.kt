package org.opentibia.canary.game.movement

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript of `Teleport` (src/game/movement/teleport.cpp:35-113):
 * the cylinder query rules, `checkInfinityLoop` and the destination/effect
 * rules of `addThing`.
 */
class TeleportTest {

    @Test
    fun `exposes the Canary teleport cylinder query rules`() {
        val teleport = Teleport(id = TELEPORT_ITEM_ID)

        assertThat(teleport.queryAdd()).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(teleport.queryMaxCount()).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(teleport.queryRemove()).isEqualTo(ReturnValue.NOERROR)
        assertThat(teleport.queryDestination()).isSameAs(teleport)
    }

    @Test
    fun `stores the destination position defaulting to 0 0 0`() {
        val teleport = Teleport(id = TELEPORT_ITEM_ID)
        assertThat(teleport.getDestPos()).isEqualTo(Position(0, 0, 0))

        teleport.setDestPos(Position(320, 320, 7))
        assertThat(teleport.getDestPos()).isEqualTo(Position(320, 320, 7))
    }

    @Test
    fun `checkInfinityLoop detects the destination teleport sending back to this tile`() {
        val world = RecordingWorld()
        val sourcePosition = Position(100, 100, 7)
        val destinationPosition = Position(200, 200, 7)
        val sourceTeleport = Teleport(TELEPORT_ITEM_ID, destPos = destinationPosition)

        val destinationTile = Tile(destinationPosition).apply {
            addItem(Teleport(TELEPORT_ITEM_ID, destPos = sourcePosition))
        }

        assertThat(sourceTeleport.checkInfinityLoop(destinationTile, sourcePosition, world)).isTrue()
    }

    @Test
    fun `checkInfinityLoop follows a chain of teleports`() {
        val targetPosition = Position(100, 100, 7)
        val world = RecordingWorld(
            mutableMapOf(
                Position(300, 300, 7) to Tile(Position(300, 300, 7)).apply {
                    addItem(Teleport(TELEPORT_ITEM_ID, destPos = targetPosition))
                },
            ),
        )
        val sourceTeleport = Teleport(TELEPORT_ITEM_ID, destPos = Position(200, 200, 7))
        val destinationTile = Tile(Position(200, 200, 7)).apply {
            addItem(Teleport(TELEPORT_ITEM_ID, destPos = Position(300, 300, 7)))
        }

        assertThat(sourceTeleport.checkInfinityLoop(destinationTile, targetPosition, world)).isTrue()
    }

    @Test
    fun `checkInfinityLoop returns false without a teleport or a next tile`() {
        val world = RecordingWorld()
        val sourceTeleport = Teleport(TELEPORT_ITEM_ID, destPos = Position(200, 200, 7))
        val sourcePosition = Position(100, 100, 7)

        assertThat(sourceTeleport.checkInfinityLoop(null, sourcePosition, world)).isFalse()

        val plainTile = Tile(Position(200, 200, 7)).apply { addItem(Item(id = 2148)) }
        assertThat(sourceTeleport.checkInfinityLoop(plainTile, sourcePosition, world)).isFalse()

        val chainTile = Tile(Position(200, 200, 7)).apply {
            addItem(Teleport(TELEPORT_ITEM_ID, destPos = Position(999, 999, 7)))
        }
        assertThat(sourceTeleport.checkInfinityLoop(chainTile, sourcePosition, world)).isFalse()
    }

    @Test
    fun `addThing skips a teleport whose destination is 0 0 0`() {
        val world = RecordingWorld()
        val teleport = Teleport(TELEPORT_ITEM_ID)
        val teleportTile = Tile(Position(100, 100, 7))
        val creature = playerAt(Position(100, 100, 7))

        teleport.addThing(creature, teleportTile, world)

        assertThat(world.effects).isEmpty()
        assertThat(world.turnedCreatures).isEmpty()
        assertThat(world.movedCreatures).isEmpty()
    }

    @Test
    fun `addThing skips a missing destination tile`() {
        val world = RecordingWorld()
        val teleport = Teleport(TELEPORT_ITEM_ID, destPos = Position(200, 200, 7), type = effectType(EFFECT))
        val creature = playerAt(Position(100, 100, 7))

        teleport.addThing(creature, Tile(Position(100, 100, 7)), world)

        assertThat(world.effects).isEmpty()
        assertThat(world.movedCreatures).isEmpty()
    }

    @Test
    fun `addThing skips an infinity loop`() {
        val sourcePosition = Position(100, 100, 7)
        val destinationPosition = Position(200, 200, 7)
        val destinationTile = Tile(destinationPosition).apply {
            addItem(Teleport(TELEPORT_ITEM_ID, destPos = sourcePosition))
        }
        val world = RecordingWorld(mutableMapOf(destinationPosition to destinationTile))
        val teleport = Teleport(TELEPORT_ITEM_ID, destPos = destinationPosition, type = effectType(EFFECT))
        val creature = playerAt(sourcePosition)

        teleport.addThing(creature, Tile(sourcePosition), world)

        assertThat(world.turnedCreatures).isEmpty()
        assertThat(world.movedCreatures).isEmpty()
        assertThat(world.effects).isEmpty()
    }

    @Test
    fun `creature teleport turns toward the destination and shows the effect at both ends`() {
        val sourcePosition = Position(100, 100, 7)
        val destinationPosition = Position(200, 200, 7)
        val destinationTile = Tile(destinationPosition)
        val world = RecordingWorld(mutableMapOf(destinationPosition to destinationTile))
        val teleport = Teleport(TELEPORT_ITEM_ID, destPos = destinationPosition, type = effectType(EFFECT))
        val creature = playerAt(sourcePosition)

        teleport.addThing(creature, Tile(sourcePosition), world)

        assertThat(world.turnedCreatures).containsExactly(creature to Direction.East)
        assertThat(world.movedCreatures).containsExactly(creature to destinationTile)
        assertThat(world.effects).containsExactly(
            sourcePosition to EFFECT,
            destinationPosition to EFFECT,
        )
    }

    @Test
    fun `creature teleport turns west when the destination is west and east when x is equal`() {
        val world = RecordingWorld()
        val westTeleport = Teleport(TELEPORT_ITEM_ID, destPos = Position(50, 100, 7), type = effectType(EFFECT))
        val westCreature = playerAt(Position(100, 100, 7))
        world.tiles[Position(50, 100, 7)] = Tile(Position(50, 100, 7))

        westTeleport.addThing(westCreature, Tile(Position(100, 100, 7)), world)

        val sameXTeleport = Teleport(TELEPORT_ITEM_ID, destPos = Position(100, 200, 7), type = effectType(EFFECT))
        val sameXCreature = playerAt(Position(100, 100, 7))
        world.tiles[Position(100, 200, 7)] = Tile(Position(100, 200, 7))

        sameXTeleport.addThing(sameXCreature, Tile(Position(100, 100, 7)), world)

        assertThat(world.turnedCreatures).containsExactly(
            westCreature to Direction.West,
            sameXCreature to Direction.East,
        )
    }

    @Test
    fun `creature teleport without a magic effect shows nothing`() {
        val destinationPosition = Position(200, 200, 7)
        val world = RecordingWorld(mutableMapOf(destinationPosition to Tile(destinationPosition)))
        val teleport = Teleport(TELEPORT_ITEM_ID, destPos = destinationPosition, type = effectType(CONST_ME_NONE))
        val creature = playerAt(Position(100, 100, 7))

        teleport.addThing(creature, Tile(Position(100, 100, 7)), world)

        assertThat(world.turnedCreatures).hasSize(1)
        assertThat(world.movedCreatures).hasSize(1)
        assertThat(world.effects).isEmpty()
    }

    @Test
    fun `item teleport shows the effect at the destination first and then moves the item`() {
        val teleportPosition = Position(100, 100, 7)
        val destinationPosition = Position(200, 200, 7)
        val teleportTile = Tile(teleportPosition)
        val destinationTile = Tile(destinationPosition)
        val world = RecordingWorld(mutableMapOf(destinationPosition to destinationTile))
        val teleport = Teleport(TELEPORT_ITEM_ID, destPos = destinationPosition, type = effectType(EFFECT))
        val item = Item(id = 2148, count = 5)

        teleport.addThing(item, teleportTile, world)

        assertThat(world.effects).containsExactly(
            destinationPosition to EFFECT,
            teleportPosition to EFFECT,
        )
        assertThat(world.itemMoves).containsExactly(
            ItemMoveCall(
                fromTile = teleportTile,
                toTile = destinationTile,
                index = Game.INDEX_WHEREEVER,
                item = item,
                count = 5,
                flags = 0,
            ),
        )
    }

    private fun playerAt(position: Position): Player =
        Player(1, "Hero", 100, 100, position = position)

    private fun effectType(effect: Int): ItemType =
        ItemType(id = TELEPORT_ITEM_ID, name = "teleport", magicEffect = effect)

    private class RecordingWorld(
        val tiles: MutableMap<Position, Tile> = mutableMapOf(),
    ) : TeleportWorld {
        val turnedCreatures = mutableListOf<Pair<Creature, Direction>>()
        val movedCreatures = mutableListOf<Pair<Creature, Tile>>()
        val effects = mutableListOf<Pair<Position, Int>>()
        val itemMoves = mutableListOf<ItemMoveCall>()

        override fun getTile(position: Position): Tile? = tiles[position]

        override fun internalCreatureTurn(creature: Creature, direction: Direction) {
            turnedCreatures.add(creature to direction)
        }

        override fun moveCreature(creature: Creature, destTile: Tile) {
            movedCreatures.add(creature to destTile)
        }

        override fun addMagicEffect(position: Position, effect: Int) {
            effects.add(position to effect)
        }

        override fun internalMoveItem(
            fromTile: Tile?,
            toTile: Tile?,
            index: Int,
            item: Item?,
            count: Int,
            flags: Int,
        ): ReturnValue {
            itemMoves.add(ItemMoveCall(fromTile, toTile, index, item, count, flags))
            return ReturnValue.NOERROR
        }
    }

    private data class ItemMoveCall(
        val fromTile: Tile?,
        val toTile: Tile?,
        val index: Int,
        val item: Item?,
        val count: Int,
        val flags: Int,
    )

    private companion object {
        /** `ITEM_TELEPORT` item id, src/items/items.hpp. */
        const val TELEPORT_ITEM_ID = 1387

        /** `CONST_ME_NONE` (src/utils/utils_definitions.hpp:59). */
        const val CONST_ME_NONE = 0

        /** Any non-`CONST_ME_NONE` magic effect for the effect assertions. */
        const val EFFECT = 11
    }
}
