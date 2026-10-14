package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionGeneric
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.items.SLOTP_ARMOR
import org.opentibia.canary.items.SLOTP_RIGHT
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the last movement and item-interaction `Game` handlers:
 *
 *  - `Game::internalGetCylinder` (game.cpp:1115)
 *  - `Game::checkMoveItemToCylinder` (game.cpp:2301)
 *  - `Game::playerMoveThing` (game.cpp:1711)
 *  - `Game::playerMoveCreatureByID` (game.cpp:1776)
 *  - `Game::playerMoveCreature` (game.cpp:1796)
 *  - `Game::playerMoveItemByPlayerID` (game.cpp:2031)
 *  - `Game::playerMoveItem` (game.cpp:2039)
 *  - `Game::playerInspectItem` (game.cpp:1635, 1652)
 *  - `Game::playerConfigureShowOffSocket` (game.cpp:5161)
 *  - `Game::playerWrapableItem` (game.cpp:5361)
 *  - `Game::playerLookInShop` (game.cpp:6328)
 *  - `Game::playerEquipItem` (game.cpp:4159)
 */
class GameMovementHandlersTest {

    private val center = Position(1000, 1000, 7)

    private class Harness(
        val game: Game,
        val items: Items,
        val cancels: MutableList<ReturnValue>,
        val texts: MutableList<Triple<Player, MessageClass, String>>,
        val cancelTexts: MutableList<String>,
    )

    private fun itemsRegistry(): Items = Items().also {
        it.register(ItemType(id = 100, name = "ground", isGroundTile = true))
        it.register(ItemType(id = 2160, name = "magic sword", isMovable = true))
        it.register(ItemType(id = 2170, name = "gold coin", isMovable = true, isStackable = true, stackSize = 100))
        it.register(ItemType(id = 2385, name = "backpack", isContainer = true, capacity = 20, isMovable = true))
        it.register(
            ItemType(id = 2171, name = "armor", isMovable = true, slotPosition = SLOTP_ARMOR, upgradeClassification = 10),
        )
        it.register(ItemType(id = 2172, name = "old armor", isMovable = true, slotPosition = SLOTP_ARMOR))
        it.register(ItemType(id = 3031, name = "gold coin", isMovable = true, isStackable = true, stackSize = 100))
        it.register(ItemType(id = 2250, name = "necklace", isMovable = true, slotPosition = 1 shl 1))
        it.register(ItemType(id = 2251, name = "ring", isMovable = true, slotPosition = 1 shl 8))
        it.register(
            ItemType(
                id = Items.ITEM_DECORATION_KIT,
                name = "decoration kit",
                isMovable = true,
                wrapableTo = 2160,
            ),
        )
        it.register(
            ItemType(id = 2173, name = "wrappable", isMovable = true, wrapable = true, wrapableTo = Items.ITEM_DECORATION_KIT),
        )
    }

