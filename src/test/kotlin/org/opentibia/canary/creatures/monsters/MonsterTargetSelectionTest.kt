package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

/**
 * Transcribes the remaining Canary target-selection surface:
 * - `Monster::searchTarget` (src/creatures/monsters/monster.cpp:899-905)
 * - `Monster::selectTarget` (src/creatures/monsters/monster.cpp:1464-1497)
 * - `Monster::addFriend`/`removeFriend`/`countsAsPlayerOnScreenTarget`/
 *   `forgetTargetReference`/`addTarget`/`removeTarget`/`clearTargetList`/`clearFriendList`
 *   (src/creatures/monsters/monster.cpp:652-784)
 * - `Monster::onCreatureFound`/`onCreatureEnter`/`onCreatureLeave`
 *   (src/creatures/monsters/monster.cpp:784-898)
 * - `Monster::updateSummonTarget` (src/creatures/monsters/monster.cpp:1332-1345)
 * - `Monster::onThinkTarget` (src/creatures/monsters/monster.cpp:2141-2200)
 * - `Monster::markTargetStateChanged`/`markTargetDecisionChanged`
 *   (src/creatures/monsters/monster.cpp:1312-1330)
 */
class MonsterTargetSelectionTest {

    private val origin = Position(100, 100, 7)

    private fun monster(
        id: Long = 1,
        faction: Faction = Faction.Default,
        enemyFactions: Set<Faction> = emptySet(),
        isSummon: Boolean = false,
        isHostile: Boolean = true,
        masterId: Long? = null,
        masterPlayerId: Long? = null,
    ) = MonsterTargetFacts(
        origin = origin,
        id = id,
        faction = faction,
        enemyFactions = enemyFactions,
        isSummon = isSummon,
        isHostile = isHostile,
        masterId = masterId,
        masterPlayerId = masterPlayerId,
    )

    private fun creature(
        id: Long,
        x: Int = 101,
        y: Int = 100,
        health: Int = 100,
        faction: Faction = Faction.Default,
        playerId: Long? = null,
        masterId: Long? = null,
        masterPlayerId: Long? = null,
        isRemoved: Boolean = false,
        isAttackable: Boolean = true,
        zoneType: ZoneType = ZoneType.Normal,
        isInvisible: Boolean = false,
        isSummon: Boolean = false,
        isMonster: Boolean = false,
        ignoresMonsters: Boolean = false,
        isLoginProtected: Boolean = false,
    ) = TargetCreatureFacts(
        id = id,
        position = Position(x, y, 7),
        health = health,
        faction = faction,
        playerId = playerId,
        masterId = masterId,
        masterPlayerId = masterPlayerId,
        isRemoved = isRemoved,
        isAttackable = isAttackable,
        zoneType = zoneType,
        isInvisible = isInvisible,
        isSummon = isSummon,
        isMonster = isMonster,
        ignoresMonsters = ignoresMonsters,
        isLoginProtected = isLoginProtected,
    )

    // --- Monster::searchTarget ---

    @Test
    fun `searchTarget delegates to the async request inside a barrier-parallel context`() {
        val requested = mutableListOf<TargetSearchType>()

        val result = MonsterTargeting.searchTarget(
            searchType = TargetSearchType.Hp,
            isBarrierParallel = true,
            requestTargetSearchCompute = { requested.add(it); true },
            searchTargetImmediate = { false },
        )

        assertThat(result).isTrue()
        assertThat(requested).containsExactly(TargetSearchType.Hp)
    }

    @Test
    fun `searchTarget runs the immediate search outside a barrier-parallel context`() {
        val immediate = mutableListOf<TargetSearchType>()

        val result = MonsterTargeting.searchTarget(
            searchType = TargetSearchType.Nearest,
            isBarrierParallel = false,
            requestTargetSearchCompute = { false },
            searchTargetImmediate = { immediate.add(it); true },
        )

        assertThat(result).isTrue()
        assertThat(immediate).containsExactly(TargetSearchType.Nearest)
    }

    // --- Monster::selectTarget ---

    @Test
    fun `selectTarget rejects a creature that is not a valid target`() {
        val state = MonsterTargetListState().apply { targetList.add(MonsterTargetReference(9)) }

        val selected = MonsterTargeting.selectTarget(
            monster = monster(),
            creature = creature(9, isRemoved = true),
            monsterPerfTestFriendlyFire = false,
            state = state,
        )

        assertThat(selected).isFalse()
        assertThat(state.targetList).hasSize(1)
    }

