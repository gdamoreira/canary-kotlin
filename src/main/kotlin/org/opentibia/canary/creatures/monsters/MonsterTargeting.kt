package org.opentibia.canary.creatures.monsters

import org.opentibia.canary.map.Position
import kotlin.math.abs
import kotlin.math.max

/**
 * Port of `enum class MonsterTargetRankMode`
 * (src/creatures/monsters/monster_targeting.hpp:20-24).
 */
enum class MonsterTargetRankMode {
    Nearest,
    Health,
    Damage,
}

/**
 * Port of `struct MonsterTargetCandidate`
 * (src/creatures/monsters/monster_targeting.hpp:26-33).
 */
class MonsterTargetCandidate(
    val creatureId: Long = 0L,
    val position: Position = Position(0, 0, 0),
    val faction: Int = 0,
    val health: Int = 0,
    val damage: Int = 0,
    val hasDamage: Boolean = false,
)

/**
 * Port of `struct MonsterTargetRankingRequest`
 * (src/creatures/monsters/monster_targeting.hpp:35-39).
 */
class MonsterTargetRankingRequest(
    val mode: MonsterTargetRankMode = MonsterTargetRankMode.Nearest,
    val origin: Position = Position(0, 0, 0),
    val candidates: List<MonsterTargetCandidate> = emptyList(),
)

/**
 * Port of `struct MonsterTargetRankingResult`
 * (src/creatures/monsters/monster_targeting.hpp:41-44).
 */
class MonsterTargetRankingResult {
    var suggestedCreatureId: Long = 0L
    var canceled: Boolean = false
}

/**
 * Port of `MonsterTargetRanker::rank`
 * (src/creatures/monsters/monster_targeting.cpp:18-83). Candidates are ranked by
 * `max(distanceX, distanceY) + faction * 100` (Nearest), `health + faction * 100000`
 * (Health) or `damage + faction * 100000` (Damage). Ties keep the earlier candidate;
 * Damage only considers candidates carrying a damage entry. [stopRequested] replaces
 * the C++ `std::stop_token` and aborts with [MonsterTargetRankingResult.canceled].
 */
object MonsterTargetRanker {
    fun rank(
        request: MonsterTargetRankingRequest,
        stopRequested: () -> Boolean = { false },
    ): MonsterTargetRankingResult {
        val result = MonsterTargetRankingResult()
        if (request.candidates.isEmpty()) {
            return result
        }
        if (stopRequested()) {
            result.canceled = true
            return result
        }

        var selected = request.candidates.first()
        when (request.mode) {
            MonsterTargetRankMode.Nearest -> {
                var minimumRange = diagonalDistance(request.origin, selected.position) + selected.faction * 100
                for (index in 1 until request.candidates.size) {
                    if (stopRequested()) {
                        result.canceled = true
                        return result
                    }
                    val candidate = request.candidates[index]
                    val distance = diagonalDistance(request.origin, candidate.position) + candidate.faction * 100
                    if (distance < minimumRange) {
                        selected = candidate
                        minimumRange = distance
                    }
                }
            }

            MonsterTargetRankMode.Health -> {
                var minimumHealth = selected.health + selected.faction * 100000
                for (index in 1 until request.candidates.size) {
                    if (stopRequested()) {
                        result.canceled = true
                        return result
                    }
                    val candidate = request.candidates[index]
                    val health = candidate.health + candidate.faction * 100000
                    if (health < minimumHealth) {
                        selected = candidate
                        minimumHealth = health
                    }
                }
            }

            MonsterTargetRankMode.Damage -> {
                var mostDamage = if (selected.hasDamage) {
                    selected.damage + selected.faction * 100000
                } else {
                    Int.MIN_VALUE
                }
                for (index in 1 until request.candidates.size) {
                    if (stopRequested()) {
                        result.canceled = true
                        return result
                    }
                    val candidate = request.candidates[index]
                    if (!candidate.hasDamage) {
                        continue
                    }
                    val damageScore = candidate.damage + candidate.faction * 100000
                    if (damageScore > mostDamage) {
                        mostDamage = damageScore
                        selected = candidate
                    }
                }
            }
        }

        result.suggestedCreatureId = selected.creatureId
        return result
    }

    private fun diagonalDistance(origin: Position, position: Position): Int =
        max(abs(origin.x - position.x), abs(origin.y - position.y))
}

/** Values of `enum TargetSearchType_t` (src/lua/lua_definitions.hpp:144-148). */
enum class TargetSearchType {
    Default,
    Nearest,
    Hp,
    Damage,
    Random,
}

/** `enum Faction_t` (src/game/game_definitions.hpp:43-55). */
enum class Faction(val value: Int) {
    Default(0),
    Player(1),
    Lion(2),
    LionUsurpers(3),
    Marid(4),
    Efreet(5),
    Deepling(6),
    Deathling(7),
    Anuma(8),
    Fafnar(9),
}

