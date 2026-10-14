package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionInvisible
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the extended `Creature` state added by wave 27 task 1:
 * outfit/light/speed, combat event hooks, master/summon linkage, spectator
 * visibility and creature icons.
 *
 * Every expectation cites the exact Canary source it transcribes
 * (`../.projects/canary`).
 */
class CreatureStateAndHooksTest {

    private fun monster(
        id: Long,
        maxHealth: Int = 100,
        health: Int = maxHealth,
        speed: Int = 200,
        position: Position = Position(100, 100, 7),
    ) = Monster(
        id = id,
        name = "Rat $id",
        maxHealth = maxHealth,
        health = health,
        speed = speed,
        position = position,
    )

    /** Direct [Creature] subclass so the open hooks can be observed. */
    private class ProbeCreature(
        id: Long,
        maxHealth: Int = 100,
        health: Int = maxHealth,
        speed: Int = 200,
        position: Position = Position(100, 100, 7),
    ) : Creature(id, "Probe $id", maxHealth, health, 0, 0, speed, position) {
        var deathCalls: Int = 0
            private set

        var iconChanges: Int = 0
            private set

        override fun death(lastHitCreature: Creature?) {
            deathCalls++
        }

        override fun iconChanged() {
            iconChanges++
        }
    }

    // --- canSee (creature.cpp:68-98, map_const.hpp:12-19) -------------------------

    @Test
    fun `canSee hides underground positions from surface creatures`() {
        // creature.cpp:70-75: ground level and above (7 -> 0) never sees z > 7.
        assertThat(Creature.canSee(Position(100, 100, 7), Position(100, 100, 7), 11, 11)).isTrue()
        assertThat(Creature.canSee(Position(100, 100, 7), Position(100, 100, 8), 11, 11)).isFalse()
        assertThat(Creature.canSee(Position(100, 100, 0), Position(100, 100, 7), 11, 11)).isTrue()
    }

    @Test
    fun `canSee limits underground view to two floors`() {
        // creature.cpp:76-82: underground view is +/- MAP_LAYER_VIEW_LIMIT (2).
        assertThat(Creature.canSee(Position(100, 100, 8), Position(100, 100, 6), 11, 11)).isTrue()
        assertThat(Creature.canSee(Position(100, 100, 8), Position(100, 100, 10), 11, 11)).isTrue()
        assertThat(Creature.canSee(Position(100, 100, 8), Position(100, 100, 5), 11, 11)).isFalse()
        assertThat(Creature.canSee(Position(100, 100, 8), Position(100, 100, 11), 11, 11)).isFalse()
    }

    @Test
    fun `canSee shifts the window by the z offset`() {
        // creature.cpp:84-86: offsetz widens the window in x and y.
        assertThat(Creature.canSee(Position(100, 100, 6), Position(112, 112, 5), 11, 11)).isTrue()
        assertThat(Creature.canSee(Position(100, 100, 6), Position(113, 112, 5), 11, 11)).isFalse()
        assertThat(Creature.canSee(Position(100, 100, 6), Position(90, 90, 5), 11, 11)).isTrue()
        assertThat(Creature.canSee(Position(100, 100, 6), Position(89, 90, 5), 11, 11)).isFalse()
    }

    @Test
    fun `instance canSee uses the map max view port`() {
        // creature.cpp:89-91: MAP_MAX_VIEW_PORT_X/Y = 11 (map_const.hpp:12-15).
        val creature = monster(1, position = Position(100, 100, 7))
        assertThat(creature.canSee(Position(111, 106, 7))).isTrue()
        assertThat(creature.canSee(Position(112, 100, 7))).isFalse()
        assertThat(creature.canSee(Position(100, 111, 7))).isTrue()
        assertThat(creature.canSee(Position(100, 112, 7))).isFalse()
    }

    @Test
    fun `canSeeCreature hides invisible creatures when sight is not granted`() {
        // creature.cpp:93-98: without canSeeInvisibility an invisible creature is hidden.
        val observer = monster(1)
        val hidden = monster(2)
        hidden.addCondition(
            ConditionInvisible(
                ConditionId.CONDITIONID_COMBAT,
                ConditionType.CONDITION_INVISIBLE,
                ticks = 10000,
                now = { 0 },
            ),
        )

        assertThat(hidden.isInvisible()).isTrue()
        assertThat(observer.canSeeCreature(hidden)).isFalse()
        assertThat(observer.canSeeCreature(monster(3))).isTrue()

        // Player::canSeeInvisibility (player.cpp:12989-12991) grants sight.
        val player = Player(
            id = 4,
            name = "Seer",
            maxHealth = 150,
            health = 150,
            position = Position(100, 100, 7),
        )
        player.setFlag(PlayerFlags_t.CanSenseInvisibility)
        assertThat(player.canSeeInvisibility()).isTrue()
        assertThat(player.canSeeCreature(hidden)).isTrue()
    }

