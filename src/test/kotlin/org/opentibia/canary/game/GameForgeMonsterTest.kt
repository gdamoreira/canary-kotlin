package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.FeatureFlags
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.monsters.ForgeClassification
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.forge.ForgeRandom

/**
 * Transcript tests for the fiendish/influenced forge event family:
 *
 *  - `Game::makeInfluencedMonster` (game.cpp:11882)
 *  - `Game::makeFiendishMonster` (game.cpp:11928)
 *  - `Game::updateFiendishMonsterStatus` (game.cpp:12045)
 *  - `Game::removeForgeMonster` (game.cpp:12057)
 *  - `Game::removeInfluencedMonster` (game.cpp:12067)
 *  - `Game::removeFiendishMonster` (game.cpp:12084)
 *  - `Game::updateForgeableMonsters` (game.cpp:12103)
 *  - `Game::createFiendishMonsters` (game.cpp:12139)
 *  - `Game::createInfluencedMonsters` (game.cpp:12170)
 *  - `Game::checkForgeEventId` (game.cpp:12201)
 *  - `Game::addInfluencedMonster` (game.cpp:12209)
 *  - `Game::addMonster` / `Game::removeMonster` / `Game::getMonsterByID`
 *    (game.cpp:11671, 11683, 1309)
 *
 * The `Monster::` forge accessors are injected seams (the Kotlin [Monster] does
 * not carry forge state yet); the dispatcher event scheduling is injected
 * (`scheduleEvent`/`stopEvent`) so the exact C++ delays can be asserted.
 *
 * The fixture keeps both creation limits at zero so `updateForgeableMonsters`
 * only rebuilds the forgeable list; tests re-arm the limits before the explicit
 * `make*Monster` call.
 */
class GameForgeMonsterTest {

    private val center = Position(1000, 1000, 7)

    private val noCreation = FeatureFlags(forgeFiendishCreaturesLimit = 0, forgeInfluencedCreaturesLimit = 0)

    private class ForgeHarness(
        val game: Game,
        val scheduled: MutableList<Pair<Long, () -> Unit>>,
        val stopped: MutableList<Long>,
        val classifications: MutableList<Pair<Long, ForgeClassification>>,
        val configured: MutableList<Pair<Long, Int>>,
        val times: MutableList<Pair<Long, Long>>,
        val cleared: MutableList<Long>,
    )

    private fun newGame(
        rolls: List<Int> = emptyList(),
        now: Long = 0L,
        forgeable: (Monster) -> Boolean = { false },
        stacks: (Monster) -> Int = { 0 },
    ): ForgeHarness {
        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
        val stopped = mutableListOf<Long>()
        val classifications = mutableListOf<Pair<Long, ForgeClassification>>()
        val configured = mutableListOf<Pair<Long, Int>>()
        val times = mutableListOf<Pair<Long, Long>>()
        val cleared = mutableListOf<Long>()
        var eventId = 0L
        var rollIndex = 0
        val game = Game(
            GameMap(),
            GameDispatcher(),
            forgeRandom = ForgeRandom { _, _ -> rolls.getOrElse(rollIndex++) { 0 } },
        )
        game.normalRandom = { minNumber, _ -> minNumber }
        game.getTimeNow = { now }
        game.scheduleEvent = { delay, task ->
            scheduled.add(delay to task)
            ++eventId
        }
        game.stopEvent = { stopped.add(it) }
        game.featureFlags = noCreation
        game.canBeForgeMonster = forgeable
        game.getForgeStack = stacks
        game.setMonsterForgeClassification = { monster, classification ->
            classifications.add(monster.id to classification)
        }
        game.configureForgeSystem = { monster, stack -> configured.add(monster.id to stack) }
        game.setTimeToChangeFiendish = { monster, time -> times.add(monster.id to time) }
        game.clearFiendishStatus = { monster -> cleared.add(monster.id) }

        return ForgeHarness(game, scheduled, stopped, classifications, configured, times, cleared)
    }