/** `enum ZoneType_t` (src/items/items_definitions.hpp:479-485). */
enum class ZoneType {
    Protection,
    NoPvp,
    Pvp,
    NoLogout,
    Normal,
}

/**
 * Read-only projection of the `Monster` state the targeting rules read.
 *
 * [faction] is `Monster::getFaction()` (src/creatures/monsters/monster.cpp:260-265):
 * when the master is a player the caller passes `Faction.Player` here, and when the
 * master is a monster it is set on [master] so faction and enemy-faction lookups
 * follow the same delegation chain as the C++ getters. [masterPlayerId] is
 * `getMaster()->getPlayer()->getID()`, used by the summon friend/opponent checks.
 */
class MonsterTargetFacts(
    val origin: Position,
    val id: Long = 0L,
    val faction: Faction = Faction.Default,
    val enemyFactions: Set<Faction> = emptySet(),
    val master: MonsterTargetFacts? = null,
    val isSummon: Boolean = false,
    val isHostile: Boolean = true,
    val canSeeInvisibility: Boolean = false,
    val masterId: Long? = null,
    val masterPlayerId: Long? = null,
) {
    /** `Monster::getFaction` (src/creatures/monsters/monster.cpp:260-265). */
    fun effectiveFaction(): Faction = master?.effectiveFaction() ?: faction

    /** `Monster::isEnemyFaction` (src/creatures/monsters/monster.cpp:267-273). */
    fun isEnemyFaction(faction: Faction): Boolean =
        master?.isEnemyFaction(faction) ?: enemyFactions.contains(faction)
}

/**
 * Read-only projection of the `Creature` state the targeting rules read.
 *
 * [playerId] is `creature->getPlayer()->getID()` (null for non-players),
 * [masterId] `creature->getMaster()->getID()` and [masterPlayerId]
 * `creature->getMaster()->getPlayer()->getID()`.
 */
class TargetCreatureFacts(
    val id: Long,
    val position: Position,
    val health: Int = 1,
    val faction: Faction = Faction.Default,
    val playerId: Long? = null,
    val masterId: Long? = null,
    val masterPlayerId: Long? = null,
    val isRemoved: Boolean = false,
    val isAttackable: Boolean = true,
    val zoneType: ZoneType = ZoneType.Normal,
    val isInvisible: Boolean = false,
    val isSummon: Boolean = false,
    val isMonster: Boolean = false,
    val ignoresMonsters: Boolean = false,
)

/**
 * Port of `struct TargetSearchComputeRequest` reduced to the immediate search inputs
 * (src/creatures/monsters/monster.hpp:70-73) plus the `Monster::searchTargetImmediate`
 * dependencies (src/creatures/monsters/monster.cpp:906-1064).
 *
 * [creature] resolves a target-list id to its live state (`null` is the C++
 * expired `std::weak_ptr`); [canUseAttack] wraps `Monster::canUseAttack`;
 * [damageDealt] reads the `damageMap` total for an id (`null` when absent).
 */
class MonsterTargetSearchRequest(
    val searchType: TargetSearchType,
    val monster: MonsterTargetFacts,
    val targetIds: List<Long>,
    val currentAttackedId: Long? = null,
    val targetDistance: Int = 1,
    val hasFollowPath: Boolean = false,
    val monsterPerfTestFriendlyFire: Boolean = false,
    val strategiesTargetNearest: Int = 0,
    val strategiesTargetHealth: Int = 0,
    val strategiesTargetDamage: Int = 0,
    val creature: (Long) -> TargetCreatureFacts?,
    val canUseAttack: (Position, TargetCreatureFacts) -> Boolean = { _, _ -> true },
    val damageDealt: (Long) -> Int? = { null },
)

/**
 * Port of the monster target selection rules:
 * - `Monster::isTarget` (src/creatures/monsters/monster.cpp:1434-1454)
 * - `Monster::isFriend` (src/creatures/monsters/monster.cpp:812-834)
 * - `Monster::isOpponent` (src/creatures/monsters/monster.cpp:840-871)
 * - `Monster::searchTargetImmediate` (src/creatures/monsters/monster.cpp:906-1064)
 * - `Monster::updateTargetList` weak-ref/health/visibility pruning (src/creatures/monsters/monster.cpp:741-757)
 *
 * The asynchronous target-ranking pipeline (`requestTargetSearchCompute` and the
 * `MonsterComputeService`) is not ported; [searchTargetImmediate] is the synchronous
 * path Canary takes outside the barrier-parallel dispatcher context.
 */
object MonsterTargeting {

    /** `Creature::canSeeCreature` (src/creatures/creature.cpp:93-98). */
    fun canSeeCreature(monster: MonsterTargetFacts, creature: TargetCreatureFacts): Boolean =
        monster.canSeeInvisibility || !creature.isInvisible

