package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.appearance.outfit.Outfit as OutfitDefinition
import org.opentibia.canary.creatures.players.PlayerTask
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.mount.Mount
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for `Game::playerSetShowOffSocket` (game.cpp:5227): the
 * podium outfit/mount write path used by `Game::playerRotatePodium` and the
 * `OutfitPacketHandler` show-off opcode.
 *
 * The C++ `Outfits::getInstance().getOutfitByLookType`, `Player::canWear` and
 * `Mounts::getMountByClientID` globals are injected seams (AGENTS.md 1.1).
 */
class GameShowOffSocketTest {

    private val center = Position(1000, 1000, 7)

    private fun newGame(): Game = Game(GameMap(), GameDispatcher())

    private fun Game.player(id: Long, name: String, position: Position = center): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = position).also {
            addPlayer(it)
            map.getOrCreateTile(position).also { tile -> if (tile.ground == null) tile.ground = Item(id = 100) }
            internalPlaceCreature(it, position)
        }

    private fun podiumItem(id: Int = ITEM_PODIUM_OF_RENOWN1, name: String = "podium"): Item =
        Item(id = id, type = ItemType(id = id, name = name, isPodium = true), count = 1)

    private fun Game.tileWith(item: Item, position: Position = center): Tile {
        val tile = map.getOrCreateTile(position)
        tile.ground = Item(id = 100)
        tile.addThing(item)
        return tile
    }

    private fun citizen(): OutfitDefinition =
        OutfitDefinition(name = "Citizen", lookType = 128, premium = false, unlocked = true, from = "")

    private fun bear(): Mount =
        Mount(id = 7, clientId = 7, name = "Bear", speed = 10, premium = false, type = "")

    @Test
    fun `playerSetShowOffSocket ignores an unknown player`() {
        val game = newGame()
        val item = podiumItem()
        game.tileWith(item)

        game.playerSetShowOffSocket(404, Outfit(lookType = 128), center, 0, ITEM_PODIUM_OF_RENOWN1, 1, 0)

        assertThat(game.getItemCustomAttribute(item, "LookType")).isNull()
    }

    @Test
    fun `playerSetShowOffSocket ignores the 0xFFFF position`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem()
        game.tileWith(item)
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerSetShowOffSocket(
            player.id,
            Outfit(lookType = 128),
            Position(0xFFFF, 1000, 7),
            0,
            ITEM_PODIUM_OF_RENOWN1,
            1,
            0,
        )

        assertThat(cancels).isEmpty()
        assertThat(game.getItemCustomAttribute(item, "LookType")).isNull()
    }

    @Test
    fun `playerSetShowOffSocket rejects a non-podium item`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = Item(id = ITEM_PODIUM_OF_RENOWN1, type = ItemType(id = ITEM_PODIUM_OF_RENOWN1, name = "stone"))
        game.tileWith(item)
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerSetShowOffSocket(player.id, Outfit(lookType = 128), center, 0, ITEM_PODIUM_OF_RENOWN1, 1, 0)

        assertThat(cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerSetShowOffSocket rejects a unique-id podium`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem()
        item.setAttribute(ItemAttribute.UNIQUEID, 10L)
        game.tileWith(item)
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerSetShowOffSocket(player.id, Outfit(lookType = 128), center, 0, ITEM_PODIUM_OF_RENOWN1, 1, 0)

        assertThat(cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerSetShowOffSocket rejects a podium owned by somebody else`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem()
        item.setOwner(99)
        game.tileWith(item)
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerSetShowOffSocket(player.id, Outfit(lookType = 128), center, 0, ITEM_PODIUM_OF_RENOWN1, 1, 0)

        assertThat(cancels).containsExactly(ReturnValue.ITEMISNOTYOURS)
    }

    @Test
    fun `playerSetShowOffSocket rejects a house podium when only invited players may use it`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem()
        game.tileWith(item)
        game.onlyInvitedCanMoveHouseItems = true
        game.playerCanUseItemOnHouseTile = { _, _ -> false }
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerSetShowOffSocket(player.id, Outfit(lookType = 128), center, 0, ITEM_PODIUM_OF_RENOWN1, 1, 0)

        assertThat(cancels).containsExactly(ReturnValue.CANNOTUSETHISOBJECT)
    }

    @Test
    fun `playerSetShowOffSocket reports there is no way when out of range without a path`() {
        val game = newGame()
        val player = game.player(1, "Hero", position = Position(1005, 1000, 7))
        game.tileWith(podiumItem())
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerSetShowOffSocket(player.id, Outfit(lookType = 128), center, 0, ITEM_PODIUM_OF_RENOWN1, 1, 0)

        assertThat(cancels).containsExactly(ReturnValue.THEREISNOWAY)
    }

    @Test
    fun `playerSetShowOffSocket walks to the podium and queues the browse task`() {
        val game = newGame()
        val player = game.player(1, "Hero", position = Position(1005, 1000, 7))
        game.tileWith(podiumItem())
        game.getPathTo = { _, _, _, _, _, _ -> listOf(Direction.West) }
        val walked = mutableListOf<List<Direction>>()
        game.queuePlayerAutoWalk = { _, dirs -> walked.add(dirs); true }
        val tasks = mutableListOf<Any?>()
        game.setNextWalkActionTask = { _, task -> tasks.add(task) }
        val browsed = mutableListOf<Position>()
        game.playerBrowseField = { _, pos -> browsed.add(pos) }

        game.playerSetShowOffSocket(player.id, Outfit(lookType = 128), center, 0, ITEM_PODIUM_OF_RENOWN1, 1, 0)

        assertThat(walked).containsExactly(listOf(Direction.West))
        val task = tasks.single() as PlayerTask
        assertThat(task.delayMs).isEqualTo(400)
        task.action()
        assertThat(browsed).containsExactly(center)
    }

    @Test
    fun `playerSetShowOffSocket clears the past look type with a refused outfit`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem()
        game.tileWith(item)
        game.playerCanWear = { _, _, _ -> false }

        game.playerSetShowOffSocket(player.id, Outfit(lookType = 128, lookAddons = 3), center, 0, ITEM_PODIUM_OF_RENOWN1, 1, 0)

        assertThat(game.getItemCustomAttribute(item, "LookType")).isNull()
        assertThat(game.getItemCustomAttribute(item, "PastLookType")).isNull()
    }

    @Test
    fun `playerSetShowOffSocket stores the outfit and renames the podium`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem()
        val tile = game.tileWith(item)
        game.playerCanWear = { _, lookType, _ -> lookType == 128 }
        game.getOutfitByLookType = { _, lookType -> if (lookType == 128) citizen() else null }
        val updated = mutableListOf<Pair<Tile, Item>>()
        game.sendUpdateTileItem = { _, actualTile, _, actualItem -> updated.add(actualTile to actualItem) }

        val outfit = Outfit(
            lookType = 128,
            lookHead = 1,
            lookBody = 2,
            lookLegs = 3,
            lookFeet = 4,
            lookAddons = 5,
        )
        game.playerSetShowOffSocket(player.id, outfit, center, 0, ITEM_PODIUM_OF_RENOWN1, 1, 2)

        assertThat(game.getItemCustomAttribute(item, "LookType")).isEqualTo(128L)
        assertThat(game.getItemCustomAttribute(item, "LookHead")).isEqualTo(1L)
        assertThat(game.getItemCustomAttribute(item, "LookBody")).isEqualTo(2L)
        assertThat(game.getItemCustomAttribute(item, "LookLegs")).isEqualTo(3L)
        assertThat(game.getItemCustomAttribute(item, "LookFeet")).isEqualTo(4L)
        assertThat(game.getItemCustomAttribute(item, "LookAddons")).isEqualTo(5L)
        assertThat(game.getItemCustomAttribute(item, "PodiumVisible")).isEqualTo(1L)
        assertThat(game.getItemCustomAttribute(item, "LookDirection")).isEqualTo(2L)
        assertThat(item.getAttribute(ItemAttribute.NAME)).isEqualTo("podium displaying the Citizen outfit")
        assertThat(updated).containsExactly(tile to item)
    }

    @Test
    fun `playerSetShowOffSocket stops after the outfit lookup fails`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem()
        game.tileWith(item)
        game.playerCanWear = { _, _, _ -> true }
        game.getOutfitByLookType = { _, _ -> null }
        var updated = false
        game.sendUpdateTileItem = { _, _, _, _ -> updated = true }

        game.playerSetShowOffSocket(player.id, Outfit(lookType = 128), center, 0, ITEM_PODIUM_OF_RENOWN1, 1, 0)

        assertThat(game.getItemCustomAttribute(item, "LookType")).isEqualTo(128L)
        assertThat(item.hasAttribute(ItemAttribute.NAME)).isFalse()
        assertThat(updated).isFalse()
    }

    @Test
    fun `playerSetShowOffSocket clears the past look type when the outfit becomes empty`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem()
        item.setAttribute(ItemAttribute.NAME, "podium displaying the Citizen outfit")
        game.tileWith(item)
        game.setItemCustomAttribute(item, "LookType", 128L)
        game.setItemCustomAttribute(item, "PastLookType", 128L)

        game.playerSetShowOffSocket(player.id, Outfit(), center, 0, ITEM_PODIUM_OF_RENOWN1, 1, 0)

        assertThat(game.getItemCustomAttribute(item, "LookType")).isNull()
        assertThat(game.getItemCustomAttribute(item, "PastLookType")).isNull()
        assertThat(item.hasAttribute(ItemAttribute.NAME)).isFalse()
    }

    @Test
    fun `playerSetShowOffSocket drops an unknown or unowned mount`() {
        val unknown = newGame()
        val unknownPlayer = unknown.player(1, "Hero")
        val unknownItem = podiumItem().also { unknown.tileWith(it) }

        unknown.playerSetShowOffSocket(
            unknownPlayer.id,
            Outfit(lookType = 0, lookMount = 7),
            center,
            0,
            ITEM_PODIUM_OF_RENOWN1,
            1,
            0,
        )

        val unowned = newGame()
        val unownedPlayer = unowned.player(1, "Hero")
        val unownedItem = podiumItem().also { unowned.tileWith(it) }
        unowned.getMountByClientID = { clientId -> if (clientId == 7) bear() else null }

        unowned.playerSetShowOffSocket(
            unownedPlayer.id,
            Outfit(lookType = 0, lookMount = 7),
            center,
            0,
            ITEM_PODIUM_OF_RENOWN1,
            1,
            0,
        )

        assertThat(unknown.getItemCustomAttribute(unknownItem, "LookMount")).isNull()
        assertThat(unowned.getItemCustomAttribute(unownedItem, "LookMount")).isNull()
    }

    @Test
    fun `playerSetShowOffSocket stores an owned mount and renames the podium`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem()
        game.tileWith(item)
        game.getMountByClientID = { clientId -> if (clientId == 7) bear() else null }
        player.features.mounts.setOwnedMounts(setOf(7))

        game.playerSetShowOffSocket(
            player.id,
            Outfit(lookMount = 7, lookMountHead = 1, lookMountBody = 2, lookMountLegs = 3, lookMountFeet = 4),
            center,
            0,
            ITEM_PODIUM_OF_RENOWN1,
            0,
            0,
        )

        assertThat(game.getItemCustomAttribute(item, "LookMount")).isEqualTo(7L)
        assertThat(game.getItemCustomAttribute(item, "LookMountHead")).isEqualTo(1L)
        assertThat(game.getItemCustomAttribute(item, "LookMountBody")).isEqualTo(2L)
        assertThat(game.getItemCustomAttribute(item, "LookMountLegs")).isEqualTo(3L)
        assertThat(game.getItemCustomAttribute(item, "LookMountFeet")).isEqualTo(4L)
        assertThat(item.getAttribute(ItemAttribute.NAME)).isEqualTo("podium displaying the Bear mount")
    }

    @Test
    fun `playerSetShowOffSocket combines outfit and mount in the podium name`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem()
        game.tileWith(item)
        game.playerCanWear = { _, _, _ -> true }
        game.getOutfitByLookType = { _, _ -> citizen() }
        game.getMountByClientID = { _ -> bear() }
        player.features.mounts.setOwnedMounts(setOf(7))

        game.playerSetShowOffSocket(
            player.id,
            Outfit(lookType = 128, lookMount = 7),
            center,
            0,
            ITEM_PODIUM_OF_RENOWN1,
            1,
            0,
        )

        assertThat(item.getAttribute(ItemAttribute.NAME)).isEqualTo("podium displaying the Citizen outfit on the Bear mount")
    }

    @Test
    fun `playerSetShowOffSocket refuses to use the own mount while wearing a support outfit`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem()
        game.tileWith(item)
        game.getMountByClientID = { _ -> bear() }
        player.features.mounts.setOwnedMounts(setOf(7))
        player.setCurrentOutfit(Outfit(lookType = 75))

        game.playerSetShowOffSocket(
            player.id,
            Outfit(lookMount = 7),
            center,
            0,
            ITEM_PODIUM_OF_RENOWN1,
            1,
            0,
        )

        assertThat(game.getItemCustomAttribute(item, "LookMount")).isNull()
    }

    @Test
    fun `playerSetShowOffSocket keeps the past mount when the new mount is rejected`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem()
        game.tileWith(item)
        game.setItemCustomAttribute(item, "LookMount", 7L)
        game.setItemCustomAttribute(item, "PastLookMount", 7L)

        game.playerSetShowOffSocket(player.id, Outfit(), center, 0, ITEM_PODIUM_OF_RENOWN1, 1, 0)

        assertThat(game.getItemCustomAttribute(item, "LookMount")).isNull()
        assertThat(game.getItemCustomAttribute(item, "PastLookMount")).isNull()
    }

    @Test
    fun `playerSetShowOffSocket sends the update to every player spectator`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val spectator = game.player(2, "Watcher", position = Position(1001, 1000, 7))
        val item = podiumItem()
        val tile = game.tileWith(item)
        game.playerCanWear = { _, _, _ -> true }
        game.getOutfitByLookType = { _, _ -> citizen() }
        val updated = mutableListOf<Creature>()
        game.sendUpdateTileItem = { target, _, _, _ -> updated.add(target) }

        game.playerSetShowOffSocket(player.id, Outfit(lookType = 128), center, 0, ITEM_PODIUM_OF_RENOWN1, 1, 0)

        assertThat(tile.position).isEqualTo(center)
        assertThat(updated).containsExactlyInAnyOrder(player, spectator)
    }

    companion object {
        /** `ITEM_PODIUM_OF_RENOWN1` (src/utils/utils_definitions.hpp:699). */
        private const val ITEM_PODIUM_OF_RENOWN1 = 35973
    }
}
