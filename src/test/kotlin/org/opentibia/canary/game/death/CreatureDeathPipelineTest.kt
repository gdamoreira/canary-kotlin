package org.opentibia.canary.game.death

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.creatures.monsters.LootBlock
import org.opentibia.canary.creatures.monsters.MonsterDeathHost
import org.opentibia.canary.creatures.monsters.MonsterLoot
import org.opentibia.canary.creatures.monsters.MonsterLootDropRequest
import org.opentibia.canary.creatures.monsters.RaceType
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile

/**
 * Transcript tests for the `Creature::onDeath` world pipeline
 * (src/creatures/creature.cpp:579-720) and the `Creature::dropCorpse`
 * corpse/loot branch it owns (creature.cpp:732-828):
 *
 * - last-hit resolution through `g_game().getCreatureByID(lastHitCreatureId)`
 *   (creature.cpp:583);
 * - most-damage resolution over the `damageMap` inside the `PZ_LOCKED` window
 *   (creature.cpp:600-607);
 * - the per-attacker experience map with the shared-party leader resolution
 *   (creature.cpp:609-663);
 * - the corpse `CORPSEOWNER` stamp and the loot roll
 *   (monster.cpp:3305-3320, 3415-3436);
 * - the `CreatureEvent::executeOnDeath` payload (creature.cpp:819-824).
 */
class CreatureDeathPipelineTest {

    private val pos = Position(100, 100, 7)

    private class DeathPayload(
        val creature: Creature,
        val corpse: Item?,
        val killer: Creature?,
        val mostDamageKiller: Creature?,
        val lastHitUnjustified: Boolean,
        val mostDamageUnjustified: Boolean,
    )

    private class World {
        val byId = mutableMapOf<Long, Creature>()
        var pzLocked = CreatureDeathPipeline.PZ_LOCKED_DEFAULT
        val events = mutableListOf<DeathPayload>()
        val removed = mutableListOf<Creature>()
        val killedMonsters = mutableListOf<Pair<Player, Monster>>()
        val deprecated = mutableListOf<Triple<Creature, Creature, Boolean>>()
        val removedAttacked = mutableListOf<Pair<Player, Creature>>()
        val addedItems = mutableListOf<Pair<Tile, Item>>()
        val decayed = mutableListOf<Item>()
        val effects = mutableListOf<Pair<Position, Int>>()
        var deprecatedResult = false
        var onKilledPlayerResult = false

        fun pipeline(): CreatureDeathPipeline = CreatureDeathPipeline(
            resolveCreature = { id -> byId[id] },
            pzLocked = { pzLocked },
            removeCreature = { creature -> removed.add(creature) },
            addMagicEffect = { position, effect -> effects.add(position to effect) },
            addItemToTile = { tile, item -> addedItems.add(tile to item); tile.addItem(item) },
            startDecaying = { item -> decayed.add(item) },
            onKilledMonster = { killer, monster -> killedMonsters.add(killer to monster) },
            onKilledPlayer = { _, _, _ -> onKilledPlayerResult },
            deprecatedOnKilledCreature = { killer, target, lastHit ->
                deprecated.add(Triple(killer, target, lastHit))
                deprecatedResult
            },
            removeAttacked = { killer, target -> removedAttacked.add(killer to target) },
        )
    }

    private fun newWorld(): World = World()

    private fun monster(world: World, id: Long = 1, health: Int = 100): Monster =
        Monster(id = id, name = "Rat", maxHealth = health, health = health, position = pos).also {
            world.byId[it.id] = it
        }

    private fun player(world: World, id: Long, experience: Long = 0, level: Int = 1): Player =
        Player(
            id = id,
            name = "Hero$id",
            maxHealth = 150,
            health = 150,
            position = pos,
            experience = experience,
            level = level,
        ).also {
            world.byId[it.id] = it
        }

    private fun Monster.captureEvents(world: World) {
        onDeathEventsHost = { creature, corpse, killer, mostDamage, lastHitUnjustified, mostDamageUnjustified ->
            world.events.add(
                DeathPayload(creature, corpse, killer, mostDamage, lastHitUnjustified, mostDamageUnjustified)
            )
        }
    }

    // --- last-hit and most-damage resolution ---

