package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.LootBlock
import org.opentibia.canary.creatures.monsters.MonsterLoot
import org.opentibia.canary.creatures.monsters.MonsterPipelinesHost
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.monsters.RaceType
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.SpectatorMapAccess
import org.opentibia.canary.map.SpectatorSector
import org.opentibia.canary.map.Spectators

/**
 * World-wiring transcript for the wave-70 G1 death pipeline: `Game` places the
 * `Creature::onDeath` composition (creature.cpp:577-720) on every creature it
 * accepts, and a placed monster's `m_monsterType` death facts
 * (`Monster::getCorpse`/`dropLoot`/`getLostExperience`, monster.cpp:873-881,
 * 3305-3436) resolve from the registered [MonsterPipelinesHost] entry.
 *
 * The test kills a monster through `Creature::changeHealth` (creature.cpp:845)
 * and asserts the production end to end: the corpse container lands on the
 * tile with the `CORPSEOWNER` of the top damager, the loot table rolled into
 * it, the monster removed from the world and the top damager credited the
 * monster's experience share (creature.cpp:655-657).
 */
class GameDeathPipelineTest {

    private val center = Position(1000, 1000, 7)
    private lateinit var service: MonsterComputeService

    @BeforeEach
    fun setUp() {
        Spectators.clearCache()
        service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
    }

    @Test
    fun `killing a monster drops its owner-locked looted corpse and awards the top damager`() {
        val host = MonsterPipelinesHost(
            service = service,
            mapAccess = SpectatorMapAccess { _, _ -> SpectatorSector() },
            resolveCreature = { null },
            clock = { 0L },
        )
        val type = MonsterType("Rat").apply {
            info.lookcorpse = 3058
            info.race = RaceType.RACE_BLOOD
            info.experience = 100
            info.lootItems.add(LootBlock(id = 2148, chance = MonsterLoot.MAX_LOOT_CHANCE))
        }
        val items = Items().apply {
            register(ItemType(id = 3058, name = "corpse", isContainer = true, capacity = 10))
        }
        val game = Game(
            GameMap(),
            GameDispatcher(),
            itemTypes = items,
            monsterHost = host,
            monsterTypeProvider = { name -> if (name.equals("Rat", ignoreCase = true)) type else null },
        )
        game.lootRandom = { 0 }

        val tile = game.map.getOrCreateTile(center).also { it.ground = Item(id = 100) }
        val monster = Monster(id = 7, name = "Rat", maxHealth = 100, health = 100, position = center)
        assertThat(game.internalPlaceCreature(monster, center)).isTrue()

        val player = Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = center)
        game.addPlayer(player)

        monster.addDamagePoints(player, 100)
        monster.changeHealth(-monster.health)

        assertThat(monster.isRemoved()).isTrue()
        assertThat(host.entryOf(7)).isNull()
        assertThat(tile.items.map { it.id }).containsExactlyInAnyOrder(2886, 3058)

        val corpse = tile.items.single { it.id == 3058 }
        assertThat(corpse).isInstanceOf(Container::class.java)
        assertThat(corpse.getAttribute(ItemAttribute.CORPSEOWNER)).isEqualTo(1L)
        assertThat((corpse as Container).items.map { it.id }).containsExactly(2148)
        assertThat(player.experience).isEqualTo(100L)
    }

    @Test
    fun `killing a monster without a registered host entry leaves the world without a corpse`() {
        val game = Game(GameMap(), GameDispatcher())
        val tile = game.map.getOrCreateTile(center).also { it.ground = Item(id = 100) }
        val monster = Monster(id = 7, name = "Rat", maxHealth = 100, health = 100, position = center)
        assertThat(game.internalPlaceCreature(monster, center)).isTrue()

        monster.changeHealth(-monster.health)

        assertThat(monster.isRemoved()).isTrue()
        assertThat(tile.items.map { it.id }).isEmpty()
    }
}
