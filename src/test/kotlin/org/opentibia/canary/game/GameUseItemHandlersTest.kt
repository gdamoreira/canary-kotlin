package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionSpeed
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypes_t
import org.opentibia.canary.items.Items
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the use-item/container `Game` handlers:
 *
 *  - `Game::playerUpdateContainer` (game.cpp:5090)
 *  - `Game::playerRotateItem` (game.cpp:5104)
 *  - `Game::playerWriteItem` (game.cpp:5535)
 *  - `Game::playerSeekInContainer` (game.cpp:5755)
 *  - `Game::playerUseItem` (game.cpp:4739)
 *  - `Game::playerUseItemEx` (game.cpp:4576)
 *  - `Game::playerUseWithCreature` (game.cpp:4853)
 *  - `Game::queuePlayerAutoWalkAfterItemPickup` (game.cpp:4500)
 *  - `Game::forcePlayerAutoWalk` (game.cpp:4548)
 */
class GameUseItemHandlersTest {

    private val center = Position(1000, 1000, 7)
    private val far = Position(1000, 1002, 7)

    private class Harness(
        val game: Game,
        val items: Items,
        val cancels: MutableList<ReturnValue>,
    )

    private fun itemsRegistry(): Items = Items().also {
        it.register(ItemType(id = 100, name = "ground", isGroundTile = true))
        it.register(ItemType(id = 2160, name = "magic sword", isMovable = true, rotateTo = 2161))
        it.register(ItemType(id = 2161, name = "magic longsword", isMovable = true))
        it.register(ItemType(id = 1978, name = "book", isMovable = true, canWriteText = true))
        it.register(ItemType(id = 1987, name = "backpack", isContainer = true, capacity = 8))
        it.register(ItemType(id = 23396, name = "store inbox", isContainer = true, capacity = 32))
        it.register(ItemType(id = 1949, name = "health potion", isMovable = true, type = ItemTypes_t.ITEM_TYPE_POTION))
        it.register(ItemType(id = 3147, name = "rune", isMovable = true, multiUse = true, type = ItemTypes_t.ITEM_TYPE_RUNE))
    }

