package org.opentibia.canary.network.protocol

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * State-change wiring tests: a mutation performed through the ported `Game` /
 * `Player` / `Container` entry points must reach the per-player [ProtocolSink]
 * and produce the Canary frame for that sender.
 *
 * Canary references:
 *  - `Game::placeCreature` -> `Player::sendCreatureAppear`   (game.cpp:1524, player.cpp:8785)
 *  - `Game::removeCreature` -> `Player::sendRemoveTileThing` (game.cpp:1551, player.cpp:7076)
 *  - `Game::changeSpeed` -> `Player::sendChangeSpeed`        (game.cpp:7747)
 *  - `Creature::changeHealth` -> `Game::addCreatureHealth`   (creature.cpp:845)
 *  - `Game::internalCreatureChangeOutfit`                    (game.cpp:7780)
 *  - `Player::addExperience` -> `Player::sendStats`          (player.cpp:3613, 3742)
 *  - `Player::addSkillAdvance` -> `sendSkills`/`sendStats`   (player.cpp:1190, 1246)
 *  - `Player::addThing`/`removeThing` -> `sendInventoryItem` (player.cpp:5013, 5100)
 *  - `Container::onAddContainerItem`/`onRemoveContainerItem` (container.cpp:465, 501)
 *  - `Game::playerCloseContainer`                            (game.cpp:5028)
 */
class SenderWiringTest {

    private val center = Position(1000, 1000, 7)
    private val adjacent = Position(1001, 1000, 7)

    private fun newGame(): Game = Game(GameMap(), GameDispatcher())

    private fun Game.groundTile(position: Position): Tile =
        map.getOrCreateTile(position).also { it.ground = Item(id = 100) }

    private fun newPlayer(id: Long, name: String, position: Position = center): Player =
        Player(
            id = id,
            name = name,
            maxHealth = 150,
            health = 100,
            maxMana = 50,
            mana = 50,
            capacity = 400.0,
            position = position,
        )

