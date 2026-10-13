package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position
import org.opentibia.canary.spells.SoundEffect

/**
 * Transcribes the remaining Canary monster combat surface:
 * - `Monster::doAttacking` (src/creatures/monsters/monster.cpp:1754-1767)
 * - `Monster::hasExtraSwing` (src/creatures/monsters/monster.cpp:2091-2093)
 * - `Monster::getCombatValues` (src/creatures/monsters/monster.cpp:3346-3355)
 * - `Monster::updateLookDirection` (src/creatures/monsters/monster.cpp:3356-3413)
 * - `Monster::onThinkDefense` summon roll (src/creatures/monsters/monster.cpp:2224-2267)
 */
class MonsterCombatTest {

    private val pos = Position(100, 100, 7)

    private fun monster(id: Long = 1, position: Position = pos) = Monster(id, "Rat", 100, 100, position = position)

    private fun player(id: Long = 2, position: Position = pos) = Player(id, "Hero", 100, 100, position = position)

    private fun summonBlock(
        name: String = "Rat",
        chance: Int = 100,
        speed: Int = 1000,
        count: Int = 1,
        force: Boolean = false,
    ) = SummonBlock(name = name, chance = chance, speed = speed, count = count, force = force)

    private fun defenseCall(
        state: MonsterBehaviorState,
        monster: Monster = monster(),
        interval: Int = 100,
        isSummon: Boolean = false,
        summonBlocks: List<SummonBlock> = emptyList(),
        maxSummons: Int = 10,
        summons: MutableList<Monster> = mutableListOf(),
        hasFollowPath: Boolean = true,
        soulPit: Boolean = false,
        forgeStack: Int = 0,
        roll: Int = 1,
        createMonster: (String) -> Monster? = { Monster(99, it, 100, 100, position = pos) },
        placeCreature: (Monster, Position, Boolean, Boolean) -> Boolean = { _, _, _, _ -> true },
        setMaster: (Monster, Monster, Boolean) -> Unit = { _, _, _ -> },
        setSoulPitStack: (Monster, Int, Boolean) -> Unit = { _, _, _ -> },
        effects: MutableList<Pair<Position, Int>> = mutableListOf(),
        sounds: MutableList<Pair<Position, SoundEffect>> = mutableListOf(),
    ) = MonsterBehavior.onThinkDefense(
        state = state,
        monster = monster,
        defenseSpells = emptyList(),
        interval = interval,
        nextRandom = { _, _ -> roll },
        isSummon = isSummon,
        summonBlocks = summonBlocks,
        maxSummons = maxSummons,
        summons = summons,
        hasFollowPath = hasFollowPath,
        soulPit = soulPit,
        forgeStack = forgeStack,
        createMonster = createMonster,
        placeCreature = placeCreature,
        setMaster = setMaster,
        setSoulPitStack = setSoulPitStack,
        addMagicEffect = { position, effect -> effects.add(position to effect) },
        sendSoundEffect = { position, sound, _ -> sounds.add(position to sound) },
    )

    // --- Monster::doAttacking ---

    @Test
    fun `doAttacking does nothing without an attacked creature`() {
        val state = MonsterBehaviorState()
        val intentions = mutableListOf<Creature>()

        MonsterBehavior.doAttacking(
            MonsterAttackingRequest(
                state = state,
                monsterId = 1,
                isSummon = false,
                attackedCreature = null,
                interval = 100,
                requestCombatIntention = { _, target -> intentions.add(target) },
            )
        )

        assertThat(state.attackTicks).isZero()
        assertThat(intentions).isEmpty()
    }

    @Test
    fun `doAttacking skips a lifeless attacked creature`() {
        val state = MonsterBehaviorState()
        var intentions = 0

        MonsterBehavior.doAttacking(
            MonsterAttackingRequest(
                state = state,
                monsterId = 1,
                isSummon = false,
                attackedCreature = player(),
                attackedIsLifeless = true,
                interval = 100,
                requestCombatIntention = { _, _ -> intentions++ },
            )
        )

        assertThat(state.attackTicks).isZero()
        assertThat(intentions).isZero()
    }

    @Test
    fun `doAttacking skips a summon that attacked itself`() {
        val state = MonsterBehaviorState()

        MonsterBehavior.doAttacking(
            MonsterAttackingRequest(
                state = state,
                monsterId = 1,
                isSummon = true,
                attackedCreature = monster(id = 1),
                interval = 100,
                requestCombatIntention = { _, _ -> },
            )
        )

        assertThat(state.attackTicks).isZero()
    }

    @Test
    fun `doAttacking skips a login protected player`() {
        val state = MonsterBehaviorState()

        MonsterBehavior.doAttacking(
            MonsterAttackingRequest(
                state = state,
                monsterId = 1,
                isSummon = false,
                attackedCreature = player(),
                attackedIsLoginProtected = true,
                interval = 100,
                requestCombatIntention = { _, _ -> },
            )
        )

        assertThat(state.attackTicks).isZero()
    }

