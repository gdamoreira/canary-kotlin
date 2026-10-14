package org.opentibia.canary.creatures.monsters

import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.creatures.FlagAsyncClass_t
import org.opentibia.canary.creatures.FindPathParams
import org.opentibia.canary.creatures.MonsterPathRequest
import org.opentibia.canary.creatures.MonsterPathResult
import org.opentibia.canary.creatures.MonsterPathStatus
import org.opentibia.canary.creatures.MonsterPathTraits
import org.opentibia.canary.creatures.MonsterPathfinder
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.movement.getDistanceX
import org.opentibia.canary.game.movement.getDistanceY
import org.opentibia.canary.game.movement.getNextPosition
import org.opentibia.canary.game.movement.getOffsetX
import org.opentibia.canary.game.movement.getOffsetY
import org.opentibia.canary.game.scheduling.CoalescedTaskState
import org.opentibia.canary.game.scheduling.DispatcherPolicy
import org.opentibia.canary.game.scheduling.MonsterComputeCompletion
import org.opentibia.canary.game.scheduling.MonsterComputePriority
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.cache.NavRegionSnapshot

/**
 * Host seam for the game-side state and mutations the deferred `Monster` pipelines
 * read. Every lambda maps to a C++ expression (`g_game()`, `g_configManager()`,
 * `Monster`/`Creature` getters) that is constructor-injected per AGENTS.md 1.1
 * instead of reaching a global. The pipeline never retains a caller object across
 * the off-thread boundary: ids are re-resolved through [resolveMonster] and
 * [resolveCreature] when the completion runs back on the world lane.
 */
class MonsterAsyncHost {
    /** `g_game().getMonsterByID(monsterId)`; the completion re-resolves its owner. */
    var resolveMonster: (Long) -> MonsterAsyncPipelines? = { null }

    /** `Monster::isRemoved()`. */
    var isRemoved: () -> Boolean = { false }

    /** `Monster::isDead()`. */
    var isDead: () -> Boolean = { false }

    /** `Creature::isAlive()` (`health > 0`). */
    var isAlive: () -> Boolean = { true }

    /** `g_dispatcher().observeInternalWork(MonsterPostThinkLateness, ...)`, elapsed milliseconds. */
    var observePostThinkLateness: (Long) -> Unit = {}

    /** `Monster::getPosition()`. */
    var getPosition: () -> Position = { Position(0, 0, 0) }

    /** Monotonic (steady-clock) milliseconds; `MonsterRelevancePolicy::Clock::now()`. */
    var now: () -> Long = System::currentTimeMillis

    /** `Creature::setAsyncTaskFlag`. */
    var setAsyncTaskFlag: (FlagAsyncClass_t) -> Unit = {}

    /** `Creature::hasAsyncTaskFlag`. */
    var hasAsyncTaskFlag: (FlagAsyncClass_t) -> Boolean = { false }

    /** `Monster` facts read by `Monster::isTarget`. */
    var monsterFacts: () -> MonsterTargetFacts = { MonsterTargetFacts(origin = Position(0, 0, 0)) }

    /** `g_configManager().getBoolean(MONSTER_PERF_TEST_FRIENDLY_FIRE)`. */
    var monsterPerfTestFriendlyFire: () -> Boolean = { false }

    /** `getAttackedCreature()->getID()` as a re-resolvable id. */
    var currentAttackedId: () -> Long? = { null }

    /** `getFollowCreature()->getID()` as a re-resolvable id. */
    var followCreatureId: () -> Long? = { null }

    /** `g_game().getCreatureByID`. */
    var resolveCreature: (Long) -> TargetCreatureFacts? = { null }

    /** `Monster::targetDistance`. */
    var targetDistance: () -> Int = { 1 }

    /** `Creature::hasFollowPath`. */
    var hasFollowPath: () -> Boolean = { false }

    /** `Monster::canUseAttack`. */
    var canUseAttack: (Position, TargetCreatureFacts) -> Boolean = { _, _ -> true }

    /** `damageMap` total for a creature id (`null` when absent). */
    var damageDealt: (Long) -> Int? = { null }

    /** `Monster::selectTarget`. */
    var selectTarget: (TargetCreatureFacts) -> Boolean = { true }

    /** `uniform_random(min, max)`, inclusive. */
    var random: (Int, Int) -> Int = { min, _ -> min }

    /** `m_monsterType->info.strategiesTargetNearest` / `...Health` / `...Damage`. */
    var strategiesTargetNearest: () -> Int = { 0 }
    var strategiesTargetHealth: () -> Int = { 0 }
    var strategiesTargetDamage: () -> Int = { 0 }

    /** `Creature::promoteWalkEventToPlayerVisibleLane()`. */
    var promoteWalkEventToPlayerVisibleLane: () -> Unit = {}

    /** `Monster::attackSpells`. */
    var attackSpells: () -> List<MonsterSpellBlock> = { emptyList() }

    /** `g_monsters().getReloadEpoch()`. */
    var monsterReloadEpoch: () -> Long = { 0L }

    /** `Monster::isFleeing()`. */
    var isFleeing: () -> Boolean = { false }

    /** `Monster::getZoneType() == ZONE_PROTECTION`. */
    var isProtectionZone: () -> Boolean = { false }

    /** `g_game().isSightClear(origin, target, true)`. */
    var isSightClear: (Position, Position) -> Boolean = { _, _ -> true }

    /** `Monster::updateLookDirection()`. */
    var updateLookDirection: () -> Unit = {}

    /** `spellBlock.spell->castSpell(monster, target)`. */
    var castSpell: (MonsterSpellBlock, TargetCreatureFacts) -> Unit = { _, _ -> }

    /** `g_game().map.getNavigationSnapshot(start, radius)`. */
    var getNavigationSnapshot: (Position, Int) -> NavRegionSnapshot? = { _, _ -> null }

    /** `g_game().map.isNavigationTopologyCurrent(navigation)`. */
    var isNavigationTopologyCurrent: (NavRegionSnapshot) -> Boolean = { true }

    /** `Creature::startAutoWalk`. */
    var startAutoWalk: (List<Direction>) -> Unit = {}

    /** `Creature::hasFollowPath` write shared with the walk subsystem. */
    var setHasFollowPath: (Boolean) -> Unit = {}

    /** `Monster::onFollowCreatureComplete`. */
    var onFollowCreatureComplete: (TargetCreatureFacts) -> Unit = {}

    /** `Creature::goToFollowCreature_async()`. */
    var goToFollowCreatureAsync: () -> Unit = {}

    /** `Creature::forceUpdateFollowPath = true` (monster.cpp:3602). */
    var markForceUpdateFollowPath: () -> Unit = {}

    /** `Monster::canPushItems` / `canPushCreatures` / `isSummon` / `canSeeInvisibility`. */
    var canPushItems: () -> Boolean = { false }
    var canPushCreatures: () -> Boolean = { false }
    var isSummon: () -> Boolean = { false }
    var canSeeInvisibility: () -> Boolean = { false }

    /** `Creature::isMoveLocked()`. */
    var isMoveLocked: () -> Boolean = { false }

    /** `Monster::isFamiliar()`. */
    var isFamiliar: () -> Boolean = { false }

    /** `getMaster()->getAttackedCreature() != nullptr`. */
    var masterHasAttackedCreature: () -> Boolean = { false }

    /** `getMaster()->getPlayer()->getID()` as a re-resolvable id. */
    var masterPlayerId: () -> Long? = { null }

    /** `house->isInvited(masterPlayer)`. */
    var isHouseInvited: (Int, Long) -> Boolean = { _, _ -> false }

    /** `Monster::getIgnoreFieldDamage()`. */
    var ignoreFieldDamage: () -> Boolean = { false }

    /** `Monster::isImmune(combatType)`. */
    var isImmuneCombat: (CombatType) -> Boolean = { false }

    /** `Monster::canWalkOnFieldType(combatType)`. */
    var canWalkOnField: (CombatType) -> Boolean = { false }

    /** `hasCondition(Combat::DamageToConditionType(combatType))`. */
    var hasFieldCondition: (CombatType) -> Boolean = { false }

    /** `g_game().queueMonsterPostThink(monsterId, playerVisible)`. */
    var queueMonsterPostThink: (Long, Boolean) -> Boolean = { _, _ -> true }