    private fun bind(player: Player, game: Game): InMemoryProtocolSink {
        val sink = InMemoryProtocolSink(player, game.map)
        player.protocolSink = sink
        return sink
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `sendTextMessageTyped reaches the per-player sink`() {
        val game = newGame()
        val talker = newPlayer(1, "Talker")
        val sink = bind(talker, game)

        talker.sendTextMessageTyped(MessageClass.MESSAGE_FAILURE, "Hi")

        assertThat(sink.frames).containsExactly(
            bytes(0xB4, 0x15, 0x02, 0x00, 'H'.code, 'i'.code),
        )
    }

    @Test
    fun `sendCancelMessage reaches the per-player sink`() {
        val game = newGame()
        val talker = newPlayer(1, "Talker")
        val sink = bind(talker, game)

        talker.sendCancelMessage(ReturnValue.NOTPOSSIBLE)

        assertThat(sink.frames).containsExactly(
            bytes(0xB4, 0x15, 0x14, 0x00) + "Sorry, not possible.".toByteArray(),
        )
    }

    @Test
    fun `placeCreature emits the appear frame to every spectator sink`() {
        val game = newGame()
        game.groundTile(center)
        game.groundTile(adjacent)
        val observer = newPlayer(1, "Observer")
        assertThat(game.internalPlaceCreature(observer, center)).isTrue()
        val sink = bind(observer, game)
        val rat = Monster(5000, "Rat", maxHealth = 100, health = 80, position = adjacent)

        assertThat(game.placeCreature(rat, rat.position)).isTrue()

        assertThat(sink.frames).hasSize(1)
        assertThat(sink.frames[0]).startsWith(
            *bytes(0x6A, 0xE9, 0x03, 0xE8, 0x03, 0x07, 0x01, 0x61, 0x00, 0x00, 0x00, 0x00, 0x00),
        )
    }

    @Test
    fun `removeCreature emits the remove-tile-thing frame with the client stack position`() {
        val game = newGame()
        game.groundTile(center)
        game.groundTile(adjacent)
        val observer = newPlayer(1, "Observer")
        assertThat(game.internalPlaceCreature(observer, center)).isTrue()
        val rat = Monster(5000, "Rat", maxHealth = 100, health = 80, position = adjacent)
        assertThat(game.placeCreature(rat, rat.position)).isTrue()
        val sink = bind(observer, game)

        assertThat(game.removeCreature(rat)).isTrue()

        assertThat(sink.frames).containsExactly(
            bytes(0x6C, 0xE9, 0x03, 0xE8, 0x03, 0x07, 0x01),
        )
    }

    @Test
    fun `changeSpeed emits the change-speed frame to same-floor spectators`() {
        val game = newGame()
        game.groundTile(center)
        game.groundTile(adjacent)
        val observer = newPlayer(1, "Observer")
        assertThat(game.internalPlaceCreature(observer, center)).isTrue()
        val rat = Monster(5000, "Rat", maxHealth = 100, health = 80, position = adjacent)
        assertThat(game.placeCreature(rat, rat.position)).isTrue()
        val sink = bind(observer, game)

        game.changeSpeed(rat, 10)

        assertThat(sink.frames).containsExactly(
            bytes(0x8F, 0x88, 0x13, 0x00, 0x00, 0xC8, 0x00, 0xD2, 0x00),
        )
    }

    @Test
    fun `changeHealth emits the health frame to every spectator sink`() {
        val game = newGame()
        game.groundTile(center)
        game.groundTile(adjacent)
        val observer = newPlayer(1, "Observer")
        assertThat(game.internalPlaceCreature(observer, center)).isTrue()
        val rat = Monster(5000, "Rat", maxHealth = 100, health = 100, position = adjacent)
        assertThat(game.placeCreature(rat, rat.position)).isTrue()
        val sink = bind(observer, game)

        rat.changeHealth(-20)

        assertThat(sink.frames).containsExactly(
            bytes(0x8C, 0x88, 0x13, 0x00, 0x00, 0x50),
        )
    }

    @Test
    fun `internalCreatureChangeOutfit emits the outfit frame to every spectator sink`() {
        val game = newGame()
        game.groundTile(center)
        game.groundTile(adjacent)
        val observer = newPlayer(1, "Observer")
        assertThat(game.internalPlaceCreature(observer, center)).isTrue()
        val rat = Monster(5000, "Rat", maxHealth = 100, health = 80, position = adjacent)
        assertThat(game.placeCreature(rat, rat.position)).isTrue()
        val sink = bind(observer, game)

        game.internalCreatureChangeOutfit(rat, Outfit(lookType = 128))

        assertThat(sink.frames).containsExactly(
            bytes(0x8E, 0x88, 0x13, 0x00, 0x00, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
        )
    }

    @Test
    fun `addExperience emits the stats frame after the level percent update`() {
        val game = newGame()
        val player = newPlayer(1, "Hero")
        val sink = bind(player, game)

        player.addExperience(100)

        assertThat(player.level).isEqualTo(2)
        assertThat(sink.frames).hasSize(1)
        assertThat(sink.frames[0]).startsWith(
            *bytes(
                0xA0,
                0x64, 0x00, 0x00, 0x00,
                0x96, 0x00, 0x00, 0x00,
                0x90, 0x01, 0x00, 0x00,
                0x64, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x02, 0x00,
            ),
        )
    }

    @Test
    fun `addSkillAdvance emits the advance message skills and stats frames`() {
        val game = newGame()
        val player = newPlayer(1, "Hero")
        val vocations = Vocations()
        assertThat(
            vocations.loadFromXml(
                """
                <vocations>
                    <vocation id="1" name="Knight" clientid="1" baseid="1">
                        <skill id="0" multiplier="1.0"/>
                    </vocation>
                </vocations>
                """.trimIndent(),
            ),
        ).isTrue()
        player.vocations = vocations
        player.vocationId = 1
        val sink = bind(player, game)

        player.addSkillTries(skills_t.SKILL_FIST, 50)

        val advance = "You advanced to fist fighting level 11."
        assertThat(player.getSkillLevel(skills_t.SKILL_FIST)).isEqualTo(11)
        assertThat(sink.frames).hasSize(3)
        assertThat(sink.frames[0]).isEqualTo(
            bytes(0xB4, 0x13, advance.length, 0x00) + advance.toByteArray(),
        )
        assertThat(sink.frames[1][0]).isEqualTo(0xA1.toByte())
        assertThat(sink.frames[2][0]).isEqualTo(0xA0.toByte())
    }

    @Test
    fun `inventory add and remove emit the inventory item frames`() {
        val game = newGame()
        val player = newPlayer(1, "Hero")
        val sink = bind(player, game)
        val item = Item(id = 1234)

        assertThat(player.addItem(item, Slots_t.CONST_SLOT_BACKPACK)).isEqualTo(ReturnValue.NOERROR)
        assertThat(player.removeItem(item)).isEqualTo(ReturnValue.NOERROR)

        assertThat(sink.frames).containsExactly(
            bytes(0x78, 0x03, 0xD2, 0x04),
            bytes(0x79, 0x03),
        )
    }

    @Test
    fun `open container item add and remove emit the container item frames`() {
        val game = newGame()
        val player = newPlayer(1, "Hero")
        val sink = bind(player, game)
        val container = Container(
            id = 2853,
            capacity = 20,
            type = ItemType(2853, "Backpack", isContainer = true),
        )
        player.addContainer(1, container)
        val item = Item(id = 1234)

        player.sendAddContainerItem(container, item)
        player.sendRemoveContainerItem(container, 0)

        assertThat(sink.frames).containsExactly(
            bytes(0x70, 0x01, 0x00, 0x00, 0xD2, 0x04),
            bytes(0x72, 0x01, 0x00, 0x00, 0x00, 0x00),
        )
    }

    @Test
    fun `playerCloseContainer emits the close container frame`() {
        val game = newGame()
        val player = newPlayer(1, "Hero")
        game.addPlayer(player)
        val container = Container(
            id = 2853,
            capacity = 20,
            type = ItemType(2853, "Backpack", isContainer = true),
        )
        player.addContainer(1, container)
        val sink = bind(player, game)

        game.playerCloseContainer(player.id, 1)

        assertThat(sink.frames).containsExactly(bytes(0x6F, 0x01))
    }
}