    @Test
    fun `selectTarget rejects a login protected player`() {
        val state = MonsterTargetListState().apply { targetList.add(MonsterTargetReference(9)) }

        val selected = MonsterTargeting.selectTarget(
            monster = monster(),
            creature = creature(9, playerId = 9, isLoginProtected = true),
            monsterPerfTestFriendlyFire = false,
            state = state,
        )

        assertThat(selected).isFalse()
    }

    @Test
    fun `selectTarget rejects a creature missing from the target list`() {
        val state = MonsterTargetListState()

        val selected = MonsterTargeting.selectTarget(
            monster = monster(),
            creature = creature(9),
            monsterPerfTestFriendlyFire = false,
            state = state,
        )

        assertThat(selected).isFalse()
    }

    @Test
    fun `selectTarget forgets and erases an expired target list reference`() {
        val state = MonsterTargetListState().apply {
            targetList.add(MonsterTargetReference(9, countsAsPlayerOnScreen = true, isExpired = true))
            totalPlayersOnScreen = 1
        }

        val selected = MonsterTargeting.selectTarget(
            monster = monster(),
            creature = creature(9),
            monsterPerfTestFriendlyFire = false,
            state = state,
        )

        assertThat(selected).isFalse()
        assertThat(state.targetList).isEmpty()
        assertThat(state.totalPlayersOnScreen).isZero()
        assertThat(state.targetStateEpoch).isEqualTo(2L)
    }

    @Test
    fun `selectTarget hostile sets the attacked creature then triggers the attack check and follows`() {
        val state = MonsterTargetListState().apply { targetList.add(MonsterTargetReference(9)) }
        val events = mutableListOf<String>()

        val selected = MonsterTargeting.selectTarget(
            monster = monster(isHostile = true),
            creature = creature(9),
            monsterPerfTestFriendlyFire = false,
            state = state,
            setAttackedCreature = { events.add("attacked"); true },
            checkCreatureAttack = { events.add("checkAttack") },
            setFollowCreature = { events.add("follow"); true },
        )

        assertThat(selected).isTrue()
        assertThat(events).containsExactly("attacked", "checkAttack", "follow")
    }

    @Test
    fun `selectTarget summon also refreshes the attacked creature`() {
        val state = MonsterTargetListState().apply { targetList.add(MonsterTargetReference(9)) }
        val events = mutableListOf<String>()

        MonsterTargeting.selectTarget(
            monster = monster(isSummon = true, isHostile = false),
            creature = creature(9),
            monsterPerfTestFriendlyFire = false,
            state = state,
            setAttackedCreature = { events.add("attacked"); true },
            checkCreatureAttack = { events.add("checkAttack") },
            setFollowCreature = { events.add("follow"); true },
        )

        assertThat(events).containsExactly("attacked", "checkAttack", "follow")
    }

    @Test
    fun `selectTarget non hostile monster only follows without touching the attack`() {
        val state = MonsterTargetListState().apply { targetList.add(MonsterTargetReference(9)) }
        val events = mutableListOf<String>()

        val selected = MonsterTargeting.selectTarget(
            monster = monster(isHostile = false),
            creature = creature(9, isMonster = true),
            monsterPerfTestFriendlyFire = false,
            state = state,
            setAttackedCreature = { events.add("attacked"); true },
            setFollowCreature = { events.add("follow"); true },
        )

        assertThat(selected).isTrue()
        assertThat(events).containsExactly("follow")
    }

    // --- target/friend list management ---

    @Test
    fun `addTarget rejects the monster itself`() {
        val state = MonsterTargetListState()

        assertThat(MonsterTargeting.addTarget(state, monster(id = 5), creature(5), pushFront = false)).isFalse()
        assertThat(state.targetList).isEmpty()
    }

    @Test
    fun `addTarget rejects a live duplicate and does not stack the player counter`() {
        val state = MonsterTargetListState()

        assertThat(
            MonsterTargeting.addTarget(
                state,
                monster(faction = Faction.Lion, enemyFactions = setOf(Faction.Player)),
                creature(9, playerId = 9, faction = Faction.Player),
                pushFront = false,
            )
        ).isTrue()
        assertThat(state.totalPlayersOnScreen).isEqualTo(1)

        assertThat(
            MonsterTargeting.addTarget(
                state,
                monster(faction = Faction.Lion, enemyFactions = setOf(Faction.Player)),
                creature(9, playerId = 9, faction = Faction.Player),
                pushFront = false,
            )
        ).isFalse()
        assertThat(state.targetList).hasSize(1)
        assertThat(state.totalPlayersOnScreen).isEqualTo(1)
    }