    /** `Monster::onAttacking(interval)`. */
    var onAttacking: (Int) -> Unit = {}

    /** `Creature::executeConditions(interval)`. */
    var executeConditions: (Int) -> Unit = {}

    /** `Monster::onThink_async()`. */
    var onThinkAsync: () -> Boolean = { false }

    /** `Monster::updateTargetList()`. */
    var updateTargetList: () -> Unit = {}

    /** `Monster::updateIdleStatus()`. */
    var updateIdleStatus: () -> Unit = {}

    /** `Creature::canSee(position)`. */
    var canSee: (Position) -> Boolean = { true }

    /** `Creature::addAsyncTask`; the default pumps the batch inline like the port's async task queue. */
    var addAsyncTask: (() -> Unit) -> Unit = { it() }

    /** `g_dispatcher().observeInternalWork(MonsterMovementRefreshLateness, ...)`, elapsed milliseconds. */
    var observeMovementRefreshLateness: (Long) -> Unit = {}

    /** `Monster::getIdleStatus()`. */
    var isIdle: () -> Boolean = { false }

    /** `Monster::isOpponent(creature)`. */
    var isOpponent: (TargetCreatureFacts) -> Boolean = { false }

    /** `m_monsterType->info.changeTargetChance`. */
    var changeTargetChance: () -> Int = { 0 }

    /** `Creature::getDirectionTo(pos, targetPos)`. */
    var directionTo: (Position, Position) -> Direction = { _, _ -> Direction.North }

    /** `tile->getTopCreature()->getID()` as a re-resolvable id. */
    var tileTopCreature: (Position) -> Long? = { null }

    /** `Monster::onCreatureEnter(creature)`. */
    var onCreatureEnter: (TargetCreatureFacts) -> Unit = {}

    /** `Monster::onCreatureLeave(creature)`. */
    var onCreatureLeave: (TargetCreatureFacts) -> Unit = {}
}

/** `struct TargetSearchComputeRequest` (src/creatures/monsters/monster.hpp:71-75). */
class MonsterTargetSearchComputeRequest(
    var searchType: TargetSearchType,
    var stateEpoch: Long,
    var decisionEpoch: Long,
)

/** `struct FollowPathComputeRequest` (src/creatures/monsters/monster.hpp:61-69). */
class MonsterFollowPathComputeRequest(
    val start: Position,
    val target: Position,
    val params: FindPathParams,
    val targetId: Long,
    val executeOnFollow: Boolean,
) {
    /** `Monster::FollowPathComputeRequest::matches` (monster.cpp:69-75). */
    fun matches(other: MonsterFollowPathComputeRequest): Boolean =
        start == other.start && target == other.target && targetId == other.targetId &&
            executeOnFollow == other.executeOnFollow &&
            params.fullPathSearch == other.params.fullPathSearch &&
            params.clearSight == other.params.clearSight &&
            params.allowDiagonal == other.params.allowDiagonal &&
            params.keepDistance == other.params.keepDistance &&
            params.maxSearchDist == other.params.maxSearchDist &&
            params.minTargetDist == other.params.minTargetDist &&
            params.maxTargetDist == other.params.maxTargetDist
}

/** `struct CombatIntentionComputeRequest` (src/creatures/monsters/monster.hpp:77-87). */
class MonsterCombatIntentionComputeRequest(
    val generation: Long,
    val targetDecisionEpoch: Long,
    val monsterReloadEpoch: Long,
    val interval: Int,
    val targetId: Long,
    val attackTicksSnapshot: Int,
    val origin: Position,
    val target: Position,
    val fleeing: Boolean,
)

/**
 * Port of the deferred `Monster` async pipelines:
 * - target-search generations
 *   (`requestTargetSearchCompute`/`prepare`/`complete`/`retry`/`clear`,
 *   monster.cpp:1066-1330)
 * - combat-intention generations
 *   (`requestCombatIntention`/`startPending`/`prepare`/`complete`/`defer`/
 *   `clear`/`commit`, monster.cpp:1769-2007)
 * - follow-path compute
 *   (`requestFollowPathCompute`/`supersede`/`prepare`/`complete`/`reject`/
 *   `discard`/`capturePathTraits`, monster.cpp:3567-3818)
 * - post-think scheduler
 *   (`trySchedulePostThink`/`cancelScheduledPostThink`/`executePostThink`/
 *   `queuePostThinkAfterAsync`/`promotePostThinkToPlayerVisibleQueue`,
 *   monster.cpp:1633-1706)
 * - movement-AI refresh
 *   (`queueMovementAiRefresh`/`executeMovementAiRefresh`/
 *   `processMovementAiRefresh`/`deferTargetSelection`, monster.cpp:479-566,
 *   1347-1360)
 * - spectator/visibility bookkeeping and compute relevance
 *   (`isPlayerVisibleForScheduling`/`observeVisiblePlayerForScheduling`/
 *   `addVisiblePlayerSpectator`/`removeVisiblePlayerSpectator`/
 *   `captureComputeRelevance`/`isComputeRelevant`, monster.cpp:2009-2089)
 * - the async task dispatch (`onExecuteAsyncTasks`, monster.cpp:3972-3996)
 *
 * Off-thread work only sees immutable snapshots; the returned completion
 * re-resolves the owning monster through [MonsterAsyncHost.resolveMonster] when
 * the dispatcher drains it, and every mutation is guarded by the owning
 * generation/epoch identity captured before submission.
 */