    @Test
    fun `player canSeeCreature hides ghosts from players without access`() {
        // player.cpp:1409-1423: a ghost creature is hidden unless group->access.
        val observer = Player(
            id = 1,
            name = "Watcher",
            maxHealth = 150,
            health = 150,
            position = Position(100, 100, 7),
        )
        val ghost = Player(
            id = 2,
            name = "Ghost",
            maxHealth = 150,
            health = 150,
            position = Position(100, 100, 7),
        ).apply { switchGhostMode() }

        assertThat(observer.canSeeCreature(ghost)).isFalse()

        observer.group = Group(name = "GM", access = true)
        assertThat(observer.canSeeCreature(ghost)).isTrue()
    }

    @Test
    fun `player canSeeCreature always sees itself even in ghost mode`() {
        // player.cpp:1410-1412: `creature.get() == this` returns true first.
        val player = Player(
            id = 1,
            name = "Ghost",
            maxHealth = 150,
            health = 150,
            position = Position(100, 100, 7),
        )
        player.switchGhostMode()

        assertThat(player.canSeeCreature(player)).isTrue()
    }

    @Test
    fun `player canSeeCreature hides invisible monsters but not invisible players`() {
        // player.cpp:1418-1420: the invisibility check is skipped when the
        // creature is a player (`!creature->getPlayer()` fails).
        val observer = Player(
            id = 1,
            name = "Watcher",
            maxHealth = 150,
            health = 150,
            position = Position(100, 100, 7),
        )
        val invisibleMonster = monster(2)
        invisibleMonster.addCondition(
            ConditionInvisible(
                ConditionId.CONDITIONID_COMBAT,
                ConditionType.CONDITION_INVISIBLE,
                ticks = 10000,
                now = { 0 },
            ),
        )
        val invisiblePlayer = Player(
            id = 3,
            name = "Shade",
            maxHealth = 150,
            health = 150,
            position = Position(100, 100, 7),
        )
        invisiblePlayer.addCondition(
            ConditionInvisible(
                ConditionId.CONDITIONID_COMBAT,
                ConditionType.CONDITION_INVISIBLE,
                ticks = 10000,
                now = { 0 },
            ),
        )

        assertThat(observer.canSeeCreature(invisibleMonster)).isFalse()
        assertThat(observer.canSeeCreature(invisiblePlayer)).isTrue()
    }

    // --- speed (creature.cpp:1643-1664, creature.hpp:258-269, player.hpp:1909-1913) ---

    @Test
    fun `speed is base plus variance clamped to uint16`() {
        // creature.cpp:1643-1645: clamp(baseSpeed + varSpeed, 0, 65535).
        val creature = monster(1, speed = 200)
        assertThat(creature.getBaseSpeed()).isEqualTo(200)
        assertThat(creature.getSpeed()).isEqualTo(200)
        assertThat(creature.getStepSpeed()).isEqualTo(200)

        creature.setSpeed(50)
        assertThat(creature.getSpeed()).isEqualTo(250)

        creature.setBaseSpeed(65530)
        assertThat(creature.getSpeed()).isEqualTo(65535)
    }

    @Test
    fun `setSpeed refuses to change at max speed`() {
        // creature.cpp:1647-1651: the guard returns before varSpeed is assigned.
        val creature = monster(1, speed = 200)
        creature.setBaseSpeed(65535)
        creature.setSpeed(10)

        creature.setBaseSpeed(100)
        assertThat(creature.getSpeed()).isEqualTo(100)
    }

    @Test
    fun `speed property maps onto getSpeed and setBaseSpeed`() {
        // Port seam: `speed` stays the storage/Lua-facing alias
        // (g_game().setCreatureSpeed -> setBaseSpeed, game.cpp:7759-7761).
        val creature = monster(1, speed = 200)
        creature.speed = 300
        assertThat(creature.getBaseSpeed()).isEqualTo(300)
        assertThat(creature.speed).isEqualTo(300)

        creature.setSpeed(25)
        assertThat(creature.speed).isEqualTo(325)
    }