    @Test
    fun `addTarget replaces an expired reference and forgets its player counter`() {
        val state = MonsterTargetListState().apply {
            targetList.add(MonsterTargetReference(9, countsAsPlayerOnScreen = true, isExpired = true))
            totalPlayersOnScreen = 1
        }

        assertThat(MonsterTargeting.addTarget(state, monster(), creature(9), pushFront = false)).isTrue()

        assertThat(state.targetList.map { it.creatureId }).containsExactly(9L)
        assertThat(state.totalPlayersOnScreen).isZero()
        assertThat(state.targetStateEpoch).isEqualTo(2L)
    }

    @Test
    fun `addTarget pushFront inserts at the head`() {
        val state = MonsterTargetListState().apply { targetList.add(MonsterTargetReference(1)) }

        MonsterTargeting.addTarget(state, monster(), creature(2), pushFront = true)

        assertThat(state.targetList.map { it.creatureId }).containsExactly(2L, 1L)
    }

    @Test
    fun `addTarget counts a factionless player on screen only for a masterless faction monster`() {
        val state = MonsterTargetListState()

        MonsterTargeting.addTarget(
            state,
            monster(faction = Faction.Lion, enemyFactions = setOf(Faction.Player)),
            creature(9, playerId = 9, faction = Faction.Player),
            pushFront = false,
        )

        assertThat(state.totalPlayersOnScreen).isEqualTo(1)
    }

    @Test
    fun `countsAsPlayerOnScreenTarget requires no master a real faction and a player raw`() {
        val factionMonster = monster(faction = Faction.Lion)
        val defaultMonster = monster()
        val masteredMonster = monster(faction = Faction.Lion, masterId = 3)

        assertThat(MonsterTargeting.countsAsPlayerOnScreenTarget(factionMonster, creature(9, playerId = 9))).isTrue()
        assertThat(MonsterTargeting.countsAsPlayerOnScreenTarget(defaultMonster, creature(9, playerId = 9))).isFalse()
        assertThat(MonsterTargeting.countsAsPlayerOnScreenTarget(masteredMonster, creature(9, playerId = 9))).isFalse()
        assertThat(MonsterTargeting.countsAsPlayerOnScreenTarget(factionMonster, creature(9))).isFalse()
    }

    @Test
    fun `removeTarget reports a missing reference and clears an expired one`() {
        val state = MonsterTargetListState()

        assertThat(MonsterTargeting.removeTarget(state, creature(9))).isFalse()

        state.targetList.add(MonsterTargetReference(9, countsAsPlayerOnScreen = true, isExpired = true))
        state.totalPlayersOnScreen = 1

        assertThat(MonsterTargeting.removeTarget(state, creature(9))).isFalse()
        assertThat(state.targetList).isEmpty()
        assertThat(state.totalPlayersOnScreen).isZero()
        assertThat(state.targetStateEpoch).isEqualTo(2L)
    }

    @Test
    fun `removeTarget erases a live reference and reports true`() {
        val state = MonsterTargetListState().apply { targetList.add(MonsterTargetReference(9)) }

        assertThat(MonsterTargeting.removeTarget(state, creature(9))).isTrue()
        assertThat(state.targetList).isEmpty()
    }

    @Test
    fun `addFriend rejects duplicates and removeFriend reports membership`() {
        val state = MonsterTargetListState()

        assertThat(MonsterTargeting.addFriend(state, 9)).isTrue()
        assertThat(MonsterTargeting.addFriend(state, 9)).isFalse()
        assertThat(MonsterTargeting.removeFriend(state, 9)).isTrue()
        assertThat(MonsterTargeting.removeFriend(state, 9)).isFalse()
    }

    @Test
    fun `addFriend rejects the monster itself`() {
        val state = MonsterTargetListState()

        assertThat(MonsterTargeting.addFriend(state, 5, monsterId = 5)).isFalse()
        assertThat(state.friendList).isEmpty()
    }

