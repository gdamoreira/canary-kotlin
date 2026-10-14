package org.opentibia.canary.creatures.monsters

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.FlagAsyncClass_t
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.game.scheduling.ConcurrentTimedWork
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.map.SpectatorMapAccess
import org.opentibia.canary.map.Spectators

/**
 * Host that instantiates and owns the deferred [MonsterAsyncPipelines] for the
 * world monsters, replacing the per-`Monster` members of the C++ entity with a
 * world-confined registry keyed by re-resolvable monster id (AGENTS.md 1.1 and
 * deferred-callback lifetime rules).
 *
 * Wiring points and their Canary sources:
 * - [register]/[unregister]: the pipeline/state ownership `Monster` carries in
 *   C++ (monster.hpp:346-424); a completion that drains after the owner was
 *   removed re-resolves to `null` and no-ops, and a replaced owner resolves to
 *   a fresh pipeline whose generation guards reject the stale completion.
 * - [trySchedulePostThink]: the `Monster::trySchedulePostThink` call
 *   `Game::checkCreatures` performs after `Creature::onThink`
 *   (game.cpp:7719-7741, monster.cpp:1633-1638).
 * - [onExecuteAsyncTasks]: the body of the `Monster::onExecuteAsyncTasks`
 *   override (monster.cpp:3972-3996) reached from `Creature::executeAsyncTasks`
 *   (creature.cpp:2148-2184).
 * - [queueMonsterPostThink]/[processMonsterPostThinkQueue]/
 *   [cancelMonsterPostThinkQueue]: `Game::queueMonsterPostThink`
 *   (game.cpp:7694-7717) plus the anonymous-namespace queue helpers
 *   (game.cpp:85-200). The `g_dispatcher().addEvent`/`addDeferredGameplayEvent`
 *   admission is the injected [scheduleMonsterPostThinkQueue] seam.
 * - [requestUpdateTargetList]/[updateTargetList]: `Monster::updateTargetList`
 *   (monster.cpp:735-770). The world-lane callers set the async flag (the C++
 *   non-barrier branch); the barrier body prunes the lists, resets the
 *   spectator hold and scans `Spectators().find<Creature>(position, true, 0, 0,
 *   0, 0, false)` (monster.cpp:741-769).
 * - [onCreatureAppear]/[onRemoveCreature]: the `Monster` tails of the virtual
 *   hooks (monster.cpp:348-389 and 391-432) after the Lua
 *   `creatureAppearEvent`/`creatureDisappearEvent` dispatch and the
 *   `Creature` base call (AGENTS.md 1.4: the Lua dispatch stays Lua).
 * - [postThinkLateness]/[movementRefreshLateness]: the
 *   `g_dispatcher().observeInternalWork(MonsterPostThinkLateness/
 *   MonsterMovementRefreshLateness, 1, elapsed)` observations
 *   (monster.cpp:1668 and 513, dispatcher.cpp:378).
 *
 * COORDINATOR WIRING (outside the monster package):
 * - `Game.checkCreatures` (game.cpp:7719-7741): after `creature.onThink(...)`
 *   call `monsterHost.trySchedulePostThink(creature)`; non-monster creatures
 *   keep the `onAttacking`/`executeConditions` fallback.
 * - `Monster.onExecuteAsyncTasks` override (monster.cpp:3972):
 *   `override fun onExecuteAsyncTasks() = monsterHost.onExecuteAsyncTasks(id)`.
 * - `Monster.onCreatureAppear`/`onRemoveCreature` overrides (monster.cpp:348,
 *   391): after the Lua dispatch and `super` call,
 *   `monsterHost.onCreatureAppear(id, creature, isLogin)` /
 *   `monsterHost.onRemoveCreature(id, creature, isLogout)`.
 * - `Monster` placement/removal: `monsterHost.register(this)` when a monster
 *   enters the world and `monsterHost.unregister(id)` when it is removed.
 * - `Creature.scheduleAsyncTaskBatch` for each registered monster: route the
 *   batch to the dispatcher's worker-completion lane instead of the inline
 *   default.
 * - `scheduleMonsterPostThinkQueue`: `playerVisible` selects
 *   `GameDispatcher.dispatch(WorldCommit)`; background selects the deferred
 *   gameplay lane (`g_dispatcher().addDeferredGameplayEvent`, game.cpp:155-156).
 * - `GameDispatcher` has no `observeInternalWork` yet (PARITY GAP); the two
 *   [ConcurrentTimedWork] sinks below are the ported dispatcher telemetry
 *   primitives, and the coordinator rebinds the two host lambdas when the
 *   dispatcher exposes the sink.
 * - The type-dependent seams of each entry's [MonsterAsyncHost]
 *   (`attackSpells`, `castSpell`, `canUseAttack`, `strategiesTarget*`,
 *   `changeTargetChance`) are bound when [register] receives the monster's
 *   [MonsterType] (see [MonsterTypeState.bindHost]); without a type they stay
 *   at their inert defaults. `updateLookDirection`, `onThinkAsync`,
 *   `updateIdleStatus`, the navigation/path/walk seams and the faction/type
 *   fields of [MonsterAsyncHost.monsterFacts] stay with the coordinator.
 */