    @Test
    fun `player step speed floors at ten and caps at max`() {
        // player.hpp:1909-1913: max(PLAYER_MIN_SPEED, min(maxStepSpeed, getSpeed())).
        val player = Player(
            id = 1,
            name = "Runner",
            maxHealth = 150,
            health = 150,
            speed = 0,
            position = Position(100, 100, 7),
        )
        assertThat(player.getStepSpeed()).isEqualTo(10)

        player.setBaseSpeed(5000)
        assertThat(player.getStepSpeed()).isEqualTo(5000)

        player.setBaseSpeed(70000)
        assertThat(player.getStepSpeed()).isEqualTo(65535)
    }

    // --- outfit (creature.hpp:364-379) ---------------------------------------------

    @Test
    fun `default outfit starts empty`() {
        val creature = monster(1)
        assertThat(creature.getCurrentOutfit()).isEqualTo(Outfit())
        assertThat(creature.getDefaultOutfit()).isEqualTo(Outfit())
    }

    @Test
    fun `outfit accessors copy on set and get`() {
        // The C++ accessors pass Outfit_t by value (creature.hpp:364-375).
        val creature = monster(1)
        val outfit = Outfit(lookType = 136, lookHead = 5, lookBody = 6)
        creature.setCurrentOutfit(outfit)
        outfit.lookType = 999
        assertThat(creature.getCurrentOutfit().lookType).isEqualTo(136)

        val snapshot = creature.getCurrentOutfit()
        snapshot.lookType = 1
        assertThat(creature.getCurrentOutfit().lookType).isEqualTo(136)

        creature.setDefaultOutfit(outfit)
        assertThat(creature.getDefaultOutfit().lookType).isEqualTo(999)
    }

    @Test
    fun `support outfit look types are 75 266 and 302`() {
        // creature.hpp:376-379.
        val creature = monster(1)
        for (lookType in listOf(75, 266, 302)) {
            creature.setCurrentOutfit(Outfit(lookType = lookType))
            assertThat(creature.isWearingSupportOutfit()).isTrue()
        }

        creature.setCurrentOutfit(Outfit(lookType = 136))
        assertThat(creature.isWearingSupportOutfit()).isFalse()
    }

    // --- light (creature.cpp:1639-1672, creatures_definitions.hpp:1717-1723) ---------

    @Test
    fun `default creature light is level zero color 215`() {
        val creature = monster(1)
        assertThat(creature.getCreatureLight()).isEqualTo(LightInfo(0, 215))
    }

    @Test
    fun `setCreatureLight stores a copy and normal light resets it`() {
        // creature.cpp:1666-1672.
        val creature = monster(1)
        val light = LightInfo(6, 215)
        creature.setCreatureLight(light)
        light.level = 1
        assertThat(creature.getCreatureLight().level).isEqualTo(6)

        val snapshot = creature.getCreatureLight()
        snapshot.level = 1
        assertThat(creature.getCreatureLight().level).isEqualTo(6)

        creature.setNormalCreatureLight()
        assertThat(creature.getCreatureLight()).isEqualTo(LightInfo(0, 215))
    }

    // --- master/summon (creature.cpp:1339-1369, creature.hpp:460-484, 624-639) -------

    @Test
    fun `setMaster links the summon to the master`() {
        val master = monster(1)
        val summon = monster(2)

        assertThat(summon.isSummon()).isFalse()
        assertThat(summon.hasBeenSummoned()).isFalse()

        assertThat(summon.setMaster(master)).isTrue()
        assertThat(summon.isSummon()).isTrue()
        assertThat(summon.hasBeenSummoned()).isTrue()
        assertThat(summon.getMaster()).isSameAs(master)
        assertThat(master.getSummons()).containsExactly(summon)
        assertThat(master.getSummonCount()).isEqualTo(1)
        assertThat(master.hasSummons()).isTrue()
    }

    @Test
    fun `setMaster switches masters and removes the old link`() {
        val first = monster(1)
        val second = monster(2)
        val summon = monster(3)

        summon.setMaster(first)
        summon.setMaster(second)

        assertThat(summon.getMaster()).isSameAs(second)
        assertThat(first.getSummons()).isEmpty()
        assertThat(first.hasSummons()).isFalse()
        assertThat(second.getSummons()).containsExactly(summon)
    }

    @Test
    fun `setMaster with the same master keeps a single entry`() {
        // creature.cpp:1357-1368: the old-master erase removes one occurrence.
        val master = monster(1)
        val summon = monster(2)

        summon.setMaster(master)
        summon.setMaster(master)

        assertThat(master.getSummonCount()).isEqualTo(1)
    }