    @Test
    fun `clearTargetList resets the player counter and bumps the state epoch`() {
        val state = MonsterTargetListState().apply {
            targetList.add(MonsterTargetReference(9, countsAsPlayerOnScreen = true))
            totalPlayersOnScreen = 1
        }

        MonsterTargeting.clearTargetList(state)

        assertThat(state.targetList).isEmpty()
        assertThat(state.totalPlayersOnScreen).isZero()
        assertThat(state.targetStateEpoch).isEqualTo(2L)
    }

    @Test
    fun `markTargetDecisionChanged bumps both epochs and wraps at zero`() {
        val state = MonsterTargetListState().apply {
            targetStateEpoch = -1L
            targetDecisionEpoch = -1L
        }

        MonsterTargeting.markTargetDecisionChanged(state)

        assertThat(state.targetStateEpoch).isEqualTo(1L)
        assertThat(state.targetDecisionEpoch).isEqualTo(1L)
    }

    // --- onCreatureFound / onCreatureEnter / onCreatureLeave ---

    @Test
    fun `onCreatureFound adds friends and opponents and updates the idle status`() {
        val state = MonsterTargetListState()
        var idleUpdates = 0

        val changed = MonsterTargeting.onCreatureFound(
            state = state,
            monster = monster(),
            creature = creature(9, playerId = 9),
            pushFront = false,
            monsterPerfTestFriendlyFire = false,
            isIdle = false,
            updateIdleStatus = { idleUpdates++ },
        )

        assertThat(changed).isTrue()
        assertThat(state.targetList.map { it.creatureId }).containsExactly(9L)
        assertThat(idleUpdates).isEqualTo(1)
    }

    @Test
    fun `onCreatureFound keeps an idle monster updating even without a list change`() {
        val state = MonsterTargetListState().apply { targetList.add(MonsterTargetReference(9)) }
        var idleUpdates = 0

        val changed = MonsterTargeting.onCreatureFound(
            state = state,
            monster = monster(),
            creature = creature(9, playerId = 9),
            pushFront = false,
            monsterPerfTestFriendlyFire = false,
            isIdle = true,
            updateIdleStatus = { idleUpdates++ },
        )

        assertThat(changed).isFalse()
        assertThat(state.targetList).hasSize(1)
        assertThat(idleUpdates).isEqualTo(1)
    }

    @Test
    fun `onCreatureFound records a non summon monster as friend`() {
        val state = MonsterTargetListState()

        MonsterTargeting.onCreatureFound(
            state = state,
            monster = monster(isHostile = false),
            creature = creature(9, isMonster = true, isSummon = false),
            pushFront = true,
            monsterPerfTestFriendlyFire = false,
            isIdle = false,
            updateIdleStatus = {},
        )

        assertThat(state.friendList).containsExactly(9L)
        assertThat(state.targetList).isEmpty()
    }

    @Test
    fun `onCreatureLeave removes friends and idle-updates when the target list empties`() {
        val state = MonsterTargetListState().apply { targetList.add(MonsterTargetReference(9)) }
        var idleUpdates = 0

        MonsterTargeting.onCreatureLeave(
            state = state,
            monster = monster(),
            creature = creature(9, playerId = 9),
            monsterPerfTestFriendlyFire = false,
            updateIdleStatus = { idleUpdates++ },
        )

        assertThat(state.targetList).isEmpty()
        assertThat(idleUpdates).isEqualTo(1)

        state.friendList.add(7)
        MonsterTargeting.onCreatureLeave(
            state = state,
            monster = monster(),
            creature = creature(7, isMonster = true, isSummon = false),
            monsterPerfTestFriendlyFire = false,
            updateIdleStatus = { idleUpdates++ },
        )

        assertThat(state.friendList).isEmpty()
    }

    // --- updateSummonTarget ---

    @Test
    fun `updateSummonTarget clears the follow when the summon attacked itself`() {
        val follows = mutableListOf<Long?>()

        MonsterTargeting.updateSummonTarget(
            monsterId = 5,
            attackedCreatureId = 5,
            followCreatureId = 5,
            masterId = 3,
            masterAttackedCreatureId = 4,
            resolve = { creature(it) },
            selectTarget = { true },
            setFollowCreature = { follows.add(it?.id) },
        )

        assertThat(follows).containsExactly(null)
    }