    private fun Game.tile(position: Position = center, noLogout: Boolean = false): Tile {
        val tile = map.getOrCreateTile(position)
        if (tile.ground == null) {
            tile.ground = Item(id = 100)
        }
        if (noLogout) {
            tile.setFlag(TileFlags.TILESTATE_NOLOGOUT.value)
        }
        return tile
    }

    private fun Game.monster(id: Long, name: String = "Rat", tile: Tile? = null, attachTile: Boolean = true): Monster {
        val resolvedTile = when {
            tile != null -> tile
            attachTile -> this.tile(Position(1000 + id.toInt(), 1000, 7))
            else -> null
        }
        val monster = Monster(
            id = id,
            name = name,
            maxHealth = 100,
            health = 100,
            position = resolvedTile?.position ?: center,
        )
        if (resolvedTile != null) {
            monster.setParent(resolvedTile)
        }
        addMonster(monster)
        return monster
    }

    // ----- getMonsterByID / addMonster / removeMonster -----

    @Test
    fun `getMonsterByID resolves registered monsters and rejects unknown ids`() {
        val harness = newGame()
        val monster = harness.game.monster(10)

        assertThat(harness.game.getMonsterByID(0)).isNull()
        assertThat(harness.game.getMonsterByID(10)).isSameAs(monster)
        assertThat(harness.game.getMonsterByID(11)).isNull()
    }

    @Test
    fun `removeMonster swaps the removed entry with the last one`() {
        val harness = newGame()
        val first = harness.game.monster(10, "Rat")
        val second = harness.game.monster(11, "Wolf")
        val third = harness.game.monster(12, "Bear")

        harness.game.removeMonster(first)

        assertThat(harness.game.getMonsterByID(10)).isNull()
        assertThat(harness.game.getMonsterByID(11)).isSameAs(second)
        assertThat(harness.game.getMonsterByID(12)).isSameAs(third)
    }

    // ----- makeInfluencedMonster -----

    @Test
    fun `makeInfluencedMonster returns zero without a forgeable monster`() {
        val harness = newGame(forgeable = { true })
        harness.game.monster(10)

        assertThat(harness.game.makeInfluencedMonster()).isZero()
    }

    @Test
    fun `makeInfluencedMonster classifies the drawn monster and registers it`() {
        val harness = newGame(forgeable = { true })
        harness.game.monster(10)
        harness.game.updateForgeableMonsters()
        harness.game.featureFlags = FeatureFlags(forgeInfluencedCreaturesLimit = 1)

        assertThat(harness.game.makeInfluencedMonster()).isEqualTo(10)
        assertThat(harness.classifications).containsExactly(10L to ForgeClassification.Influenced)
        assertThat(harness.configured).containsExactly(10L to 0)
        assertThat(harness.game.getInfluencedMonsters()).containsExactly(10L)
    }

    @Test
    fun `makeInfluencedMonster stops at the configured limit`() {
        val harness = newGame(forgeable = { true })
        harness.game.monster(10)
        harness.game.monster(11, tile = harness.game.tile(Position(1001, 1000, 7)))
        harness.game.updateForgeableMonsters()
        harness.game.featureFlags = FeatureFlags(forgeInfluencedCreaturesLimit = 1)

        assertThat(harness.game.makeInfluencedMonster()).isEqualTo(10)
        assertThat(harness.game.makeInfluencedMonster()).isZero()
        assertThat(harness.game.getInfluencedMonsters()).containsExactly(10L)
    }

    @Test
    fun `makeInfluencedMonster retries past a monster that already has a forge stack`() {
        val harness = newGame(forgeable = { false }, stacks = { 5 })
        harness.game.monster(10)
        harness.game.updateForgeableMonsters()
        harness.game.featureFlags = FeatureFlags(forgeInfluencedCreaturesLimit = 1)

        assertThat(harness.game.makeInfluencedMonster()).isZero()
        assertThat(harness.classifications).isEmpty()
    }

    @Test
    fun `makeInfluencedMonster returns zero when the drawn monster cannot be forged`() {
        val harness = newGame(forgeable = { false })
        harness.game.monster(10)
        harness.game.updateForgeableMonsters()
        harness.game.featureFlags = FeatureFlags(forgeInfluencedCreaturesLimit = 1)

        assertThat(harness.game.makeInfluencedMonster()).isZero()
        assertThat(harness.classifications).isEmpty()
    }