    @Test
    fun `onDeath resolves the last hit and the biggest damage total into the death payload`() {
        val world = newWorld()
        val lastHit = player(world, 10)
        val topDamage = player(world, 11)
        val dying = monster(world)
        dying.captureEvents(world)
        dying.addDamagePoints(topDamage, 300)
        dying.addDamagePoints(lastHit, 100)
        dying.deathPipeline = world.pipeline()

        dying.onDeath()

        assertThat(dying.lastHitCreatureId).isEqualTo(10L)
        assertThat(world.events).hasSize(1)
        val payload = world.events.single()
        assertThat(payload.creature).isSameAs(dying)
        assertThat(payload.killer).isSameAs(lastHit)
        assertThat(payload.mostDamageKiller).isSameAs(topDamage)
    }

    @Test
    fun `most damage resolution ignores damage older than the pz locked window`() {
        val world = newWorld()
        val stale = player(world, 10)
        val fresh = player(world, 11)
        val dying = monster(world)
        dying.captureEvents(world)
        dying.now = { 0L }
        dying.addDamagePoints(stale, 900)
        dying.now = { 100_000L }
        dying.addDamagePoints(fresh, 100)
        world.pzLocked = 60_000
        dying.deathPipeline = world.pipeline()

        dying.onDeath()

        assertThat(world.events.single().mostDamageKiller).isSameAs(fresh)
    }

    @Test
    fun `most damage resolution reports the unjustified flag from the kill hook`() {
        val world = newWorld()
        val lastHit = player(world, 10)
        val topDamage = player(world, 11)
        val dying = monster(world)
        dying.captureEvents(world)
        dying.addDamagePoints(topDamage, 300)
        dying.addDamagePoints(lastHit, 100)
        world.deprecatedResult = true
        dying.deathPipeline = world.pipeline()

        dying.onDeath()

        val payload = world.events.single()
        assertThat(payload.lastHitUnjustified).isFalse
        assertThat(payload.mostDamageUnjustified).isTrue
        assertThat(world.deprecated).containsExactly(Triple(topDamage, dying, false))
    }

    @Test
    fun `the damage map is resolved through the world creature lookup`() {
        val world = newWorld()
        val removed = player(world, 10)
        val dying = monster(world)
        dying.captureEvents(world)
        dying.now = { 0L }
        dying.addDamagePoints(removed, 400)
        dying.now = { 100_000L }
        world.byId.remove(10L)
        dying.deathPipeline = world.pipeline()

        dying.onDeath()

        assertThat(world.events.single().mostDamageKiller).isNull()
        assertThat(world.events.single().killer).isNull()
    }

    // --- experience map ---

    @Test
    fun `onDeath awards every attacker its damage ratio share of the lost experience`() {
        val world = newWorld()
        val topDamage = player(world, 10)
        val other = player(world, 11)
        val dying = monster(world)
        dying.captureEvents(world)
        dying.monsterDeathHost = MonsterDeathHost(experience = 100)
        dying.addDamagePoints(topDamage, 800)
        dying.addDamagePoints(other, 200)
        dying.deathPipeline = world.pipeline()

        dying.onDeath()

        assertThat(topDamage.experience).isEqualTo(80L)
        assertThat(other.experience).isEqualTo(20L)
    }

    @Test
    fun `onDeath routes the experience to the party leader under shared experience`() {
        val world = newWorld()
        val leader = player(world, 10)
        val member = player(world, 11)
        val party = Party(leader)
        party.invitePlayer(member)
        assertThat(party.joinParty(member)).isTrue()
        party.updatePlayerTicks(member, 1)
        party.updatePlayerTicks(leader, 1)
        assertThat(party.setSharedExperience(leader, true)).isTrue()
        assertThat(party.isSharedExperienceEnabled()).isTrue

        val dying = monster(world)
        dying.captureEvents(world)
        dying.monsterDeathHost = MonsterDeathHost(experience = 100)
        dying.addDamagePoints(member, 100)
        dying.deathPipeline = world.pipeline()

        dying.onDeath()

        // `Party::shareExperience` splits the leader-resolved gain across the party.
        assertThat(member.experience).isEqualTo(100L)
        assertThat(leader.experience).isEqualTo(100L)
        assertThat(world.killedMonsters.map { it.first }).containsExactlyInAnyOrder(member, leader)
    }

