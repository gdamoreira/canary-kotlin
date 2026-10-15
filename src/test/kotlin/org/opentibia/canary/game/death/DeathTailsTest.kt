package org.opentibia.canary.game.death

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.MonsterDeathHost
import org.opentibia.canary.creatures.monsters.RaceType
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.monitoring.MetricsRegistry
import org.opentibia.canary.network.protocol.GameWindowWriter
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.network.protocol.TextMessageFrame

/**
 * Transcript tests for the wave-71 death tails of `Creature::dropCorpse`
 * (src/creatures/creature.cpp:786-816), the death metric counters
 * (creature.cpp:688-717) and the `MESSAGE_EXPERIENCE_OTHERS` notice of
 * `Creature::onGainExperience` (creature.cpp:1322-1327):
 *
 * - the reward-corpse/player-corpse `disallowedCorpses` gate;
 * - the `"Loot of {name}: {content}"` message with the `LOOTMESSAGE_SUFFIX` and
 *   the `getProtocolVersion() > 1200` colour gate;
 * - the `checkAutoLoot` + reachability deferred quick-loot dispatch;
 * - the corpse container `sendUpdateToClient`;
 * - the `player_death`/`monster_death` counter label sets;
 * - the position/primary/colour `MESSAGE_EXPERIENCE_OTHERS` frame.
 */
class DeathTailsTest {

    private val pos = Position(100, 100, 7)

    private class World {
        val byId = mutableMapOf<Long, Creature>()
        var pzLocked = CreatureDeathPipeline.PZ_LOCKED_DEFAULT
        val removed = mutableListOf<Creature>()
        val addedItems = mutableListOf<Pair<Tile, Item>>()
        val decayed = mutableListOf<Item>()
        val effects = mutableListOf<Pair<Position, Int>>()
        val killedMonsters = mutableListOf<Pair<Player, Monster>>()
        val deprecated = mutableListOf<Triple<Creature, Creature, Boolean>>()
        val removedAttacked = mutableListOf<Pair<Player, Creature>>()

        // --- wave-71 death tails ---
        var protocolVersion = 0
        var rewardCorpse = false
        var reachable = false
        var autoLoot = false
        val spectators = mutableListOf<Player>()
        val lootMessages = mutableListOf<Pair<Player, String>>()
        val scheduledQuickLoot = mutableListOf<Triple<Player, Container, Position>>()
        val corpseUpdates = mutableListOf<Pair<Player, Item>>()
        val experienceFrames = mutableListOf<Pair<Player, TextMessageFrame>>()
        val counters = mutableListOf<Triple<String, Double, Map<String, String>>>()

        fun pipeline(): CreatureDeathPipeline = CreatureDeathPipeline(
            resolveCreature = { id -> byId[id] },
            pzLocked = { pzLocked },
            removeCreature = { removed.add(it) },
            addMagicEffect = { position, effect -> effects.add(position to effect) },
            addItemToTile = { tile, item -> addedItems.add(tile to item); tile.addItem(item); item.setParent(tile) },
            startDecaying = { decayed.add(it) },
            onKilledMonster = { killer, monster -> killedMonsters.add(killer to monster) },
            onKilledPlayer = { _, _, _ -> false },
            deprecatedOnKilledCreature = { killer, target, lastHit ->
                deprecated.add(Triple(killer, target, lastHit))
                false
            },
            removeAttacked = { killer, target -> removedAttacked.add(killer to target) },
            getProtocolVersion = { protocolVersion },
            sendLootMessage = { player, message -> lootMessages.add(player to message) },
            isRewardCorpse = { rewardCorpse },
            isCorpseReachable = { _, _ -> reachable },
            checkAutoLoot = { _, _ -> autoLoot },
            scheduleQuickLoot = { player, container, position ->
                scheduledQuickLoot.add(Triple(player, container, position))
            },
            sendCorpseUpdate = { player, corpse -> corpseUpdates.add(player to corpse) },
            findSpectatorPlayers = { spectators.toList() },
            sendTextMessageFrame = { player, frame -> experienceFrames.add(player to frame) },
            addCounter = { name, value, attrs -> counters.add(Triple(name, value, attrs)) },
        )

        /** Same pipeline over a live [MetricsRegistry] for the counter transcript. */
        fun metricPipeline(registry: MetricsRegistry): CreatureDeathPipeline =
            CreatureDeathPipeline(
                resolveCreature = { id -> byId[id] },
                pzLocked = { pzLocked },
                removeCreature = { removed.add(it) },
                addMagicEffect = { position, effect -> effects.add(position to effect) },
                addItemToTile = { tile, item -> addedItems.add(tile to item); tile.addItem(item); item.setParent(tile) },
                startDecaying = { decayed.add(it) },
                onKilledMonster = { killer, monster -> killedMonsters.add(killer to monster) },
                onKilledPlayer = { _, _, _ -> false },
                deprecatedOnKilledCreature = { killer, target, lastHit ->
                    deprecated.add(Triple(killer, target, lastHit))
                    false
                },
                removeAttacked = { killer, target -> removedAttacked.add(killer to target) },
                addCounter = { name, value, attrs -> registry.addCounter(name, value, attrs) },
            )
    }