class MonsterAsyncPipelines(
    val monsterId: Long,
    private val service: MonsterComputeService,
    private val targetState: MonsterTargetListState,
    private val behaviorState: MonsterBehaviorState,
    private val host: MonsterAsyncHost,
) {
    /** `Monster::targetSearchComputeOutstanding` (monster.hpp:400). */
    var targetSearchComputeOutstanding: Boolean = false
        private set

    /** `Monster::activeTargetSearchComputeGeneration` (monster.hpp:399). */
    var activeTargetSearchComputeGeneration: Long = 0
        private set

    /** `Monster::targetSearchComputeGeneration` (monster.hpp:398). */
    var targetSearchComputeGeneration: Long = 0
        private set

    /** `Monster::pendingTargetSearchCompute` (monster.hpp:395). */
    var pendingTargetSearchCompute: MonsterTargetSearchComputeRequest? = null
        private set

    /** `Monster::visiblePlayerSpectatorIds` (monster.hpp:423). */
    val visiblePlayerSpectatorIds: MutableList<Long> = mutableListOf()

    /** `Monster::playerVisibleUntil` (monster.hpp:424), steady-clock milliseconds. */
    var playerVisibleUntil: Long = 0
        private set

    /** `Monster::computeRelevance` (monster.hpp:405). */
    var computeRelevance: MonsterRelevanceState = MonsterRelevanceState()
        private set

    /** `Monster::combatIntentionOutstanding` (monster.hpp:404). */
    var combatIntentionOutstanding: Boolean = false
        private set

    /** `Monster::activeCombatIntentionGeneration` (monster.hpp:403). */
    var activeCombatIntentionGeneration: Long = 0
        private set

    /** `Monster::combatIntentionGeneration` (monster.hpp:402). */
    var combatIntentionGeneration: Long = 0
        private set

    /** `Monster::pendingCombatIntention` (monster.hpp:401). */
    var pendingCombatIntention: MonsterCombatIntentionComputeRequest? = null
        private set

    /** `Monster::followPathComputeOutstanding` (monster.hpp:393). */
    var followPathComputeOutstanding: Boolean = false
        private set

    /** `Monster::activeFollowPathComputeGeneration` (monster.hpp:392). */
    var activeFollowPathComputeGeneration: Long = 0
        private set

    /** `Monster::followPathComputeGeneration` (monster.hpp:391). */
    var followPathComputeGeneration: Long = 0
        private set

    /** `Monster::followPathComputeSuperseded` (monster.hpp:394). */
    var followPathComputeSuperseded: Boolean = false
        private set

    /** `Monster::pendingFollowPathCompute` (monster.hpp:390). */
    var pendingFollowPathCompute: MonsterFollowPathComputeRequest? = null
        private set

    // --- target-search compute (monster.cpp:1066-1330) --------------------------

    /**
     * `Monster::requestTargetSearchCompute` (monster.cpp:1066-1090): an identical
     * outstanding request is deduped; otherwise the pending request is replaced
     * with a fresh generation and, when no worker is outstanding, the world-lane
     * prepare start is attempted through `safeCall`.
     */
    fun requestTargetSearchCompute(searchType: TargetSearchType): Boolean {
        val pending = pendingTargetSearchCompute
        if (targetSearchComputeOutstanding && pending != null &&
            pending.searchType == searchType &&
            pending.stateEpoch == targetState.targetStateEpoch &&
            pending.decisionEpoch == targetState.targetDecisionEpoch
        ) {
            return true
        }

        pendingTargetSearchCompute = MonsterTargetSearchComputeRequest(
            searchType = searchType,
            stateEpoch = targetState.targetStateEpoch,
            decisionEpoch = targetState.targetDecisionEpoch,
        )
        val generation = nextTargetSearchComputeGeneration()
        if (targetSearchComputeOutstanding) {
            return true
        }

        targetSearchComputeOutstanding = true
        activeTargetSearchComputeGeneration = generation
        val accepted = safeCall { prepareTargetSearchCompute(generation) }
        if (!accepted) {
            clearTargetSearchCompute()
            return false
        }
        return true
    }

    /**
     * `Monster::prepareTargetSearchCompute` (monster.cpp:1092-1233): revalidates
     * the active generation, refreshes the pending state epoch, rolls the default
     * strategy, snapshots the eligible targets and either completes the ranking
     * inline (single/random path) or submits it to [MonsterComputeService].
     */
    fun prepareTargetSearchCompute(generation: Long) {
        val pending = pendingTargetSearchCompute
        if (!targetSearchComputeOutstanding || activeTargetSearchComputeGeneration != generation || pending == null) {
            return
        }
        if (generation != targetSearchComputeGeneration) {
            activeTargetSearchComputeGeneration = targetSearchComputeGeneration
            prepareTargetSearchCompute(activeTargetSearchComputeGeneration)
            return
        }
        if (pending.decisionEpoch != targetState.targetDecisionEpoch) {
            clearTargetSearchCompute()
            return
        }
        pending.stateEpoch = targetState.targetStateEpoch
        if (host.isRemoved() || host.isDead()) {
            clearTargetSearchCompute()
            return
        }

        val searchType = if (pending.searchType == TargetSearchType.Default) {
            MonsterTargeting.chooseSearchType(
                strategiesTargetNearest = host.strategiesTargetNearest(),
                strategiesTargetHealth = host.strategiesTargetHealth(),
                strategiesTargetDamage = host.strategiesTargetDamage(),
                roll = host.random(1, 100),
            )
        } else {
            pending.searchType
        }

        val origin = host.getPosition()
        val stateEpoch = targetState.targetStateEpoch
        val decisionEpoch = targetState.targetDecisionEpoch
        val monsterPerfTestFriendlyFire = host.monsterPerfTestFriendlyFire()
        val currentAttacked = host.currentAttackedId()
        val skipCurrentUnreachable = currentAttacked != null && host.targetDistance() <= 1 && !host.hasFollowPath()
        val monsterFacts = host.monsterFacts()

        val eligibleTargets = mutableListOf<TargetCreatureFacts>()
        val fallbackIds = mutableListOf<Long>()
        val candidates = mutableListOf<MonsterTargetCandidate>()

        for (targetRef in targetState.targetList) {
            val creature = host.resolveCreature(targetRef.creatureId) ?: continue
            if (creature.health <= 0 || !MonsterTargeting.isTarget(monsterFacts, creature, monsterPerfTestFriendlyFire)) {
                continue
            }
            if (fallbackIds.size < MAX_TARGET_RANK_CANDIDATES) {
                fallbackIds.add(creature.id)
            }
            if (candidates.size >= MAX_TARGET_RANK_CANDIDATES) {
                continue
            }
            if (skipCurrentUnreachable && creature.id == currentAttacked) {
                continue
            }
            if (host.targetDistance() != 1 && !host.canUseAttack(origin, creature)) {
                continue
            }

            val damage = host.damageDealt(creature.id)
            candidates.add(
                MonsterTargetCandidate(
                    creatureId = creature.id,
                    position = creature.position,
                    faction = creature.faction.value,
                    health = creature.health,
                    damage = damage ?: 0,
                    hasDamage = damage != null,
                ),
            )
            eligibleTargets.add(creature)
        }

        if (candidates.isEmpty()) {
            clearTargetSearchCompute()
            return
        }

        if (searchType == TargetSearchType.Random ||
            (searchType != TargetSearchType.Nearest && searchType != TargetSearchType.Hp && searchType != TargetSearchType.Damage)
        ) {
            val selectedIndex = host.random(0, eligibleTargets.size - 1)
            val selectedTarget = eligibleTargets[selectedIndex]
            clearTargetSearchCompute()
            host.selectTarget(selectedTarget)
            return
        }

        val mode = when (searchType) {
            TargetSearchType.Hp -> MonsterTargetRankMode.Health
            TargetSearchType.Damage -> MonsterTargetRankMode.Damage
            else -> MonsterTargetRankMode.Nearest
        }
        val rankingRequest = MonsterTargetRankingRequest(mode = mode, origin = origin, candidates = candidates)

        if (candidates.size == 1) {
            val result = MonsterTargetRankingResult()
            result.suggestedCreatureId = candidates.first().creatureId
            completeTargetSearchCompute(generation, stateEpoch, decisionEpoch, origin, searchType, fallbackIds, result)
            return
        }

        submitRanking(
            priority = computePriority(),
            generation = generation,
            stateEpoch = stateEpoch,
            decisionEpoch = decisionEpoch,
            origin = origin,
            searchType = searchType,
            fallbackIds = fallbackIds,
            rankingRequest = rankingRequest,
        )
    }

    /**
     * `Monster::completeTargetSearchCompute` (monster.cpp:1235-1291): the worker
     * completion. Stale generations retry with the latest pending search type;
     * canceled work, removal, death and a changed decision epoch clear; a changed
     * state epoch/origin retries (or clears on an emptied target list); otherwise
     * the suggestion is revalidated and selected with the fallback id tail.
     */
    fun completeTargetSearchCompute(
        generation: Long,
        stateEpoch: Long,
        decisionEpoch: Long,
        origin: Position,
        searchType: TargetSearchType,
        fallbackIds: List<Long>,
        result: MonsterTargetRankingResult,
    ) {
        val pending = pendingTargetSearchCompute
        if (!targetSearchComputeOutstanding || activeTargetSearchComputeGeneration != generation || pending == null) {
            return
        }
        if (generation != targetSearchComputeGeneration) {
            retryTargetSearchCompute(pending.searchType)
            return
        }
        if (result.canceled) {
            clearTargetSearchCompute()
            return
        }
        if (host.isRemoved() || host.isDead()) {
            clearTargetSearchCompute()
            return
        }
        if (targetState.targetDecisionEpoch != decisionEpoch) {
            clearTargetSearchCompute()
            return
        }
        if (targetState.targetStateEpoch != stateEpoch || host.getPosition() != origin) {
            if (targetState.targetList.isEmpty()) {
                clearTargetSearchCompute()
            } else {
                retryTargetSearchCompute(searchType)
            }
            return
        }

        clearTargetSearchCompute()
        val monsterPerfTestFriendlyFire = host.monsterPerfTestFriendlyFire()
        val currentAttacked = host.currentAttackedId()
        val skipCurrentUnreachable = currentAttacked != null && host.targetDistance() <= 1 && !host.hasFollowPath()
        val monsterFacts = host.monsterFacts()

        val suggested = host.resolveCreature(result.suggestedCreatureId)
        if (suggested != null) {
            val eligible = suggested.health > 0 &&
                MonsterTargeting.isTarget(monsterFacts, suggested, monsterPerfTestFriendlyFire) &&
                (!skipCurrentUnreachable || suggested.id != currentAttacked) &&
                (host.targetDistance() == 1 || host.canUseAttack(origin, suggested))
            if (eligible && host.selectTarget(suggested)) {
                return
            }
        }

        for (creatureId in fallbackIds) {
            if (creatureId == result.suggestedCreatureId) {
                continue
            }
            val candidate = host.resolveCreature(creatureId) ?: continue
            if (candidate.health <= 0 ||
                !MonsterTargeting.isTarget(monsterFacts, candidate, monsterPerfTestFriendlyFire) ||
                (skipCurrentUnreachable && candidate.id == currentAttacked)
            ) {
                continue
            }
            if (host.selectTarget(candidate)) {
                return
            }
        }
    }

    /**
     * `Monster::retryTargetSearchCompute` (monster.cpp:1293-1300): drop the
     * compute state without releasing the post-think, re-arm the pending request
     * with the current epochs and flag the async task for the dispatcher batch.
     */
    fun retryTargetSearchCompute(searchType: TargetSearchType) {
        clearTargetSearchCompute(false)
        if (host.isRemoved() || host.isDead()) {
            return
        }
        pendingTargetSearchCompute = MonsterTargetSearchComputeRequest(
            searchType = searchType,
            stateEpoch = targetState.targetStateEpoch,
            decisionEpoch = targetState.targetDecisionEpoch,
        )
        host.setAsyncTaskFlag(FlagAsyncClass_t.TargetRanking)
    }

    /**
     * `Monster::clearTargetSearchCompute` (monster.cpp:1302-1310): when the
     * post-think was parked on the target decision, release it back to the
     * scheduling path.
     */
    fun clearTargetSearchCompute(releasePostThink: Boolean = true) {
        targetSearchComputeOutstanding = false
        activeTargetSearchComputeGeneration = 0
        pendingTargetSearchCompute = null
        if (releasePostThink && postThinkWaitingForTargetDecision) {
            postThinkWaitingForTargetDecision = false
            queuePostThinkAfterAsync()
        }
    }

    // --- spectator/visibility bookkeeping (monster.cpp:2009-2089) ---------------

    /**
     * `Monster::isPlayerVisibleForScheduling` (monster.cpp:2045-2052): a live
     * player spectator refreshes the hold window; otherwise the stored hold is
     * consulted against the injected clock.
     */
    fun isPlayerVisibleForScheduling(): Boolean {
        val now = host.now()
        if (visiblePlayerSpectatorIds.isNotEmpty()) {
            playerVisibleUntil = now + WALK_PLAYER_VISIBLE_HOLD_MILLIS
            return true
        }
        return now < playerVisibleUntil
    }

    /**
     * `Monster::observeVisiblePlayerForScheduling` (monster.cpp:2054-2065): a
     * newly visible player promotes the walk event and the queued post-think to
     * the player-visible lanes.
     */
    fun observeVisiblePlayerForScheduling(creature: TargetCreatureFacts?) {
        if (creature == null || creature.playerId == null) {
            return
        }

        val wasPlayerVisible = isPlayerVisibleForScheduling()
        val becamePlayerVisible = addVisiblePlayerSpectator(creature)
        if (becamePlayerVisible && !wasPlayerVisible) {
            host.promoteWalkEventToPlayerVisibleLane()
            promotePostThinkToPlayerVisibleQueue()
        }
    }

    /** `Monster::addVisiblePlayerSpectator` (monster.cpp:2067-2080). */
    fun addVisiblePlayerSpectator(creature: TargetCreatureFacts?): Boolean {
        val playerId = creature?.playerId ?: return false
        if (visiblePlayerSpectatorIds.contains(playerId)) {
            return false
        }

        val becameVisible = visiblePlayerSpectatorIds.isEmpty()
        visiblePlayerSpectatorIds.add(playerId)
        return becameVisible
    }

    /** `Monster::removeVisiblePlayerSpectator` (monster.cpp:2082-2089). */
    fun removeVisiblePlayerSpectator(creature: TargetCreatureFacts?) {
        val playerId = creature?.playerId ?: return
        val removed = visiblePlayerSpectatorIds.remove(playerId)
        if (removed && visiblePlayerSpectatorIds.isEmpty()) {
            playerVisibleUntil = host.now() + WALK_PLAYER_VISIBLE_HOLD_MILLIS
        }
    }

    /**
     * `Monster::captureComputeRelevance` (monster.cpp:2009-2038): counts the
     * player-controlled target/friend list entries and their nearest Chebyshev
     * distance, and reports whether the monster is engaged with a player.
     */
    fun captureComputeRelevance(): MonsterRelevanceSnapshot {
        var playerSpectators = 0
        var nearestPlayerDistance = UShort.MAX_VALUE.toInt()

        fun observe(creature: TargetCreatureFacts?) {
            if (creature == null || (creature.playerId == null && creature.masterPlayerId == null)) {
                return
            }
            if (playerSpectators < UShort.MAX_VALUE.toInt()) {
                playerSpectators++
            }
            val origin = host.getPosition()
            val distance = maxOf(
                kotlin.math.abs(origin.x - creature.position.x),
                kotlin.math.abs(origin.y - creature.position.y),
            )
            nearestPlayerDistance = minOf(nearestPlayerDistance, distance)
        }

        for (targetRef in targetState.targetList) {
            observe(host.resolveCreature(targetRef.creatureId))
        }
        for (friendId in targetState.friendList) {
            observe(host.resolveCreature(friendId))
        }

        val engagedWithPlayer = isPlayerControlled(host.currentAttackedId()) ||
            isPlayerControlled(host.followCreatureId())
        return MonsterRelevanceSnapshot(playerSpectators, nearestPlayerDistance, engagedWithPlayer)
    }

    /** `Monster::isComputeRelevant` (monster.cpp:2040-2043). */
    fun isComputeRelevant(): Boolean {
        computeRelevance = MonsterRelevancePolicy.update(computeRelevance, captureComputeRelevance(), host.now())
        return computeRelevance.tier == MonsterRelevanceTier.Visible
    }

    private fun isPlayerControlled(creatureId: Long?): Boolean {
        if (creatureId == null) {
            return false
        }
        val creature = host.resolveCreature(creatureId) ?: return false
        return creature.playerId != null || creature.masterPlayerId != null
    }

    private fun computePriority(): MonsterComputePriority =
        if (isPlayerVisibleForScheduling() || isComputeRelevant()) {
            MonsterComputePriority.Visible
        } else {
            MonsterComputePriority.Background
        }

    // --- combat intention (monster.cpp:1769-2007) -------------------------------

    /**
     * `Monster::requestCombatIntention` (monster.cpp:1769-1785): snapshots the
     * target/attack identity into a fresh generation, then starts the pending
     * request when no worker is outstanding.
     */
    fun requestCombatIntention(interval: Int, target: TargetCreatureFacts) {
        val request = MonsterCombatIntentionComputeRequest(
            generation = nextCombatIntentionGeneration(),
            targetDecisionEpoch = targetState.targetDecisionEpoch,
            monsterReloadEpoch = host.monsterReloadEpoch(),
            interval = interval,
            targetId = target.id,
            attackTicksSnapshot = behaviorState.attackTicks,
            origin = host.getPosition(),
            target = target.position,
            fleeing = host.isFleeing(),
        )
        pendingCombatIntention = request

        if (!combatIntentionOutstanding) {
            startPendingCombatIntention()
        }
    }

    /** `Monster::startPendingCombatIntention` (monster.cpp:1787-1804). */
    fun startPendingCombatIntention() {
        val pending = pendingCombatIntention
        if (combatIntentionOutstanding || pending == null) {
            return
        }

        combatIntentionOutstanding = true
        activeCombatIntentionGeneration = pending.generation
        val accepted = safeCall { prepareCombatIntention(activeCombatIntentionGeneration) }
        if (!accepted) {
            clearCombatIntention()
        }
    }

    /**
     * `Monster::prepareCombatIntention` (monster.cpp:1806-1874): revalidates the
     * generation and the full request snapshot, then either commits the empty or
     * oversized spell list synchronously or submits the geometry evaluation.
     */
    fun prepareCombatIntention(generation: Long) {
        val pending = pendingCombatIntention
        if (!combatIntentionOutstanding || activeCombatIntentionGeneration != generation || pending == null) {
            return
        }
        if (pending.generation != generation || combatIntentionGeneration != generation) {
            combatIntentionOutstanding = false
            activeCombatIntentionGeneration = 0
            startPendingCombatIntention()
            return
        }

        val target = host.resolveCreature(pending.targetId)
        val invalid = host.isRemoved() || host.isDead() || target == null ||
            target.id != host.currentAttackedId() || target.health <= 0 ||
            targetState.targetDecisionEpoch != pending.targetDecisionEpoch ||
            host.monsterReloadEpoch() != pending.monsterReloadEpoch ||
            host.getPosition() != pending.origin || target.position != pending.target ||
            behaviorState.attackTicks != pending.attackTicksSnapshot ||
            host.isFleeing() != pending.fleeing
        if (invalid) {
            clearCombatIntention()
            return
        }

        val attackSpells = host.attackSpells()
        if (attackSpells.isEmpty() || attackSpells.size > MAX_COMBAT_INTENTION_SPELLS) {
            clearCombatIntention()
            commitCombatIntention(pending, target, emptyList(), false)
            return
        }

        val workerRequest = MonsterCombatIntentionRequest(
            origin = pending.origin,
            target = pending.target,
            fleeing = pending.fleeing,
            spells = attackSpells.mapIndexed { index, spellBlock ->
                MonsterCombatSpellGeometry(
                    index = index,
                    range = spellBlock.range,
                    enabled = spellBlock.spell != null,
                    melee = spellBlock.isMelee,
                )
            },
        )

        val submission = service.submit(
            computePriority(),
            { _, stopToken ->
                val result = MonsterCombatIntentionEvaluator.evaluate(workerRequest, stopToken::isStopRequested)
                val completion: MonsterComputeCompletion = {
                    host.resolveMonster(monsterId)?.completeCombatIntention(generation, result)
                }
                completion
            },
            "Monster::combatIntention",
            failureCompletion = {
                host.resolveMonster(monsterId)?.completeCombatIntention(
                    generation,
                    MonsterCombatIntentionResult().apply { canceled = true },
                )
            },
        )
        if (!submission.accepted) {
            clearCombatIntention()
            host.updateLookDirection()
        }
    }

    /**
     * `Monster::completeCombatIntention` (monster.cpp:1876-1919): the worker
     * completion. Stale generations defer the pending request back to the async
     * batch; canceled work clears; the target snapshot is revalidated including
     * login protection, protection zones, targeting and sight; the geometry index
     * list must be strictly increasing and in range before the commit runs with
     * the hint required.
     */
    fun completeCombatIntention(generation: Long, result: MonsterCombatIntentionResult) {
        val pending = pendingCombatIntention
        if (!combatIntentionOutstanding || activeCombatIntentionGeneration != generation || pending == null) {
            return
        }
        if (pending.generation != generation || combatIntentionGeneration != generation) {
            deferPendingCombatIntention()
            return
        }
        if (result.canceled) {
            clearCombatIntention()
            return
        }

        val target = host.resolveCreature(pending.targetId)
        val invalid = host.isRemoved() || host.isDead() || target == null ||
            target.id != host.currentAttackedId() || target.health <= 0 ||
            target.isLoginProtected || targetState.targetDecisionEpoch != pending.targetDecisionEpoch ||
            host.monsterReloadEpoch() != pending.monsterReloadEpoch ||
            host.getPosition() != pending.origin || target.position != pending.target ||
            behaviorState.attackTicks != pending.attackTicksSnapshot ||
            host.isFleeing() != pending.fleeing ||
            host.isProtectionZone() || target.zoneType == ZoneType.Protection ||
            !MonsterTargeting.isTarget(host.monsterFacts(), target, host.monsterPerfTestFriendlyFire()) ||
            !host.isSightClear(pending.origin, pending.target)
        if (invalid) {
            clearCombatIntention()
            return
        }

        var previousIndex = 0
        var firstIndex = true
        for (index in result.geometricallyEligibleSpellIndices) {
            if (index >= host.attackSpells().size || (!firstIndex && index <= previousIndex)) {
                clearCombatIntention()
                return
            }
            firstIndex = false
            previousIndex = index
        }

        clearCombatIntention()
        commitCombatIntention(pending, target, result.geometricallyEligibleSpellIndices, true)
    }

    /** `Monster::deferPendingCombatIntention` (monster.cpp:1921-1927). */
    fun deferPendingCombatIntention() {
        combatIntentionOutstanding = false
        activeCombatIntentionGeneration = 0
        if (pendingCombatIntention != null) {
            host.setAsyncTaskFlag(FlagAsyncClass_t.CombatIntention)
        }
    }

    /** `Monster::clearCombatIntention` (monster.cpp:1929-1933). */
    fun clearCombatIntention() {
        combatIntentionOutstanding = false
        activeCombatIntentionGeneration = 0
        pendingCombatIntention = null
    }

    /**
     * `Monster::commitCombatIntention` (monster.cpp:1935-2000): walks the attack
     * spells in source order, breaking when any snapshot identity changed, casting
     * the due spells the geometry hint approved and folding the melee swing state
     * and attack tick window.
     */
    fun commitCombatIntention(
        request: MonsterCombatIntentionComputeRequest,
        target: TargetCreatureFacts,
        geometricallyEligibleSpellIndices: List<Int>,
        requireGeometryHint: Boolean,
    ) {
        var updateLook = true
        var resetTicks = request.interval != 0
        var eligibleIndex = 0
        val attackSpells = host.attackSpells()
        val monsterFacts = host.monsterFacts()
        val monsterPerfTestFriendlyFire = host.monsterPerfTestFriendlyFire()

        for (spellIndex in attackSpells.indices) {
            if (combatIntentionGeneration != request.generation ||
                host.isRemoved() || host.isDead() ||
                target.health <= 0 || host.currentAttackedId() != target.id
            ) {
                break
            }
            if (targetState.targetDecisionEpoch != request.targetDecisionEpoch ||
                host.monsterReloadEpoch() != request.monsterReloadEpoch ||
                host.getPosition() != request.origin || target.position != request.target ||
                host.isFleeing() != request.fleeing
            ) {
                break
            }
            if (target.isLoginProtected || host.isProtectionZone() || target.zoneType == ZoneType.Protection ||
                !MonsterTargeting.isTarget(monsterFacts, target, monsterPerfTestFriendlyFire) ||
                !host.isSightClear(host.getPosition(), target.position)
            ) {
                break
            }

            val spellBlock = attackSpells[spellIndex]
            if (spellBlock.spell == null || (spellBlock.isMelee && host.isFleeing())) {
                continue
            }

            while (eligibleIndex < geometricallyEligibleSpellIndices.size &&
                geometricallyEligibleSpellIndices[eligibleIndex] < spellIndex
            ) {
                eligibleIndex++
            }
            val geometrySuggested = !requireGeometryHint ||
                (eligibleIndex < geometricallyEligibleSpellIndices.size &&
                    geometricallyEligibleSpellIndices[eligibleIndex] == spellIndex)

            val useResult = MonsterBehavior.canUseSpell(
                state = behaviorState,
                spellBlock = spellBlock,
                pos = host.getPosition(),
                targetPos = target.position,
                interval = request.interval,
                isFleeing = host.isFleeing(),
                resetTicks = resetTicks,
                now = host.now(),
            )
            resetTicks = useResult.resetTicks

            if (useResult.canUse && geometrySuggested) {
                if (spellBlock.chance >= host.random(1, 100)) {
                    if (updateLook) {
                        host.updateLookDirection()
                        updateLook = false
                    }

                    behaviorState.minCombatValue = spellBlock.minCombatValue
                    behaviorState.maxCombatValue = spellBlock.maxCombatValue
                    host.castSpell(spellBlock, target)

                    if (spellBlock.isMelee) {
                        behaviorState.extraMeleeAttack = false
                    }
                }
            }

            if (!useResult.inRange && spellBlock.isMelee) {
                behaviorState.extraMeleeAttack = true
            }
        }

        if (updateLook && !host.isRemoved()) {
            host.updateLookDirection()
        }

        if (resetTicks && combatIntentionGeneration == request.generation) {
            behaviorState.attackTicks = 0
        }
    }

    /** `Monster::nextCombatIntentionGeneration` (monster.cpp:2002-2007). */
    fun nextCombatIntentionGeneration(): Long {
        if (++combatIntentionGeneration == 0L) {
            combatIntentionGeneration = 1L
        }
        return combatIntentionGeneration
    }

    // --- follow path compute (monster.cpp:3567-3818) ----------------------------

    /**
     * `Monster::requestFollowPathCompute` (monster.cpp:3567-3606): validates the
     * bounds, dedupes an identical outstanding request, otherwise parks the
     * pending request behind a fresh generation and starts the prepare through
     * the world-lane safe hand-back.
     */
    fun requestFollowPathCompute(
        followCreature: TargetCreatureFacts,
        params: FindPathParams,
        executeOnFollow: Boolean,
    ): Boolean {
        if (followCreature.id == 0L || params.maxSearchDist <= 0 ||
            params.maxSearchDist > MAX_BACKGROUND_FOLLOW_PATH_RADIUS
        ) {
            return false
        }

        val request = MonsterFollowPathComputeRequest(
            start = host.getPosition(),
            target = followCreature.position,
            params = params.copyParams(),
            targetId = followCreature.id,
            executeOnFollow = executeOnFollow,
        )
        if (followPathComputeOutstanding && pendingFollowPathCompute?.matches(request) == true) {
            return true
        }

        pendingFollowPathCompute = request
        val generation = nextFollowPathComputeGeneration()
        followPathComputeSuperseded = false
        if (followPathComputeOutstanding) {
            return true
        }

        followPathComputeOutstanding = true
        activeFollowPathComputeGeneration = generation
        val accepted = safeCall { prepareFollowPathCompute(generation) }
        if (!accepted) {
            followPathComputeOutstanding = false
            activeFollowPathComputeGeneration = 0
            followPathComputeSuperseded = false
            pendingFollowPathCompute = null
            host.markForceUpdateFollowPath()
            return true
        }
        return true
    }

    /**
     * `Monster::supersedeFollowPathCompute` (monster.cpp:3608-3618): an idle
     * tracker just drops the pending request; an outstanding one advances the
     * generation and marks the worker result stale.
     */
    fun supersedeFollowPathCompute() {
        if (!followPathComputeOutstanding) {
            pendingFollowPathCompute = null
            followPathComputeSuperseded = false
            return
        }

        nextFollowPathComputeGeneration()
        pendingFollowPathCompute = null
        followPathComputeSuperseded = true
    }

    /**
     * `Monster::prepareFollowPathCompute` (monster.cpp:3620-3678): revalidates
     * the generation and the follow identity, captures the navigation snapshot
     * and submits the A* path search to the compute service.
     */
    fun prepareFollowPathCompute(generation: Long) {
        if (!followPathComputeOutstanding || activeFollowPathComputeGeneration != generation) {
            return
        }
        if (followPathComputeSuperseded && pendingFollowPathCompute == null) {
            followPathComputeOutstanding = false
            activeFollowPathComputeGeneration = 0
            followPathComputeSuperseded = false
            return
        }
        val request = pendingFollowPathCompute
        if (request == null) {
            discardFollowPathCompute(false)
            return
        }
        if (generation != followPathComputeGeneration) {
            activeFollowPathComputeGeneration = followPathComputeGeneration
            prepareFollowPathCompute(activeFollowPathComputeGeneration)
            return
        }

        val followCreature = host.followCreatureId()?.let { host.resolveCreature(it) }
        if (host.isRemoved() || host.isDead() || host.getPosition() != request.start ||
            followCreature == null || followCreature.id != request.targetId ||
            followCreature.position != request.target
        ) {
            discardFollowPathCompute(followCreature != null)
            return
        }

        val navigation = host.getNavigationSnapshot(request.start, MAX_BACKGROUND_FOLLOW_PATH_RADIUS)
        if (navigation == null) {
            discardFollowPathCompute(true)
            return
        }

        val workerRequest = MonsterPathRequest(
            start = request.start,
            target = request.target,
            params = request.params,
            traits = capturePathTraits(navigation),
            radius = navigation.getRadius(),
            getCell = navigation::getCell,
        )

        val submission = service.submit(
            computePriority(),
            { _, stopToken ->
                val result = MonsterPathfinder.find(workerRequest, stopToken::isStopRequested)
                val completion: MonsterComputeCompletion = {
                    host.resolveMonster(monsterId)?.completeFollowPathCompute(generation, navigation, result)
                }
                completion
            },
            "Monster::followPath",
            failureCompletion = {
                host.resolveMonster(monsterId)?.rejectFollowPathCompute(generation)
            },
        )
        if (!submission.accepted) {
            rejectFollowPathCompute(generation)
        }
    }

    /**
     * `Monster::completeFollowPathCompute` (monster.cpp:3680-3730): the worker
     * completion. Supersession, a moved/removed follow identity or a stale
     * navigation topology discard the path (with a re-follow refresh when the
     * monster still follows something); a found path must still satisfy the
     * frozen endpoint condition and reach its endpoint before it is applied.
     */
    fun completeFollowPathCompute(generation: Long, navigation: NavRegionSnapshot, result: MonsterPathResult) {
        if (!followPathComputeOutstanding || activeFollowPathComputeGeneration != generation) {
            return
        }
        if (followPathComputeSuperseded && pendingFollowPathCompute == null) {
            followPathComputeOutstanding = false
            activeFollowPathComputeGeneration = 0
            followPathComputeSuperseded = false
            return
        }
        val request = pendingFollowPathCompute
        if (request == null) {
            discardFollowPathCompute(false)
            return
        }
        if (generation != followPathComputeGeneration) {
            discardFollowPathCompute(true)
            return
        }
        if (result.status == MonsterPathStatus.Cancelled) {
            discardFollowPathCompute(false)
            return
        }

        val followCreature = host.followCreatureId()?.let { host.resolveCreature(it) }
        val resolvedTarget = host.resolveCreature(request.targetId)
        val identityIsCurrent = !host.isRemoved() && !host.isDead() && host.getPosition() == request.start &&
            followCreature != null && resolvedTarget != null && followCreature.id == resolvedTarget.id &&
            resolvedTarget.position == request.target
        if (!identityIsCurrent || !host.isNavigationTopologyCurrent(navigation)) {
            discardFollowPathCompute(followCreature != null)
            return
        }

        if (result.found()) {
            if (!endpointIsCurrent(navigation, request, result.endpoint) ||
                !pathReachesEndpoint(navigation, request.start, result.directions, result.endpoint)
            ) {
                discardFollowPathCompute(true)
                return
            }
        }

        followPathComputeOutstanding = false
        activeFollowPathComputeGeneration = 0
        followPathComputeSuperseded = false
        pendingFollowPathCompute = null
        host.setHasFollowPath(result.found())
        host.startAutoWalk(result.directions)
        if (request.executeOnFollow) {
            host.onFollowCreatureComplete(resolvedTarget)
        }
    }

    /**
     * `Monster::rejectFollowPathCompute` (monster.cpp:3732-3763): a stale
     * generation re-prepares; otherwise the path is cleared, the auto-walk
     * emptied and the follow completion still delivered.
     */
    fun rejectFollowPathCompute(generation: Long) {
        if (!followPathComputeOutstanding || activeFollowPathComputeGeneration != generation) {
            return
        }
        if (followPathComputeSuperseded && pendingFollowPathCompute == null) {
            followPathComputeOutstanding = false
            activeFollowPathComputeGeneration = 0
            followPathComputeSuperseded = false
            return
        }
        val request = pendingFollowPathCompute
        if (request == null) {
            discardFollowPathCompute(false)
            return
        }
        if (generation != followPathComputeGeneration) {
            activeFollowPathComputeGeneration = followPathComputeGeneration
            prepareFollowPathCompute(activeFollowPathComputeGeneration)
            return
        }

        val followCreature = host.followCreatureId()?.let { host.resolveCreature(it) }
        followPathComputeOutstanding = false
        activeFollowPathComputeGeneration = 0
        followPathComputeSuperseded = false
        pendingFollowPathCompute = null
        host.setHasFollowPath(false)
        host.startAutoWalk(emptyList())
        if (request.executeOnFollow && followCreature != null && followCreature.id == request.targetId) {
            host.onFollowCreatureComplete(followCreature)
        }
    }

    /** `Monster::discardFollowPathCompute` (monster.cpp:3765-3773). */
    fun discardFollowPathCompute(requestRefresh: Boolean) {
        followPathComputeOutstanding = false
        activeFollowPathComputeGeneration = 0
        followPathComputeSuperseded = false
        pendingFollowPathCompute = null
        if (requestRefresh && !host.isRemoved() && !host.isDead() && host.followCreatureId() != null) {
            host.goToFollowCreatureAsync()
        }
    }

    /**
     * `Monster::capturePathTraits` (monster.cpp:3775-3811): the monster-type
     * movement traits plus the summon invited-house list derived from the
     * snapshot sectors and the field immunity/penalty tables.
     */
    fun capturePathTraits(navigation: NavRegionSnapshot): MonsterPathTraits {
        val traits = MonsterPathTraits()
        traits.canPushItems = host.canPushItems()
        traits.canPushCreatures = host.canPushCreatures()
        traits.isSummon = host.isSummon()
        traits.canSeeInvisibility = host.canSeeInvisibility()
        traits.moveLocked = host.isMoveLocked()
        traits.canEnterProtectionZone = host.isFamiliar() && !host.masterHasAttackedCreature()

        if (traits.isSummon) {
            val masterPlayerId = host.masterPlayerId()
            if (masterPlayerId != null) {
                val checkedHouseIds = mutableListOf<Int>()
                for (sector in navigation.getSectors()) {
                    for (cell in sector.getCells()) {
                        if (cell.houseId == 0 || checkedHouseIds.contains(cell.houseId)) {
                            continue
                        }
                        checkedHouseIds.add(cell.houseId)
                        if (host.isHouseInvited(cell.houseId, masterPlayerId)) {
                            traits.summonInvitedHouseIds.add(cell.houseId)
                        }
                    }
                }
                traits.summonInvitedHouseIds.sort()
            }
        }

        val ignoresFieldDamage = host.ignoreFieldDamage()
        for (index in 0 until CombatType.COUNT.value) {
            val combatType = CombatType.entries[index]
            val immune = host.isImmuneCombat(combatType)
            val canWalk = host.canWalkOnField(combatType)
            traits.fieldAllowed[index] = immune || ignoresFieldDamage || canWalk
            traits.fieldPenalty[index] = !immune && !canWalk && !host.hasFieldCondition(combatType)
        }
        return traits
    }

    /** `Monster::nextFollowPathComputeGeneration` (monster.cpp:3813-3818). */
    fun nextFollowPathComputeGeneration(): Long {
        if (++followPathComputeGeneration == 0L) {
            followPathComputeGeneration = 1L
        }
        return followPathComputeGeneration
    }

    /** `FrozenPathingConditionCall(request.target)(request.start, endpoint, params, bestMatchDistance)` (monster.cpp:3714). */
    private fun endpointIsCurrent(
        navigation: NavRegionSnapshot,
        request: MonsterFollowPathComputeRequest,
        endpoint: Position,
    ): Boolean {
        val params = request.params
        val targetPos = request.target
        if (params.fullPathSearch) {
            if (endpoint.x > targetPos.x + params.maxTargetDist ||
                endpoint.x < targetPos.x - params.maxTargetDist ||
                endpoint.y > targetPos.y + params.maxTargetDist ||
                endpoint.y < targetPos.y - params.maxTargetDist
            ) {
                return false
            }
        } else {
            val dx = getOffsetX(request.start, targetPos)
            val dxMax = if (dx >= 0) params.maxTargetDist else 0
            if (endpoint.x > targetPos.x + dxMax) {
                return false
            }
            val dxMin = if (dx <= 0) params.maxTargetDist else 0
            if (endpoint.x < targetPos.x - dxMin) {
                return false
            }
            val dy = getOffsetY(request.start, targetPos)
            val dyMax = if (dy >= 0) params.maxTargetDist else 0
            if (endpoint.y > targetPos.y + dyMax) {
                return false
            }
            val dyMin = if (dy <= 0) params.maxTargetDist else 0
            if (endpoint.y < targetPos.y - dyMin) {
                return false
            }
        }

        if (params.clearSight && !MonsterPathfinder.isSightClear(navigation::getCell, endpoint, targetPos)) {
            return false
        }

        val testDistance = maxOf(
            kotlin.math.abs(targetPos.x - endpoint.x),
            kotlin.math.abs(targetPos.y - endpoint.y),
        )
        if (params.maxTargetDist == 1) {
            return testDistance >= params.minTargetDist && testDistance <= params.maxTargetDist
        }
        if (testDistance > params.maxTargetDist || testDistance < params.minTargetDist) {
            return false
        }
        if (testDistance == params.maxTargetDist) {
            return true
        }
        return testDistance > 0
    }

    /** `pathReachesEndpoint` (monster.cpp:48-60): walk the reconstructed directions from the start. */
    private fun pathReachesEndpoint(
        navigation: NavRegionSnapshot,
        start: Position,
        directions: List<Direction>,
        expectedEndpoint: Position,
    ): Boolean {
        var position = start
        for (direction in directions.asReversed()) {
            position = getNextPosition(direction, position)
            if (navigation.getCell(position) == null) {
                return false
            }
        }
        return position == expectedEndpoint
    }

    private fun FindPathParams.copyParams(): FindPathParams = FindPathParams().also {
        it.fullPathSearch = fullPathSearch
        it.clearSight = clearSight
        it.allowDiagonal = allowDiagonal
        it.keepDistance = keepDistance
        it.maxSearchDist = maxSearchDist
        it.minTargetDist = minTargetDist
        it.maxTargetDist = maxTargetDist
    }

    // --- movement AI refresh (monster.cpp:479-566, 1347-1360) --------------------

    /** `Monster::pendingMovementAiRefresh.state` (monster.hpp:57). */
    private val pendingMovementAiRefreshState = CoalescedTaskState { host.now() }

    private var pendingMovementAiRefreshCreatureId: Long? = null
    private var pendingMovementAiRefreshOldPos: Position = Position(0, 0, 0)
    private var pendingMovementAiRefreshNewPos: Position = Position(0, 0, 0)

    /** `Monster::PendingMovementAiRefresh::needsFullRefresh` (monster.hpp:58). */
    var pendingMovementAiRefreshNeedsFullRefresh: Boolean = false
        private set

    /**
     * `Monster::queueMovementAiRefresh` (monster.cpp:479-497): an entering player
     * refreshes the scheduling visibility immediately; the movement itself is
     * coalesced into one async batch, and further movements only mark the batch
     * as needing a full refresh.
     */
    fun queueMovementAiRefresh(creature: TargetCreatureFacts, oldPos: Position, newPos: Position) {
        if (creature.playerId != null && host.canSee(newPos) && !host.canSee(oldPos)) {
            observeVisiblePlayerForScheduling(creature)
        }

        if (!pendingMovementAiRefreshState.tryEnqueue()) {
            pendingMovementAiRefreshNeedsFullRefresh = true
            return
        }

        pendingMovementAiRefreshCreatureId = creature.id
        pendingMovementAiRefreshOldPos = oldPos
        pendingMovementAiRefreshNewPos = newPos
        pendingMovementAiRefreshNeedsFullRefresh = false

        host.addAsyncTask { executeMovementAiRefresh() }
    }

    /** `Monster::executeMovementAiRefresh` (monster.cpp:499-525). */
    fun executeMovementAiRefresh() {
        val readyAt = pendingMovementAiRefreshState.consume() ?: return

        val needsFullRefresh = pendingMovementAiRefreshNeedsFullRefresh
        val creatureId = pendingMovementAiRefreshCreatureId
        val oldPos = pendingMovementAiRefreshOldPos
        val newPos = pendingMovementAiRefreshNewPos

        pendingMovementAiRefreshCreatureId = null
        pendingMovementAiRefreshNeedsFullRefresh = false
        host.observeMovementRefreshLateness(DispatcherPolicy.elapsed(readyAt, host.now()))

        val movedCreature = creatureId?.let { host.resolveCreature(it) }
        if (needsFullRefresh || movedCreature == null) {
            host.updateTargetList()
            host.updateIdleStatus()
            return
        }

        processMovementAiRefresh(movedCreature, oldPos, newPos)
    }

    /** `Monster::processMovementAiRefresh` (monster.cpp:527-566). */
    fun processMovementAiRefresh(creature: TargetCreatureFacts, oldPos: Position, newPos: Position) {
        val canSeeNewPos = host.canSee(newPos)
        val canSeeOldPos = host.canSee(oldPos)

        if (canSeeNewPos && !canSeeOldPos) {
            host.onCreatureEnter(creature)
        } else if (!canSeeNewPos && canSeeOldPos) {
            host.onCreatureLeave(creature)
        }

        if (host.isIdle()) {
            host.updateIdleStatus()
        }

        if (host.isSummon()) {
            return
        }

        val followCreatureId = host.followCreatureId()
        if (followCreatureId != null) {
            val followCreature = host.resolveCreature(followCreatureId) ?: return
            val pos = host.getPosition()
            val offsetX = getDistanceX(followCreature.position, pos)
            val offsetY = getDistanceY(followCreature.position, pos)

            if ((offsetX > 1 || offsetY > 1) && host.changeTargetChance() > 0) {
                val direction = host.directionTo(pos, followCreature.position)
                val checkPosition = getNextPosition(direction, pos)
                val topCreatureId = host.tileTopCreature(checkPosition)
                if (topCreatureId != null) {
                    val topCreature = host.resolveCreature(topCreatureId)
                    if (topCreature != null && topCreature.id != followCreatureId && host.isOpponent(topCreature)) {
                        deferTargetSelection(topCreature.id)
                    }
                }
            }
        } else if (host.isOpponent(creature)) {
            deferTargetSelection(creature.id)
        }
    }

    /**
     * `Monster::deferTargetSelection` (monster.cpp:1347-1360): the decision epoch
     * captured on the world lane must still be current when the deferred
     * selection runs, and the creature must re-resolve.
     */
    fun deferTargetSelection(creatureId: Long) {
        if (creatureId == 0L) {
            return
        }

        val decisionEpoch = targetState.targetDecisionEpoch
        safeCall {
            val creature = host.resolveCreature(creatureId) ?: return@safeCall
            if (targetState.targetDecisionEpoch == decisionEpoch) {
                host.selectTarget(creature)
            }
        }
    }

    // --- post-think scheduler (monster.cpp:1633-1706, 3972-3996) ----------------

    /** `Monster::pendingPostThink` (monster.hpp:386). */
    private val pendingPostThink = CoalescedTaskState { host.now() }

    /** `Monster::postThinkQueued` (monster.hpp:387). */
    var postThinkQueued: Boolean = false
        private set

    /** `Monster::postThinkPlayerVisible` (monster.hpp:388). */
    var postThinkPlayerVisible: Boolean = false
        private set

    /** `Monster::postThinkWaitingForTargetDecision` (monster.hpp:389). */
    var postThinkWaitingForTargetDecision: Boolean = false
        private set

    /** `Monster::pendingPostThink.isPending()` read view for tests/scheduling. */
    val isPostThinkPending: Boolean get() = pendingPostThink.isPending

    /** `Monster::trySchedulePostThink` (monster.cpp:1633-1638). */
    fun trySchedulePostThink(): Boolean {
        if (!host.hasAsyncTaskFlag(FlagAsyncClass_t.OnThink)) {
            return false
        }
        return pendingPostThink.tryEnqueue()
    }

    /**
     * `Monster::cancelScheduledPostThink` (monster.cpp:1640-1648): only the
     * matching lane may cancel the queued tick.
     */
    fun cancelScheduledPostThink(playerVisible: Boolean) {
        if (!postThinkQueued || postThinkPlayerVisible != playerVisible) {
            return
        }

        postThinkQueued = false
        postThinkWaitingForTargetDecision = false
        pendingPostThink.consume()
    }

    /**
     * `Monster::executePostThink` (monster.cpp:1650-1680): an outstanding target
     * decision parks the tick; otherwise the coalesced interval advances combat
     * and conditions once after observing the lateness.
     */
    fun executePostThink(interval: Int, playerVisible: Boolean) {
        if (!postThinkQueued || postThinkPlayerVisible != playerVisible) {
            return
        }

        if (targetSearchComputeOutstanding) {
            postThinkQueued = false
            postThinkWaitingForTargetDecision = true
            return
        }

        postThinkQueued = false
        val readyAt = pendingPostThink.consume() ?: return

        host.observePostThinkLateness(DispatcherPolicy.elapsed(readyAt, host.now()))
        if (host.isRemoved() || !host.isAlive()) {
            return
        }

        host.onAttacking(interval)
        host.executeConditions(interval)
    }

    /**
     * `Monster::queuePostThinkAfterAsync` (monster.cpp:1682-1697): queues the
     * pending tick on the player-visible or scheduled lane; an outstanding target
     * search parks it instead, and a rejected queue admission cancels it.
     */
    fun queuePostThinkAfterAsync() {
        if (!pendingPostThink.isPending || postThinkQueued) {
            return
        }
        if (targetSearchComputeOutstanding) {
            postThinkWaitingForTargetDecision = true
            return
        }

        postThinkWaitingForTargetDecision = false
        postThinkQueued = true
        postThinkPlayerVisible = isPlayerVisibleForScheduling()
        if (!host.queueMonsterPostThink(monsterId, postThinkPlayerVisible)) {
            cancelScheduledPostThink(postThinkPlayerVisible)
        }
    }

    /** `Monster::promotePostThinkToPlayerVisibleQueue` (monster.cpp:1699-1706). */
    fun promotePostThinkToPlayerVisibleQueue() {
        if (!postThinkQueued || !pendingPostThink.isPending) {
            return
        }

        postThinkQueued = false
        queuePostThinkAfterAsync()
    }

    /** `Monster::onExecuteAsyncTasks` (monster.cpp:3972-3996). */
    fun onExecuteAsyncTasks() {
        if (host.hasAsyncTaskFlag(FlagAsyncClass_t.UpdateTargetList)) {
            host.updateTargetList()
        }

        if (host.hasAsyncTaskFlag(FlagAsyncClass_t.UpdateIdleStatus)) {
            host.updateIdleStatus()
        }

        val parkedSearch = pendingTargetSearchCompute
        if (host.hasAsyncTaskFlag(FlagAsyncClass_t.TargetRanking) && parkedSearch != null) {
            pendingTargetSearchCompute = null
            requestTargetSearchCompute(parkedSearch.searchType)
        }

        if (host.hasAsyncTaskFlag(FlagAsyncClass_t.CombatIntention)) {
            startPendingCombatIntention()
        }

        if (host.hasAsyncTaskFlag(FlagAsyncClass_t.OnThink)) {
            if (!host.onThinkAsync()) {
                queuePostThinkAfterAsync()
            }
        }
    }

    private fun safeCall(action: () -> Unit): Boolean {
        if (host.isRemoved()) {
            return false
        }
        action()
        return true
    }

    private fun nextTargetSearchComputeGeneration(): Long {
        if (++targetSearchComputeGeneration == 0L) {
            targetSearchComputeGeneration = 1L
        }
        return targetSearchComputeGeneration
    }

    private fun submitRanking(
        priority: MonsterComputePriority,
        generation: Long,
        stateEpoch: Long,
        decisionEpoch: Long,
        origin: Position,
        searchType: TargetSearchType,
        fallbackIds: List<Long>,
        rankingRequest: MonsterTargetRankingRequest,
    ) {
        val submission = service.submit(
            priority,
            { _, stopToken ->
                val result = MonsterTargetRanker.rank(rankingRequest, stopToken::isStopRequested)
                val completion: MonsterComputeCompletion = {
                    host.resolveMonster(monsterId)?.completeTargetSearchCompute(
                        generation,
                        stateEpoch,
                        decisionEpoch,
                        origin,
                        searchType,
                        fallbackIds,
                        result,
                    )
                }
                completion
            },
            "Monster::targetRanking",
            failureCompletion = {
                host.resolveMonster(monsterId)?.completeTargetSearchCompute(
                    generation,
                    stateEpoch,
                    decisionEpoch,
                    origin,
                    searchType,
                    emptyList(),
                    MonsterTargetRankingResult().apply { canceled = true },
                )
            },
        )
        if (!submission.accepted) {
            clearTargetSearchCompute()
        }
    }

    /** `WALK_PLAYER_VISIBLE_HOLD` (monster.cpp:39), `std::chrono::seconds(3)` as ms. */
    companion object {
        const val WALK_PLAYER_VISIBLE_HOLD_MILLIS: Long = 3_000L

        /** `MAX_TARGET_RANK_CANDIDATES` (monster.cpp:38). */
        const val MAX_TARGET_RANK_CANDIDATES: Int = 256

        /** `MAX_COMBAT_INTENTION_SPELLS` (monster.cpp:37). */
        const val MAX_COMBAT_INTENTION_SPELLS: Int = 256

        /** `MAX_BACKGROUND_FOLLOW_PATH_RADIUS` (monster.cpp:36). */
        const val MAX_BACKGROUND_FOLLOW_PATH_RADIUS: Int = 12
    }
}