    // ----- makeFiendishMonster -----

    @Test
    fun `makeFiendishMonster returns zero without forgeable monsters`() {
        val harness = newGame(forgeable = { true })

        assertThat(harness.game.makeFiendishMonster()).isZero()
    }

    @Test
    fun `makeFiendishMonster returns zero when the fiendish limit is reached`() {
        val harness = newGame(forgeable = { true })
        harness.game.monster(10)
        harness.game.updateForgeableMonsters()

        assertThat(harness.game.makeFiendishMonster()).isZero()
    }

    @Test
    fun `makeFiendishMonster registers the monster and schedules the status update`() {
        val harness = newGame(forgeable = { true }, now = 5000L)
        harness.game.monster(10)
        harness.game.updateForgeableMonsters()
        harness.game.featureFlags = FeatureFlags(
            forgeFiendishCreaturesLimit = 3,
            forgeFiendishIntervalType = "hour",
            forgeFiendishIntervalTime = "2",
        )

        assertThat(harness.game.makeFiendishMonster()).isEqualTo(10)
        assertThat(harness.classifications).containsExactly(10L to ForgeClassification.Fiendish)
        assertThat(harness.configured).containsExactly(10L to 0)
        assertThat(harness.times).containsExactly(10L to 8600L)
        assertThat(harness.game.getFiendishMonsters()).containsExactly(10L)
        assertThat(harness.scheduled.map { it.first }).containsExactly(2L * 3_600_000L)
    }

    @Test
    fun `makeFiendishMonster scales the interval by second and minute types`() {
        val seconds = newGame(forgeable = { true })
        seconds.game.monster(10)
        seconds.game.updateForgeableMonsters()
        seconds.game.featureFlags = FeatureFlags(
            forgeFiendishCreaturesLimit = 3,
            forgeFiendishIntervalType = "second",
            forgeFiendishIntervalTime = "7",
        )
        seconds.game.makeFiendishMonster()

        val minutes = newGame(forgeable = { true })
        minutes.game.monster(10)
        minutes.game.updateForgeableMonsters()
        minutes.game.featureFlags = FeatureFlags(
            forgeFiendishCreaturesLimit = 3,
            forgeFiendishIntervalType = "minute",
            forgeFiendishIntervalTime = "2",
        )
        minutes.game.makeFiendishMonster()

        assertThat(seconds.scheduled.map { it.first }).containsExactly(7_000L)
        assertThat(minutes.scheduled.map { it.first }).containsExactly(120_000L)
    }

    @Test
    fun `makeFiendishMonster falls back to one hour for an unknown interval type`() {
        val harness = newGame(forgeable = { true }, now = 100L)
        harness.game.monster(10)
        harness.game.updateForgeableMonsters()
        harness.game.featureFlags = FeatureFlags(
            forgeFiendishCreaturesLimit = 3,
            forgeFiendishIntervalType = "week",
            forgeFiendishIntervalTime = "9",
        )

        harness.game.makeFiendishMonster()

        assertThat(harness.scheduled.map { it.first }).containsExactly(3_600_000L)
        assertThat(harness.times).containsExactly(10L to 3700L)
    }

    @Test
    fun `makeFiendishMonster with createForgeableMonsters rebuilds the eligible list`() {
        val harness = newGame(forgeable = { true })
        harness.game.featureFlags = FeatureFlags(forgeFiendishCreaturesLimit = 3)
        harness.game.monster(10, tile = harness.game.tile(Position(1000, 1000, 7)))
        harness.game.monster(11, tile = harness.game.tile(Position(1001, 1000, 7), noLogout = true))
        harness.game.monster(12, attachTile = false)

        assertThat(harness.game.makeFiendishMonster(0, createForgeableMonsters = true)).isEqualTo(10)
        assertThat(harness.game.getFiendishMonsters()).containsExactly(10L)
    }