    /** `isMonsterPerfTestFriendlyFireTarget` (src/creatures/monsters/monster.cpp:41-46). */
    fun isMonsterPerfTestFriendlyFireTarget(
        monster: MonsterTargetFacts,
        creature: TargetCreatureFacts,
        monsterPerfTestFriendlyFire: Boolean,
    ): Boolean = monsterPerfTestFriendlyFire &&
        monster.isHostile &&
        !monster.isSummon &&
        creature.isMonster &&
        creature.id != monster.id &&
        !creature.isSummon

    /** `Monster::isTarget(creature, monsterPerfTestFriendlyFire)` (monster.cpp:1434-1454). */
    fun isTarget(
        monster: MonsterTargetFacts,
        creature: TargetCreatureFacts,
        monsterPerfTestFriendlyFire: Boolean = false,
    ): Boolean {
        if (creature.isRemoved ||
            !creature.isAttackable ||
            creature.zoneType == ZoneType.Protection ||
            !canSeeCreature(monster, creature)
        ) {
            return false
        }

        if (creature.position.z != monster.origin.z) {
            return false
        }

        if (isMonsterPerfTestFriendlyFireTarget(monster, creature, monsterPerfTestFriendlyFire)) {
            return true
        }

        if (!monster.isSummon && monster.effectiveFaction() != Faction.Default) {
            return monster.isEnemyFaction(creature.faction)
        }

        return true
    }

    /** `Monster::isFriend(creature, monsterPerfTestFriendlyFire)` (monster.cpp:812-834). */
    fun isFriend(
        monster: MonsterTargetFacts,
        creature: TargetCreatureFacts,
        monsterPerfTestFriendlyFire: Boolean = false,
        isPartner: (Long, Long) -> Boolean = { _, _ -> false },
    ): Boolean {
        val masterPlayerId = monster.masterPlayerId
        if (monster.isSummon && masterPlayerId != null) {
            val tmpPlayerId = creature.playerId ?: creature.masterPlayerId
            if (tmpPlayerId != null &&
                (tmpPlayerId == monster.masterId || isPartner(masterPlayerId, tmpPlayerId))
            ) {
                return true
            }
        }

        if (isMonsterPerfTestFriendlyFireTarget(monster, creature, monsterPerfTestFriendlyFire)) {
            return false
        }

        return creature.isMonster && !creature.isSummon
    }

    /** `Monster::isOpponent(creature, monsterPerfTestFriendlyFire)` (monster.cpp:840-871). */
    fun isOpponent(
        monster: MonsterTargetFacts,
        creature: TargetCreatureFacts,
        monsterPerfTestFriendlyFire: Boolean = false,
    ): Boolean {
        val masterPlayerId = monster.masterPlayerId
        if (monster.isSummon && masterPlayerId != null) {
            return creature.id != monster.masterId
        }

        val playerId = creature.playerId
        if (playerId != null && creature.ignoresMonsters) {
            return false
        }

        if (isMonsterPerfTestFriendlyFireTarget(monster, creature, monsterPerfTestFriendlyFire)) {
            return true
        }

        if (monster.effectiveFaction() != Faction.Default) {
            return monster.isEnemyFaction(creature.faction) || creature.faction == Faction.Player
        }

        if (playerId != null || creature.masterPlayerId != null) {
            return true
        }

        return false
    }