    private fun newWorld(): World = World()

    private fun monster(world: World, id: Long = 1, name: String = "Rat"): Monster =
        Monster(id = id, name = name, maxHealth = 100, health = 100, position = pos).also {
            world.byId[it.id] = it
        }

    private fun player(world: World, id: Long, level: Int = 1): Player =
        Player(
            id = id,
            name = "Hero$id",
            maxHealth = 150,
            health = 150,
            position = pos,
            level = level,
        ).also {
            world.byId[it.id] = it
        }

    /** A corpse container factory that stamps [suffix] and holds one plain item. */
    private fun corpseFactory(suffix: String = "", itemName: String = "gold coin"): (Int) -> Container =
        { id ->
            Container(id, 10).also { container ->
                if (suffix.isNotEmpty()) {
                    container.setAttribute(ItemAttribute.LOOTMESSAGE_SUFFIX, suffix)
                }
                container.addItem(Item(2148, 3, ItemType(id = 2148, name = itemName)))
            }
        }

    // --- the dropCorpse reward/loot tail (creature.cpp:786-816) ---

    @Test
    fun `dropCorpse sends the loot message with the suffix and the coloured content description`() {
        val world = newWorld()
        val killer = player(world, 10)
        val dying = monster(world)
        dying.setParent(Tile(pos))
        dying.addDamagePoints(killer, 300)
        world.protocolVersion = 1310
        dying.monsterDeathHost = MonsterDeathHost(
            lookCorpse = 3058,
            race = RaceType.RACE_NONE,
            createCorpse = corpseFactory(suffix = "Bonus"),
        )
        dying.deathPipeline = world.pipeline()

        dying.onDeath()

        // `collorMessage = getProtocolVersion() > 1200` (creature.cpp:789) renders
        // the `{id|name}` content description; the monster name is the
        // constructor-seeded lowercase `getNameDescription` (monster.cpp:92).
        assertThat(world.lootMessages).containsExactly(killer to "Loot of rat: {2148|gold coin} (Bonus).")
        assertThat(world.corpseUpdates.map { it.first }).containsExactly(killer)
    }

    @Test
    fun `dropCorpse renders the plain content description for an old protocol client`() {
        val world = newWorld()
        val killer = player(world, 10)
        val dying = monster(world)
        dying.setParent(Tile(pos))
        dying.addDamagePoints(killer, 300)
        world.protocolVersion = 1200
        dying.monsterDeathHost = MonsterDeathHost(
            lookCorpse = 3058,
            race = RaceType.RACE_NONE,
            createCorpse = corpseFactory(),
        )
        dying.deathPipeline = world.pipeline()

        dying.onDeath()

        assertThat(world.lootMessages).containsExactly(killer to "Loot of rat: gold coin.")
    }

    @Test
    fun `dropCorpse skips the loot tail for a reward corpse`() {
        val world = newWorld()
        val killer = player(world, 10)
        val dying = monster(world)
        dying.setParent(Tile(pos))
        dying.addDamagePoints(killer, 300)
        world.rewardCorpse = true
        world.autoLoot = true
        world.reachable = true
        dying.monsterDeathHost = MonsterDeathHost(
            lookCorpse = 3058,
            race = RaceType.RACE_NONE,
            createCorpse = corpseFactory(),
        )
        dying.deathPipeline = world.pipeline()

        dying.onDeath()

        assertThat(world.lootMessages).isEmpty()
        assertThat(world.scheduledQuickLoot).isEmpty()
        assertThat(world.corpseUpdates).isEmpty()
    }

    @Test
    fun `dropCorpse skips the loot message for a reward boss but still updates the container`() {
        val world = newWorld()
        val killer = player(world, 10)
        val dying = monster(world)
        dying.setParent(Tile(pos))
        dying.addDamagePoints(killer, 300)
        dying.monsterDeathHost = MonsterDeathHost(
            lookCorpse = 3058,
            race = RaceType.RACE_NONE,
            isRewardBoss = true,
            createCorpse = corpseFactory(),
        )
        dying.deathPipeline = world.pipeline()

        dying.onDeath()

        assertThat(world.lootMessages).isEmpty()
        assertThat(world.corpseUpdates.map { it.first }).containsExactly(killer)
    }

    @Test
    fun `dropCorpse defers the quick loot when auto loot is enabled and the corpse is reachable`() {
        val world = newWorld()
        val killer = player(world, 10)
        val dying = monster(world)
        dying.setParent(Tile(pos))
        dying.addDamagePoints(killer, 300)
        world.autoLoot = true
        world.reachable = true
        dying.monsterDeathHost = MonsterDeathHost(
            lookCorpse = 3058,
            race = RaceType.RACE_NONE,
            createCorpse = corpseFactory(),
        )
        dying.deathPipeline = world.pipeline()

        dying.onDeath()

        val scheduled = world.scheduledQuickLoot.single()
        assertThat(scheduled.first).isSameAs(killer)
        assertThat(scheduled.second).isSameAs(world.addedItems.single().second.getContainer())
        assertThat(scheduled.third).isEqualTo(pos)
    }