    @Test
    fun `makeFiendishMonster with createForgeableMonsters drops a lost fiendish`() {
        val harness = newGame(forgeable = { true })
        harness.game.featureFlags = FeatureFlags(forgeFiendishCreaturesLimit = 3)
        val monster = harness.game.monster(10, tile = harness.game.tile())

        assertThat(harness.game.makeFiendishMonster(0, createForgeableMonsters = true)).isEqualTo(10)
        harness.game.removeMonster(monster)

        assertThat(harness.game.makeFiendishMonster(0, createForgeableMonsters = true)).isZero()
        assertThat(harness.game.getFiendishMonsters()).isEmpty()
    }

    @Test
    fun `makeFiendishMonster makes room when the limit is already full`() {
        val harness = newGame(forgeable = { true })
        harness.game.featureFlags = FeatureFlags(forgeFiendishCreaturesLimit = 1)
        harness.game.monster(10, tile = harness.game.tile(Position(1000, 1000, 7)))
        assertThat(harness.game.makeFiendishMonster(0, createForgeableMonsters = true)).isEqualTo(10)

        harness.game.canBeForgeMonster = { monster -> monster.id != 10L }
        harness.game.monster(11, tile = harness.game.tile(Position(1001, 1000, 7)))

        assertThat(harness.game.makeFiendishMonster(0, createForgeableMonsters = true)).isEqualTo(11)
        assertThat(harness.cleared).containsExactly(10L)
        assertThat(harness.game.getFiendishMonsters()).containsExactly(11L)
    }

    // ----- checkForgeEventId / removeFiendishMonster / removeInfluencedMonster -----

    @Test
    fun `checkForgeEventId stops and forgets the scheduled event`() {
        val harness = newGame(forgeable = { true })
        harness.game.monster(10)
        harness.game.updateForgeableMonsters()
        harness.game.featureFlags = FeatureFlags(forgeFiendishCreaturesLimit = 3)
        harness.game.makeFiendishMonster()

        harness.game.checkForgeEventId(10)

        assertThat(harness.stopped).containsExactly(1L)
        harness.game.checkForgeEventId(10)
        assertThat(harness.stopped).containsExactly(1L)
    }

    @Test
    fun `removeFiendishMonster stops the event and schedules a replacement`() {
        val harness = newGame(forgeable = { true })
        harness.game.monster(10)
        harness.game.updateForgeableMonsters()
        harness.game.featureFlags = FeatureFlags(forgeFiendishCreaturesLimit = 3)
        harness.game.makeFiendishMonster()

        assertThat(harness.game.removeFiendishMonster(10)).isFalse()

        assertThat(harness.game.getFiendishMonsters()).isEmpty()
        assertThat(harness.stopped).containsExactly(1L)
        assertThat(harness.scheduled.map { it.first }).containsExactly(3_600_000L, 270_000L)
    }

    @Test
    fun `removeFiendishMonster without create does not schedule a replacement`() {
        val harness = newGame(forgeable = { true })
        harness.game.monster(10)
        harness.game.updateForgeableMonsters()
        harness.game.featureFlags = FeatureFlags(forgeFiendishCreaturesLimit = 3)
        harness.game.makeFiendishMonster()

        harness.game.removeFiendishMonster(10, create = false)

        assertThat(harness.scheduled.map { it.first }).containsExactly(3_600_000L)
    }

    @Test
    fun `removeInfluencedMonster erases the id and schedules a replacement`() {
        val harness = newGame(forgeable = { true })
        harness.game.monster(10)
        harness.game.updateForgeableMonsters()
        harness.game.featureFlags = FeatureFlags(forgeInfluencedCreaturesLimit = 1)
        harness.game.makeInfluencedMonster()

        assertThat(harness.game.removeInfluencedMonster(10, create = true)).isFalse()

        assertThat(harness.game.getInfluencedMonsters()).isEmpty()
        assertThat(harness.scheduled.map { it.first }).containsExactly(10_000L)
    }