    @Test
    fun `setMaster null clears the master and latches summoned`() {
        // creature.cpp:1341-1347: `summoned` is set before the null/null guard.
        val master = monster(1)
        val summon = monster(2)
        summon.setMaster(master)

        assertThat(summon.setMaster(null)).isTrue()
        assertThat(summon.getMaster()).isNull()
        assertThat(summon.isSummon()).isFalse()
        assertThat(summon.hasBeenSummoned()).isTrue()
        assertThat(master.getSummons()).isEmpty()

        val neverSummoned = monster(3)
        assertThat(neverSummoned.setMaster(null)).isFalse()
        assertThat(neverSummoned.hasBeenSummoned()).isTrue()
    }

    @Test
    fun `removeMaster clears only the summon pointer`() {
        // creature.hpp:462-466: removeMaster only resets m_master; the master's
        // m_summons entry is dropped by setMaster, not by removeMaster.
        val master = monster(1)
        val summon = monster(2)
        summon.setMaster(master)

        summon.removeMaster()

        assertThat(summon.getMaster()).isNull()
        assertThat(summon.isSummon()).isFalse()
        assertThat(summon.hasBeenSummoned()).isTrue()
        assertThat(master.getSummons()).containsExactly(summon)
    }

    // --- creature icons (creature.hpp:325-362, 948; creatures_definitions.hpp:1500-1578)

    @Test
    fun `icon accessors follow the keyed map`() {
        val creature = monster(1)

        assertThat(creature.hasIcon("forge")).isFalse()
        assertThat(creature.getIcon("forge").isNone()).isTrue()
        assertThat(creature.getIcons()).isEmpty()

        val fiendish = CreatureIcon(CreatureIconModifications_t.Fiendish)
        creature.setIcon("forge", fiendish)
        assertThat(creature.hasIcon("forge")).isTrue()
        assertThat(creature.getIcon("forge")).isEqualTo(fiendish)
        assertThat(creature.getIcons()).containsExactly(fiendish)

        creature.removeIcon("forge")
        assertThat(creature.hasIcon("forge")).isFalse()
        assertThat(creature.getIcons()).isEmpty()
    }

    @Test
    fun `getIcons skips none entries and keeps map order`() {
        // creature.hpp:325-334 filters isSet(); creature.hpp:947-948 uses
        // std::map, so keys iterate in lexicographic order.
        val creature = monster(1)
        creature.setIcon("soulpit", CreatureIcon(CreatureIconModifications_t.ReducedHealth, 1))
        creature.setIcon("forge", CreatureIcon(CreatureIconModifications_t.Influenced, 2))
        creature.setIcon("alpha", CreatureIcon(CreatureIconQuests_t.WhiteCross))
        creature.setIcon("empty", CreatureIcon())

        assertThat(creature.hasIcon("empty")).isTrue()
        assertThat(creature.getIcons()).containsExactly(
            CreatureIcon(CreatureIconQuests_t.WhiteCross),
            CreatureIcon(CreatureIconModifications_t.Influenced, 2),
            CreatureIcon(CreatureIconModifications_t.ReducedHealth, 1),
        )

        creature.clearIcons()
        assertThat(creature.getIcons()).isEmpty()
        assertThat(creature.hasIcon("empty")).isFalse()
    }

    @Test
    fun `icon mutations notify iconChanged`() {
        // creature.hpp:347-360 calls iconChanged(), whose spectator broadcast
        // (creature.cpp:2020-2029) is a world side effect.
        val creature = ProbeCreature(1)

        creature.setIcon("forge", CreatureIcon(CreatureIconModifications_t.Fiendish))
        assertThat(creature.iconChanges).isEqualTo(1)

        creature.removeIcon("forge")
        assertThat(creature.iconChanges).isEqualTo(2)

        creature.clearIcons()
        assertThat(creature.iconChanges).isEqualTo(3)
    }

    @Test
    fun `icon serialization uses the category ordinal`() {
        // creatures_definitions.hpp:1570-1577.
        assertThat(CreatureIcon().serialize()).isEqualTo(0)
        assertThat(CreatureIcon(CreatureIconModifications_t.HigherDamageReceived).serialize()).isEqualTo(1)
        assertThat(CreatureIcon(CreatureIconModifications_t.BountyTaskMonster, 4).serialize()).isEqualTo(9)
        assertThat(CreatureIcon(CreatureIconQuests_t.WhiteCross).serialize()).isEqualTo(1)
        assertThat(CreatureIcon(CreatureIconQuests_t.BloodDrop).serialize()).isEqualTo(24)
        assertThat(CreatureIcon().isNone()).isTrue()
        assertThat(CreatureIcon(CreatureIconQuests_t.Dove).isSet()).isTrue()
    }

