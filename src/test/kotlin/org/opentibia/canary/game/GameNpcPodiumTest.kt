package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.npcs.NpcType
import org.opentibia.canary.creatures.players.PlayerTask
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.modal.ModalWindow
import org.opentibia.canary.game.modal.ModalWindowClient
import org.opentibia.canary.game.modal.ModalWindowManager
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the remaining NPC/modal/podium `Game` handlers:
 *
 *  - `Game::playerNpcGreet` (game.cpp:10236)
 *  - `Game::playerAnswerModalWindow` (game.cpp:11144)
 *  - `Game::playerSetMonsterPodium` (game.cpp:11283)
 *  - `Game::playerRotatePodium` (game.cpp:11395)
 */
class GameNpcPodiumTest {

    private val center = Position(1000, 1000, 7)

    private fun newGame(): Game = Game(GameMap(), GameDispatcher())

    private fun Game.player(id: Long, name: String, position: Position = center): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = position).also {
            addPlayer(it)
            place(it)
        }

    private fun Game.place(creature: Creature, position: Position = creature.position) {
        map.getOrCreateTile(position).also { if (it.ground == null) it.ground = Item(id = 100) }
        internalPlaceCreature(creature, position)
    }

    private fun Game.npc(id: Long, speechBubble: Int = 1, position: Position = Position(1001, 1000, 7)): Npc {
        val type = NpcType("Guard")
        type.info.speechBubble = speechBubble
        return Npc(id = id, name = "Guard", position = position, npcType = type).also { place(it) }
    }

    // ----- playerNpcGreet -----

    @Test
    fun `playerNpcGreet ignores an unknown player`() {
        val game = newGame()
        game.npc(10)
        var said = false
        game.internalCreatureSay = { _, _, _, _, _ -> said = true }

        game.playerNpcGreet(404, 10)

        assertThat(said).isFalse()
    }

    @Test
    fun `playerNpcGreet ignores an unknown npc`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        var said = false
        game.internalCreatureSay = { _, _, _, _, _ -> said = true }

        game.playerNpcGreet(player.id, 10)

        assertThat(said).isFalse()
    }

    @Test
    fun `playerNpcGreet refuses an exhausted player`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        game.npc(10)
        game.isUIExhausted = { true }
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }
        var said = false
        game.internalCreatureSay = { _, _, _, _, _ -> said = true }

        game.playerNpcGreet(player.id, 10)

        assertThat(cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)
        assertThat(said).isFalse()
    }

    @Test
    fun `playerNpcGreet stops when the player cannot speak with the npc`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        game.npc(10)
        game.canSpeakWithHireling = { _, _ -> false }
        var said = false
        game.internalCreatureSay = { _, _, _, _, _ -> said = true }

        game.playerNpcGreet(player.id, 10)

        assertThat(said).isFalse()
    }

    @Test
    fun `playerNpcGreet says hi to the player and npc spectators`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val npc = game.npc(10)
        data class Say(val speaker: Creature, val type: SpeakClasses, val text: String, val spectators: List<Creature>)

        val says = mutableListOf<Say>()
        game.internalCreatureSay = { speaker, type, text, _, spectators -> says.add(Say(speaker, type, text, spectators)) }
        var exhausted = false
        game.updateUIExhausted = { exhausted = true }

        game.playerNpcGreet(player.id, 10)

        assertThat(says).hasSize(1)
        assertThat(says[0].speaker).isSameAs(player)
        assertThat(says[0].type).isEqualTo(SpeakClasses.TALKTYPE_SAY)
        assertThat(says[0].text).isEqualTo("hi")
        assertThat(says[0].spectators).containsExactlyInAnyOrder(player, npc)
        assertThat(exhausted).isTrue()
    }

    @Test
    fun `playerNpcGreet opens the trade dialog for a trade speech bubble`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val npc = game.npc(10, speechBubble = SPEECHBUBBLE_TRADE)
        data class Say(val type: SpeakClasses, val text: String, val spectators: List<Creature>)

        val says = mutableListOf<Say>()
        game.internalCreatureSay = { _, type, text, _, spectators -> says.add(Say(type, text, spectators)) }

        game.playerNpcGreet(player.id, 10)

        assertThat(says).hasSize(2)
        assertThat(says[1].type).isEqualTo(SpeakClasses.TALKTYPE_PRIVATE_PN)
        assertThat(says[1].text).isEqualTo("trade")
        assertThat(says[1].spectators).containsExactly(npc)
    }

    @Test
    fun `playerNpcGreet opens the sail dialog for a sailor speech bubble`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        game.npc(10, speechBubble = SPEECHBUBBLE_SAILOR)
        val texts = mutableListOf<String>()
        game.internalCreatureSay = { _, _, text, _, _ -> texts.add(text) }

        game.playerNpcGreet(player.id, 10)

        assertThat(texts).containsExactly("hi", "sail")
    }

    // ----- playerAnswerModalWindow -----

    private class RecordingModalClient : ModalWindowClient {
        val sent = mutableListOf<Long>()

        override fun sendModalWindow(modalWindow: ModalWindow) {
            sent.add(modalWindow.id)
        }
    }

    @Test
    fun `playerAnswerModalWindow ignores an unknown player`() {
        val manager = ModalWindowManager(client = RecordingModalClient())
        val game = Game(GameMap(), GameDispatcher(), modalWindowManager = manager)
        val player = game.player(1, "Hero")
        manager.sendModalWindow(player, ModalWindow(42L, "Quest", "Accept?"))

        game.playerAnswerModalWindow(404, 42L, 1, 0)

        assertThat(manager.hasModalWindowOpen(player, 42L)).isTrue()
    }

    @Test
    fun `playerAnswerModalWindow delegates the answer to the modal manager`() {
        val manager = ModalWindowManager(client = RecordingModalClient())
        val game = Game(GameMap(), GameDispatcher(), modalWindowManager = manager)
        val player = game.player(1, "Hero")
        manager.sendModalWindow(player, ModalWindow(42L, "Quest", "Accept?"))

        game.playerAnswerModalWindow(player.id, 42L, 1, 2)

        assertThat(manager.hasModalWindowOpen(player, 42L)).isFalse()
    }

    @Test
    fun `playerAnswerModalWindow leaves another open window untouched`() {
        val manager = ModalWindowManager(client = RecordingModalClient())
        val game = Game(GameMap(), GameDispatcher(), modalWindowManager = manager)
        val player = game.player(1, "Hero")
        manager.sendModalWindow(player, ModalWindow(42L, "Quest", "Accept?"))
        manager.sendModalWindow(player, ModalWindow(43L, "Other", "Other?"))

        game.playerAnswerModalWindow(player.id, 42L, 1, 2)

        assertThat(manager.hasModalWindowOpen(player, 43L)).isTrue()
    }

    // ----- playerSetMonsterPodium -----

    private fun podiumItem(id: Int = ITEM_PODIUM): Item =
        Item(id = id, type = ItemType(id = id, name = "podium", isPodium = true), count = 1)

    private fun Game.tileWith(item: Item, position: Position = center): Tile {
        val tile = map.getOrCreateTile(position)
        tile.ground = Item(id = 100)
        tile.addThing(item)
        return tile
    }

    @Test
    fun `playerSetMonsterPodium ignores a container position`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        var cancelled = false
        game.sendCancelMessage = { _, _ -> cancelled = true }

        game.playerSetMonsterPodium(player.id, 5, Position(0xFFFF, 1000, 7), 0, ITEM_PODIUM, 0, 1 to 1)

        assertThat(cancelled).isFalse()
    }

    @Test
    fun `playerSetMonsterPodium rejects a non podium item`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = Item(id = ITEM_PODIUM, type = ItemType(id = ITEM_PODIUM, name = "stone"), count = 1)
        game.tileWith(item)
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerSetMonsterPodium(player.id, 5, center, 0, ITEM_PODIUM, 0, 1 to 1)

        assertThat(cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerSetMonsterPodium rejects an item with a unique id`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem().also { it.setAttribute(ItemAttribute.UNIQUEID, 1L) }
        game.tileWith(item)
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerSetMonsterPodium(player.id, 5, center, 0, ITEM_PODIUM, 0, 1 to 1)

        assertThat(cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerSetMonsterPodium reports there is no way when out of range without a path`() {
        val game = newGame()
        val player = game.player(1, "Hero", position = Position(1005, 1000, 7))
        game.tileWith(podiumItem())
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerSetMonsterPodium(player.id, 5, center, 0, ITEM_PODIUM, 0, 1 to 1)

        assertThat(cancels).containsExactly(ReturnValue.THEREISNOWAY)
    }

    @Test
    fun `playerSetMonsterPodium walks to the podium and queues the browse task`() {
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

        game.playerSetMonsterPodium(player.id, 5, center, 0, ITEM_PODIUM, 0, 1 to 1)

        assertThat(walked).containsExactly(listOf(Direction.West))
        val task = tasks.single() as PlayerTask
        assertThat(task.delayMs).isEqualTo(400)
        task.action()
        assertThat(browsed).containsExactly(center)
    }

    @Test
    fun `playerSetMonsterPodium stores the monster outfit and renames the podium`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem()
        val tile = game.tileWith(item)
        val monster = org.opentibia.canary.creatures.monsters.MonsterType("Dragon")
        monster.info.outfit = monster.info.outfit.copy(lookType = 34, lookHead = 1, lookBody = 2, lookLegs = 3, lookFeet = 4, lookAddons = 5)
        game.getMonsterTypeByRaceId = { raceId, _ -> if (raceId == 5) monster else null }
        val updated = mutableListOf<Pair<Tile, Item>>()
        game.sendUpdateTileItem = { _, actualTile, _, actualItem -> updated.add(actualTile to actualItem) }
        var exhausted = false
        game.updateUIExhausted = { exhausted = true }

        game.playerSetMonsterPodium(player.id, 5, center, 0, ITEM_PODIUM, 3, 1 to 1)

        assertThat(game.getItemCustomAttribute(item, "PodiumMonsterRaceId")).isEqualTo(5L)
        assertThat(game.getItemCustomAttribute(item, "LookType")).isEqualTo(34L)
        assertThat(game.getItemCustomAttribute(item, "LookHead")).isEqualTo(1L)
        assertThat(game.getItemCustomAttribute(item, "LookDirection")).isEqualTo(3L)
        assertThat(game.getItemCustomAttribute(item, "PodiumVisible")).isEqualTo(1L)
        assertThat(game.getItemCustomAttribute(item, "MonsterVisible")).isEqualTo(1L)
        assertThat(item.getAttribute(ItemAttribute.NAME)).isEqualTo("podium displaying Dragon")
        assertThat(updated).containsExactly(tile to item)
        assertThat(exhausted).isTrue()
    }

    @Test
    fun `playerSetMonsterPodium renames the Tentugly boss`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem()
        game.tileWith(item)
        val monster = org.opentibia.canary.creatures.monsters.MonsterType("Tentugly's Head")
        monster.info.outfit = monster.info.outfit.copy(lookTypeEx = 35105)
        game.getMonsterTypeByRaceId = { _, _ -> monster }

        game.playerSetMonsterPodium(player.id, 5, center, 0, ITEM_PODIUM, 0, 1 to 1)

        assertThat(game.getItemCustomAttribute(item, "LookTypeEx")).isEqualTo(39003L)
        assertThat(item.getAttribute(ItemAttribute.NAME)).isEqualTo("podium displaying Tentugly")
    }

    @Test
    fun `playerSetMonsterPodium removes the monster name when it is hidden`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem().also { it.setAttribute(ItemAttribute.NAME, "podium displaying Dragon") }
        game.tileWith(item)
        val monster = org.opentibia.canary.creatures.monsters.MonsterType("Dragon")
        monster.info.outfit = monster.info.outfit.copy(lookType = 34)
        game.getMonsterTypeByRaceId = { _, _ -> monster }

        game.playerSetMonsterPodium(player.id, 5, center, 0, ITEM_PODIUM, 0, 1 to 0)

        assertThat(item.hasAttribute(ItemAttribute.NAME)).isFalse()
        assertThat(game.getItemCustomAttribute(item, "LookType")).isNull()
    }

    @Test
    fun `playerSetMonsterPodium rejects an unknown stored monster race`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem()
        game.tileWith(item)
        game.setItemCustomAttribute(item, "PodiumMonsterRaceId", 9L)
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerSetMonsterPodium(player.id, 0, center, 0, ITEM_PODIUM, 0, 1 to 1)

        assertThat(cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    // ----- playerRotatePodium -----

    @Test
    fun `playerRotatePodium rotates a bestiary podium to the next direction`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem()
        game.tileWith(item)
        val monster = org.opentibia.canary.creatures.monsters.MonsterType("Dragon")
        monster.info.outfit = monster.info.outfit.copy(lookType = 34)
        game.getMonsterTypeByRaceId = { _, _ -> monster }
        game.setItemCustomAttribute(item, "PodiumMonsterRaceId", 5L)
        game.setItemCustomAttribute(item, "LookDirection", 1L)
        game.setItemCustomAttribute(item, "MonsterVisible", 1L)
        game.setItemCustomAttribute(item, "LookTypeEx", 123L)

        game.playerRotatePodium(player.id, center, 0, ITEM_PODIUM)

        assertThat(game.getItemCustomAttribute(item, "LookDirection")).isEqualTo(2L)
    }

    @Test
    fun `playerRotatePodium wraps the direction back to north`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem()
        game.tileWith(item)
        val monster = org.opentibia.canary.creatures.monsters.MonsterType("Dragon")
        monster.info.outfit = monster.info.outfit.copy(lookType = 34)
        game.getMonsterTypeByRaceId = { _, _ -> monster }
        game.setItemCustomAttribute(item, "PodiumMonsterRaceId", 5L)
        game.setItemCustomAttribute(item, "LookDirection", 3L)
        game.setItemCustomAttribute(item, "MonsterVisible", 1L)
        game.setItemCustomAttribute(item, "LookTypeEx", 123L)

        game.playerRotatePodium(player.id, center, 0, ITEM_PODIUM)

        assertThat(game.getItemCustomAttribute(item, "LookDirection")).isEqualTo(0L)
    }

    @Test
    fun `playerRotatePodium rejects a bestiary podium without a visible monster`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem()
        game.tileWith(item)
        game.setItemCustomAttribute(item, "PodiumMonsterRaceId", 5L)
        game.setItemCustomAttribute(item, "LookDirection", 0L)
        game.setItemCustomAttribute(item, "MonsterVisible", 0L)
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerRotatePodium(player.id, center, 0, ITEM_PODIUM)

        assertThat(cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerRotatePodium hand the renown outfit to the show off socket`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem(id = ITEM_PODIUM_OF_RENOWN1)
        game.tileWith(item)
        game.setItemCustomAttribute(item, "LookType", 128L)
        game.setItemCustomAttribute(item, "LookMount", 7L)
        game.setItemCustomAttribute(item, "LookMountHead", 1L)
        game.setItemCustomAttribute(item, "LookDirection", 0L)
        game.setItemCustomAttribute(item, "PodiumVisible", 1L)
        game.setItemCustomAttribute(item, "MonsterVisible", 1L)
        game.playerCanWear = { _, lookType, _ -> lookType == 128 }
        game.getMountByClientID = { clientId ->
            if (clientId == 7) {
                org.opentibia.canary.systems.mount.Mount(
                    id = 7,
                    clientId = 7,
                    name = "Bear",
                    speed = 10,
                    premium = false,
                    type = "",
                )
            } else {
                null
            }
        }
        player.features.mounts.setOwnedMounts(setOf(7))
        game.getOutfitByLookType = { _, lookType ->
            if (lookType == 128) {
                org.opentibia.canary.creatures.appearance.outfit.Outfit(
                    name = "Citizen",
                    lookType = 128,
                    premium = false,
                    unlocked = true,
                    from = "",
                )
            } else {
                null
            }
        }

        game.playerRotatePodium(player.id, center, 0, ITEM_PODIUM_OF_RENOWN1)

        assertThat(game.getItemCustomAttribute(item, "LookType")).isEqualTo(128L)
        assertThat(game.getItemCustomAttribute(item, "LookMount")).isEqualTo(7L)
        assertThat(game.getItemCustomAttribute(item, "LookDirection")).isEqualTo(1L)
        assertThat(item.getAttribute(ItemAttribute.NAME)).isEqualTo("podium displaying the Citizen outfit on the Bear mount")
    }

    @Test
    fun `playerRotatePodium rejects an empty renown podium`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val item = podiumItem(id = ITEM_PODIUM_OF_RENOWN1)
        game.tileWith(item)
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerRotatePodium(player.id, center, 0, ITEM_PODIUM_OF_RENOWN1)

        assertThat(cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    companion object {
        /** `ITEM_PODIUM` (src/utils/utils_definitions.hpp:697). */
        private const val ITEM_PODIUM = 39004

        /** `ITEM_PODIUM_OF_RENOWN1` (src/utils/utils_definitions.hpp:699). */
        private const val ITEM_PODIUM_OF_RENOWN1 = 35973

        /** `SPEECHBUBBLE_TRADE` (src/creatures/creatures_definitions.hpp:336). */
        private const val SPEECHBUBBLE_TRADE = 2

        /** `SPEECHBUBBLE_SAILOR` (src/creatures/creatures_definitions.hpp:339). */
        private const val SPEECHBUBBLE_SAILOR = 5
    }
}