    private fun newGame(): Harness {
        val items = itemsRegistry()
        val game = Game(GameMap(), GameDispatcher(), itemTypes = items)
        val cancels = mutableListOf<ReturnValue>()
        val texts = mutableListOf<Triple<Player, MessageClass, String>>()
        val cancelTexts = mutableListOf<String>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }
        game.sendTextMessage = { player, type, text -> texts.add(Triple(player, type, text)) }
        game.sendCancelMessageText = { _, message -> cancelTexts.add(message) }
        return Harness(game, items, cancels, texts, cancelTexts)
    }

    private fun Game.tile(position: Position = center): Tile =
        map.getOrCreateTile(position).also {
            if (it.ground == null) {
                it.ground = Item(id = 100, type = ItemType(id = 100, name = "ground", isGroundTile = true))
            }
        }

    private fun player(game: Game, id: Long = 1, name: String = "Hero", position: Position = center): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = position).also {
            game.addPlayer(it)
            game.tile(position)
            game.internalPlaceCreature(it, position)
        }

    private fun item(harness: Harness, id: Int, count: Int = 1): Item =
        Item(id = id, count = count, type = harness.items.get(id))

    private class TestCreature(id: Long, position: Position, private val ghost: Boolean = false, private val pushable: Boolean = true) :
        Creature(id, "creature-$id", maxHealth = 100, health = 100, position = position) {
        override fun isInGhostMode(): Boolean = ghost
        override fun isPushable(): Boolean = pushable
    }

    // ----- internalGetCylinder -----

    @Test
    fun `internalGetCylinder resolves the map tile outside 0xFFFF`() {
        val harness = newGame()
        val tile = harness.game.tile()

        assertThat(harness.game.internalGetCylinder(player(harness.game), center)).isSameAs(tile)
    }

    @Test
    fun `internalGetCylinder returns null for a missing tile`() {
        val harness = newGame()

        assertThat(harness.game.internalGetCylinder(player(harness.game), Position(1005, 1005, 7))).isNull()
    }

    @Test
    fun `internalGetCylinder resolves an open container by cid`() {
        val harness = newGame()
        val user = player(harness.game)
        val backpack = Container(id = 2385, capacity = 20)
        user.addContainer(2, backpack)

        assertThat(harness.game.internalGetCylinder(user, Position(0xFFFF, 0x42, 0))).isSameAs(backpack)
    }

    @Test
    fun `internalGetCylinder has no inventory cylinder because Player is not a Cylinder`() {
        val harness = newGame()
        val user = player(harness.game)

        assertThat(harness.game.internalGetCylinder(user, Position(0xFFFF, Slots_t.CONST_SLOT_BACKPACK.value, 0))).isNull()
    }

    // ----- checkMoveItemToCylinder -----

    @Test
    fun `checkMoveItemToCylinder rejects missing arguments`() {
        val harness = newGame()
        val user = player(harness.game)
        val item = item(harness, 2170)

        assertThat(harness.game.checkMoveItemToCylinder(null, harness.game.tile(), harness.game.tile(), item, center))
            .isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(harness.game.checkMoveItemToCylinder(user, harness.game.tile(), harness.game.tile(), null, center))
            .isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(
            harness.game.checkMoveItemToCylinder(
                user,
                harness.game.tile(),
                null,
                item,
                center,
            ),
        ).isEqualTo(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `checkMoveItemToCylinder rejects the gold pouch when quick-loot-only is on`() {
        val harness = newGame()
        harness.game.toggleGoldPouchQuickLootOnly = true
        val user = player(harness.game)
        val pouch = Container(id = Container.ITEM_GOLD_POUCH, capacity = 20)
        val coin = item(harness, 2170)

        assertThat(harness.game.checkMoveItemToCylinder(user, harness.game.tile(), pouch, coin, center))
            .isEqualTo(ReturnValue.CONTAINERNOTENOUGHROOM)
    }

    @Test
    fun `checkMoveItemToCylinder rejects foreign items in a strict gold pouch`() {
        val harness = newGame()
        val user = player(harness.game)
        val pouch = Container(id = Container.ITEM_GOLD_POUCH, capacity = 20)
        val sword = item(harness, 2160)

        assertThat(harness.game.checkMoveItemToCylinder(user, harness.game.tile(), pouch, sword, center))
            .isEqualTo(ReturnValue.ITEMCANNOTBEMOVEDPOUCH)
    }

    @Test
    fun `checkMoveItemToCylinder allows coins in a strict gold pouch`() {
        val harness = newGame()
        val user = player(harness.game)
        val pouch = Container(id = Container.ITEM_GOLD_POUCH, capacity = 20)
        val coin = item(harness, 3031)

        assertThat(harness.game.checkMoveItemToCylinder(user, harness.game.tile(), pouch, coin, center))
            .isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `checkMoveItemToCylinder blocks a plain item from a tile into the store inbox`() {
        val harness = newGame()
        val user = player(harness.game)
        val inbox = Container(id = Container.ITEM_STORE_INBOX, capacity = 30)
        val sword = item(harness, 2160)

        assertThat(harness.game.checkMoveItemToCylinder(user, harness.game.tile(), inbox, sword, center))
            .isEqualTo(ReturnValue.NOTBOUGHTINSTORE)
    }

    @Test
    fun `checkMoveItemToCylinder rejects a store item moved onto a tile outside a house`() {
        val harness = newGame()
        val user = player(harness.game)
        val source = Container(id = 2385, capacity = 20)
        val storeItem = item(harness, 2160)
        storeItem.setAttribute(org.opentibia.canary.items.ItemAttribute.STORE, 1L)

        assertThat(harness.game.checkMoveItemToCylinder(user, source, harness.game.tile(), storeItem, center))
            .isEqualTo(ReturnValue.ITEMCANNOTBEMOVEDTHERE)
    }

    @Test
    fun `checkMoveItemToCylinder rejects a store item owned by somebody else`() {
        val harness = newGame()
        val user = player(harness.game)
        val inbox = Container(id = Container.ITEM_STORE_INBOX, capacity = 30)
        val storeItem = item(harness, 2160)
        storeItem.setAttribute(org.opentibia.canary.items.ItemAttribute.STORE, 1L)
        storeItem.setOwner(99)

        assertThat(harness.game.checkMoveItemToCylinder(user, inbox, inbox, storeItem, center))
            .isEqualTo(ReturnValue.ITEMISNOTYOURS)
    }

    @Test
    fun `checkMoveItemToCylinder allows a plain container-to-tile move`() {
        val harness = newGame()
        val user = player(harness.game)
        val source = Container(id = 2385, capacity = 20)
        val sword = item(harness, 2160)

        assertThat(harness.game.checkMoveItemToCylinder(user, source, harness.game.tile(), sword, center))
            .isEqualTo(ReturnValue.NOERROR)
    }

    // ----- playerInspectItem -----

    @Test
    fun `playerInspectItem inspects the top-down item in range`() {
        val harness = newGame()
        val user = player(harness.game)
        val sword = item(harness, 2160)
        harness.game.tile().addThing(sword)
        val inspected = mutableListOf<Triple<Int, Int, Boolean>>()
        harness.game.sendItemInspection = { _, itemId, count, item, cyclopedia ->
            inspected.add(Triple(itemId, count, cyclopedia))
            assertThat(item).isSameAs(sword)
        }

        harness.game.playerInspectItem(user, center)

        assertThat(inspected).containsExactly(Triple(2160, 1, false))
    }

    @Test
    fun `playerInspectItem cancels when there is no item`() {
        val harness = newGame()
        val user = player(harness.game)

        harness.game.playerInspectItem(user, center)

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerInspectItem by id forwards the cyclopedia flag`() {
        val harness = newGame()
        val user = player(harness.game)
        var inspected: Triple<Int, Int, Boolean>? = null
        harness.game.sendItemInspection = { _, itemId, count, item, cyclopedia ->
            assertThat(item).isNull()
            inspected = Triple(itemId, count, cyclopedia)
        }

        harness.game.playerInspectItem(user, 2160, 3, true)

        assertThat(inspected).isEqualTo(Triple(2160, 3, true))
    }

    // ----- playerMoveThing -----

    @Test
    fun `playerMoveThing ignores an unknown player`() {
        val harness = newGame()

        harness.game.playerMoveThing(404, center, 2160, 0, center, 1)

        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `playerMoveThing ignores a player holding an imbuing item`() {
        val harness = newGame()
        val user = player(harness.game)
        user.setImbuingItem(item(harness, 2160))

        harness.game.playerMoveThing(user.id, center, 2160, 0, center, 1)

        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `playerMoveThing cancels a depot-search position when the search is closed`() {
        val harness = newGame()
        val user = player(harness.game)

        harness.game.playerMoveThing(user.id, Position(0xFFFF, 0x20, 0), 2160, 0, center, 1)

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerMoveThing cancels an empty tile`() {
        val harness = newGame()
        val user = player(harness.game)

        harness.game.playerMoveThing(user.id, center, 2160, 0, Position(1000, 1001, 7), 1)

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerMoveThing delegates item moves to playerMoveItem`() {
        val harness = newGame()
        val user = player(harness.game)
        val sword = item(harness, 2160)
        harness.game.tile().addThing(sword)
        harness.game.tile(Position(1000, 1001, 7))

        harness.game.playerMoveThing(user.id, center, 2160, 0, Position(1000, 1001, 7), 1)

        assertThat(harness.game.map.getTile(Position(1000, 1001, 7))!!.items).contains(sword)
    }

    @Test
    fun `playerMoveThing reports there is no way for a creature without a path`() {
        val harness = newGame()
        val user = player(harness.game)
        val creature = TestCreature(90, Position(1002, 1000, 7))
        harness.game.tile(creature.position).addThing(creature)
        harness.game.tile(Position(1001, 1000, 7))
        harness.game.getPathTo = { _, _, _, _, _, _ -> null }
        var event: (() -> Unit)? = null
        harness.game.addWalkEvent = { _, _, task -> event = task; true }

        harness.game.playerMoveThing(user.id, creature.position, 0, 0, Position(1001, 1000, 7), 1)
        event!!.invoke()

        assertThat(harness.cancels).containsExactly(ReturnValue.THEREISNOWAY)
    }

    @Test
    fun `playerMoveThing cancels a creature move without a destination tile`() {
        val harness = newGame()
        val user = player(harness.game)
        val creature = TestCreature(90, Position(1001, 1000, 7))
        harness.game.tile(creature.position).addThing(creature)

        harness.game.playerMoveThing(user.id, creature.position, 0, 0, Position(1005, 1000, 7), 1)

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    // ----- playerMoveCreature / ByID -----

    @Test
    fun `playerMoveCreatureByID ignores unknown ids`() {
        val harness = newGame()
        var accepted = false
        harness.game.addWalkEvent = { _, _, _ -> accepted = true; true }

        harness.game.playerMoveCreatureByID(404, 90, center, Position(1001, 1000, 7))
        player(harness.game)
        harness.game.playerMoveCreatureByID(1, 90, center, Position(1002, 1000, 7))

        assertThat(accepted).isFalse()
    }

    @Test
    fun `playerMoveCreatureByID cancels when the destination tile is missing`() {
        val harness = newGame()
        val user = player(harness.game)
        val creature = TestCreature(90, Position(1001, 1000, 7))
        harness.game.tile(creature.position)
        assertThat(harness.game.internalPlaceCreature(creature, creature.position)).isTrue()

        harness.game.playerMoveCreatureByID(user.id, 90, creature.position, Position(1005, 1000, 7))

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerMoveCreatureByID accepts the walk event then moves the creature`() {
        val harness = newGame()
        val user = player(harness.game)
        val creature = TestCreature(90, Position(1001, 1000, 7))
        harness.game.tile(creature.position)
        assertThat(harness.game.internalPlaceCreature(creature, creature.position)).isTrue()
        val destination = harness.game.tile(Position(1002, 1000, 7))
        var acceptedPlayer: Long? = null
        var event: (() -> Unit)? = null
        harness.game.addWalkEvent = { playerId, _, task -> acceptedPlayer = playerId; event = task; true }

        harness.game.playerMoveCreatureByID(user.id, 90, creature.position, Position(1002, 1000, 7))
        event!!.invoke()

        assertThat(acceptedPlayer).isEqualTo(1L)
        assertThat(destination.getCreatures()).contains(creature)
    }

    @Test
    fun `playerMoveCreature cancels when the walk event is refused`() {
        val harness = newGame()
        val user = player(harness.game)
        val creature = TestCreature(90, center)
        harness.game.internalPlaceCreature(creature, center)
        val destination = harness.game.tile(Position(1001, 1000, 7))
        harness.game.addWalkEvent = { _, _, _ -> false }

        harness.game.playerMoveCreature(user, creature, center, destination)

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerMoveCreature queues the walk when the creature is out of range`() {
        val harness = newGame()
        val user = player(harness.game)
        val creature = TestCreature(90, Position(1003, 1000, 7))
        harness.game.tile(creature.position).addThing(creature)
        val destination = harness.game.tile(Position(1003, 1001, 7))
        var event: (() -> Unit)? = null
        harness.game.addWalkEvent = { _, _, task -> event = task; true }
        harness.game.getPathTo = { _, _, _, _, _, _ -> listOf(Direction.East) }
        var queued: List<Direction>? = null
        harness.game.queuePlayerAutoWalk = { _, dirs -> queued = dirs; true }

        harness.game.playerMoveCreature(user, creature, creature.position, destination)
        event!!.invoke()

        assertThat(queued).containsExactly(Direction.East)
    }

    @Test
    fun `playerMoveCreature reports there is no way without a path`() {
        val harness = newGame()
        val user = player(harness.game)
        val creature = TestCreature(90, Position(1003, 1000, 7))
        harness.game.tile(creature.position).addThing(creature)
        val destination = harness.game.tile(Position(1003, 1001, 7))
        var event: (() -> Unit)? = null
        harness.game.addWalkEvent = { _, _, task -> event = task; true }
        harness.game.getPathTo = { _, _, _, _, _, _ -> null }

        harness.game.playerMoveCreature(user, creature, creature.position, destination)
        event!!.invoke()

        assertThat(harness.cancels).containsExactly(ReturnValue.THEREISNOWAY)
    }

    @Test
    fun `playerMoveCreature rejects a non-pushable creature`() {
        val harness = newGame()
        val user = player(harness.game)
        val creature = TestCreature(90, center, pushable = false)
        harness.game.internalPlaceCreature(creature, center)
        val destination = harness.game.tile(Position(1001, 1000, 7))
        var event: (() -> Unit)? = null
        harness.game.addWalkEvent = { _, _, task -> event = task; true }

        harness.game.playerMoveCreature(user, creature, center, destination)
        event!!.invoke()

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTMOVABLE)
    }

    @Test
    fun `playerMoveCreature rejects an invisible creature for a regular player`() {
        val harness = newGame()
        val user = player(harness.game)
        val creature = TestCreature(90, center, ghost = true)
        harness.game.internalPlaceCreature(creature, center)
        val destination = harness.game.tile(Position(1001, 1000, 7))
        var event: (() -> Unit)? = null
        harness.game.addWalkEvent = { _, _, task -> event = task; true }

        harness.game.playerMoveCreature(user, creature, center, destination)
        event!!.invoke()

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTMOVABLE)
    }

    @Test
    fun `playerMoveCreature rejects an out-of-range throw`() {
        val harness = newGame()
        val user = player(harness.game)
        val creature = TestCreature(90, Position(1000, 1001, 7))
        harness.game.tile(creature.position).addThing(creature)
        val destination = harness.game.tile(Position(1003, 1001, 7))
        var event: (() -> Unit)? = null
        harness.game.addWalkEvent = { _, _, task -> event = task; true }

        harness.game.playerMoveCreature(user, creature, creature.position, destination)
        event!!.invoke()

        assertThat(harness.cancels).containsExactly(ReturnValue.DESTINATIONOUTOFREACH)
    }

    @Test
    fun `playerMoveCreature rejects a blocked-path destination`() {
        val harness = newGame()
        val user = player(harness.game)
        val creature = TestCreature(90, Position(1000, 1001, 7))
        harness.game.tile(creature.position).addThing(creature)
        val destination = harness.game.tile(Position(1001, 1001, 7))
        destination.setFlag(TileFlags.TILESTATE_BLOCKPATH.value)
        var event: (() -> Unit)? = null
        harness.game.addWalkEvent = { _, _, task -> event = task; true }

        harness.game.playerMoveCreature(user, creature, creature.position, destination)
        event!!.invoke()

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTENOUGHROOM)
    }

    @Test
    fun `playerMoveCreature rejects a protecting-zone creature leaving the zone`() {
        val harness = newGame()
        val user = player(harness.game)
        val creature = TestCreature(90, Position(1000, 1001, 7))
        val source = harness.game.tile(creature.position)
        source.setFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
        source.addThing(creature)
        val destination = harness.game.tile(Position(1001, 1001, 7))
        var event: (() -> Unit)? = null
        harness.game.addWalkEvent = { _, _, task -> event = task; true }

        harness.game.playerMoveCreature(user, creature, creature.position, destination)
        event!!.invoke()

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerMoveCreature obeys the creature event veto`() {
        val harness = newGame()
        val user = player(harness.game)
        val creature = TestCreature(90, Position(1000, 1001, 7))
        harness.game.tile(creature.position).addThing(creature)
        val destination = harness.game.tile(Position(1001, 1001, 7))
        var event: (() -> Unit)? = null
        harness.game.addWalkEvent = { _, _, task -> event = task; true }
        harness.game.eventPlayerOnMoveCreature = { _, _, _, _ -> false }

        harness.game.playerMoveCreature(user, creature, creature.position, destination)
        event!!.invoke()

        assertThat(destination.getCreatures()).doesNotContain(creature)
    }

    @Test
    fun `playerMoveCreature moves an adjacent creature`() {
        val harness = newGame()
        val user = player(harness.game)
        val creature = TestCreature(90, Position(1000, 1001, 7))
        harness.game.tile(creature.position).addThing(creature)
        val destination = harness.game.tile(Position(1001, 1001, 7))
        var event: (() -> Unit)? = null
        harness.game.addWalkEvent = { _, _, task -> event = task; true }

        harness.game.playerMoveCreature(user, creature, creature.position, destination)
        event!!.invoke()

        assertThat(harness.cancels).isEmpty()
        assertThat(destination.getCreatures()).contains(creature)
    }

    // ----- playerMoveItem / ByPlayerID -----

    @Test
    fun `playerMoveItemByPlayerID ignores an unknown player`() {
        val harness = newGame()

        harness.game.playerMoveItemByPlayerID(404, center, 2160, 0, Position(1000, 1001, 7), 1)

        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `playerMoveItem schedules a retry while the player cannot act`() {
        val harness = newGame()
        val user = player(harness.game)
        val sword = item(harness, 2160)
        harness.game.tile().addThing(sword)
        user.setNextAction(System.currentTimeMillis() + 60_000)
        var scheduled: Pair<Long, String>? = null
        harness.game.setNextActionTask = { _, task -> scheduled = task?.let { it.delayMs to it.context } }

        harness.game.playerMoveItemByPlayerID(user.id, center, 2160, 0, Position(1000, 1001, 7), 1)

        assertThat(scheduled!!.second).isEqualTo("Game::playerMoveItem")
    }

    @Test
    fun `playerMoveItem cancels when the resolved item id does not match`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.tile().addThing(item(harness, 2160))

        harness.game.playerMoveItemByPlayerID(user.id, center, 2170, 0, Position(1000, 1001, 7), 1)

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerMoveItem cancels on a different floor`() {
        val harness = newGame()
        val user = player(harness.game, position = Position(1000, 1000, 8))
        val sword = item(harness, 2160)
        harness.game.tile().addThing(sword)
        harness.game.tile(Position(1001, 1000, 8))

        harness.game.playerMoveItemByPlayerID(user.id, center, 2160, 0, Position(1001, 1000, 8), 1)

        assertThat(harness.cancels).containsExactly(ReturnValue.FIRSTGOUPSTAIRS)
    }

    @Test
    fun `playerMoveItem reports there is no way to reach an item without a path`() {
        val harness = newGame()
        val user = player(harness.game, position = Position(1005, 1000, 7))
        val tile = harness.game.tile()
        tile.addThing(item(harness, 2160))
        harness.game.tile(Position(1006, 1000, 7))
        harness.game.getPathTo = { _, _, _, _, _, _ -> null }

        harness.game.playerMoveItemByPlayerID(user.id, center, 2160, 0, Position(1006, 1000, 7), 1)

        assertThat(harness.cancels).containsExactly(ReturnValue.THEREISNOWAY)
    }

    @Test
    fun `playerMoveItem queues the walk to a distant item`() {
        val harness = newGame()
        val user = player(harness.game, position = Position(1005, 1000, 7))
        harness.game.tile().addThing(item(harness, 2160))
        harness.game.tile(Position(1006, 1000, 7))
        harness.game.getPathTo = { _, _, _, _, _, _ -> listOf(Direction.West) }
        var queued: List<Direction>? = null
        harness.game.queuePlayerAutoWalk = { _, dirs -> queued = dirs; true }
        var task: Pair<Long, String>? = null
        harness.game.setNextWalkActionTask = { _, playerTask ->
            val typed = playerTask as? org.opentibia.canary.creatures.players.PlayerTask
            task = typed?.let { it.delayMs to it.context }
        }

        harness.game.playerMoveItemByPlayerID(user.id, center, 2160, 0, Position(1006, 1000, 7), 1)

        assertThat(queued).containsExactly(Direction.West)
        assertThat(task!!.first).isEqualTo(400L)
    }

    @Test
    fun `playerMoveItem rejects an out-of-reach destination`() {
        val harness = newGame()
        val user = player(harness.game)
        val sword = item(harness, 2160)
        harness.game.tile().addThing(sword)
        harness.game.tile(Position(1016, 1000, 7))

        harness.game.playerMoveItemByPlayerID(user.id, center, 2160, 0, Position(1016, 1000, 7), 1)

        assertThat(harness.cancels).containsExactly(ReturnValue.DESTINATIONOUTOFREACH)
    }

    @Test
    fun `playerMoveItem rejects a blocked throw line`() {
        val harness = newGame()
        val user = player(harness.game)
        val sword = item(harness, 2160)
        harness.game.tile().addThing(sword)
        harness.game.tile(Position(1000, 1001, 7))
        harness.game.canThrowObjectTo = { _, _ -> false }

        harness.game.playerMoveItemByPlayerID(user.id, center, 2160, 0, Position(1000, 1001, 7), 1)

        assertThat(harness.cancels).containsExactly(ReturnValue.CANNOTTHROW)
    }

    @Test
    fun `playerMoveItem obeys the move-item event veto`() {
        val harness = newGame()
        val user = player(harness.game)
        val sword = item(harness, 2160)
        harness.game.tile().addThing(sword)
        val destination = harness.game.tile(Position(1000, 1001, 7))
        harness.game.eventPlayerOnMoveItem = { _, _, _, _, _, _, _ -> false }

        harness.game.playerMoveItemByPlayerID(user.id, center, 2160, 0, Position(1000, 1001, 7), 1)

        assertThat(destination.items).doesNotContain(sword)
    }

    @Test
    fun `playerMoveItem rejects a non-pushable item`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.items.register(ItemType(id = 2199, name = "stone", isMovable = false))
        val stone = Item(id = 2199, type = harness.items.get(2199))
        val source = Container(id = 2385, capacity = 20, type = harness.items.get(2385))
        harness.game.tile().addThing(source)
        user.addContainer(2, source)
        source.addThing(stone)
        harness.game.tile(Position(1000, 1001, 7))

        harness.game.playerMoveItem(
            user,
            Position(0xFFFF, 0x42, 0),
            2199,
            0,
            Position(1000, 1001, 7),
            1,
            null,
            null,
        )

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTMOVABLE)
    }

    @Test
    fun `playerMoveItem moves a tile item to another tile`() {
        val harness = newGame()
        val user = player(harness.game)
        val coin = item(harness, 2170, count = 5)
        harness.game.tile().addThing(coin)
        val destination = harness.game.tile(Position(1000, 1001, 7))
        gameEvents(harness)

        harness.game.playerMoveItemByPlayerID(user.id, center, 2170, 0, Position(1000, 1001, 7), 3)

        assertThat(destination.items).anySatisfy { moved ->
            assertThat(moved.id).isEqualTo(2170)
        }
    }

    @Test
    fun `playerMoveItem stows an item into a locker stash`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.items.register(ItemType(id = 3497, name = "locker", isContainer = true, capacity = 30, isMovable = false))
        val locker = Container(id = 3497, capacity = 30, type = harness.items.get(3497))
        harness.game.tile().addThing(locker)
        user.addContainer(2, locker)
        val sword = item(harness, 2160)
        harness.game.tile().addThing(sword)
        val stowed = mutableListOf<Triple<Item, Int, Boolean>>()
        harness.game.stowItem = { _, actual, count, all -> stowed.add(Triple(actual, count, all)) }

        harness.game.playerMoveItem(
            user,
            center,
            2160,
            0,
            Position(0xFFFF, 0x40 or 2, 1),
            1,
            sword,
            locker,
        )

        assertThat(stowed.single().first).isSameAs(sword)
        assertThat(stowed.single().third).isFalse()
    }

    private fun gameEvents(harness: Harness) {
        harness.game.eventPlayerOnMoveItem = { _, _, _, _, _, _, _ -> true }
        harness.game.callbackPlayerOnMoveItem = { _, _, _, _, _, _, _ -> true }
        harness.game.eventPlayerOnItemMoved = { _, _, _, _, _, _, _ -> }
        harness.game.callbackPlayerOnItemMoved = { _, _, _, _, _, _, _ -> }
    }

    // ----- playerConfigureShowOffSocket -----

    @Test
    fun `playerConfigureShowOffSocket ignores an unknown player`() {
        val harness = newGame()
        val podium = podiumItem(harness)
        harness.game.tile().addThing(podium)

        harness.game.playerConfigureShowOffSocket(404, center, 0, podium.id)

        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `playerConfigureShowOffSocket opens the renown window for a podium of renown`() {
        val harness = newGame()
        val user = player(harness.game)
        val podium = podiumItem(harness)
        harness.game.tile().addThing(podium)
        var opened: Pair<Item, Position>? = null
        harness.game.sendPodiumWindow = { _, item, pos, _, _ -> opened = item to pos }
        harness.game.sendMonsterPodiumWindow = { _, _, _, _, _ -> }

        harness.game.playerConfigureShowOffSocket(user.id, center, 0, podium.id)

        assertThat(opened!!.first).isSameAs(podium)
    }

    @Test
    fun `playerConfigureShowOffSocket opens the monster window for a bestiary podium`() {
        val harness = newGame()
        val user = player(harness.game)
        val podium = podiumItem(harness, id = 39000)
        harness.game.tile().addThing(podium)
        var opened: Item? = null
        harness.game.sendPodiumWindow = { _, _, _, _, _ -> }
        harness.game.sendMonsterPodiumWindow = { _, item, _, _, _ -> opened = item }

        harness.game.playerConfigureShowOffSocket(user.id, center, 0, podium.id)

        assertThat(opened).isSameAs(podium)
    }

    @Test
    fun `playerConfigureShowOffSocket rejects a non-podium item`() {
        val harness = newGame()
        val user = player(harness.game)
        val sword = item(harness, 2160)
        harness.game.tile().addThing(sword)

        harness.game.playerConfigureShowOffSocket(user.id, center, 0, 2160)

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerConfigureShowOffSocket queues the window open after walking`() {
        val harness = newGame()
        val user = player(harness.game, position = Position(1005, 1000, 7))
        val podium = podiumItem(harness)
        harness.game.tile().addThing(podium)
        harness.game.getPathTo = { _, _, _, _, _, _ -> listOf(Direction.West) }
        harness.game.queuePlayerAutoWalk = { _, _ -> true }
        var task: Long? = null
        harness.game.setNextWalkActionTask = { _, playerTask ->
            task = (playerTask as? org.opentibia.canary.creatures.players.PlayerTask)?.delayMs
        }
        var opened: Item? = null
        harness.game.sendPodiumWindow = { _, item, _, _, _ -> opened = item }

        harness.game.playerConfigureShowOffSocket(user.id, center, 0, podium.id)

        assertThat(task).isEqualTo(400L)
        assertThat(opened).isNull()
    }

    private fun podiumItem(harness: Harness, id: Int = 35973): Item {
        harness.items.register(ItemType(id = id, name = "podium", isPodium = true))
        return Item(id = id, type = harness.items.get(id))
    }

    // ----- playerWrapableItem -----

    @Test
    fun `playerWrapableItem wraps a wrapable item inside an owned house`() {
        val harness = newGame()
        val user = player(harness.game)
        val house = harness.game.map.houses.addHouse(1)
        house.owner = user.getGUID()
        val tile = harness.game.tile()
        tile.houseId = 1
        tile.house = house
        tile.setFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
        val item = item(harness, 2173)
        tile.addThing(item)

        harness.game.playerWrapableItem(user.id, center, tile.getThingIndex(item), 2173)

        assertThat(tile.items).anySatisfy {
            assertThat(it.id).isEqualTo(Items.ITEM_DECORATION_KIT)
        }
    }

    @Test
    fun `playerWrapableItem rejects a tile outside a house`() {
        val harness = newGame()
        val user = player(harness.game)
        val tile = harness.game.tile()
        val item = item(harness, 2173)
        tile.addThing(item)

        harness.game.playerWrapableItem(user.id, center, tile.getThingIndex(item), 2173)

        assertThat(harness.cancelTexts).containsExactly("You may construct this only inside a house.")
    }

    @Test
    fun `playerWrapableItem rejects a non-owner`() {
        val harness = newGame()
        val user = player(harness.game)
        val house = harness.game.map.houses.addHouse(1)
        house.owner = 99L
        val tile = harness.game.tile()
        tile.houseId = 1
        tile.house = house
        tile.setFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
        val item = item(harness, 2173)
        tile.addThing(item)

        harness.game.playerWrapableItem(user.id, center, tile.getThingIndex(item), 2173)

        assertThat(harness.cancelTexts).containsExactly("You are not allowed to construct this here.")
    }

    @Test
    fun `playerWrapableItem rejects a non-wrappable item`() {
        val harness = newGame()
        val user = player(harness.game)
        val house = harness.game.map.houses.addHouse(1)
        house.owner = user.getGUID()
        val tile = harness.game.tile()
        tile.houseId = 1
        tile.house = house
        tile.setFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
        val sword = item(harness, 2160)
        tile.addThing(sword)

        harness.game.playerWrapableItem(user.id, center, tile.getThingIndex(sword), 2160)

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerWrapableItem queues the walk when out of range`() {
        val harness = newGame()
        val user = player(harness.game, position = Position(1005, 1000, 7))
        val house = harness.game.map.houses.addHouse(1)
        house.owner = user.getGUID()
        val tile = harness.game.tile()
        tile.houseId = 1
        tile.house = house
        tile.setFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
        val item = item(harness, 2173)
        tile.addThing(item)
        harness.game.getPathTo = { _, _, _, _, _, _ -> listOf(Direction.West) }
        var queued: List<Direction>? = null
        harness.game.queuePlayerAutoWalk = { _, dirs -> queued = dirs; true }
        var task: Long? = null
        harness.game.setNextWalkActionTask = { _, playerTask ->
            task = (playerTask as? org.opentibia.canary.creatures.players.PlayerTask)?.delayMs
        }

        harness.game.playerWrapableItem(user.id, center, tile.getThingIndex(item), 2173)

        assertThat(queued).containsExactly(Direction.West)
        assertThat(task).isEqualTo(400L)
    }

    // ----- playerLookInShop -----

    @Test
    fun `playerLookInShop ignores an unknown player`() {
        val harness = newGame()

        harness.game.playerLookInShop(404, 2160, 1)

        assertThat(harness.texts).isEmpty()
    }

    @Test
    fun `playerLookInShop ignores a closed shop`() {
        val harness = newGame()
        val user = player(harness.game)

        harness.game.playerLookInShop(user.id, 2160, 1)

        assertThat(harness.texts).isEmpty()
    }

    @Test
    fun `playerLookInShop sends the look text and informs the merchant`() {
        val harness = newGame()
        val user = player(harness.game)
        val npc = Npc(id = 50, name = "Merchant", position = Position(1001, 1000, 7))
        harness.game.getShopOwner = { npc }
        harness.game.getItemDescription = { _, count -> "a magic sword (x$count)" }
        var checked: Triple<Int, Int, Int>? = null
        npc.registerOnPlayerCheckItemHandler { _, player, itemId, count ->
            checked = Triple(player.id.toInt(), itemId, count)
        }

        harness.game.playerLookInShop(user.id, 2160, 2)

        assertThat(harness.texts.single().third).isEqualTo("You see a magic sword (x2)")
        assertThat(harness.texts.single().second).isEqualTo(MessageClass.MESSAGE_LOOK)
        assertThat(checked).isEqualTo(Triple(1, 2160, 2))
    }

    @Test
    fun `playerLookInShop obeys the shop look veto`() {
        val harness = newGame()
        val user = player(harness.game)
        val npc = Npc(id = 50, name = "Merchant", position = Position(1001, 1000, 7))
        harness.game.getShopOwner = { npc }
        harness.game.eventPlayerOnLookInShop = { _, _, _ -> false }

        harness.game.playerLookInShop(user.id, 2160, 1)

        assertThat(harness.texts).isEmpty()
    }

    // ----- playerEquipItem -----

    @Test
    fun `playerEquipItem ignores an unknown player`() {
        val harness = newGame()

        harness.game.playerEquipItem(404, 2171)

        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `playerEquipItem blocks a necklace during the equip cooldown`() {
        val harness = newGame()
        val user = player(harness.game)
        user.setNextNecklaceAction(Long.MAX_VALUE)

        harness.game.playerEquipItem(user.id, 2250)

        assertThat(user.getInventoryItem(Slots_t.CONST_SLOT_NECKLACE)).isNull()
    }

    @Test
    fun `playerEquipItem blocks a ring during the equip cooldown`() {
        val harness = newGame()
        val user = player(harness.game)
        user.setNextRingAction(Long.MAX_VALUE)

        harness.game.playerEquipItem(user.id, 2251)

        assertThat(user.getInventoryItem(Slots_t.CONST_SLOT_RING)).isNull()
    }

    @Test
    fun `playerEquipItem refuses to equip while feared`() {
        val harness = newGame()
        val user = player(harness.game)
        user.addCondition(
            ConditionGeneric(ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_FEARED, ticks = 10),
        )

        harness.game.playerEquipItem(user.id, 2171)

        assertThat(harness.texts.single().third).isEqualTo("You are feared.")
        assertThat(harness.texts.single().second).isEqualTo(MessageClass.MESSAGE_FAILURE)
    }

    @Test
    fun `playerEquipItem equips an armor from the backpack`() {
        val harness = newGame()
        val user = player(harness.game)
        val backpack = Container(id = 2385, capacity = 20, type = harness.items.get(2385))
        user.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)
        val armor = item(harness, 2171)
        backpack.addThing(armor)

        harness.game.playerEquipItem(user.id, 2171)

        assertThat(user.getInventoryItem(Slots_t.CONST_SLOT_ARMOR)).isSameAs(armor)
        assertThat(backpack.items).doesNotContain(armor)
    }

    @Test
    fun `playerEquipItem moves the previously equipped armor away`() {
        val harness = newGame()
        val user = player(harness.game)
        val backpack = Container(id = 2385, capacity = 20, type = harness.items.get(2385))
        user.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)
        val oldArmor = item(harness, 2172)
        user.addItem(oldArmor, Slots_t.CONST_SLOT_ARMOR)
        val newArmor = item(harness, 2171)
        backpack.addThing(newArmor)

        harness.game.playerEquipItem(user.id, 2171)

        assertThat(user.getInventoryItem(Slots_t.CONST_SLOT_ARMOR)).isSameAs(newArmor)
        assertThat(user.getInventoryItem(Slots_t.CONST_SLOT_HEAD)).isSameAs(oldArmor)
    }

    @Test
    fun `playerEquipItem cancels when the same armor is already equipped without a managed container`() {
        val harness = newGame()
        val user = player(harness.game)
        val backpack = Container(id = 2385, capacity = 20, type = harness.items.get(2385))
        user.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)
        val oldArmor = item(harness, 2171)
        user.addItem(oldArmor, Slots_t.CONST_SLOT_ARMOR)
        val newArmor = item(harness, 2171)
        backpack.addThing(newArmor)

        harness.game.playerEquipItem(user.id, 2171)

        assertThat(user.getInventoryItem(Slots_t.CONST_SLOT_ARMOR)).isSameAs(oldArmor)
        assertThat(backpack.items).contains(newArmor)
        assertThat(harness.cancels).contains(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerEquipItem sends the cancel message when the item cannot be placed`() {
        val harness = newGame()
        val user = player(harness.game)
        val armor = item(harness, 2171)
        user.addItem(armor, Slots_t.CONST_SLOT_ARMOR)

        harness.game.playerEquipItem(user.id, 2171)

        assertThat(harness.cancels).contains(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerEquipItem does nothing when the item is not in the backpack`() {
        val harness = newGame()
        val user = player(harness.game)

        harness.game.playerEquipItem(user.id, 2171)

        assertThat(user.getInventoryItem(Slots_t.CONST_SLOT_ARMOR)).isNull()
        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `playerEquipItem honours the tier filter`() {
        val harness = newGame()
        val user = player(harness.game)
        val backpack = Container(id = 2385, capacity = 20, type = harness.items.get(2385))
        user.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)
        val tierOne = item(harness, 2171)
        tierOne.setTier(1)
        val tierTwo = item(harness, 2171)
        tierTwo.setTier(2)
        backpack.addThing(tierOne)
        backpack.addThing(tierTwo)

        harness.game.playerEquipItem(user.id, 2171, hasTier = true, tier = 2)

        assertThat(user.getInventoryItem(Slots_t.CONST_SLOT_ARMOR)).isSameAs(tierTwo)
        assertThat(backpack.items).contains(tierOne)
    }

    @Test
    fun `playerEquipItem equips a right-hand weapon`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.items.register(
            ItemType(
                id = 2400,
                name = "wand",
                isMovable = true,
                slotPosition = SLOTP_RIGHT,
                weaponType = WeaponType_t.WEAPON_WAND,
            ),
        )
        val wand = Item(id = 2400, type = harness.items.get(2400))
        val backpack = Container(id = 2385, capacity = 20, type = harness.items.get(2385))
        user.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)
        backpack.addThing(wand)

        harness.game.playerEquipItem(user.id, 2400)

        assertThat(user.getInventoryItem(Slots_t.CONST_SLOT_RIGHT)).isSameAs(wand)
    }
}