    @Test
    fun `removeInfluencedMonster without create schedules nothing`() {
        val harness = newGame(forgeable = { true })
        harness.game.monster(10)
        harness.game.updateForgeableMonsters()
        harness.game.featureFlags = FeatureFlags(forgeInfluencedCreaturesLimit = 1)
        harness.game.makeInfluencedMonster()

        harness.game.removeInfluencedMonster(10)

        assertThat(harness.scheduled).isEmpty()
    }

    @Test
    fun `removeForgeMonster dispatches by the forge classification`() {
        val harness = newGame(forgeable = { true })
        harness.game.monster(10)
        harness.game.updateForgeableMonsters()
        harness.game.featureFlags = FeatureFlags(forgeInfluencedCreaturesLimit = 1)
        harness.game.makeInfluencedMonster()

        assertThat(harness.game.removeForgeMonster(10, ForgeClassification.Influenced, create = false)).isTrue()
        assertThat(harness.game.getInfluencedMonsters()).isEmpty()

        harness.game.featureFlags = noCreation
        harness.game.updateForgeableMonsters()
        harness.game.featureFlags = FeatureFlags(forgeFiendishCreaturesLimit = 3)
        harness.game.makeFiendishMonster()
        assertThat(harness.game.removeForgeMonster(10, ForgeClassification.Fiendish, create = false)).isTrue()
        assertThat(harness.game.getFiendishMonsters()).isEmpty()

        assertThat(harness.game.removeForgeMonster(10, ForgeClassification.Normal, create = false)).isTrue()
    }

    // ----- addInfluencedMonster -----

    @Test
    fun `addInfluencedMonster rejects a null or unforgeable monster`() {
        val harness = newGame(forgeable = { false })
        val monster = harness.game.monster(10)

        assertThat(harness.game.addInfluencedMonster(null)).isFalse()
        assertThat(harness.game.addInfluencedMonster(monster)).isFalse()
        assertThat(harness.game.getInfluencedMonsters()).isEmpty()
    }

    @Test
    fun `addInfluencedMonster registers the monster with the requested stack`() {
        val harness = newGame(forgeable = { true })
        harness.game.featureFlags = FeatureFlags(forgeInfluencedCreaturesLimit = 1)
        val monster = harness.game.monster(10)

        assertThat(harness.game.addInfluencedMonster(monster, stack = 4)).isTrue()

        assertThat(harness.classifications).containsExactly(10L to ForgeClassification.Influenced)
        assertThat(harness.configured).containsExactly(10L to 4)
        assertThat(harness.game.getInfluencedMonsters()).containsExactly(10L)
    }

    @Test
    fun `addInfluencedMonster enforces the configured limit but keeps a known id`() {
        val harness = newGame(forgeable = { true })
        harness.game.featureFlags = FeatureFlags(forgeInfluencedCreaturesLimit = 1)
        val first = harness.game.monster(10)
        val second = harness.game.monster(11)

        assertThat(harness.game.addInfluencedMonster(first)).isTrue()
        assertThat(harness.game.addInfluencedMonster(second)).isFalse()
        assertThat(harness.game.addInfluencedMonster(first)).isTrue()
        assertThat(harness.game.getInfluencedMonsters()).containsExactly(10L)
    }

    @Test
    fun `addInfluencedMonster prunes ids whose monster is gone`() {
        val harness = newGame(forgeable = { true })
        harness.game.featureFlags = FeatureFlags(forgeInfluencedCreaturesLimit = 1)
        val stale = harness.game.monster(10)
        harness.game.addInfluencedMonster(stale)
        harness.game.removeMonster(stale)
        val replacement = harness.game.monster(11)

        assertThat(harness.game.addInfluencedMonster(replacement)).isTrue()
        assertThat(harness.game.getInfluencedMonsters()).containsExactly(11L)
    }

    // ----- updateForgeableMonsters -----