    @Test
    fun `dropCorpse keeps the quick loot deferred when the corpse is unreachable`() {
        val world = newWorld()
        val killer = player(world, 10)
        val dying = monster(world)
        dying.setParent(Tile(pos))
        dying.addDamagePoints(killer, 300)
        world.autoLoot = true
        world.reachable = false
        dying.monsterDeathHost = MonsterDeathHost(
            lookCorpse = 3058,
            race = RaceType.RACE_NONE,
            createCorpse = corpseFactory(),
        )
        dying.deathPipeline = world.pipeline()

        dying.onDeath()

        assertThat(world.scheduledQuickLoot).isEmpty()
        assertThat(world.corpseUpdates).hasSize(1)
    }

    // --- the death metric counters (creature.cpp:688-717) ---

    @Test
    fun `a monster death records the monster_death counter with the C++ labels`() {
        val world = newWorld()
        val killer = player(world, 10)
        val dying = monster(world)
        dying.addDamagePoints(killer, 300)

        val registry = MetricsRegistry()
        dying.deathPipeline = world.metricPipeline(registry)

        dying.onDeath()

        assertThat(registry.scrape()).contains(
            """monster_death_total{by_player="1",is_summon="0",killer="Hero10",name="Rat"} 1""",
        )
    }

    @Test
    fun `a player death records the player_death counter with the C++ labels`() {
        val world = newWorld()
        val killer = player(world, 11)
        val victim = player(world, 10, level = 42)
        victim.addDamagePoints(killer, 300)

        val registry = MetricsRegistry()
        victim.deathPipeline = world.metricPipeline(registry)

        victim.onDeath()

        assertThat(registry.scrape()).contains(
            """player_death_total{by_player="1",last_hit_creature="Hero11",last_hit_unjustified="0",""" +
                """level="42",most_damage_creature="Hero11",most_damage_creature_master="(none)",""" +
                """most_damage_dealt="300",most_damage_unjustified="0",name="Hero10"} 1""",
        )
    }

    @Test
    fun `a summon master death records the monster_death master labels`() {
        val world = newWorld()
        val master = player(world, 10)
        val summon = monster(world, id = 20, name = "Summon")
        summon.setMaster(master)
        val dying = monster(world)
        dying.setMaster(master)
        dying.addDamagePoints(summon, 300)

        val registry = MetricsRegistry()
        dying.deathPipeline = world.metricPipeline(registry)

        dying.onDeath()

        assertThat(registry.scrape()).contains(
            """monster_death_total{by_player="1",is_summon="1",killer="Hero10",name="Rat"} 1""",
        )
    }

    // --- the monster name description (monster.cpp:92, 167-178) ---

    @Test
    fun `a monster name description seeds lowercase and keeps an explicit override`() {
        val world = newWorld()
        val seeded = monster(world)
        seeded.setParent(Tile(pos))
        seeded.monsterDeathHost = MonsterDeathHost(nameDescription = "A Rat")

        assertThat(seeded.getNameDescription()).isEqualTo("a rat")

        seeded.setNameDescription("Bossy Rat")

        assertThat(seeded.getNameDescription()).isEqualTo("Bossy Rat")
    }

    // --- the MESSAGE_EXPERIENCE_OTHERS notice (creature.cpp:1322-1327) ---
    @Test
    fun `a summon gain notifies the position spectators with the halved experience`() {
        val world = newWorld()
        val master = player(world, 10)
        val summon = monster(world, id = 20, name = "Rat")
        summon.setMaster(master)
        val dying = monster(world)
        dying.monsterDeathHost = MonsterDeathHost(experience = 100)
        dying.addDamagePoints(summon, 100)
        world.spectators += player(world, 30)
        dying.deathPipeline = world.pipeline()

        dying.onDeath()

        val frame = world.experienceFrames.single().second
        assertThat(world.experienceFrames.single().first).isSameAs(world.spectators.single())
        assertThat(frame.type).isEqualTo(MessageClass.MESSAGE_EXPERIENCE_OTHERS)
        assertThat(frame.text).isEqualTo("Rat gained 50 experience points.")
        assertThat(frame.position).isEqualTo(pos)
        assertThat(frame.primary.value).isEqualTo(50L)
        assertThat(frame.primary.color).isEqualTo(GameWindowWriter.TEXTCOLOR_WHITE_EXP)
    }

    @Test
    fun `a familiar gain skips the spectator notice`() {
        val world = newWorld()
        val master = player(world, 10)
        val familiar = monster(world, id = 20, name = "Familiar")
        familiar.setMaster(master)
        familiar.monsterDeathHost = MonsterDeathHost(isFamiliar = true)
        val dying = monster(world)
        dying.monsterDeathHost = MonsterDeathHost(experience = 100)
        dying.addDamagePoints(familiar, 100)
        world.spectators += player(world, 30)
        dying.deathPipeline = world.pipeline()

        dying.onDeath()

        assertThat(world.experienceFrames).isEmpty()
    }
}
