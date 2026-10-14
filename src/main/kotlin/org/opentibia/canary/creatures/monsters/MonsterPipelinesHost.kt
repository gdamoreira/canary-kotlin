package org.opentibia.canary.creatures.monsters

import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.FindPathParams
import org.opentibia.canary.creatures.FlagAsyncClass_t
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.ConcurrentTimedWork
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.SpectatorMapAccess
import org.opentibia.canary.map.Spectators
import org.opentibia.canary.map.cache.NavRegionSnapshot
import org.opentibia.canary.spells.SoundEffect
import java.lang.ref.WeakReference

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
 * - `Monster` placement/removal: `monsterHost.register(this, monsterType)` when
 *   a monster enters the world and `monsterHost.unregister(id)` when it is
 *   removed. `register` binds the creature-hook hosts
 *   (`onCreatureAppearHost`/`onRemoveCreatureHost`/`onCreatureMoveHost`) and the
 *   attack/extra-swing state (`hasExtraSwingProvider`, `checkCreatureAttack`)
 *   onto the `Monster`, so the existing virtual-hook overrides in
 *   `Creature.kt`/`Creature.Monster` need no call-site change.
 * - `Creature.kt` call-site needs (the overrides do not exist yet):
 *   `Monster.onExecuteAsyncTasks` must delegate to
 *   `monsterHost.onExecuteAsyncTasks(id)` (monster.cpp:3972-3996), and
 *   `Creature.scheduleAsyncTaskBatch` for each registered monster must route
 *   the batch to the dispatcher's `WorkerCompletion` lane instead of the
 *   inline default (creature.cpp:2048-2054). `Monster.updateCreatureWalk`
 *   (`goToFollowCreature`, creature.hpp:412-414) has no Monster override yet:
 *   the pathfinder flag hop (`executeAsyncTasks` → `goToFollowCreature`,
 *   creature.cpp:2162-2164) is the remaining call-site so the bound
 *   [MonsterAsyncHost.goToFollowCreatureAsync] can be reached from movement.
 * - The environment seams below are constructor-injected per AGENTS.md 1.1 and
 *   default to inert/inline values until the production composition root (no
 *   `Game`/`GameHandler` root wires them yet): [dispatcher],
 *   [navigationSnapshot]/[isNavigationTopologyCurrent], [startAutoWalk],
 *   [addEventWalk], [addCreatureCheck]/[removeCreatureCheck],
 *   [internalCreatureTurn], [monsterSay], [sendSoundEffect], [createMonster],
 *   [placeCreature], [setSoulPitStack], [addMagicEffect], [hasSpawnOwner],
 *   [pathSatisfiesFollow], [getDistanceStep] and [isMovementCommit].
 * - `Game.kt` call-site needs: the composition root must construct the host
 *   with `resolveCreature = game::getCreatureByID`,
 *   `dispatcher = g_dispatcher`, `navigationSnapshot`/`isNavigationTopologyCurrent`
 *   from `map`, the walk seams, the summon creation/placement seams,
 *   `addCreatureCheck`/`removeCreatureCheck` from `Game`, and the
 *   say/sound/turn senders. `Game.checkCreatureAttack` does not exist in this
 *   Canary revision; the attack entry is `Creature::checkCreatureAttack`
 *   (creature.cpp:174-186), bound by `register` and scheduled through
 *   [dispatcher] when present.
 * - `GameDispatcher` has no `observeInternalWork` yet (PARITY GAP); the two
 *   [ConcurrentTimedWork] sinks below are the ported dispatcher telemetry
 *   primitives, and the coordinator rebinds the two host lambdas when the
 *   dispatcher exposes the sink.
 * - The type-dependent seams of each entry's [MonsterAsyncHost]
 *   (`attackSpells`, `castSpell`, `canUseAttack`, `strategiesTarget*`,
 *   `changeTargetChance`) are bound when [register] receives the monster's
 *   [MonsterType] (see [MonsterTypeState.bindHost]); without a type they stay
 *   at their inert defaults.
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

    /**
     * `Creature::checkCreatureAttack(bool now = false)` (creature.cpp:174-186)
     * scheduling seam: with a running [dispatcher] the `now=true` body is
     * deferred to the `WorldCommit` lane (the C++ `g_dispatcher().addEvent`),
     * otherwise it runs inline on the world lane. A removed or replaced owner is
     * re-resolved by id and becomes a no-op.
     */
    var checkCreatureAttack: (Monster) -> Unit = { monster -> scheduleCheckCreatureAttack(monster) }

    /**
     * `g_dispatcher()` lane hop for the post-think queues: `playerVisible`
     * selects `dispatch(WorldCommit)` (`Game::visibleMonsterPostThink`,
     * game.cpp:150-160), background selects `dispatch(Deferred)`
     * (`g_dispatcher().addDeferredGameplayEvent`, game.cpp:155-156). `null`
     * runs the queue task inline (world-confined tests/embedded use).
     */
    var dispatcher: GameDispatcher? = null

    /**
     * `g_game().map.getNavigationSnapshot(start, radius)`
     * (`Map::getNavigationSnapshot`); the map does not build
     * [NavRegionSnapshot]s yet (PARITY GAP), so the coordinator injects the
     * provider.
     */
    var navigationSnapshot: (Position, Int) -> NavRegionSnapshot? = { _, _ -> null }

    /** `g_game().map.isNavigationTopologyCurrent(navigation)`. */
    var isNavigationTopologyCurrent: (NavRegionSnapshot) -> Boolean = { true }

    /** `Creature::startAutoWalk` (creature.cpp:326-340) destination seam. */
    var startAutoWalk: (Monster, List<Direction>) -> Unit = { _, _ -> }

    /** `Creature::addEventWalk` (creature.cpp:342-383) admission seam. */
    var addEventWalk: (Monster) -> Unit = {}

    /** `g_game().addCreatureCheck` (game.cpp:7679) / `Game::removeCreatureCheck` (game.cpp:7687). */
    var addCreatureCheck: (Monster) -> Unit = {}
    var removeCreatureCheck: (Monster) -> Unit = {}

    /** `g_game().internalCreatureTurn(monster, direction)` spectator-turn seam. */
    var internalCreatureTurn: (Monster, Direction) -> Unit = { monster, direction ->
        monster.direction = direction
    }

    /** `Monster::say(text, yell)` (monster.cpp:2274-2294) sender seam. */
    var monsterSay: (Monster, String, Boolean) -> Unit = { _, _, _ -> }

    /** `g_game().sendSingleSoundEffect(position, effect, source)` sender seam. */
    var sendSoundEffect: (Monster, SoundEffect) -> Unit = { _, _ -> }

    /** `Game::createMonster(name)` summon-creation seam. */
    var createMonster: (String) -> Monster? = { null }

    /** `Game::placeCreature(monster, position, forceTeleport, force)` summon-placement seam. */
    var placeCreature: (Monster, Position, Boolean, Boolean) -> Boolean = { _, _, _, _ -> false }

    /** `Monster::setSoulPitStack(summon, stack)` forge seam. */
    var setSoulPitStack: (Monster, Int, Boolean) -> Unit = { _, _, _ -> }

    /** `g_game().addMagicEffect(position, effect)` sender seam. */
    var addMagicEffect: (Position, Int) -> Unit = { _, _ -> }

    /** `Monster::masterPos` spawn-owner latch behind `Monster::isInSpawnLocation` (monster.cpp:1562-1567). */
    var hasSpawnOwner: (Monster) -> Boolean = { false }

    /** `FrozenPathingConditionCall(target)(start, test, fpp, bestMatch)` (creature.cpp:1819-1861). */
    var pathSatisfiesFollow: (Position, Position, FindPathParams) -> Boolean = { _, _, _ -> false }

    /** `Monster::getDistanceStep` (monster.cpp:2632-3127) step seam for the follow path. */
    var getDistanceStep: (Monster, Position, Boolean) -> DistanceStepResult = { _, _, _ ->
        DistanceStepResult(handled = false)
    }

    /** `g_dispatcher().context().isMovementCommit()` branch of `Monster::onCreatureMove` (monster.cpp:471-476). */
    var isMovementCommit: () -> Boolean = { true }

    /**
     * `g_dispatcher().addEvent(task, "Game::visibleMonsterPostThink", 0,
     * WorldCommit)` / `g_dispatcher().addDeferredGameplayEvent(task,
     * "Game::backgroundMonsterPostThink")` admission seam (game.cpp:150-160).
     * Defaults to the [dispatcher] lane hop; a test/embedding host can rebind it.
     */
    var scheduleMonsterPostThinkQueue: (Boolean, () -> Unit) -> Boolean = { playerVisible, task ->
        schedulePostThinkQueueOnLane(playerVisible, task)
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
        bindMonsterHooks(entry)
        if (monsterType != null) {
            typeState.bindHost(host, monster) { creatureId -> resolveCreature(creatureId) }
        }
        return pipeline
    }

    /**
     * `Monster::hasExtraSwing` (monster.cpp:2091-2093),
     * `Creature::checkCreatureAttack` (creature.cpp:174-186) and the
     * `Monster::onCreatureAppear`/`onRemoveCreature`/`onCreatureMove` tails
     * (monster.cpp:348-477) bound onto the owning [Monster]. Every lambda
     * re-resolves the entry by id so a removed/replaced owner becomes a no-op.
     */
    private fun bindMonsterHooks(entry: Entry) {
        val monster = entry.monster
        val monsterId = monster.id
        monster.hasExtraSwingProvider = { MonsterBehavior.hasExtraSwing(entry.behaviorState) }
        monster.checkCreatureAttack = { checkCreatureAttack(monster) }
        monster.onCreatureAppearHost = { creature, isLogin ->
            if (isCurrentOwner(monsterId, monster)) onCreatureAppear(monsterId, creature, isLogin)
        }
        monster.onRemoveCreatureHost = { creature, isLogout ->
            if (isCurrentOwner(monsterId, monster)) onRemoveCreature(monsterId, creature, isLogout)
        }
        monster.onCreatureMoveHost = { creature, oldPos, newPos ->
            if (isCurrentOwner(monsterId, monster)) onCreatureMove(monsterId, creature, oldPos, newPos)
        }
    }

    /** A reused id must not let a stale owner drive the replacement's entry. */
    private fun isCurrentOwner(monsterId: Long, monster: Monster): Boolean =
        entries[monsterId]?.monster === monster

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
        host.monsterFacts = { monsterFactsOf(entry) }
        host.monsterPerfTestFriendlyFire = { this.monsterPerfTestFriendlyFire() }
        host.currentAttackedId = { monster.getAttackedCreature()?.id }
        host.followCreatureId = { monster.getFollowCreature()?.id }
        host.targetDistance = { entry.behaviorState.targetDistance }
        host.hasFollowPath = { monster.hasFollowPath }
        host.isSummon = { monster.isSummon() }
        host.canSeeInvisibility = { monster.canSeeInvisibility() }
        host.masterPlayerId = { (monster.getMaster() as? Player)?.id }
        host.masterHasAttackedCreature = { monster.getMaster()?.getAttackedCreature() != null }
        host.isFleeing = { isFleeingOf(entry) }
        host.isFamiliar = { entry.typeState.monsterType?.info?.isFamiliar ?: false }
        host.ignoreFieldDamage = { entry.behaviorState.ignoreFieldDamage }
        host.canPushItems = { entry.typeState.monsterType?.info?.canPushItems ?: false }
        host.canPushCreatures = { entry.typeState.monsterType?.info?.canPushCreatures ?: false }
        host.isImmuneCombat = { combatType ->
            MonsterBehavior.isImmune(
                combatType = combatType,
                immuneOverride = entry.behaviorState.immune,
                damageImmunities = entry.typeState.monsterType?.info?.m_damageImmunities
                    ?.map { CombatType.fromValue(it.value) }
                    ?.toSet()
                    ?: emptySet(),
            )
        }
        host.isOpponent = { facts ->
            MonsterTargeting.isOpponent(host.monsterFacts(), facts, host.monsterPerfTestFriendlyFire())
        }
        host.selectTarget = { facts -> selectTarget(entry, facts) }
        host.onCreatureEnter = { facts -> onCreatureEnter(entry, facts) }
        host.onCreatureLeave = { facts -> onCreatureLeave(entry, facts) }
        host.onAttacking = { interval -> runOnAttacking(entry, interval) }
        host.updateIdleStatus = { runUpdateIdleStatus(entry) }
        host.onThinkAsync = { runOnThinkAsync(entry) }
        host.updateLookDirection = { runUpdateLookDirection(entry) }
        host.getNavigationSnapshot = { position, radius -> navigationSnapshot(position, radius) }
        host.isNavigationTopologyCurrent = { navigation -> isNavigationTopologyCurrent(navigation) }
        host.startAutoWalk = { directions -> startAutoWalk(monster, directions) }
        host.setHasFollowPath = { value -> monster.hasFollowPath = value }
        host.markForceUpdateFollowPath = { monster.forceUpdateFollowPath = true }
        host.goToFollowCreatureAsync = { runGoToFollowCreature(entry) }
        host.onFollowCreatureComplete = { target -> runOnFollowCreatureComplete(entry, target) }
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
     * `Monster::onCreatureMove` tail (monster.cpp:434-477) after the Lua
     * `creatureMoveEvent` dispatch and `Creature::onCreatureMove`: a self move
     * refreshes the target list and the idle status; another creature's move is
     * coalesced through [MonsterAsyncPipelines.queueMovementAiRefresh] when the
     * movement commit owns the context, otherwise processed inline.
     */
    fun onCreatureMove(monsterId: Long, creature: Creature, oldPos: Position, newPos: Position) {
        val entry = entries[monsterId] ?: return
        if (creature === entry.monster) {
            runUpdateTargetList(entry)
            runUpdateIdleStatus(entry)
            return
        }

        val facts = creatureFacts(creature)
        if (isMovementCommit()) {
            entry.pipeline.queueMovementAiRefresh(facts, oldPos, newPos)
        } else {
            entry.pipeline.processMovementAiRefresh(facts, oldPos, newPos)
        }
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
     * state that the Kotlin `Creature` does not model yet. The monster-side
     * `faction`/`isHostile`/`enemyFactions` facts are bound by [monsterFactsOf]
     * over the registered [MonsterTypeState].
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

    /**
     * `Monster::getFaction`/`getEnemyFactions`/`isHostile`/`isSummon`/
     * `getMaster()->getID()` projection of the owning monster
     * (monster.cpp:260-297) over the registered type state. A monster master
     * delegates its facts recursively so `effectiveFaction` follows the C++
     * `master->getFaction()` chain.
     */
    private fun monsterFactsOf(entry: Entry): MonsterTargetFacts {
        val monster = entry.monster
        val info = entry.typeState.monsterType?.info
        val master = monster.getMaster()
        val masterFacts = if (master is Monster) {
            entries[master.id]?.let { monsterFactsOf(it) }
        } else {
            null
        }
        return MonsterTargetFacts(
            origin = monster.position,
            id = monster.id,
            faction = if (master is Player) Faction.Player else (info?.faction ?: Faction.Default),
            enemyFactions = info?.enemyFactions ?: emptySet(),
            master = masterFacts,
            isSummon = monster.isSummon(),
            isHostile = info?.isHostile ?: true,
            canSeeInvisibility = monster.canSeeInvisibility(),
            masterId = master?.id,
            masterPlayerId = (master as? Player)?.id,
        )
    }

    /**
     * `Monster::isFleeing` (monster.cpp:1456-1458): a non-summon at or below
     * its type's `runAwayHealth` while no challenge/fatal hold is active.
     */
    private fun isFleeingOf(entry: Entry): Boolean {
        val monster = entry.monster
        val state = entry.behaviorState
        val runAwayHealth = entry.typeState.monsterType?.info?.runAwayHealth ?: 0
        return !monster.isSummon() && monster.health <= runAwayHealth &&
            state.challengeFocusDuration <= 0 && state.challengeMeleeDuration <= 0 && state.fatalHoldDuration <= 0
    }

    /**
     * `Creature::checkCreatureAttack` (creature.cpp:174-186). The weak owner
     * reference plus the registry re-resolution keep a removed/replaced monster
     * a no-op across the dispatcher hop (AGENTS.md deferred-callback rules).
     */
    private fun scheduleCheckCreatureAttack(monster: Monster) {
        val monsterId = monster.id
        val dispatcher = dispatcher
        if (dispatcher != null && dispatcher.isRunning) {
            val weakMonster = WeakReference(monster)
            dispatcher.dispatch(DispatcherLane.WorldCommit) {
                val owner = weakMonster.get() ?: return@dispatch
                val current = entries[monsterId] ?: return@dispatch
                if (current.monster === owner && owner.isAlive()) {
                    runOnAttacking(current, 0)
                }
            }
            return
        }
        val current = entries[monsterId] ?: return
        if (current.monster === monster && monster.isAlive()) {
            runOnAttacking(current, 0)
        }
    }

    /** `scheduleMonsterPostThinkQueue` lane hop (game.cpp:150-160). */
    private fun schedulePostThinkQueueOnLane(playerVisible: Boolean, task: () -> Unit): Boolean {
        val dispatcher = dispatcher
        if (dispatcher == null) {
            task()
            return true
        }
        if (!dispatcher.isRunning) {
            return false
        }
        val lane = if (playerVisible) DispatcherLane.WorldCommit else DispatcherLane.Deferred
        dispatcher.dispatch(lane) { task() }
        return true
    }

    /**
     * `Creature::onAttacking` (creature.cpp:188-207) over the bound monster:
     * the sight-clear gate and the `Monster::doAttacking` accumulation. The
     * `onAttacked` hook fan-out is the coordinator/sender owner (PARITY GAP).
     */
    private fun runOnAttacking(entry: Entry, interval: Int) {
        if (!isRegistered(entry)) {
            return
        }
        val monster = entry.monster
        val attackedCreature = monster.getAttackedCreature() ?: return
        if (!entry.host.isSightClear(monster.position, attackedCreature.position)) {
            return
        }
        MonsterBehavior.doAttacking(
            MonsterAttackingRequest(
                state = entry.behaviorState,
                monsterId = monster.id,
                isSummon = monster.isSummon(),
                attackedCreature = attackedCreature,
                interval = interval,
                attackedIsLifeless = !attackedCreature.isAlive(),
                attackedIsLoginProtected = (attackedCreature as? Player)?.let { isLoginProtected(it) } ?: false,
                requestCombatIntention = { intentionInterval, target ->
                    entry.pipeline.requestCombatIntention(intentionInterval, creatureFacts(target))
                },
            ),
        )
    }

    /**
     * `Monster::updateIdleStatus` decision body (monster.cpp:1540-1555) plus
     * `Monster::setIdle` (monster.cpp:1499-1519). The non-barrier
     * `setAsyncTaskFlag(UpdateIdleStatus)` hop and the
     * `MONSTER_PERF_TEST_FORCE_ACTIVE` branch stay with the caller
     * (`MonsterThink.onThink`/`MonsterPipelinesHost` flag dispatch); the
     * single world lane has no barrier context.
     */
    private fun runUpdateIdleStatus(entry: Entry) {
        if (!isRegistered(entry)) {
            return
        }
        val monster = entry.monster
        val master = monster.getMaster()
        MonsterBehavior.updateIdleStatus(
            MonsterIdleRequest(
                state = entry.behaviorState,
                position = monster.position,
                masterPos = master?.position ?: Position(0, 0, 0),
                hasSpawnOwner = hasSpawnOwner(monster),
                hasConditions = monster.conditions.isNotEmpty(),
                isSummon = monster.isSummon(),
                hasMaster = master != null,
                targetListEmpty = entry.targetState.targetList.isEmpty(),
                totalPlayersOnScreen = entry.targetState.totalPlayersOnScreen,
                masterMonsterPlayersOnScreen = (master as? Monster)?.let { entries[it.id]?.targetState?.totalPlayersOnScreen },
                factionIsDefault = entry.host.monsterFacts().effectiveFaction() == Faction.Default,
                isRemoved = monster.isRemoved(),
                health = monster.health,
                onIdleStatus = { monster.onIdleStatus() },
                clearTargetList = { MonsterTargeting.clearTargetList(entry.targetState) },
                clearFriendList = { MonsterTargeting.clearFriendList(entry.targetState) },
                addCreatureCheck = { addCreatureCheck(monster) },
                removeCreatureCheck = { removeCreatureCheck(monster) },
            ),
        )
    }

    /** `Monster::onThink_async` body (monster.cpp:1708-1752) over the bound entry. */
    private fun runOnThinkAsync(entry: Entry): Boolean {
        if (!isRegistered(entry)) {
            return false
        }
        val monster = entry.monster
        val state = entry.behaviorState
        val attacked = monster.getAttackedCreature()
        return MonsterThink.onThinkAsync(
            MonsterThinkAsyncRequest(
                state = state,
                position = monster.position,
                isIdle = state.isIdle,
                isSummon = monster.isSummon(),
                targetListEmpty = entry.targetState.targetList.isEmpty(),
                monsterPerfTestFriendlyFire = monsterPerfTestFriendlyFire(),
                attackedCreatureId = attacked?.id,
                followCreatureId = monster.getFollowCreature()?.id,
                hasFollowPath = monster.hasFollowPath,
                targetDistance = state.targetDistance,
                targetListEmptyAfterRefresh = { entry.targetState.targetList.isEmpty() },
                canUseAttack = { position ->
                    attacked?.let { target -> entry.host.canUseAttack(position, creatureFacts(target)) } ?: true
                },
                isFleeing = isFleeingOf(entry),
                addEventWalk = { addEventWalk(monster) },
                updateSummonTarget = { runUpdateSummonTarget(entry) },
                updateTargetList = { runUpdateTargetList(entry) },
                searchTarget = { searchType -> entry.pipeline.requestTargetSearchCompute(searchType) },
                onThinkTarget = { runOnThinkTarget(entry) },
                onThinkYell = { runOnThinkYell(entry) },
                onThinkDefense = { runOnThinkDefense(entry) },
                onThinkSound = { runOnThinkSound(entry) },
                queuePostThinkAfterAsync = { entry.pipeline.queuePostThinkAfterAsync() },
            ),
        )
    }

    /**
     * `Monster::updateSummonTarget` (monster.cpp:1332-1345) behind the
     * `safeCall` target-decision-epoch guard of `Monster::onThink_async`
     * (monster.cpp:1712-1720).
     */
    private fun runUpdateSummonTarget(entry: Entry) {
        if (!isRegistered(entry) || entry.monster.isRemoved()) {
            return
        }
        val monster = entry.monster
        val decisionEpoch = entry.targetState.targetDecisionEpoch
        val master = monster.getMaster()
        MonsterTargeting.updateSummonTarget(
            monsterId = monster.id,
            attackedCreatureId = monster.getAttackedCreature()?.id,
            followCreatureId = monster.getFollowCreature()?.id,
            masterId = master?.id,
            masterAttackedCreatureId = master?.getAttackedCreature()?.id,
            resolve = { id -> resolveCreature(id)?.let(::creatureFacts) },
            selectTarget = { facts -> selectTarget(entry, facts) },
            setFollowCreature = { facts ->
                if (entry.targetState.targetDecisionEpoch == decisionEpoch) {
                    monster.setFollowCreature(facts?.let { resolveCreature(it.id) })
                }
            },
        )
    }

    /** `Monster::onThinkTarget` (monster.cpp:2141-2200) over the bound entry. */
    private fun runOnThinkTarget(entry: Entry) {
        if (!isRegistered(entry)) {
            return
        }
        val monster = entry.monster
        val decisionEpoch = entry.targetState.targetDecisionEpoch
        MonsterTargeting.onThinkTarget(
            MonsterTargetThinkRequest(
                state = entry.behaviorState,
                interval = EVENT_CREATURE_THINK_INTERVAL,
                isSummon = monster.isSummon(),
                changeTargetSpeed = entry.typeState.monsterType?.info?.changeTargetSpeed ?: 0,
                changeTargetChance = entry.host.changeTargetChance(),
                targetDistance = entry.behaviorState.targetDistance,
                monsterPerfTestFriendlyFire = monsterPerfTestFriendlyFire(),
                runAwayHealth = entry.typeState.monsterType?.info?.runAwayHealth ?: 0,
                rollTargetChange = { entry.host.random(1, 100) },
                isDecisionEpochCurrent = { entry.targetState.targetDecisionEpoch == decisionEpoch },
                requestTargetSearchCompute = { searchType -> entry.pipeline.requestTargetSearchCompute(searchType) },
            ),
        )
    }

    /** `Monster::onThinkYell` (monster.cpp:2274-2294) over the type voice vector. */
    private fun runOnThinkYell(entry: Entry) {
        if (!isRegistered(entry)) {
            return
        }
        val info = entry.typeState.monsterType?.info ?: return
        MonsterThink.onThinkYell(
            MonsterYellRequest(
                state = entry.behaviorState,
                interval = EVENT_CREATURE_THINK_INTERVAL,
                yellSpeedTicks = info.yellSpeedTicks,
                yellChance = info.yellChance,
                voiceVector = info.voiceVector,
                nextRandom = { min, max -> entry.host.random(min, max) },
                nextIndex = { size -> if (size <= 0) 0 else entry.host.random(0, size - 1) },
                say = { text, yell -> monsterSay(entry.monster, text, yell) },
            ),
        )
    }

    /** `Monster::onThinkDefense` (monster.cpp:2202-2272) over the type defense spells/summons. */
    private fun runOnThinkDefense(entry: Entry) {
        if (!isRegistered(entry)) {
            return
        }
        val monster = entry.monster
        val info = entry.typeState.monsterType?.info
        MonsterBehavior.onThinkDefense(
            state = entry.behaviorState,
            monster = monster,
            defenseSpells = entry.typeState.defenseSpells,
            interval = EVENT_CREATURE_THINK_INTERVAL,
            nextRandom = { min, max -> entry.host.random(min, max) },
            isSummon = monster.isSummon(),
            summonBlocks = info?.summons ?: emptyList(),
            maxSummons = info?.maxSummons ?: 0,
            summons = monster.getSummons().filterIsInstance<Monster>().toMutableList(),
            hasFollowPath = monster.hasFollowPath,
            soulPit = entry.typeState.soulPit,
            forgeStack = entry.typeState.forgeStack,
            createMonster = { name -> createMonster(name) },
            placeCreature = { summon, position, forceTeleport, force ->
                placeCreature(summon, position, forceTeleport, force)
            },
            setMaster = { summon, master, reload -> summon.setMaster(master, reload) },
            setSoulPitStack = { summon, stack, flag -> setSoulPitStack(summon, stack, flag) },
            addMagicEffect = { position, effect -> addMagicEffect(position, effect) },
            sendSoundEffect = { _, sound, _ -> sendSoundEffect(monster, sound) },
        )
    }

    /** `Monster::onThinkSound` (monster.cpp:2296-2310) over the type sound vector. */
    private fun runOnThinkSound(entry: Entry) {
        if (!isRegistered(entry)) {
            return
        }
        val info = entry.typeState.monsterType?.info ?: return
        MonsterThink.onThinkSound(
            MonsterSoundRequest(
                monster = entry.monster,
                state = entry.behaviorState,
                interval = EVENT_CREATURE_THINK_INTERVAL,
                soundSpeedTicks = info.soundSpeedTicks,
                soundChance = info.soundChance,
                soundVector = info.soundVector,
                nextRandom = { min, max -> entry.host.random(min, max) },
                nextIndex = { size -> if (size <= 0) 0 else entry.host.random(0, size - 1) },
                sendSoundEffect = { _, sound, _ -> sendSoundEffect(entry.monster, sound) },
            ),
        )
    }

    /** `Monster::updateLookDirection` (monster.cpp:3356-3413) + `internalCreatureTurn`. */
    private fun runUpdateLookDirection(entry: Entry) {
        if (!isRegistered(entry)) {
            return
        }
        val monster = entry.monster
        val newDirection = MonsterBehavior.updateLookDirection(
            currentDirection = monster.direction,
            pos = monster.position,
            attackedCreaturePos = monster.getAttackedCreature()?.position,
        ) ?: return
        internalCreatureTurn(monster, newDirection)
    }

    /** `Monster::onFollowCreatureComplete` (monster.cpp:1362-1366). */
    private fun runOnFollowCreatureComplete(entry: Entry, creature: TargetCreatureFacts) {
        if (!isRegistered(entry)) {
            return
        }
        val monster = entry.monster
        if (MonsterTargeting.removeTarget(entry.targetState, creature) &&
            (monster.hasFollowPath || !monster.isSummon())
        ) {
            MonsterTargeting.addTarget(
                state = entry.targetState,
                monster = entry.host.monsterFacts(),
                creature = creature,
                pushFront = monster.hasFollowPath,
            )
        }
    }

    /**
     * `Creature::goToFollowCreature` (creature.cpp:1058-1120) for the monster
     * over the deferred follow-path compute: the summon follower guard, the
     * distance-step pre-branch (`Monster::getDistanceStep` seam), the
     * current-position satisfaction check (`FrozenPathingConditionCall` seam)
     * and [MonsterAsyncPipelines.requestFollowPathCompute]. The synchronous
     * `getPathTo` fallback (creature.cpp:1113) is a PARITY GAP.
     */
    private fun runGoToFollowCreature(entry: Entry) {
        if (!isRegistered(entry)) {
            return
        }
        val monster = entry.monster
        val followCreature = monster.getFollowCreature() ?: return

        if (monster.isSummon() && !entry.host.isFamiliar() && !canFollowMaster(monster)) {
            entry.pipeline.supersedeFollowPathCompute()
            startAutoWalk(monster, emptyList())
            return
        }

        val followFacts = creatureFacts(followCreature)
        val fpp = pathSearchParams(entry, followCreature)
        val fleeing = isFleeingOf(entry)
        var executeOnFollow = true
        val listDir = mutableListOf<Direction>()

        if (!monster.isSummon() && (fleeing || fpp.maxTargetDist > 1)) {
            if (fleeing) {
                getDistanceStep(monster, followCreature.position, true)
            } else {
                val step = getDistanceStep(monster, followCreature.position, false)
                if (!step.handled) {
                    executeOnFollow = false
                } else if (step.direction != null) {
                    listDir.add(step.direction)
                    monster.hasFollowPath = true
                }
            }
        }

        if (listDir.isEmpty()) {
            val currentPositionSatisfiesFollow = fpp.minTargetDist == fpp.maxTargetDist &&
                pathSatisfiesFollow(followCreature.position, monster.position, fpp)
            if (currentPositionSatisfiesFollow) {
                monster.hasFollowPath = true
            } else if (entry.pipeline.requestFollowPathCompute(followFacts, fpp, executeOnFollow)) {
                return
            } else {
                monster.hasFollowPath = false
            }
        }

        entry.pipeline.supersedeFollowPathCompute()
        startAutoWalk(monster, listDir)
        if (executeOnFollow) {
            runOnFollowCreatureComplete(entry, followFacts)
        }
    }

    /**
     * `Monster::getPathSearchParams` (monster.cpp:3828-3855) over
     * `Creature::getPathSearchParams` (creature.cpp:1038-1044).
     */
    private fun pathSearchParams(entry: Entry, followCreature: Creature): FindPathParams {
        val monster = entry.monster
        val state = entry.behaviorState
        val fpp = FindPathParams()
        fpp.fullPathSearch = !monster.hasFollowPath
        fpp.clearSight = true
        fpp.maxSearchDist = MonsterAsyncPipelines.MAX_BACKGROUND_FOLLOW_PATH_RADIUS
        fpp.minTargetDist = 1
        fpp.maxTargetDist = state.targetDistance

        val followFacts = creatureFacts(followCreature)
        if (monster.isSummon()) {
            val master = monster.getMaster()
            if (master != null && master === followCreature) {
                fpp.maxTargetDist = 2
                fpp.fullPathSearch = true
            } else if (state.targetDistance <= 1) {
                fpp.fullPathSearch = true
            } else {
                fpp.fullPathSearch = !entry.host.canUseAttack(monster.position, followFacts)
            }
        } else if (isFleeingOf(entry)) {
            // Distance should be higher than the client view range.
            fpp.maxTargetDist = MAP_MAX_CLIENT_VIEW_PORT_X
            fpp.clearSight = false
            fpp.keepDistance = true
            fpp.fullPathSearch = false
        } else if (state.targetDistance <= 1) {
            fpp.fullPathSearch = true
        } else {
            fpp.fullPathSearch = !entry.host.canUseAttack(monster.position, followFacts)
        }
        return fpp
    }

    /** `Creature::canFollowMaster` (creature.cpp:1122-1130) minus the placed master tile check (PARITY GAP). */
    private fun canFollowMaster(monster: Monster): Boolean {
        val master = monster.getMaster() ?: return false
        return monster.canSeeInvisibility() || !master.isInvisible()
    }

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

        /** `MAP_MAX_CLIENT_VIEW_PORT_X` (src/map/map_const.hpp:12), the fleeing follow search distance. */
        const val MAP_MAX_CLIENT_VIEW_PORT_X: Int = 8
    }
}
