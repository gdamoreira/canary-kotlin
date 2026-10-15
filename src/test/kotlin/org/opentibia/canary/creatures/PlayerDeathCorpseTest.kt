package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.players.ITEM_FEMALE_CORPSE
import org.opentibia.canary.creatures.players.ITEM_MALE_CORPSE
import org.opentibia.canary.creatures.players.components.PlayerSex_t
import org.opentibia.canary.creatures.players.lastHitIsPlayer
import org.opentibia.canary.game.death.CreatureDeathPipeline
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.ScriptingDispatch
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Transcript tests for the player death tail of the wave-71 H2 workstream:
 *
 * - `Creature::getLookCorpse`/`Creature::getCorpse` (creature.cpp:838-843);
 * - `Player::getLookCorpse` (player.cpp:1383-1388);
 * - `Player::getCorpse` and its killer description (player.cpp:4372-4431);
 * - `Player::dropCorpse`'s PvP short-circuit (player.cpp:4364-4370);
 * - `Creature::deprecatedOnKilledCreature`'s kill-event loop and master
 *   forwarding (creature.cpp:1290-1303).
 */
class PlayerDeathCorpseTest {

    private val pos = Position(100, 100, 7)

    private fun player(id: Long, name: String = "Hero$id", sex: PlayerSex_t = PlayerSex_t.MALE): Player =
        Player(
            id = id,
            name = name,
            maxHealth = 150,
            health = 150,
            position = pos,
        ).also { it.setSex(sex) }

    private fun containerFactory(): (Int) -> Container = { id -> Container(id, 10) }

    /** Minimal concrete [Creature] with a controllable `getLookCorpse`. */
    private class RecordingCreature(
        id: Long,
        name: String,
        private val lookCorpse: Int,
        position: Position,
    ) : Creature(id, name, maxHealth = 100, health = 100, position = position) {
        override fun getLookCorpse(): Int = lookCorpse
    }

    // --- the corpse item and description (creature.cpp:838-843, player.cpp:4372-4431) ---

    @Test
    fun `Creature getCorpse creates the look corpse through the item factory`() {
        val plain = RecordingCreature(id = 1, name = "Thing", lookCorpse = 0, position = pos)
        plain.corpseItemFactory = { id -> Item(id, 1, ItemType(id = id, name = "corpse")) }

        assertThat(plain.getCorpse(null, null)).isNull()

        val corpseSource = RecordingCreature(id = 2, name = "Other", lookCorpse = 3058, position = pos)
        corpseSource.corpseItemFactory = { id -> Item(id, 1, ItemType(id = id, name = "corpse")) }

        assertThat(corpseSource.getCorpse(null, null)?.id).isEqualTo(3058)
    }

    @Test
    fun `Player getLookCorpse picks the male or female corpse`() {
        assertThat(player(1, sex = PlayerSex_t.MALE).getLookCorpse())
            .isEqualTo(ITEM_MALE_CORPSE)
        assertThat(player(2, sex = PlayerSex_t.FEMALE).getLookCorpse())
            .isEqualTo(ITEM_FEMALE_CORPSE)
    }

    @Test
    fun `Player getCorpse stamps the killer description with the master and unlisted others`() {
        // player.cpp:4382-4388: `lastHitCreature->getPlayer()` is null for a
        // summon, so the responsible name falls back to its player master.
        val victim = player(10, name = "Victim")
        victim.corpseItemFactory = containerFactory()
        val killer = player(11, name = "Villain")
        val summon = Monster(id = 20, name = "Demon", maxHealth = 100, health = 100, position = pos)
        summon.setMaster(killer)
        victim.addDamagePoints(summon, 30)
        victim.creatureByIdProvider = { id -> if (id == 20L) summon else null }

        val corpse = victim.getCorpse(summon, killer)

        assertThat(corpse?.id).isEqualTo(ITEM_MALE_CORPSE)
        assertThat(corpse?.getAttribute(ItemAttribute.DESCRIPTION))
            .isEqualTo("You recognize Victim. he was killed by Villain and others.")
    }

    @Test
    fun `Player getCorpse names both responsible killers`() {
        // player.cpp:4392-4404: the most-damage player takes the leading name
        // and the previous name becomes the secondary one.
        val victim = player(10, name = "Victim")
        victim.corpseItemFactory = containerFactory()
        val first = player(11, name = "First")
        val second = player(12, name = "Second")

        val corpse = victim.getCorpse(first, second)

        assertThat(corpse?.getAttribute(ItemAttribute.DESCRIPTION))
            .isEqualTo("You recognize Victim. he was killed by Second and First.")
    }

    @Test
    fun `Player getCorpse reports unidentified attackers without a killer`() {
        val victim = player(10, name = "Victim")
        victim.corpseItemFactory = containerFactory()

        val corpse = victim.getCorpse(null, null)

        assertThat(corpse?.getAttribute(ItemAttribute.DESCRIPTION))
            .isEqualTo("You recognize Victim. No attackers were identified.")
    }

    // --- the PvP short-circuit (player.cpp:4364-4370) ---

    private class RecordingWorld {
        val addedItems = mutableListOf<Item>()

        fun pipeline(): CreatureDeathPipeline = CreatureDeathPipeline(
            addItemToTile = { _, item -> addedItems.add(item) },
        )
    }