    @Test
    fun `updateForgeableMonsters keeps only eligible monsters on non-nologout tiles`() {
        val harness = newGame(forgeable = { monster -> monster.id != 13L })
        harness.game.monster(10, tile = harness.game.tile(Position(1000, 1000, 7)))
        harness.game.monster(11, tile = harness.game.tile(Position(1001, 1000, 7), noLogout = true))
        harness.game.monster(12, attachTile = false)
        harness.game.monster(13, tile = harness.game.tile(Position(1002, 1000, 7)))

        harness.game.updateForgeableMonsters()

        harness.game.featureFlags = FeatureFlags(forgeFiendishCreaturesLimit = 3)
        assertThat(harness.game.makeFiendishMonster()).isEqualTo(10)
    }

    @Test
    fun `updateForgeableMonsters prunes lost fiendish and influenced ids`() {
        val harness = newGame(forgeable = { true })
        harness.game.featureFlags = FeatureFlags(forgeFiendishCreaturesLimit = 3)
        val fiendish = harness.game.monster(10, tile = harness.game.tile(Position(1000, 1000, 7)))
        harness.game.makeFiendishMonster(0, createForgeableMonsters = true)

        harness.game.canBeForgeMonster = { monster -> monster.id != 10L }
        harness.game.featureFlags = noCreation
        val influenced = harness.game.monster(11, tile = harness.game.tile(Position(1001, 1000, 7)))
        harness.game.updateForgeableMonsters()
        harness.game.featureFlags = FeatureFlags(forgeInfluencedCreaturesLimit = 1)
        harness.game.makeInfluencedMonster()

        harness.game.featureFlags = noCreation
        harness.game.removeMonster(fiendish)
        harness.game.removeMonster(influenced)
        harness.game.updateForgeableMonsters()

        assertThat(harness.game.getFiendishMonsters()).isEmpty()
        assertThat(harness.game.getInfluencedMonsters()).isEmpty()
    }

    @Test
    fun `updateForgeableMonsters refills both classifications up to their limits`() {
        val harness = newGame(forgeable = { true })
        harness.game.featureFlags = FeatureFlags(forgeFiendishCreaturesLimit = 1, forgeInfluencedCreaturesLimit = 2)
        harness.game.monster(10, tile = harness.game.tile(Position(1000, 1000, 7)))
        harness.game.monster(11, tile = harness.game.tile(Position(1001, 1000, 7)))
        harness.game.monster(12, tile = harness.game.tile(Position(1002, 1000, 7)))

        harness.game.updateForgeableMonsters()

        assertThat(harness.game.getFiendishMonsters()).containsExactly(10L)
        assertThat(harness.game.getInfluencedMonsters()).containsExactly(11L, 12L)
    }

    @Test
    fun `createFiendishMonsters aborts without progress`() {
        val harness = newGame(forgeable = { false })
        harness.game.featureFlags = FeatureFlags(forgeFiendishCreaturesLimit = 2)
        harness.game.monster(10)
        harness.game.updateForgeableMonsters()

        harness.game.createFiendishMonsters()

        assertThat(harness.game.getFiendishMonsters()).isEmpty()
    }

    // ----- updateFiendishMonsterStatus -----

    @Test
    fun `updateFiendishMonsterStatus ignores an unknown monster`() {
        val harness = newGame(forgeable = { true })

        harness.game.updateFiendishMonsterStatus(404, "Rat")

        assertThat(harness.cleared).isEmpty()
        assertThat(harness.stopped).isEmpty()
    }

    @Test
    fun `updateFiendishMonsterStatus clears the status and rolls a new fiendish`() {
        val harness = newGame(forgeable = { true })
        harness.game.featureFlags = FeatureFlags(
            forgeFiendishCreaturesLimit = 1,
            forgeInfluencedCreaturesLimit = 0,
        )
        harness.game.monster(10, tile = harness.game.tile(Position(1000, 1000, 7)))
        harness.game.makeFiendishMonster(0, createForgeableMonsters = true)

        harness.game.canBeForgeMonster = { monster -> monster.id != 10L }
        harness.game.monster(11, tile = harness.game.tile(Position(1001, 1000, 7)))
        harness.game.updateForgeableMonsters()

        harness.game.updateFiendishMonsterStatus(10, "Rat")

        assertThat(harness.cleared).containsExactly(10L)
        assertThat(harness.game.getFiendishMonsters()).containsExactly(11L)
    }
}