    // --- combat event hooks (creature.hpp:552-566, 1011-1036, 845-899) ----------------

    @Test
    fun `onAttacked dispatches to registered handlers`() {
        // creature.cpp:1273-1275: the base hook body is empty; the port seam
        // registers the listeners the scripting layer used to provide.
        val creature = ProbeCreature(1)
        val seen = mutableListOf<Creature>()
        val handler: (Creature) -> Unit = { seen.add(it) }

        creature.registerOnAttackedHandler(handler)
        creature.onAttacked()
        assertThat(seen).containsExactly(creature)

        creature.unregisterOnAttackedHandler(handler)
        creature.onAttacked()
        assertThat(seen).hasSize(1)
    }

    @Test
    fun `setAttackedCreature validates sight and fires both hooks`() {
        // creature.cpp:1011-1030: same floor + canSee gate, then
        // onAttackedCreature(self) and target->onAttacked().
        val attacker = monster(1, position = Position(100, 100, 7))
        val target = monster(2, position = Position(111, 100, 7))
        val targeted = mutableListOf<Creature?>()
        val attacked = mutableListOf<Creature>()

        attacker.registerOnAttackedCreatureHandler { _, creature -> targeted.add(creature) }
        target.registerOnAttackedHandler { attacked.add(it) }

        assertThat(attacker.setAttackedCreature(target)).isTrue()
        assertThat(attacker.getAttackedCreature()).isSameAs(target)
        assertThat(targeted).containsExactly(target)
        assertThat(attacked).containsExactly(target)

        val outOfSight = monster(3, position = Position(112, 100, 7))
        assertThat(attacker.setAttackedCreature(outOfSight)).isFalse()
        assertThat(attacker.getAttackedCreature()).isNull()

        val otherFloor = monster(4, position = Position(100, 100, 8))
        assertThat(attacker.setAttackedCreature(otherFloor)).isFalse()
        assertThat(attacker.getAttackedCreature()).isNull()
    }

    @Test
    fun `setAttackedCreature propagates to summons`() {
        // creature.cpp:1032-1034: the target change is pushed to every summon.
        val master = monster(1)
        val summon = monster(2, position = Position(101, 100, 7))
        val target = monster(3, position = Position(109, 100, 7))
        summon.setMaster(master)

        assertThat(master.setAttackedCreature(target)).isTrue()
        assertThat(summon.getAttackedCreature()).isSameAs(target)

        master.setAttackedCreature(null)
        assertThat(master.getAttackedCreature()).isNull()
        assertThat(summon.getAttackedCreature()).isNull()
    }

    @Test
    fun `gainHealth fires the healer target gain hook`() {
        // creature.cpp:886-891: changeHealth then
        // healer->onTargetCreatureGainHealth(self, healthGain).
        val healer = monster(1)
        val wounded = monster(2, maxHealth = 100, health = 50)
        val healed = mutableListOf<Pair<Creature?, Int>>()
        healer.registerOnTargetCreatureGainHealthHandler { _, target, points ->
            healed.add(target to points)
        }

        wounded.gainHealth(healer, 30)

        assertThat(wounded.health).isEqualTo(80)
        assertThat(healed).containsExactly(wounded to 30)
    }

    @Test
    fun `changeHealth to zero fires onDeath once`() {
        // creature.cpp:845-875: the lifeless guard plus the queued onDeath.
        val creature = ProbeCreature(1, maxHealth = 100, health = 100)
        val deaths = mutableListOf<Creature>()
        creature.registerOnDeathHandler { deaths.add(it) }

        creature.changeHealth(-100)

        assertThat(creature.isDead).isTrue()
        assertThat(creature.deathCalls).isEqualTo(1)
        assertThat(deaths).containsExactly(creature)

        creature.changeHealth(-50)
        assertThat(creature.deathCalls).isEqualTo(1)
        assertThat(deaths).hasSize(1)
    }

    @Test
    fun `onDeath removes the master link`() {
        // creature.cpp:671-673 area: `if (thisMaster) removeMaster()`.
        val master = monster(1)
        val summon = ProbeCreature(2, maxHealth = 10, health = 10)
        summon.setMaster(master)

        summon.changeHealth(-10)

        assertThat(summon.isDead).isTrue()
        assertThat(summon.getMaster()).isNull()
        assertThat(summon.isSummon()).isFalse()
    }
}