    /**
     * `Monster::searchTargetImmediate` (src/creatures/monsters/monster.cpp:906-1064).
     *
     * [nextStrategyRoll] is `uniform_random(1, 100)` for the DEFAULT strategy roll and
     * [nextRandomIndex] is `uniform_random(0, resultList.size() - 1)` for TARGETSEARCH_RANDOM.
     * [selectTarget] is `Monster::selectTarget` (checks `isTarget`, login protection,
     * then sets the attacked/follow creature); a rejected ranked pick falls back to
     * `std::ranges::any_of(getTargetList(), ...)` exactly like the C++ tail.
     */
    fun searchTargetImmediate(
        request: MonsterTargetSearchRequest,
        selectTarget: (TargetCreatureFacts) -> Boolean,
        nextStrategyRoll: () -> Int = { 1 },
        nextRandomIndex: (Int) -> Int = { 0 },
    ): Boolean {
        var searchType = request.searchType
        if (searchType == TargetSearchType.Default) {
            searchType = chooseSearchType(
                strategiesTargetNearest = request.strategiesTargetNearest,
                strategiesTargetHealth = request.strategiesTargetHealth,
                strategiesTargetDamage = request.strategiesTargetDamage,
                roll = nextStrategyRoll(),
            )
        }

        val myPos = request.monster.origin
        val currentAttacked = request.currentAttackedId
        val skipCurrentUnreachable = currentAttacked != null && request.targetDistance <= 1 && !request.hasFollowPath

        val resultList = mutableListOf<TargetCreatureFacts>()
        for (targetId in request.targetIds) {
            val creature = request.creature(targetId) ?: continue
            if (!isTarget(request.monster, creature, request.monsterPerfTestFriendlyFire)) {
                continue
            }
            if (skipCurrentUnreachable && creature.id == currentAttacked) {
                continue
            }
            if (request.targetDistance == 1 || request.canUseAttack(myPos, creature)) {
                resultList.add(creature)
            }
        }

        if (resultList.isEmpty()) {
            return false
        }

        when (searchType) {
            TargetSearchType.Nearest -> {
                val selected = selectByNearest(request, resultList)
                if (selectTarget(selected)) {
                    return true
                }
            }

            TargetSearchType.Hp -> {
                val selected = selectByHealth(resultList)
                if (selectTarget(selected)) {
                    return true
                }
            }

            TargetSearchType.Damage -> {
                val selected = selectByDamage(request, resultList)
                if (selectTarget(selected)) {
                    return true
                }
            }

            TargetSearchType.Random, TargetSearchType.Default -> {
                val selected = resultList[nextRandomIndex(resultList.size)]
                return selectTarget(selected)
            }
        }

        for (targetId in request.targetIds) {
            val creature = request.creature(targetId) ?: continue
            if (skipCurrentUnreachable && creature.id == currentAttacked) {
                continue
            }
            if (selectTarget(creature)) {
                return true
            }
        }

        return false
    }

    /**
     * `Monster::searchTargetImmediate` DEFAULT strategy roll (monster.cpp:907-925):
     * the nearest weight claims 1..sum, then health, then damage, and anything above
     * all three is random.
     */
    fun chooseSearchType(
        strategiesTargetNearest: Int,
        strategiesTargetHealth: Int,
        strategiesTargetDamage: Int,
        roll: Int,
    ): TargetSearchType {
        var searchType = TargetSearchType.Nearest
        var sum = strategiesTargetNearest
        if (roll > sum) {
            searchType = TargetSearchType.Hp
            sum += strategiesTargetHealth
            if (roll > sum) {
                searchType = TargetSearchType.Damage
                sum += strategiesTargetDamage
                if (roll > sum) {
                    searchType = TargetSearchType.Random
                }
            }
        }
        return searchType
    }

    /**
     * `Monster::updateTargetList` target/friend-list cleanup (monster.cpp:741-757):
     * entries whose creature expired, died or left the monster's view are forgotten,
     * keeping the surviving order.
     */
    fun pruneTargetList(
        targetIds: List<Long>,
        resolve: (Long) -> TargetCreatureFacts?,
        canSee: (Position) -> Boolean,
    ): List<Long> = targetIds.filter { targetId ->
        val creature = resolve(targetId)
        creature != null && creature.health > 0 && canSee(creature.position)
    }

    private fun selectByNearest(
        request: MonsterTargetSearchRequest,
        resultList: List<TargetCreatureFacts>,
    ): TargetCreatureFacts {
        var selected = resultList.first()
        var minimumRange = max(
            abs(request.monster.origin.x - selected.position.x),
            abs(request.monster.origin.y - selected.position.y),
        ) + selected.faction.value * 100
        for (index in 1 until resultList.size) {
            val candidate = resultList[index]
            val distance = max(
                abs(request.monster.origin.x - candidate.position.x),
                abs(request.monster.origin.y - candidate.position.y),
            ) + candidate.faction.value * 100
            if (distance < minimumRange) {
                selected = candidate
                minimumRange = distance
            }
        }
        return selected
    }

    private fun selectByHealth(resultList: List<TargetCreatureFacts>): TargetCreatureFacts {
        var selected = resultList.first()
        var minimumHealth = selected.health + selected.faction.value * 100000
        for (index in 1 until resultList.size) {
            val candidate = resultList[index]
            val health = candidate.health + candidate.faction.value * 100000
            if (health < minimumHealth) {
                selected = candidate
                minimumHealth = health
            }
        }
        return selected
    }

    private fun selectByDamage(
        request: MonsterTargetSearchRequest,
        resultList: List<TargetCreatureFacts>,
    ): TargetCreatureFacts {
        var selected = resultList.first()
        var mostDamage = request.damageDealt(selected.id)?.let { it + selected.faction.value * 100000 }
            ?: Int.MIN_VALUE
        for (index in 1 until resultList.size) {
            val candidate = resultList[index]
            val damage = request.damageDealt(candidate.id) ?: continue
            val damageScore = damage + candidate.faction.value * 100000
            if (damageScore > mostDamage) {
                mostDamage = damageScore
                selected = candidate
            }
        }
        return selected
    }
}