    private fun newGame(): Harness {
        val items = itemsRegistry()
        val game = Game(GameMap(), GameDispatcher(), itemTypes = items)
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }
        return Harness(game, items, cancels)
    }

    private fun Game.tile(position: Position = center): Tile =
        map.getOrCreateTile(position).also {
            it.ground = Item(id = 100, type = ItemType(id = 100, name = "ground", isGroundTile = true))
        }

    private fun player(game: Game, id: Long = 1, name: String = "User", position: Position = center): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = position).also { game.addPlayer(it) }

    private fun item(harness: Harness, id: Int, count: Int = 1): Item =
        Item(id = id, count = count, type = harness.items.get(id))

    private fun Game.putOnTile(position: Position, item: Item): Tile =
        map.getOrCreateTile(position).also { it.addThing(item) }

    // ----- playerUpdateContainer -----

    @Test
    fun `playerUpdateContainer ignores an unknown player`() {
        val harness = newGame()
        harness.game.playerUpdateContainer(404, 0)
    }

    @Test
    fun `playerUpdateContainer ignores an unknown container id`() {
        val harness = newGame()
        val user = player(harness.game)
        var sent = false
        user.sendContainer = { _, _, _, _ -> sent = true }

        harness.game.playerUpdateContainer(user.id, 3)

        assertThat(sent).isFalse()
    }

    @Test
    fun `playerUpdateContainer sends the container with its parent state and index`() {
        val harness = newGame()
        val user = player(harness.game)
        val bag = Container(id = 1987, capacity = 8, type = harness.items.get(1987))
        user.addContainer(3, bag)
        user.setContainerIndex(3, 5)
        var sent: Quadruple<Int, Container, Boolean, Int>? = null
        user.sendContainer = { cid, container, hasParent, index -> sent = Quadruple(cid, container, hasParent, index) }

        harness.game.playerUpdateContainer(user.id, 3)

        assertThat(sent).isEqualTo(Quadruple(3, bag, bag.hasParent(), 5))
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    // ----- playerRotateItem -----

    @Test
    fun `playerRotateItem ignores an unknown player`() {
        val harness = newGame()
        harness.game.playerRotateItem(404, center, 0, 2160)
    }

    @Test
    fun `playerRotateItem cancels a mismatched item id`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.putOnTile(center, item(harness, 2160))

        harness.game.playerRotateItem(user.id, center, 0, 2161)

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerRotateItem cancels a non rotatable item`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.putOnTile(center, item(harness, 1987))

        harness.game.playerRotateItem(user.id, center, 0, 1987)

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerRotateItem cancels an item with a unique id`() {
        val harness = newGame()
        val user = player(harness.game)
        val rotatable = item(harness, 2160).also { it.setAttribute(ItemAttribute.UNIQUEID, 7) }
        harness.game.putOnTile(center, rotatable)

        harness.game.playerRotateItem(user.id, center, 0, 2160)

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerRotateItem cancels an item owned by someone else`() {
        val harness = newGame()
        val user = player(harness.game)
        val owned = item(harness, 2160).also { it.setOwner(999) }
        harness.game.putOnTile(center, owned)

        harness.game.playerRotateItem(user.id, center, 0, 2160)

        assertThat(harness.cancels).containsExactly(ReturnValue.ITEMISNOTYOURS)
    }

    @Test
    fun `playerRotateItem cancels a house item the player may not use`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.onlyInvitedCanMoveHouseItems = true
        harness.game.playerCanUseItemOnHouseTile = { _, _ -> false }
        harness.game.putOnTile(center, item(harness, 2160))

        harness.game.playerRotateItem(user.id, center, 0, 2160)

        assertThat(harness.cancels).containsExactly(ReturnValue.CANNOTUSETHISOBJECT)
    }

    @Test
    fun `playerRotateItem cancels when there is no path to a far item`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.putOnTile(far, item(harness, 2160))
        harness.game.getPathTo = { _, _, _, _, _, _ -> null }

        harness.game.playerRotateItem(user.id, far, 0, 2160)

        assertThat(harness.cancels).containsExactly(ReturnValue.THEREISNOWAY)
    }

    @Test
    fun `playerRotateItem queues the walk task when a path exists`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.putOnTile(far, item(harness, 2160))
        harness.game.getPathTo = { _, _, _, _, _, _ -> listOf(Direction.East) }
        var queued: List<Direction>? = null
        harness.game.queuePlayerAutoWalk = { _, dirs -> queued = dirs; true }

        harness.game.playerRotateItem(user.id, far, 0, 2160)

        assertThat(queued).containsExactly(Direction.East)
        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `playerRotateItem transforms through the rotateTo id`() {
        val harness = newGame()
        val user = player(harness.game)
        val rotatable = item(harness, 2160)
        val tile = harness.game.putOnTile(center, rotatable)

        harness.game.playerRotateItem(user.id, center, 0, 2160)

        assertThat(rotatable.id).isEqualTo(2161)
        assertThat(tile.items).contains(rotatable)
        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `playerRotateItem obeys the rotation callback veto`() {
        val harness = newGame()
        val user = player(harness.game)
        val rotatable = item(harness, 2160)
        harness.game.putOnTile(center, rotatable)
        harness.game.callbackPlayerOnRotateItem = { _, _, _ -> false }

        harness.game.playerRotateItem(user.id, center, 0, 2160)

        assertThat(rotatable.id).isEqualTo(2160)
        assertThat(harness.cancels).isEmpty()
    }

    // ----- playerWriteItem -----

    @Test
    fun `playerWriteItem ignores an unknown player`() {
        val harness = newGame()
        harness.game.playerWriteItem(404, 1, "text")
    }

    @Test
    fun `playerWriteItem ignores a mismatched window text id`() {
        val harness = newGame()
        val user = player(harness.game)
        val book = item(harness, 1978)
        harness.game.getWriteItem = { Triple(book, 5, 100) }

        harness.game.playerWriteItem(user.id, 4, "text")

        assertThat(book.hasAttribute(ItemAttribute.TEXT)).isFalse()
        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `playerWriteItem ignores a text longer than the maximum`() {
        val harness = newGame()
        val user = player(harness.game)
        val book = item(harness, 1978)
        harness.game.getWriteItem = { Triple(book, 5, 3) }

        harness.game.playerWriteItem(user.id, 5, "long")

        assertThat(book.hasAttribute(ItemAttribute.TEXT)).isFalse()
    }

    @Test
    fun `playerWriteItem cancels without a write item`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.getWriteItem = { Triple(null, 5, 100) }

        harness.game.playerWriteItem(user.id, 5, "text")

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerWriteItem cancels an item owned by someone else`() {
        val harness = newGame()
        val user = player(harness.game)
        val book = item(harness, 1978).also { it.setOwner(999) }
        harness.game.putOnTile(center, book)
        harness.game.getWriteItem = { Triple(book, 5, 100) }

        harness.game.playerWriteItem(user.id, 5, "text")

        assertThat(harness.cancels).containsExactly(ReturnValue.ITEMISNOTYOURS)
    }

    @Test
    fun `playerWriteItem cancels an item out of range`() {
        val harness = newGame()
        val user = player(harness.game)
        val book = item(harness, 1978)
        harness.game.putOnTile(far, book)
        harness.game.getWriteItem = { Triple(book, 5, 100) }

        harness.game.playerWriteItem(user.id, 5, "text")

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerWriteItem stops on a text edit event veto`() {
        val harness = newGame()
        val user = player(harness.game)
        val book = item(harness, 1978)
        harness.game.putOnTile(center, book)
        harness.game.getWriteItem = { Triple(book, 5, 100) }
        harness.game.executeTextEditEvents = { _, _, _ -> false }
        var cleared = false
        harness.game.setWriteItem = { _, writeItem -> cleared = writeItem == null }

        harness.game.playerWriteItem(user.id, 5, "text")

        assertThat(cleared).isTrue()
        assertThat(book.hasAttribute(ItemAttribute.TEXT)).isFalse()
    }

    @Test
    fun `playerWriteItem stamps text, writer and date`() {
        val harness = newGame()
        val user = player(harness.game)
        val book = item(harness, 1978)
        harness.game.putOnTile(center, book)
        harness.game.getWriteItem = { Triple(book, 5, 100) }
        harness.game.getTimeNow = { 1234L }
        var cleared = false
        harness.game.setWriteItem = { _, writeItem -> cleared = writeItem == null }

        harness.game.playerWriteItem(user.id, 5, "hello")

        assertThat(book.getAttribute(ItemAttribute.TEXT)).isEqualTo("hello")
        assertThat(book.getAttribute(ItemAttribute.WRITER)).isEqualTo(user.name)
        assertThat(book.getAttribute(ItemAttribute.DATE)).isEqualTo(1234L)
        assertThat(cleared).isTrue()
        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `playerWriteItem clears the text attributes for an empty text`() {
        val harness = newGame()
        val user = player(harness.game)
        val book = item(harness, 1978)
        book.setAttribute(ItemAttribute.TEXT, "old")
        book.setAttribute(ItemAttribute.WRITER, "someone")
        book.setAttribute(ItemAttribute.DATE, 1L)
        harness.game.putOnTile(center, book)
        harness.game.getWriteItem = { Triple(book, 5, 100) }

        harness.game.playerWriteItem(user.id, 5, "")

        assertThat(book.hasAttribute(ItemAttribute.TEXT)).isFalse()
        assertThat(book.hasAttribute(ItemAttribute.WRITER)).isFalse()
        assertThat(book.hasAttribute(ItemAttribute.DATE)).isFalse()
    }

    // ----- playerSeekInContainer -----

    @Test
    fun `playerSeekInContainer ignores an unknown player`() {
        val harness = newGame()
        harness.game.playerSeekInContainer(404, 1, 0, 0)
    }

    @Test
    fun `playerSeekInContainer ignores a container without pagination`() {
        val harness = newGame()
        val user = player(harness.game)
        val bag = Container(id = 1987, capacity = 8, type = harness.items.get(1987))
        user.addContainer(3, bag)
        var sent = false
        user.sendContainer = { _, _, _, _ -> sent = true }

        harness.game.playerSeekInContainer(user.id, 3, 0, 0)

        assertThat(sent).isFalse()
    }

    @Test
    fun `playerSeekInContainer ignores a misaligned or out of range index`() {
        val harness = newGame()
        val user = player(harness.game)
        val bag = Container(id = 1987, capacity = 8, type = harness.items.get(1987), pagination = true)
        user.addContainer(3, bag)
        var sent = false
        user.sendContainer = { _, _, _, _ -> sent = true }

        harness.game.playerSeekInContainer(user.id, 3, 8, 0)
        harness.game.playerSeekInContainer(user.id, 3, 3, 0)

        assertThat(sent).isFalse()
    }

    @Test
    fun `playerSeekInContainer stores the store inbox category and seeks the page`() {
        val harness = newGame()
        val user = player(harness.game)
        val storeInbox = Container(id = 23396, capacity = 32, type = harness.items.get(23396), pagination = true)
        repeat(10) { storeInbox.addThing(item(harness, 2160)) }
        user.addContainer(3, storeInbox)
        var sent: Quadruple<Int, Container, Boolean, Int>? = null
        user.sendContainer = { cid, container, hasParent, index -> sent = Quadruple(cid, container, hasParent, index) }

        harness.game.playerSeekInContainer(user.id, 3, 0, 2)

        assertThat(storeInbox.getAttribute(ItemAttribute.STORE_INBOX_CATEGORY)).isEqualTo("AmuletsAndNecklaces")
        assertThat(sent?.fourth).isEqualTo(0)
    }

    // ----- playerUseItem -----

    @Test
    fun `playerUseItem ignores an unknown player`() {
        val harness = newGame()
        harness.game.playerUseItem(404, center, 0, 0, 2160)
    }

    @Test
    fun `playerUseItem rejects a disabled hotkey`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.aimbotHotkeyEnabled = false
        var used = false
        harness.game.actionsUseItem = { _, _, _, _, _ -> used = true }

        harness.game.playerUseItem(user.id, Position(0xFFFF, 0, 0), 0, 0, 2160)

        assertThat(used).isFalse()
        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `playerUseItem rejects a multi use item`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.putOnTile(center, item(harness, 3147))

        harness.game.playerUseItem(user.id, center, 0, 0, 3147)

        assertThat(harness.cancels).containsExactly(ReturnValue.CANNOTUSETHISOBJECT)
    }

    @Test
    fun `playerUseItem rejects an exhausted potion user`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.putOnTile(center, item(harness, 1949))
        user.addCondition(
            ConditionSpeed(ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_PARALYZE, ticks = 100),
        )
        user.setWalkExhaust(10_000L)

        harness.game.playerUseItem(user.id, center, 0, 0, 1949)

        assertThat(harness.cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)
    }

    @Test
    fun `playerUseItem uses the item and refreshes an open depot search`() {
        val harness = newGame()
        val user = player(harness.game)
        val potion = item(harness, 1949)
        harness.game.putOnTile(center, potion)
        user.setDepotSearchIsOpen(1949, 0)
        harness.game.itemIsInsideDepot = { _, _ -> true }
        var used: Pair<Position, Int>? = null
        harness.game.actionsUseItem = { _, pos, index, _, _ -> used = pos to index }

        harness.game.playerUseItem(user.id, center, 0, 2, 1949)

        assertThat(used).isEqualTo(center to 2)
        assertThat(user.getDepotSearchItemId()).isEqualTo(1949)
        assertThat(harness.cancels).isEmpty()
    }

    // ----- playerUseItemEx -----

    @Test
    fun `playerUseItemEx rejects a non multi use source item`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.putOnTile(center, item(harness, 2160))

        harness.game.playerUseItemEx(user.id, center, 0, 2160, center, 0, 2160)

        assertThat(harness.cancels).containsExactly(ReturnValue.CANNOTUSETHISOBJECT)
    }

    @Test
    fun `playerUseItemEx uses the item with both positions`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.putOnTile(center, item(harness, 3147))
        var used: Pair<Position, Position>? = null
        harness.game.actionsUseItemEx = { _, from, to, _, _, _, _ -> used = from to to }

        harness.game.playerUseItemEx(user.id, center, 0, 3147, far, 0, 2160)

        assertThat(used).isEqualTo(center to far)
        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `playerUseItemEx queues the walk task when the source is too far`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.putOnTile(far, item(harness, 3147))
        harness.game.actionsCanUse = { _, pos, _ -> if (pos == center) ReturnValue.NOERROR else ReturnValue.TOOFARAWAY }
        harness.game.getPathTo = { _, _, _, _, _, _ -> listOf(Direction.East) }
        var queued: List<Direction>? = null
        harness.game.queuePlayerAutoWalk = { _, dirs -> queued = dirs; true }
        var used = false
        harness.game.actionsUseItemEx = { _, _, _, _, _, _, _ -> used = true }

        harness.game.playerUseItemEx(user.id, far, 0, 3147, center, 0, 2160)

        assertThat(queued).containsExactly(Direction.East)
        assertThat(used).isFalse()
    }

    @Test
    fun `playerUseItemEx cancels when there is no path`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.putOnTile(far, item(harness, 3147))
        harness.game.actionsCanUse = { _, pos, _ -> if (pos == center) ReturnValue.NOERROR else ReturnValue.TOOFARAWAY }
        harness.game.getPathTo = { _, _, _, _, _, _ -> null }

        harness.game.playerUseItemEx(user.id, far, 0, 3147, center, 0, 2160)

        assertThat(harness.cancels).containsExactly(ReturnValue.THEREISNOWAY)
    }

    @Test
    fun `playerUseItemEx defers the use when the action is on cooldown`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.putOnTile(center, item(harness, 3147))
        user.setNextPotionAction(Long.MAX_VALUE)
        var used = false
        harness.game.actionsUseItemEx = { _, _, _, _, _, _, _ -> used = true }

        harness.game.playerUseItemEx(user.id, center, 0, 3147, center, 0, 2160)

        assertThat(used).isFalse()
        assertThat(harness.cancels).isEmpty()
    }

    // ----- playerUseWithCreature -----

    @Test
    fun `playerUseWithCreature ignores an out of range creature`() {
        val harness = newGame()
        val user = player(harness.game)
        val monster = Monster(id = 100, name = "Rat", maxHealth = 100, health = 100, position = Position(1010, 1010, 7))
        harness.game.tile(monster.position)
        harness.game.internalPlaceCreature(monster, monster.position)
        harness.game.map.getTile(monster.position)!!.addThing(monster)

        harness.game.playerUseWithCreature(user.id, center, 0, monster.id, 3147)

        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `playerUseWithCreature rejects a disabled hotkey with the direct shoot code`() {
        val harness = newGame()
        val user = player(harness.game)
        val monster = Monster(id = 100, name = "Rat", maxHealth = 100, health = 100, position = Position(1001, 1000, 7))
        harness.game.tile(monster.position)
        harness.game.internalPlaceCreature(monster, monster.position)
        harness.game.map.getTile(monster.position)!!.addThing(monster)
        harness.game.aimbotHotkeyEnabled = false

        harness.game.playerUseWithCreature(user.id, Position(0xFFFF, 0, 0), 0, monster.id, 3147)

        assertThat(harness.cancels).containsExactly(ReturnValue.DIRECTPLAYERSHOOT)
    }

    @Test
    fun `playerUseWithCreature uses the item on the creature`() {
        val harness = newGame()
        val user = player(harness.game)
        val monster = Monster(id = 100, name = "Rat", maxHealth = 100, health = 100, position = Position(1001, 1000, 7))
        harness.game.tile(monster.position)
        harness.game.internalPlaceCreature(monster, monster.position)
        harness.game.map.getTile(monster.position)!!.addThing(monster)
        harness.game.putOnTile(center, item(harness, 3147))
        var used: Pair<Position, Monster>? = null
        harness.game.actionsUseItemEx = { _, from, _, _, _, _, creature ->
            used = from to (creature as Monster)
        }

        harness.game.playerUseWithCreature(user.id, center, 0, monster.id, 3147)

        assertThat(used).isEqualTo(center to monster)
        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `playerUseWithCreature refuses a familiar rune with the cooldown reply`() {
        val harness = newGame()
        val user = player(harness.game)
        val monster = Monster(id = 100, name = "Familiar", maxHealth = 100, health = 100, position = Position(1001, 1000, 7))
        user.isFamiliarProvider = { true }
        monster.setMaster(user)
        harness.game.tile(monster.position)
        harness.game.internalPlaceCreature(monster, monster.position)
        harness.game.map.getTile(monster.position)!!.addThing(monster)
        harness.game.putOnTile(center, item(harness, 3147))
        harness.game.exActionsDelayInterval = 1500
        var cooldown = 0
        harness.game.sendUseItemCooldown = { _, delay -> cooldown = delay }
        var used = false
        harness.game.actionsUseItemEx = { _, _, _, _, _, _, _ -> used = true }

        harness.game.playerUseWithCreature(user.id, center, 0, monster.id, 3147)

        assertThat(used).isFalse()
        assertThat(cooldown).isEqualTo(1500)
        assertThat(harness.cancels).containsExactly(ReturnValue.CANNOTUSETHISOBJECT)
    }

    // ----- queuePlayerAutoWalkAfterItemPickup / forcePlayerAutoWalk -----

    @Test
    fun `queuePlayerAutoWalkAfterItemPickup returns the accepted walk event`() {
        val harness = newGame()
        harness.game.addWalkEvent = { _, _, _ -> false }

        harness.game.queuePlayerAutoWalkAfterItemPickup(1, center, 1, harness.game.tile(), item(harness, 2160), 1) { _, _, _ -> }
    }

    @Test
    fun `queuePlayerAutoWalkAfterItemPickup moves the item and continues the walk`() {
        val harness = newGame()
        val user = player(harness.game)
        // The world composition binds every accepted creature's dispatcher
        // (`Game::internalPlaceCreature`); the queued `ImmediateWhenReady`
        // walk is admitted through it (`Creature::addEventWalk`).
        user.scheduleEvent = { _, _, _, _ -> 1L }
        val fromTile = harness.game.putOnTile(far, item(harness, 2160))
        var event: (() -> Unit)? = null
        harness.game.addWalkEvent = { _, _, e -> event = e; true }
        harness.game.pickupItem = { _, _, _, _ -> ReturnValue.NOERROR }
        harness.game.getPathTo = { _, _, _, _, _, _ -> listOf(Direction.West) }
        var afterPickup = false

        harness.game.queuePlayerAutoWalkAfterItemPickup(user.id, center, 1, fromTile, item(harness, 2160), 1) { _, _, _ ->
            afterPickup = true
        }
        event?.invoke()

        assertThat(afterPickup).isTrue()
        assertThat(user.listWalkDir).containsExactly(Direction.West)
        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `queuePlayerAutoWalkAfterItemPickup cancels the walk when the pickup fails`() {
        val harness = newGame()
        val user = player(harness.game)
        var event: (() -> Unit)? = null
        harness.game.addWalkEvent = { _, _, e -> event = e; true }
        harness.game.pickupItem = { _, _, _, _ -> ReturnValue.NOTPOSSIBLE }

        harness.game.queuePlayerAutoWalkAfterItemPickup(user.id, center, 1, harness.game.tile(), item(harness, 2160), 1) { _, _, _ -> }
        event?.invoke()

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `forcePlayerAutoWalk resets the walk state and starts the immediate policy for one step`() {
        val harness = newGame()
        val user = player(harness.game)
        var cancelledTarget = false
        var started: Pair<List<Direction>, Boolean>? = null
        user.sendCancelTarget = { cancelledTarget = true }
        harness.game.startAutoWalk = { _, dirs, ignoreConditions -> started = dirs to ignoreConditions }

        harness.game.forcePlayerAutoWalk(user.id, listOf(Direction.North))

        assertThat(cancelledTarget).isTrue()
        assertThat(started).isEqualTo(listOf(Direction.North) to true)
    }
}