class MonsterPipelinesHost(
    private val service: MonsterComputeService,
    private val mapAccess: SpectatorMapAccess,
    /** `g_game().getCreatureByID` (game.cpp:1296). */
    private val resolveCreature: (Long) -> Creature? = { null },
    /** `MonsterRelevancePolicy::Clock::now()` / `Task::Clock::now()`, milliseconds. */
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** `g_configManager().getBoolean(MONSTER_PERF_TEST_FRIENDLY_FIRE)` seam. */
    var monsterPerfTestFriendlyFire: () -> Boolean = { false }

    /** `Player::isLoginProtected` (player.cpp:3472-3474) seam. */
    var isLoginProtected: (Player) -> Boolean = { false }

    /** `g_game().checkCreatureAttack(monster)` seam. */
    var checkCreatureAttack: (Monster) -> Unit = {}

    /**
     * `g_dispatcher().addEvent(task, "Game::visibleMonsterPostThink", 0,
     * WorldCommit)` / `g_dispatcher().addDeferredGameplayEvent(task,
     * "Game::backgroundMonsterPostThink")` admission seam (game.cpp:150-160).
     */
    var scheduleMonsterPostThinkQueue: (Boolean, () -> Unit) -> Boolean = { _, task ->
        task()
        true
    }

    /** `DispatcherInternalWork::MonsterPostThinkLateness` sink (monster.cpp:1668). */
    val postThinkLateness = ConcurrentTimedWork()

    /** `DispatcherInternalWork::MonsterMovementRefreshLateness` sink (monster.cpp:513). */
    val movementRefreshLateness = ConcurrentTimedWork()

    /**
     * One world monster's pipeline state: the C++ per-`Monster` members split
     * into the standalone [MonsterTargetListState]/[MonsterBehaviorState]/
     * [MonsterTypeState], the [MonsterAsyncHost] seam bag and the
     * [MonsterAsyncPipelines] state machine.
     */
    class Entry internal constructor(
        val monster: Monster,
        val targetState: MonsterTargetListState,
        val behaviorState: MonsterBehaviorState,
        val typeState: MonsterTypeState,
        val host: MonsterAsyncHost,
        val pipeline: MonsterAsyncPipelines,
    )

    private val entries = mutableMapOf<Long, Entry>()
    private val visiblePostThinkQueue = MonsterPostThinkQueue(MonsterPostThinkQueue.VISIBLE_BATCH_SIZE)
    private val backgroundPostThinkQueue = MonsterPostThinkQueue(MonsterPostThinkQueue.BACKGROUND_BATCH_SIZE)
    private val rejectedPostThink = longArrayOf(0L, 0L)

    /** Registry lookup; `null` once the owner was unregistered/replaced. */
    fun entryOf(monsterId: Long): Entry? = entries[monsterId]

    /** The registered pipeline for [monsterId] (test/coordinator read view). */
    fun pipelineOf(monsterId: Long): MonsterAsyncPipelines? = entries[monsterId]?.pipeline

    /** The forge/hazard/spell state of [monsterId] (`Monster::m_monsterType` family). */
    fun typeStateOf(monsterId: Long): MonsterTypeState? = entries[monsterId]?.typeState

    /**
     * Instantiates and binds the pipelines for [monster]. Registering a new
     * instance for an id whose owner was not unregistered first replaces the
     * entry: pending completions then re-resolve to the new pipeline and are
     * rejected by its generation guards (stale/replaced owners no-op).
     *
     * [monsterType] is the creature's [MonsterType] (`Monster::m_monsterType`);
     * when present the entry's [MonsterTypeState] is created from it and the
     * type-driven host seams are bound through [MonsterTypeState.bindHost].
     */
    fun register(monster: Monster, monsterType: MonsterType? = null): MonsterAsyncPipelines {
        val targetState = MonsterTargetListState()
        val behaviorState = MonsterBehaviorState()
        val typeState = MonsterTypeState(monsterType)
        val host = MonsterAsyncHost()
        val pipeline = MonsterAsyncPipelines(
            monsterId = monster.id,
            service = service,
            targetState = targetState,
            behaviorState = behaviorState,
            host = host,
        )
        val entry = Entry(monster, targetState, behaviorState, typeState, host, pipeline)
        entries[monster.id] = entry
        bind(entry)
        if (monsterType != null) {
            typeState.bindHost(host, monster) { creatureId -> resolveCreature(creatureId) }
        }
        return pipeline
    }

    /** Drops the owner so every pending completion re-resolves to `null`. */
    fun unregister(monsterId: Long) {
        entries.remove(monsterId)
    }

    private fun bind(entry: Entry) {
        val monster = entry.monster
        val host = entry.host

        host.resolveMonster = { id -> entries[id]?.pipeline }
        host.isRemoved = { monster.isRemoved() }
        host.isDead = { monster.isDead }
        host.isAlive = { monster.isAlive() }
        host.getPosition = { monster.position }
        host.now = clock
        host.setAsyncTaskFlag = { flag -> monster.setAsyncTaskFlag(flag, true) }
        host.hasAsyncTaskFlag = { flag -> monster.hasAsyncTaskFlag(flag) }
        host.addAsyncTask = { task -> monster.addAsyncTask(task) }
        host.queueMonsterPostThink = { id, playerVisible -> queueMonsterPostThink(id, playerVisible) }
        host.updateTargetList = { runUpdateTargetList(entry) }
        host.isIdle = { entry.behaviorState.isIdle }
        host.resolveCreature = { id -> resolveCreature(id)?.let(::creatureFacts) }
        host.canSee = { position -> monster.canSee(position) }
        host.monsterFacts = { defaultMonsterFacts(monster) }
        host.monsterPerfTestFriendlyFire = { this.monsterPerfTestFriendlyFire() }
        host.currentAttackedId = { monster.getAttackedCreature()?.id }
        host.followCreatureId = { monster.getFollowCreature()?.id }
        host.targetDistance = { entry.behaviorState.targetDistance }
        host.hasFollowPath = { monster.hasFollowPath }
        host.isSummon = { monster.isSummon() }
        host.canSeeInvisibility = { monster.canSeeInvisibility() }
        host.masterPlayerId = { (monster.getMaster() as? Player)?.id }
        host.masterHasAttackedCreature = { monster.getMaster()?.getAttackedCreature() != null }
        host.isOpponent = { facts ->
            MonsterTargeting.isOpponent(host.monsterFacts(), facts, host.monsterPerfTestFriendlyFire())
        }
        host.selectTarget = { facts -> selectTarget(entry, facts) }
        host.onCreatureEnter = { facts -> onCreatureEnter(entry, facts) }
        host.onCreatureLeave = { facts -> onCreatureLeave(entry, facts) }
        host.observePostThinkLateness = { elapsedMs ->
            postThinkLateness.observe(elapsedMs * 1_000, 1, "MonsterPostThinkLateness")
        }
        host.observeMovementRefreshLateness = { elapsedMs ->
            movementRefreshLateness.observe(elapsedMs * 1_000, 1, "MonsterMovementRefreshLateness")
        }
    }

    /**
     * `Monster::trySchedulePostThink` host entry for `Game::checkCreatures`
     * (game.cpp:7727-7729). The creature id re-resolves the owner, so a removed
     * or non-monster creature is a no-op.
     */
    fun trySchedulePostThink(creature: Creature): Boolean =
        entries[creature.id]?.pipeline?.trySchedulePostThink() ?: false

    /**
     * `Monster::onExecuteAsyncTasks` body (monster.cpp:3972-3996) for the
     * `Monster.onExecuteAsyncTasks` override the coordinator adds.
     */
    fun onExecuteAsyncTasks(monsterId: Long) {
        entries[monsterId]?.pipeline?.onExecuteAsyncTasks()
    }

    /**
     * `Monster::updateTargetList` world-lane branch (monster.cpp:736-739): park
     * the scan on the async task flag instead of scanning inline.
     */
    fun requestUpdateTargetList(monsterId: Long) {
        entries[monsterId]?.monster?.setAsyncTaskFlag(FlagAsyncClass_t.UpdateTargetList, true)
    }

    /**
     * `Monster::updateTargetList` barrier body (monster.cpp:741-769): prunes the
     * friend/target lists, resets the visible-spectator hold and scans
     * `Spectators().find<Creature>(position, true, 0, 0, 0, 0, false)`.
     */
    fun updateTargetList(monsterId: Long) {
        val entry = entries[monsterId] ?: return
        runUpdateTargetList(entry)
    }

    private fun runUpdateTargetList(entry: Entry) {
        if (!isRegistered(entry)) {
            return
        }
        val monster = entry.monster

        val friendIterator = entry.targetState.friendList.iterator()
        while (friendIterator.hasNext()) {
            val friend = resolveCreature(friendIterator.next())
            if (friend == null || friend.health <= 0 || !monster.canSee(friend.position)) {
                friendIterator.remove()
            }
        }

        val targetCountBeforeCleanup = entry.targetState.targetList.size
        val targetIterator = entry.targetState.targetList.iterator()
        while (targetIterator.hasNext()) {
            val reference = targetIterator.next()
            val target = resolveCreature(reference.creatureId)
            if (target == null || target.health <= 0 || !monster.canSee(target.position)) {
                MonsterTargeting.forgetTargetReference(entry.targetState, reference)
                targetIterator.remove()
            }
        }
        if (entry.targetState.targetList.size != targetCountBeforeCleanup) {
            MonsterTargeting.markTargetStateChanged(entry.targetState)
        }

        if (entry.pipeline.visiblePlayerSpectatorIds.isNotEmpty()) {
            entry.pipeline.playerVisibleUntil =
                clock() + MonsterAsyncPipelines.WALK_PLAYER_VISIBLE_HOLD_MILLIS
            entry.pipeline.visiblePlayerSpectatorIds.clear()
        }

        val host = entry.host
        val monsterFacts = host.monsterFacts()
        val monsterPerfTestFriendlyFire = host.monsterPerfTestFriendlyFire()
        for (spectator in Spectators(mapAccess).find<Creature>(monster.position, multifloor = true, useCache = false)) {
            if (spectator === monster || !monster.canSee(spectator.position)) {
                continue
            }
            val facts = creatureFacts(spectator)
            entry.pipeline.addVisiblePlayerSpectator(facts)
            MonsterTargeting.onCreatureFound(
                state = entry.targetState,
                monster = monsterFacts,
                creature = facts,
                pushFront = false,
                monsterPerfTestFriendlyFire = monsterPerfTestFriendlyFire,
                isIdle = host.isIdle(),
            ) { host.updateIdleStatus() }
        }
    }

    /**
     * `Monster::onCreatureAppear` tail (monster.cpp:348-389) after the Lua
     * `creatureAppearEvent` dispatch and `Creature::onCreatureAppear`.
     */
    fun onCreatureAppear(monsterId: Long, creature: Creature, isLogin: Boolean) {
        val entry = entries[monsterId] ?: return
        if (creature === entry.monster) {
            requestUpdateTargetList(monsterId)
            entry.host.updateIdleStatus()
            return
        }

        if (creature is Player && entry.monster.canSee(creature.position)) {
            entry.pipeline.observeVisiblePlayerForScheduling(creatureFacts(creature))
        }
        queueCreatureObservation(entry, creature) { current, facts -> onCreatureEnter(current, facts) }
    }

    /**
     * `Monster::onRemoveCreature` tail (monster.cpp:391-432) after the Lua
     * `creatureDisappearEvent` dispatch and `Creature::onRemoveCreature`.
     *
     * PARITY GAP: the self branch drops the spawn-owner
     * `startSpawnMonsterCheck()` (monster.cpp:414-416) and the `setIdle(true)`
     * side effects (walk event cancel, creature-check removal) that need the
     * spawn subsystem; only the idle latch is applied.
     */
    fun onRemoveCreature(monsterId: Long, creature: Creature, isLogout: Boolean) {
        val entry = entries[monsterId] ?: return
        if (creature === entry.monster) {
            entry.behaviorState.isIdle = true
            return
        }
        queueCreatureObservation(entry, creature) { current, facts -> onCreatureLeave(current, facts) }
    }

    /**
     * `addAsyncTask([this, creature] { onCreatureEnter/Leave(creature) })`
     * (monster.cpp:383, 425) with the deferred-callback lifetime rules: the
     * owner and the creature are re-resolved when the batch runs, and a
     * replaced owner is validated against the captured pipeline identity.
     */
    private fun queueCreatureObservation(
        entry: Entry,
        creature: Creature,
        observe: (Entry, TargetCreatureFacts) -> Unit,
    ) {
        val monsterId = entry.monster.id
        val creatureId = creature.id
        val pipeline = entry.pipeline
        entry.monster.addAsyncTask {
            val current = entries[monsterId] ?: return@addAsyncTask
            if (current.pipeline !== pipeline) {
                return@addAsyncTask
            }
            val resolved = resolveCreature(creatureId) ?: return@addAsyncTask
            observe(current, creatureFacts(resolved))
        }
    }

    private fun onCreatureEnter(entry: Entry, facts: TargetCreatureFacts) {
        if (!isRegistered(entry)) {
            return
        }
        entry.pipeline.observeVisiblePlayerForScheduling(facts)
        MonsterTargeting.onCreatureEnter(
            state = entry.targetState,
            monster = entry.host.monsterFacts(),
            creature = facts,
            monsterPerfTestFriendlyFire = entry.host.monsterPerfTestFriendlyFire(),
            isIdle = entry.host.isIdle(),
        ) { entry.host.updateIdleStatus() }
    }

    private fun onCreatureLeave(entry: Entry, facts: TargetCreatureFacts) {
        if (!isRegistered(entry)) {
            return
        }
        entry.pipeline.removeVisiblePlayerSpectator(facts)
        MonsterTargeting.onCreatureLeave(
            state = entry.targetState,
            monster = entry.host.monsterFacts(),
            creature = facts,
            monsterPerfTestFriendlyFire = entry.host.monsterPerfTestFriendlyFire(),
        ) { entry.host.updateIdleStatus() }
    }

    private fun selectTarget(entry: Entry, facts: TargetCreatureFacts): Boolean =
        isRegistered(entry) && MonsterTargeting.selectTarget(
            monster = entry.host.monsterFacts(),
            creature = facts,
            monsterPerfTestFriendlyFire = entry.host.monsterPerfTestFriendlyFire(),
            state = entry.targetState,
            setAttackedCreature = { target ->
                resolveCreature(target.id)?.let { entry.monster.setAttackedCreature(it) } ?: false
            },
            checkCreatureAttack = { checkCreatureAttack(entry.monster) },
            setFollowCreature = { target ->
                resolveCreature(target.id)?.let { entry.monster.setFollowCreature(it) } ?: false
            },
        )

    /**
     * `Game::queueMonsterPostThink` (game.cpp:7694-7717): admission-gated id
     * enqueue plus the first scheduling attempt.
     */
    fun queueMonsterPostThink(monsterId: Long, playerVisible: Boolean): Boolean {
        val queue = postThinkQueue(playerVisible)
        if (!queue.admission.tryReserve(MonsterPostThinkQueue.CAPACITY)) {
            observePostThinkRejection(playerVisible)
            return false
        }

        val shouldSchedule = queue.add(monsterId)
        return !shouldSchedule || schedulePostThinkQueue(playerVisible)
    }

    /** `scheduleMonsterPostThinkQueue` (game.cpp:150-160). */
    fun schedulePostThinkQueue(playerVisible: Boolean): Boolean {
        val accepted = scheduleMonsterPostThinkQueue(playerVisible) {
            processMonsterPostThinkQueue(playerVisible)
        }
        if (!accepted) {
            cancelMonsterPostThinkQueue(playerVisible)
        }
        return accepted
    }

    /** `processMonsterPostThinkQueue` (game.cpp:163-193). */
    fun processMonsterPostThinkQueue(playerVisible: Boolean) {
        val shouldReschedule = postThinkQueue(playerVisible).drainBatch { monsterId ->
            entries[monsterId]?.pipeline?.executePostThink(EVENT_CREATURE_THINK_INTERVAL, playerVisible)
        }
        if (shouldReschedule) {
            schedulePostThinkQueue(playerVisible)
        }
    }

    /** `cancelMonsterPostThinkQueue` (game.cpp:129-147). */
    fun cancelMonsterPostThinkQueue(playerVisible: Boolean) {
        postThinkQueue(playerVisible).cancelAll { monsterId ->
            entries[monsterId]?.pipeline?.cancelScheduledPostThink(playerVisible)
        }
    }

    /** Queued ids for the visible/background lane (telemetry read view). */
    fun queuedPostThinkCount(playerVisible: Boolean): Int = postThinkQueue(playerVisible).size

    /** `observeMonsterPostThinkRejection` counters (game.cpp:114-125). */
    fun rejectedPostThinkCount(playerVisible: Boolean): Long = rejectedPostThink[postThinkIndex(playerVisible)]

    /**
     * `Creature::getPlayer`/`getMaster`/`getMaster()->getPlayer`/`getFaction`/
     * `getZoneType`/`isAttackable`/`isInvisible`/`isSummon` projection read by
     * the targeting rules.
     *
     * PARITY GAPS: [TargetCreatureFacts.zoneType] and
     * [TargetCreatureFacts.isAttackable] need the placed tile and ghost-mode
     * state that the Kotlin `Creature` does not model yet, and the per-monster
     * `faction`/`isHostile` facts come from the monster type.
     */
    fun creatureFacts(creature: Creature): TargetCreatureFacts {
        val player = creature as? Player
        val master = creature.getMaster()
        return TargetCreatureFacts(
            id = creature.id,
            position = creature.position,
            health = creature.health,
            faction = if (player != null) Faction.Player else Faction.Default,
            playerId = player?.id,
            masterId = master?.id,
            masterPlayerId = (master as? Player)?.id,
            isRemoved = creature.isRemoved(),
            isLoginProtected = player != null && isLoginProtected(player),
            isInvisible = creature.isInvisible(),
            isSummon = creature.isSummon(),
            isMonster = creature is Monster,
            ignoresMonsters = player?.hasFlag(PlayerFlags_t.IgnoredByMonsters) ?: false,
        )
    }

    private fun defaultMonsterFacts(monster: Monster): MonsterTargetFacts =
        MonsterTargetFacts(
            origin = monster.position,
            id = monster.id,
            isSummon = monster.isSummon(),
            masterId = monster.getMaster()?.id,
            masterPlayerId = (monster.getMaster() as? Player)?.id,
            canSeeInvisibility = monster.canSeeInvisibility(),
        )

    /** A stale/replaced owner must become a no-op before any state is touched. */
    private fun isRegistered(entry: Entry): Boolean = entries[entry.monster.id] === entry

    private fun postThinkQueue(playerVisible: Boolean): MonsterPostThinkQueue =
        if (playerVisible) visiblePostThinkQueue else backgroundPostThinkQueue

    private fun postThinkIndex(playerVisible: Boolean): Int = if (playerVisible) 0 else 1

    private fun observePostThinkRejection(playerVisible: Boolean) {
        rejectedPostThink[postThinkIndex(playerVisible)]++
    }

    companion object {
        /** `Creature::EVENT_CREATURE_THINK_INTERVAL` (creature.hpp:47). */
        const val EVENT_CREATURE_THINK_INTERVAL: Int = 1_000
    }
}