    @Test
    fun `doAttacking accumulates the interval and requests the combat intention`() {
        val state = MonsterBehaviorState().apply { attackTicks = 150 }
        val requested = mutableListOf<Pair<Int, Long>>()
        val attacked = player()

        MonsterBehavior.doAttacking(
            MonsterAttackingRequest(
                state = state,
                monsterId = 1,
                isSummon = false,
                attackedCreature = attacked,
                interval = 100,
                requestCombatIntention = { interval, target -> requested.add(interval to target.id) },
            )
        )

        assertThat(state.attackTicks).isEqualTo(250)
        assertThat(requested).containsExactly(100 to attacked.id)
    }

    // --- Monster::hasExtraSwing ---

    @Test
    fun `hasExtraSwing mirrors the extra melee attack latch`() {
        val state = MonsterBehaviorState()

        assertThat(MonsterBehavior.hasExtraSwing(state)).isFalse()
        state.extraMeleeAttack = true
        assertThat(MonsterBehavior.hasExtraSwing(state)).isTrue()
    }

    // --- Monster::getCombatValues ---

    @Test
    fun `getCombatValues reports absent when both bounds are zero`() {
        assertThat(MonsterBehavior.getCombatValues(MonsterBehaviorState())).isNull()
    }

    @Test
    fun `getCombatValues reports the stored bounds`() {
        val state = MonsterBehaviorState().apply {
            minCombatValue = 10
            maxCombatValue = 25
        }

        assertThat(MonsterBehavior.getCombatValues(state)).isEqualTo(10 to 25)
    }

    // --- Monster::updateLookDirection ---

    @Test
    fun `updateLookDirection does nothing without an attacked creature`() {
        assertThat(
            MonsterBehavior.updateLookDirection(Direction.North, pos, null)
        ).isNull()
    }

    @Test
    fun `updateLookDirection faces the attacked creature on the dominant axis`() {
        assertThat(MonsterBehavior.updateLookDirection(Direction.North, pos, Position(90, 98, 7))).isEqualTo(Direction.West)
        assertThat(MonsterBehavior.updateLookDirection(Direction.North, pos, Position(110, 98, 7))).isEqualTo(Direction.East)
        assertThat(MonsterBehavior.updateLookDirection(Direction.East, pos, Position(98, 90, 7))).isEqualTo(Direction.North)
        assertThat(MonsterBehavior.updateLookDirection(Direction.East, pos, Position(98, 110, 7))).isEqualTo(Direction.South)
    }

    @Test
    fun `updateLookDirection breaks diagonal ties from the current facing`() {
        // attacked NW: south becomes west, east becomes north
        assertThat(MonsterBehavior.updateLookDirection(Direction.South, pos, Position(90, 90, 7))).isEqualTo(Direction.West)
        assertThat(MonsterBehavior.updateLookDirection(Direction.East, pos, Position(90, 90, 7))).isEqualTo(Direction.North)
        assertThat(MonsterBehavior.updateLookDirection(Direction.North, pos, Position(90, 90, 7))).isEqualTo(Direction.North)

        // attacked SW: north becomes west, east becomes south
        assertThat(MonsterBehavior.updateLookDirection(Direction.North, pos, Position(90, 110, 7))).isEqualTo(Direction.West)
        assertThat(MonsterBehavior.updateLookDirection(Direction.East, pos, Position(90, 110, 7))).isEqualTo(Direction.South)

        // attacked NE: south becomes east, west becomes north
        assertThat(MonsterBehavior.updateLookDirection(Direction.South, pos, Position(110, 90, 7))).isEqualTo(Direction.East)
        assertThat(MonsterBehavior.updateLookDirection(Direction.West, pos, Position(110, 90, 7))).isEqualTo(Direction.North)

        // attacked SE: north becomes east, west becomes south
        assertThat(MonsterBehavior.updateLookDirection(Direction.North, pos, Position(110, 110, 7))).isEqualTo(Direction.East)
        assertThat(MonsterBehavior.updateLookDirection(Direction.West, pos, Position(110, 110, 7))).isEqualTo(Direction.South)
    }

    @Test
    fun `updateLookDirection turns north to east on an exact overlap`() {
        assertThat(MonsterBehavior.updateLookDirection(Direction.North, pos, pos)).isEqualTo(Direction.East)
        assertThat(MonsterBehavior.updateLookDirection(Direction.South, pos, pos)).isEqualTo(Direction.South)
    }

    // --- Monster::onThinkDefense summon roll ---

    @Test
    fun `defense summons are skipped for summons without a follow path or with a full cap`() {
        var created = 0
        val create: (String) -> Monster? = { created++; monster() }

        defenseCall(
            MonsterBehaviorState().apply { defenseTicks = 900 },
            hasFollowPath = false,
            summonBlocks = listOf(summonBlock()),
            createMonster = create,
        )
        assertThat(created).isZero()

        defenseCall(
            MonsterBehaviorState().apply { defenseTicks = 900 },
            isSummon = true,
            summonBlocks = listOf(summonBlock()),
            createMonster = create,
        )
        assertThat(created).isZero()

        defenseCall(
            MonsterBehaviorState().apply { defenseTicks = 900 },
            summonBlocks = listOf(summonBlock()),
            maxSummons = 0,
            createMonster = create,
        )
        assertThat(created).isZero()
    }