    @Test
    fun `updateSummonTarget follows the attacked creature when it differs from the follow`() {
        val follows = mutableListOf<Long?>()

        MonsterTargeting.updateSummonTarget(
            monsterId = 5,
            attackedCreatureId = 4,
            followCreatureId = 6,
            masterId = 3,
            masterAttackedCreatureId = null,
            resolve = { creature(it) },
            selectTarget = { true },
            setFollowCreature = { follows.add(it?.id) },
        )

        assertThat(follows).containsExactly(4L)
    }

    @Test
    fun `updateSummonTarget adopts the master target when available`() {
        val selected = mutableListOf<Long?>()
        val follows = mutableListOf<Long?>()

        MonsterTargeting.updateSummonTarget(
            monsterId = 5,
            attackedCreatureId = null,
            followCreatureId = null,
            masterId = 3,
            masterAttackedCreatureId = 4,
            resolve = { creature(it) },
            selectTarget = { selected.add(it.id); true },
            setFollowCreature = { follows.add(it?.id) },
        )

        assertThat(selected).containsExactly(4L)
        assertThat(follows).isEmpty()
    }

    @Test
    fun `updateSummonTarget falls back to following the master`() {
        val selected = mutableListOf<Long?>()
        val follows = mutableListOf<Long?>()

        MonsterTargeting.updateSummonTarget(
            monsterId = 5,
            attackedCreatureId = null,
            followCreatureId = null,
            masterId = 3,
            masterAttackedCreatureId = null,
            resolve = { creature(it) },
            selectTarget = { selected.add(it.id); true },
            setFollowCreature = { follows.add(it?.id) },
        )

        assertThat(selected).isEmpty()
        assertThat(follows).containsExactly(3L)
    }

    // --- onThinkTarget ---

    private fun targetThinkRequest(
        state: MonsterBehaviorState,
        interval: Int = 100,
        isSummon: Boolean = false,
        changeTargetSpeed: Int = 1000,
        changeTargetChance: Int = 100,
        targetDistance: Int = 1,
        friendlyFire: Boolean = false,
        runAwayHealth: Int = 0,
        roll: Int = 1,
        requestedTypes: MutableList<TargetSearchType> = mutableListOf(),
    ) = MonsterTargetThinkRequest(
        state = state,
        interval = interval,
        isSummon = isSummon,
        changeTargetSpeed = changeTargetSpeed,
        changeTargetChance = changeTargetChance,
        targetDistance = targetDistance,
        monsterPerfTestFriendlyFire = friendlyFire,
        runAwayHealth = runAwayHealth,
        rollTargetChange = { roll },
        requestTargetSearchCompute = { requestedTypes.add(it) },
    )

    @Test
    fun `onThinkTarget does nothing without a change target speed`() {
        val state = MonsterBehaviorState()
        val requested = mutableListOf<TargetSearchType>()

        MonsterTargeting.onThinkTarget(targetThinkRequest(state, changeTargetSpeed = 0, requestedTypes = requested))

        assertThat(state.targetChangeTicks).isZero()
        assertThat(requested).isEmpty()
    }

    @Test
    fun `onThinkTarget never changes targets for summons`() {
        val state = MonsterBehaviorState()
        val requested = mutableListOf<TargetSearchType>()

        MonsterTargeting.onThinkTarget(targetThinkRequest(state, isSummon = true, requestedTypes = requested))

        assertThat(state.targetChangeTicks).isZero()
        assertThat(requested).isEmpty()
    }

    @Test
    fun `onThinkTarget accumulates ticks and triggers the random search at melee distance`() {
        val state = MonsterBehaviorState().apply { targetChangeTicks = 950 }
        val requested = mutableListOf<TargetSearchType>()

        MonsterTargeting.onThinkTarget(targetThinkRequest(state, requestedTypes = requested))

        assertThat(state.targetChangeTicks).isZero()
        assertThat(state.targetChangeCooldown).isEqualTo(1000)
        assertThat(requested).containsExactly(TargetSearchType.Random)
    }

    @Test
    fun `onThinkTarget uses the nearest search beyond melee distance unless friendly fire is on`() {
        val state = MonsterBehaviorState().apply { targetChangeTicks = 950 }
        val requested = mutableListOf<TargetSearchType>()

        MonsterTargeting.onThinkTarget(
            targetThinkRequest(state, targetDistance = 5, requestedTypes = requested)
        )
        assertThat(requested).containsExactly(TargetSearchType.Nearest)

        val friendlyState = MonsterBehaviorState().apply { targetChangeTicks = 950 }
        val friendlyRequested = mutableListOf<TargetSearchType>()
        MonsterTargeting.onThinkTarget(
            targetThinkRequest(friendlyState, targetDistance = 5, friendlyFire = true, requestedTypes = friendlyRequested)
        )
        assertThat(friendlyRequested).containsExactly(TargetSearchType.Random)
    }