    private fun pvpTile(): Tile = Tile(pos).also { it.setFlag(TileFlagsVALUE_PVPZONE) }

    @Test
    fun `Player dropCorpse short-circuits a PvP player kill`() {
        // player.cpp:4364-4370: `ZONE_PVP` + `lastHitIsPlayer` keeps the loot
        // and returns false without dropping a corpse.
        val world = RecordingWorld()
        val victim = player(10, name = "Victim")
        victim.setParent(pvpTile())
        victim.corpseItemFactory = containerFactory()
        victim.deathPipeline = world.pipeline()
        victim.setDropLoot(false)
        val killer = player(11, name = "Villain")

        val dropped = victim.dropCorpse(killer, killer, false, false)

        assertThat(dropped).isFalse()
        assertThat(victim.lootDrop).isTrue()
        assertThat(world.addedItems).isEmpty()
    }

    @Test
    fun `Player dropCorpse delegates to the base body outside a PvP kill`() {
        val world = RecordingWorld()
        val victim = player(10, name = "Victim")
        victim.setParent(Tile(pos))
        victim.corpseItemFactory = containerFactory()
        victim.deathPipeline = world.pipeline()
        val killer = player(11, name = "Villain")

        val dropped = victim.dropCorpse(killer, killer, false, false)

        assertThat(dropped).isTrue()
        assertThat(world.addedItems.map { it.id }).contains(ITEM_MALE_CORPSE)
    }

    @Test
    fun `lastHitIsPlayer resolves a summon master`() {
        val killer = player(11)
        val summon = Monster(id = 20, name = "Demon", maxHealth = 100, health = 100, position = pos)
        summon.setMaster(killer)

        assertThat(lastHitIsPlayer(summon)).isTrue()
        assertThat(lastHitIsPlayer(null)).isFalse()
    }

    // --- the kill-event loop (creature.cpp:1290-1303) ---

    @Test
    fun `deprecatedOnKilledCreature fires the master hook before the summon hook`() {
        val master = player(10, name = "Master")
        val summon = Monster(id = 20, name = "Demon", maxHealth = 100, health = 100, position = pos)
        summon.setMaster(master)
        val target = player(11, name = "Victim")
        val calls = mutableListOf<String>()
        master.onKillEventsHost = { creature, victim, lastHit ->
            calls.add("master:${creature.name}:${victim.name}:$lastHit")
        }
        summon.onKillEventsHost = { creature, victim, lastHit ->
            calls.add("summon:${creature.name}:${victim.name}:$lastHit")
        }

        val result = summon.deprecatedOnKilledCreature(target, true)

        assertThat(result).isFalse()
        assertThat(calls).containsExactly(
            "master:Master:Victim:true",
            "summon:Demon:Victim:true",
        )
    }

    @Test
    fun `deprecatedOnKilledCreature fires the attached Lua kill event with the killer`() {
        val environment = LuaEnvironment().apply { initState() }
        environment.loadChunk(
            """
            local kill = CreatureEvent("PlayerKillLoop")
            function kill.onKill(creature, target, lastHit)
                seen_kill_killer = creature:getName()
                seen_kill_target = target:getName()
                seen_kill_last_hit = lastHit
            end
            kill:register()

            local other = CreatureEvent("PlayerThinkLoop")
            function other.onThink(creature, interval)
                seen_kill_wrong_event = creature:getName()
            end
            other:register()
            """.trimIndent(),
        )
        val dispatcher = GameDispatcher().apply { start() }
        try {
            val killer = player(10, name = "Killer")
            val target = player(11, name = "Victim")
            killer.creatureEventRegistered = { name -> environment.creatureEvents.get(name) != null }
            assertThat(killer.registerCreatureEvent("PlayerKillLoop")).isTrue()
            assertThat(killer.registerCreatureEvent("PlayerThinkLoop")).isTrue()

            val scripting = ScriptingDispatch(environment, dispatcher, creatureEventsOf = { it.getCreatureEvents() })
            killer.onKillEventsHost = { creature, victim, lastHit ->
                creature.getCreatureEvents()
                    .filter { name -> environment.creatureEvents.get(name)?.eventType == KILL_EVENT_TYPE }
                    .forEach { name -> scripting.creatureEventOnKill(name, creature, victim, lastHit) }
            }

            assertThat(killer.deprecatedOnKilledCreature(target, false)).isFalse

            val ran = CountDownLatch(1)
            dispatcher.dispatch { ran.countDown() }
            assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()

            assertThat(environment.getGlobal("seen_kill_killer").tojstring()).isEqualTo("Killer")
            assertThat(environment.getGlobal("seen_kill_target").tojstring()).isEqualTo("Victim")
            assertThat(environment.getGlobal("seen_kill_last_hit").toboolean()).isFalse()
            assertThat(environment.getGlobal("seen_kill_wrong_event").isnil()).isTrue()
        } finally {
            dispatcher.shutdown()
        }
    }

    private companion object {
        /** `TileFlags::TILESTATE_PVPZONE` (items_definitions.hpp:452). */
        const val TileFlagsVALUE_PVPZONE = 1 shl 10

        /** `CreatureEvent::getEventType` kill name (creatureevent.cpp:163). */
        const val KILL_EVENT_TYPE = "kill"
    }
}