    @Test
    fun `defense summons wait for the speed window and hold the tick reset`() {
        val state = MonsterBehaviorState().apply { defenseTicks = 100 }
        var created = 0

        defenseCall(state, summonBlocks = listOf(summonBlock(speed = 500)), createMonster = { created++; monster() })

        assertThat(created).isZero()
        assertThat(state.defenseTicks).isEqualTo(200)
    }

    @Test
    fun `defense summons skip a block already used this round and let the ticks reset`() {
        val state = MonsterBehaviorState().apply { defenseTicks = 500 }
        var created = 0

        defenseCall(state, summonBlocks = listOf(summonBlock(speed = 500)), createMonster = { created++; monster() })

        assertThat(created).isZero()
        assertThat(state.defenseTicks).isZero()
    }

    @Test
    fun `defense summons respect the per-name count and chance gates`() {
        val existing = monster(id = 7, position = pos)
        var created = 0

        val counted = MonsterBehaviorState().apply { defenseTicks = 900 }
        defenseCall(
            counted,
            summonBlocks = listOf(summonBlock(count = 1)),
            summons = mutableListOf(existing),
            createMonster = { created++; monster() },
        )
        assertThat(created).isZero()

        val chance = MonsterBehaviorState().apply { defenseTicks = 900 }
        defenseCall(
            chance,
            summonBlocks = listOf(summonBlock(chance = 30)),
            roll = 50,
            createMonster = { created++; monster() },
        )
        assertThat(created).isZero()
    }

    @Test
    fun `defense summons skip when creation or placement fails`() {
        var placed = 0

        defenseCall(
            MonsterBehaviorState().apply { defenseTicks = 900 },
            summonBlocks = listOf(summonBlock()),
            createMonster = { null },
            placeCreature = { _, _, _, _ -> placed++; true },
        )
        assertThat(placed).isZero()

        var linked = 0
        defenseCall(
            MonsterBehaviorState().apply { defenseTicks = 900 },
            summonBlocks = listOf(summonBlock()),
            placeCreature = { _, _, _, _ -> false },
            setMaster = { _, _, _ -> linked++ },
        )
        assertThat(linked).isZero()
    }

    @Test
    fun `defense summons place bind and announce the new summon`() {
        val state = MonsterBehaviorState().apply { defenseTicks = 900 }
        val master = monster(id = 1)
        val summon = monster(id = 99, position = Position(101, 100, 7))
        val masters = mutableListOf<Pair<Long, Boolean>>()
        val soulPitStacks = mutableListOf<Pair<Long, Pair<Int, Boolean>>>()
        val effects = mutableListOf<Pair<Position, Int>>()
        val sounds = mutableListOf<Pair<Position, SoundEffect>>()
        var placementFlags: Pair<Boolean, Boolean>? = null

        MonsterBehavior.onThinkDefense(
            state = state,
            monster = master,
            defenseSpells = emptyList(),
            interval = 100,
            nextRandom = { _, _ -> 1 },
            isSummon = false,
            summonBlocks = listOf(summonBlock(name = "Rat", force = true)),
            maxSummons = 3,
            summons = mutableListOf(),
            hasFollowPath = true,
            soulPit = true,
            forgeStack = 2,
            createMonster = { summon },
            placeCreature = { creature, position, extended, force ->
                assertThat(creature).isSameAs(summon)
                assertThat(position).isEqualTo(pos)
                placementFlags = extended to force
                true
            },
            setMaster = { child, parent, reload -> masters.add(child.id to (parent.id == master.id && reload)) },
            setSoulPitStack = { child, stack, isSummon -> soulPitStacks.add(child.id to (stack to isSummon)) },
            addMagicEffect = { position, effect -> effects.add(position to effect) },
            sendSoundEffect = { position, sound, _ -> sounds.add(position to sound) },
        )

        assertThat(placementFlags).isEqualTo(false to true)
        assertThat(masters).containsExactly(99L to true)
        assertThat(soulPitStacks).containsExactly(99L to (2 to true))
        assertThat(effects).containsExactly(
            pos to MonsterBehavior.CONST_ME_MAGIC_BLUE,
            summon.position to MonsterBehavior.CONST_ME_TELEPORT,
        )
        assertThat(sounds).containsExactly(summon.position to SoundEffect.MONSTER_SPELL_SUMMON)
    }

    @Test
    fun `defense summons count newly placed creatures against the cap`() {
        val summons = mutableListOf<Monster>()
        var created = 0

        defenseCall(
            MonsterBehaviorState().apply { defenseTicks = 900 },
            summonBlocks = listOf(summonBlock(name = "Rat"), summonBlock(name = "Wolf")),
            maxSummons = 1,
            summons = summons,
            createMonster = { created++; Monster(99, it, 100, 100, position = pos) },
            setMaster = { child, _, _ -> summons.add(child) },
        )

        assertThat(created).isEqualTo(1)
        assertThat(summons.map { it.name }).containsExactly("Rat")
    }
}