    @Test
    fun `onThinkTarget gates the change behind the change target chance`() {
        val state = MonsterBehaviorState().apply { targetChangeTicks = 950 }
        val requested = mutableListOf<TargetSearchType>()

        MonsterTargeting.onThinkTarget(
            targetThinkRequest(state, changeTargetChance = 50, roll = 51, requestedTypes = requested)
        )

        assertThat(state.targetChangeTicks).isZero()
        assertThat(requested).isEmpty()
    }

    @Test
    fun `onThinkTarget drops the change request when the decision epoch went stale`() {
        val state = MonsterBehaviorState().apply { targetChangeTicks = 950 }
        val requested = mutableListOf<TargetSearchType>()

        MonsterTargeting.onThinkTarget(
            MonsterTargetThinkRequest(
                state = state,
                interval = 100,
                isSummon = false,
                changeTargetSpeed = 1000,
                changeTargetChance = 100,
                targetDistance = 1,
                monsterPerfTestFriendlyFire = false,
                runAwayHealth = 0,
                rollTargetChange = { 1 },
                isDecisionEpochCurrent = { false },
                requestTargetSearchCompute = { requested.add(it) },
            )
        )

        assertThat(state.targetChangeTicks).isZero()
        assertThat(state.targetChangeCooldown).isEqualTo(1000)
        assertThat(requested).isEmpty()
    }

    @Test
    fun `onThinkTarget ticks the challenge focus down and blocks the change while it lasts`() {
        val state = MonsterBehaviorState().apply {
            targetChangeTicks = 950
            challengeFocusDuration = 250
        }
        val requested = mutableListOf<TargetSearchType>()

        MonsterTargeting.onThinkTarget(targetThinkRequest(state, interval = 100, requestedTypes = requested))

        assertThat(state.challengeFocusDuration).isEqualTo(150)
        assertThat(state.targetChangeTicks).isEqualTo(950)
        assertThat(requested).isEmpty()
    }

    @Test
    fun `onThinkTarget ticks the fatal hold down while fleeing`() {
        val state = MonsterBehaviorState().apply {
            targetChangeTicks = 950
            fatalHoldDuration = 120
        }
        val requested = mutableListOf<TargetSearchType>()

        MonsterTargeting.onThinkTarget(
            targetThinkRequest(state, interval = 100, runAwayHealth = 50, requestedTypes = requested)
        )

        assertThat(state.fatalHoldDuration).isEqualTo(20)
        assertThat(requested).containsExactly(TargetSearchType.Random)
    }

    @Test
    fun `onThinkTarget keeps skipping while the change cooldown is still running`() {
        val state = MonsterBehaviorState().apply { targetChangeCooldown = 150 }
        val requested = mutableListOf<TargetSearchType>()

        MonsterTargeting.onThinkTarget(targetThinkRequest(state, requestedTypes = requested))

        assertThat(state.targetChangeCooldown).isEqualTo(50)
        assertThat(state.targetChangeTicks).isZero()
        assertThat(requested).isEmpty()
    }

    @Test
    fun `onThinkTarget cooldown expiration rearms the tick counter and fires the change`() {
        val state = MonsterBehaviorState().apply { targetChangeCooldown = 50 }
        val requested = mutableListOf<TargetSearchType>()

        MonsterTargeting.onThinkTarget(targetThinkRequest(state, requestedTypes = requested))

        assertThat(state.targetChangeCooldown).isEqualTo(1000)
        assertThat(state.targetChangeTicks).isZero()
        assertThat(requested).containsExactly(TargetSearchType.Random)
    }

    @Test
    fun `subtractElapsedTime saturates at zero`() {
        assertThat(MonsterTargeting.subtractElapsedTime(250, 100)).isEqualTo(150)
        assertThat(MonsterTargeting.subtractElapsedTime(50, 100)).isZero()
        assertThat(MonsterTargeting.subtractElapsedTime(0, 100)).isZero()
        assertThat(MonsterTargeting.subtractElapsedTime(-5, 100)).isZero()
    }
}