    // --- corpse, loot and owner ---

    @Test
    fun `dropCorpse places the looted corpse with the most damage owner and decays it`() {
        val world = newWorld()
        val topDamage = player(world, 10)
        val other = player(world, 11)
        val dying = monster(world)
        dying.captureEvents(world)
        val tile = Tile(pos)
        dying.setParent(tile)
        dying.addDamagePoints(topDamage, 300)
        dying.addDamagePoints(other, 100)
        dying.monsterDeathHost = MonsterDeathHost(
            lookCorpse = 3058,
            race = RaceType.RACE_BLOOD,
            createCorpse = { Container(it, 10) },
            lootRequest = {
                MonsterLootDropRequest(lootItems = listOf(LootBlock(id = 2148, chance = MonsterLoot.MAX_LOOT_CHANCE)))
            },
            nextRoll = { 0 },
        )
        dying.deathPipeline = world.pipeline()

        dying.onDeath()

        val corpse = world.addedItems.single { it.second.id == 3058 }.second as Container
        assertThat(corpse.getAttribute(ItemAttribute.CORPSEOWNER)).isEqualTo(10L)
        assertThat(corpse.items.map { it.id }).containsExactly(2148)

        val splash = world.addedItems.single { it.second.id == MonsterDeathHostConstants.ITEM_FULLSPLASH }.second
        assertThat(splash.count).isEqualTo(MonsterDeathHostConstants.FLUID_BLOOD)
        assertThat(tile.items.map { it.id }).containsExactly(
            MonsterDeathHostConstants.ITEM_FULLSPLASH,
            3058,
        )
        assertThat(world.decayed).containsExactly(splash, corpse)
        assertThat(world.events.single().corpse).isSameAs(corpse)
        assertThat(world.removed).containsExactly(dying)
    }

    @Test
    fun `dropCorpse stamps the most damage summon's master player as the owner`() {
        val world = newWorld()
        val master = player(world, 10)
        val summon = monster(world, id = 20)
        summon.setMaster(master)
        val dying = monster(world)
        dying.captureEvents(world)
        dying.setParent(Tile(pos))
        dying.addDamagePoints(summon, 500)
        dying.monsterDeathHost = MonsterDeathHost(
            lookCorpse = 3058,
            race = RaceType.RACE_NONE,
            createCorpse = { Container(it, 10) },
        )
        dying.deathPipeline = world.pipeline()

        dying.onDeath()

        val corpse = world.addedItems.single().second
        assertThat(corpse.getAttribute(ItemAttribute.CORPSEOWNER)).isEqualTo(10L)
    }

    @Test
    fun `a no-loot monster puffs without a corpse and still fires its events when mastered`() {
        val world = newWorld()
        val master = player(world, 10)
        val dying = monster(world)
        dying.captureEvents(world)
        dying.setMaster(master)
        dying.setDropLoot(false)
        dying.deathPipeline = world.pipeline()

        dying.onDeath()

        assertThat(world.effects).containsExactly(pos to CreatureDeathPipelineConstants.CONST_ME_POFF)
        assertThat(world.addedItems).isEmpty()
        assertThat(world.events.single().corpse).isNull()
    }

    @Test
    fun `Monster death runs the monster death body after the corpse drop`() {
        val world = newWorld()
        val lastHit = player(world, 10)
        val dying = monster(world)
        var died = false
        dying.monsterDeathHost = MonsterDeathHost(die = { monster, killer ->
            died = true
            assertThat(monster).isSameAs(dying)
            assertThat(killer).isSameAs(lastHit)
        })
        dying.addDamagePoints(lastHit, 100)
        dying.deathPipeline = world.pipeline()

        dying.onDeath()

        assertThat(died).isTrue
    }

    /** Constants shared with the production pipeline (monster.cpp splash table). */
    private object MonsterDeathHostConstants {
        const val ITEM_FULLSPLASH = 2886
        const val FLUID_BLOOD = 5
    }

    private object CreatureDeathPipelineConstants {
        const val CONST_ME_POFF = 3
    }
}
