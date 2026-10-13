package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

/**
 * Transcribes Canary:
 * - `MonsterTargetRanker::rank` (src/creatures/monsters/monster_targeting.cpp:18-83)
 * - `Monster::isTarget` / `isFriend` / `isOpponent` (src/creatures/monsters/monster.cpp:808-871, 1434-1454)
 * - `Monster::searchTargetImmediate` (src/creatures/monsters/monster.cpp:906-1064)
 * - `Monster::updateTargetList` pruning (src/creatures/monsters/monster.cpp:741-757)
 */
class MonsterTargetingTest {

    private val origin = Position(100, 100, 7)

    private fun candidate(
        id: Long,
        x: Int,
        y: Int,
        faction: Int = 0,
        health: Int = 100,
        damage: Int = 0,
        hasDamage: Boolean = false,
    ) = MonsterTargetCandidate(
        creatureId = id,
        position = Position(x, y, 7),
        faction = faction,
        health = health,
        damage = damage,
        hasDamage = hasDamage,
    )

    private fun creature(
        id: Long,
        x: Int = 101,
        y: Int = 100,
        z: Int = 7,
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
    ) = TargetCreatureFacts(
        id = id,
        position = Position(x, y, z),
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
    )

    private fun monster(
        faction: Faction = Faction.Default,
        enemyFactions: Set<Faction> = emptySet(),
        master: MonsterTargetFacts? = null,
        isSummon: Boolean = false,
        isHostile: Boolean = true,
        canSeeInvisibility: Boolean = false,
        masterId: Long? = null,
        masterPlayerId: Long? = null,
    ) = MonsterTargetFacts(
        origin = origin,
        faction = faction,
        enemyFactions = enemyFactions,
        master = master,
        isSummon = isSummon,
        isHostile = isHostile,
        canSeeInvisibility = canSeeInvisibility,
        masterId = masterId,
        masterPlayerId = masterPlayerId,
    )

    // --- MonsterTargetRanker::rank ---

    @Test
    fun `nearest rank picks the lowest chebyshev distance`() {
        val result = MonsterTargetRanker.rank(
            MonsterTargetRankingRequest(
                mode = MonsterTargetRankMode.Nearest,
                origin = origin,
                candidates = listOf(candidate(1, 105, 100), candidate(2, 101, 102), candidate(3, 103, 103)),
            )
        )

        assertThat(result.suggestedCreatureId).isEqualTo(2L)
        assertThat(result.canceled).isFalse()
    }

    @Test
    fun `nearest rank counts faction at a hundred per point`() {
        val result = MonsterTargetRanker.rank(
            MonsterTargetRankingRequest(
                mode = MonsterTargetRankMode.Nearest,
                origin = origin,
                candidates = listOf(candidate(1, 101, 100, faction = 1), candidate(2, 110, 100, faction = 0)),
            )
        )

        assertThat(result.suggestedCreatureId).isEqualTo(2L)
    }

    @Test
    fun `nearest rank keeps the earlier candidate on a tie`() {
        val result = MonsterTargetRanker.rank(
            MonsterTargetRankingRequest(
                mode = MonsterTargetRankMode.Nearest,
                origin = origin,
                candidates = listOf(candidate(1, 102, 100), candidate(2, 100, 102), candidate(3, 98, 102)),
            )
        )

        assertThat(result.suggestedCreatureId).isEqualTo(1L)
    }

    @Test
    fun `health rank picks the lowest health and counts faction at a hundred thousand`() {
        val result = MonsterTargetRanker.rank(
            MonsterTargetRankingRequest(
                mode = MonsterTargetRankMode.Health,
                origin = origin,
                candidates = listOf(candidate(1, 101, 100, health = 10), candidate(2, 105, 100, faction = 1, health = 1), candidate(3, 103, 100, health = 5)),
            )
        )

        assertThat(result.suggestedCreatureId).isEqualTo(3L)
    }

    @Test
    fun `damage rank ignores candidates without a damage entry`() {
        val result = MonsterTargetRanker.rank(
            MonsterTargetRankingRequest(
                mode = MonsterTargetRankMode.Damage,
                origin = origin,
                candidates = listOf(
                    candidate(1, 101, 100, damage = 999, hasDamage = false),
                    candidate(2, 102, 100, damage = 10, hasDamage = true),
                    candidate(3, 103, 100, damage = 5, hasDamage = true),
                ),
            )
        )

        assertThat(result.suggestedCreatureId).isEqualTo(2L)
    }

    @Test
    fun `damage rank counts faction at a hundred thousand`() {
        val result = MonsterTargetRanker.rank(
            MonsterTargetRankingRequest(
                mode = MonsterTargetRankMode.Damage,
                origin = origin,
                candidates = listOf(
                    candidate(1, 101, 100, damage = 99999, hasDamage = true),
                    candidate(2, 105, 100, faction = 1, damage = 1, hasDamage = true),
                ),
            )
        )

        assertThat(result.suggestedCreatureId).isEqualTo(2L)
    }

    @Test
    fun `damage rank falls back to the first candidate when nobody dealt damage`() {
        val result = MonsterTargetRanker.rank(
            MonsterTargetRankingRequest(
                mode = MonsterTargetRankMode.Damage,
                origin = origin,
                candidates = listOf(candidate(1, 101, 100), candidate(2, 102, 100)),
            )
        )

        assertThat(result.suggestedCreatureId).isEqualTo(1L)
    }

    @Test
    fun `rank reports cancellation before scoring`() {
        val result = MonsterTargetRanker.rank(
            MonsterTargetRankingRequest(
                mode = MonsterTargetRankMode.Nearest,
                origin = origin,
                candidates = listOf(candidate(1, 101, 100)),
            ),
            stopRequested = { true },
        )

        assertThat(result.canceled).isTrue()
        assertThat(result.suggestedCreatureId).isZero()
    }

    @Test
    fun `rank reports cancellation during scoring`() {
        var checks = 0
        val result = MonsterTargetRanker.rank(
            MonsterTargetRankingRequest(
                mode = MonsterTargetRankMode.Nearest,
                origin = origin,
                candidates = listOf(candidate(1, 105, 100), candidate(2, 101, 100), candidate(3, 103, 103)),
            ),
            stopRequested = { ++checks > 1 },
        )

        assertThat(result.canceled).isTrue()
        assertThat(result.suggestedCreatureId).isZero()
    }

    @Test
    fun `rank with no candidates returns an empty result`() {
        val result = MonsterTargetRanker.rank(
            MonsterTargetRankingRequest(mode = MonsterTargetRankMode.Nearest, origin = origin, candidates = emptyList())
        )

        assertThat(result.canceled).isFalse()
        assertThat(result.suggestedCreatureId).isZero()
    }

    // --- Transcript of Canary tests/unit/game/monster_targeting_test.cpp ---

    private fun canaryCandidate(
        id: Long,
        x: Int,
        faction: Int = 0,
        health: Int = 100,
        damage: Int = 0,
        hasDamage: Boolean = true,
    ) = candidate(id, x, 100, faction, health, damage, hasDamage)

    @Test
    fun `ScoresEachNearestCandidateWithItsOwnFaction`() {
        val result = MonsterTargetRanker.rank(
            MonsterTargetRankingRequest(
                mode = MonsterTargetRankMode.Nearest,
                origin = origin,
                candidates = listOf(canaryCandidate(10, 105, faction = 1), canaryCandidate(20, 101, faction = 0)),
            )
        )

        assertThat(result.suggestedCreatureId).isEqualTo(20L)
        assertThat(result.canceled).isFalse()
    }

    @Test
    fun `ScoresEachHealthCandidateWithItsOwnFaction`() {
        val result = MonsterTargetRanker.rank(
            MonsterTargetRankingRequest(
                mode = MonsterTargetRankMode.Health,
                origin = origin,
                candidates = listOf(canaryCandidate(10, 105, faction = 0, health = 100), canaryCandidate(20, 104, faction = 1, health = 1)),
            )
        )

        assertThat(result.suggestedCreatureId).isEqualTo(10L)
    }

    @Test
    fun `PreservesDamageSelectionFallbackToFirstCandidate`() {
        val result = MonsterTargetRanker.rank(
            MonsterTargetRankingRequest(
                mode = MonsterTargetRankMode.Damage,
                origin = origin,
                candidates = listOf(canaryCandidate(10, 105, damage = 1000), canaryCandidate(20, 104, damage = 0)),
            )
        )

        assertThat(result.suggestedCreatureId).isEqualTo(10L)
    }

    @Test
    fun `KeepsHighestDamageCandidateWhenItAppearsFirst`() {
        val result = MonsterTargetRanker.rank(
            MonsterTargetRankingRequest(
                mode = MonsterTargetRankMode.Damage,
                origin = origin,
                candidates = listOf(
                    canaryCandidate(10, 105, damage = 1000),
                    canaryCandidate(20, 104, damage = 10),
                    canaryCandidate(30, 103, damage = 20),
                ),
            )
        )

        assertThat(result.suggestedCreatureId).isEqualTo(10L)
    }

    @Test
    fun `PreservesSelectedDamageFactionScore`() {
        val result = MonsterTargetRanker.rank(
            MonsterTargetRankingRequest(
                mode = MonsterTargetRankMode.Damage,
                origin = origin,
                candidates = listOf(
                    canaryCandidate(10, 105, faction = 0, damage = 1000),
                    canaryCandidate(20, 104, faction = 1, damage = 1),
                    canaryCandidate(30, 103, faction = 0, damage = 2),
                ),
            )
        )

        assertThat(result.suggestedCreatureId).isEqualTo(20L)
    }

    @Test
    fun `IgnoresFactionOffsetWithoutRecordedDamage`() {
        val result = MonsterTargetRanker.rank(
            MonsterTargetRankingRequest(
                mode = MonsterTargetRankMode.Damage,
                origin = origin,
                candidates = listOf(canaryCandidate(10, 105, damage = 1000), canaryCandidate(20, 104, faction = 1, damage = 0, hasDamage = false)),
            )
        )

        assertThat(result.suggestedCreatureId).isEqualTo(10L)
    }

    @Test
    fun `HonorsCancellationAndEmptyInput`() {
        var checks = 0
        val empty = MonsterTargetRanker.rank(
            MonsterTargetRankingRequest(mode = MonsterTargetRankMode.Nearest, origin = origin, candidates = emptyList()),
            stopRequested = { checks++; true },
        )
        val canceled = MonsterTargetRanker.rank(
            MonsterTargetRankingRequest(
                mode = MonsterTargetRankMode.Nearest,
                origin = origin,
                candidates = listOf(canaryCandidate(10, 105)),
            ),
            stopRequested = { true },
        )

        assertThat(empty.suggestedCreatureId).isZero()
        assertThat(empty.canceled).isFalse()
        assertThat(checks).isZero()
        assertThat(canceled.canceled).isTrue()
        assertThat(canceled.suggestedCreatureId).isZero()
    }

    // --- Monster::isTarget ---

    @Test
    fun `isTarget rejects removed unattackable protected and invisible creatures`() {
        val monster = monster()

        assertThat(MonsterTargeting.isTarget(monster, creature(1, isRemoved = true))).isFalse()
        assertThat(MonsterTargeting.isTarget(monster, creature(1, isAttackable = false))).isFalse()
        assertThat(MonsterTargeting.isTarget(monster, creature(1, zoneType = ZoneType.Protection))).isFalse()
        assertThat(MonsterTargeting.isTarget(monster, creature(1, isInvisible = true))).isFalse()
    }

    @Test
    fun `isTarget accepts invisible creatures when the monster sees invisibility`() {
        val monster = monster(canSeeInvisibility = true)

        assertThat(MonsterTargeting.isTarget(monster, creature(1, isInvisible = true))).isTrue()
    }

    @Test
    fun `isTarget rejects creatures on another floor`() {
        assertThat(MonsterTargeting.isTarget(monster(), creature(1, z = 8))).isFalse()
    }

    @Test
    fun `isTarget applies the monster enemy faction list`() {
        val factionMonster = monster(faction = Faction.Lion, enemyFactions = setOf(Faction.Player, Faction.Marid))

        assertThat(MonsterTargeting.isTarget(factionMonster, creature(1, faction = Faction.Player))).isTrue()
        assertThat(MonsterTargeting.isTarget(factionMonster, creature(2, faction = Faction.Marid))).isTrue()
        assertThat(MonsterTargeting.isTarget(factionMonster, creature(3, faction = Faction.Lion))).isFalse()
        assertThat(MonsterTargeting.isTarget(monster(), creature(4, faction = Faction.Lion))).isTrue()
    }

    @Test
    fun `isTarget defers enemy factions to a monster master`() {
        val mastered = monster(
            faction = Faction.Lion,
            master = monster(faction = Faction.Lion, enemyFactions = setOf(Faction.Player)),
        )

        assertThat(MonsterTargeting.isTarget(mastered, creature(1, faction = Faction.Player))).isTrue()
        assertThat(MonsterTargeting.isTarget(mastered, creature(2, faction = Faction.Marid))).isFalse()
    }

    @Test
    fun `isTarget friendly fire treats hostile monsters as targets`() {
        val friendlyFireMonster = monster(faction = Faction.Lion)
        val hostileMonster = creature(1, faction = Faction.Lion, isMonster = true)

        assertThat(MonsterTargeting.isTarget(friendlyFireMonster, hostileMonster, monsterPerfTestFriendlyFire = true)).isTrue()
        assertThat(MonsterTargeting.isTarget(friendlyFireMonster, hostileMonster, monsterPerfTestFriendlyFire = false)).isFalse()
    }

    @Test
    fun `isTarget skips the faction gate for summons`() {
        val summon = monster(faction = Faction.Lion, isSummon = true)

        assertThat(MonsterTargeting.isTarget(summon, creature(1, faction = Faction.Lion))).isTrue()
    }

    // --- Monster::isFriend ---

    @Test
    fun `isFriend recognizes the summon master and its party partners`() {
        val facts = monster(isSummon = true, masterId = 1L, masterPlayerId = 1L)

        assertThat(MonsterTargeting.isFriend(facts, creature(1, playerId = 1))).isTrue()
        assertThat(
            MonsterTargeting.isFriend(facts, creature(2, playerId = 2), isPartner = { a, b -> a == 1L && b == 2L }),
        ).isTrue()
        assertThat(MonsterTargeting.isFriend(facts, creature(3, playerId = 3))).isFalse()
        assertThat(MonsterTargeting.isFriend(monster(isSummon = true), creature(1, playerId = 1))).isFalse()
    }

    @Test
    fun `isFriend accepts only non-summon monsters`() {
        val monster = monster()

        assertThat(MonsterTargeting.isFriend(monster, creature(1, isMonster = true))).isTrue()
        assertThat(MonsterTargeting.isFriend(monster, creature(2, isMonster = true, isSummon = true))).isFalse()
        assertThat(MonsterTargeting.isFriend(monster, creature(3, playerId = 3))).isFalse()
    }

    @Test
    fun `isFriend rejects friendly fire targets`() {
        val friendlyFireMonster = monster()
        val hostileMonster = creature(1, isMonster = true)

        assertThat(MonsterTargeting.isFriend(friendlyFireMonster, hostileMonster, monsterPerfTestFriendlyFire = true)).isFalse()
        assertThat(MonsterTargeting.isFriend(friendlyFireMonster, hostileMonster, monsterPerfTestFriendlyFire = false)).isTrue()
    }

    // --- Monster::isOpponent ---

    @Test
    fun `isOpponent for a summon targets everything but its master`() {
        val summon = monster(isSummon = true, masterId = 1L, masterPlayerId = 1L)

        assertThat(MonsterTargeting.isOpponent(summon, creature(1, playerId = 1))).isFalse()
        assertThat(MonsterTargeting.isOpponent(summon, creature(2, playerId = 2))).isTrue()
    }

    @Test
    fun `isOpponent ignores players flagged IgnoredByMonsters`() {
        val ignored = creature(1, playerId = 1, ignoresMonsters = true)

        assertThat(MonsterTargeting.isOpponent(monster(), ignored)).isFalse()
    }

    @Test
    fun `isOpponent uses enemy factions when set`() {
        val factionMonster = monster(faction = Faction.Lion, enemyFactions = setOf(Faction.Marid))

        assertThat(MonsterTargeting.isOpponent(factionMonster, creature(1, faction = Faction.Player))).isTrue()
        assertThat(MonsterTargeting.isOpponent(factionMonster, creature(2, faction = Faction.Marid))).isTrue()
        assertThat(MonsterTargeting.isOpponent(factionMonster, creature(3, faction = Faction.Lion))).isFalse()
    }

    @Test
    fun `isOpponent prefers player controlled creatures by default`() {
        val plain = monster()

        assertThat(MonsterTargeting.isOpponent(plain, creature(1, playerId = 1))).isTrue()
        assertThat(MonsterTargeting.isOpponent(plain, creature(2, masterPlayerId = 1))).isTrue()
        assertThat(MonsterTargeting.isOpponent(plain, creature(3, isMonster = true))).isFalse()
    }

    @Test
    fun `isOpponent friendly fire targets hostile monsters`() {
        val plain = monster()
        val hostileMonster = creature(1, isMonster = true)

        assertThat(MonsterTargeting.isOpponent(plain, hostileMonster, monsterPerfTestFriendlyFire = true)).isTrue()
        assertThat(MonsterTargeting.isOpponent(plain, hostileMonster, monsterPerfTestFriendlyFire = false)).isFalse()
    }

    // --- search strategy roll ---

    @Test
    fun `search strategy roll follows the nearest health damage random bands`() {
        assertThat(MonsterTargeting.chooseSearchType(30, 30, 30, 30)).isEqualTo(TargetSearchType.Nearest)
        assertThat(MonsterTargeting.chooseSearchType(30, 30, 30, 31)).isEqualTo(TargetSearchType.Hp)
        assertThat(MonsterTargeting.chooseSearchType(30, 30, 30, 60)).isEqualTo(TargetSearchType.Hp)
        assertThat(MonsterTargeting.chooseSearchType(30, 30, 30, 61)).isEqualTo(TargetSearchType.Damage)
        assertThat(MonsterTargeting.chooseSearchType(30, 30, 30, 90)).isEqualTo(TargetSearchType.Damage)
        assertThat(MonsterTargeting.chooseSearchType(30, 30, 30, 91)).isEqualTo(TargetSearchType.Random)
        assertThat(MonsterTargeting.chooseSearchType(30, 30, 30, 100)).isEqualTo(TargetSearchType.Random)
        assertThat(MonsterTargeting.chooseSearchType(100, 0, 0, 1)).isEqualTo(TargetSearchType.Nearest)
    }

    // --- Monster::updateTargetList pruning ---

    @Test
    fun `prune drops expired dead and out of sight targets keeping order`() {
        val creatures = mapOf(
            1L to creature(1, x = 102),
            3L to creature(3, health = 0),
            4L to creature(4),
            5L to creature(5, x = 104),
        )

        val remaining = MonsterTargeting.pruneTargetList(
            targetIds = listOf(1L, 2L, 3L, 4L, 5L),
            resolve = { creatures[it] },
            canSee = { it.x != 101 },
        )

        assertThat(remaining).containsExactly(1L, 5L)
    }

    // --- Monster::searchTargetImmediate ---

    private fun searchRequest(
        searchType: TargetSearchType,
        targetIds: List<Long>,
        creatures: Map<Long, TargetCreatureFacts>,
        currentAttackedId: Long? = null,
        targetDistance: Int = 1,
        hasFollowPath: Boolean = false,
        monsterPerfTestFriendlyFire: Boolean = false,
        strategiesTargetNearest: Int = 0,
        strategiesTargetHealth: Int = 0,
        strategiesTargetDamage: Int = 0,
        canUseAttack: (Position, TargetCreatureFacts) -> Boolean = { _, _ -> true },
        damageDealt: (Long) -> Int? = { null },
    ) = MonsterTargetSearchRequest(
        searchType = searchType,
        monster = monster(),
        targetIds = targetIds,
        currentAttackedId = currentAttackedId,
        targetDistance = targetDistance,
        hasFollowPath = hasFollowPath,
        monsterPerfTestFriendlyFire = monsterPerfTestFriendlyFire,
        strategiesTargetNearest = strategiesTargetNearest,
        strategiesTargetHealth = strategiesTargetHealth,
        strategiesTargetDamage = strategiesTargetDamage,
        creature = { creatures[it] },
        canUseAttack = canUseAttack,
        damageDealt = damageDealt,
    )

    @Test
    fun `nearest search picks the nearest eligible target`() {
        val creatures = mapOf(
            1L to creature(1, x = 103),
            2L to creature(2, x = 101),
            3L to creature(3, x = 105),
        )
        var selected: Long? = null

        val found = MonsterTargeting.searchTargetImmediate(
            request = searchRequest(
                TargetSearchType.Nearest,
                listOf(1L, 2L, 3L),
                creatures,
                canUseAttack = { _, _ -> false },
            ),
            selectTarget = { selected = it.id; true },
        )

        assertThat(found).isTrue()
        assertThat(selected).isEqualTo(2L)
    }

    @Test
    fun `search filters targets the monster cannot attack`() {
        val creatures = mapOf(
            1L to creature(1, x = 102),
            2L to creature(2, x = 104),
        )
        var selected: Long? = null

        val found = MonsterTargeting.searchTargetImmediate(
            request = searchRequest(
                TargetSearchType.Nearest,
                listOf(1L, 2L),
                creatures,
                targetDistance = 10,
                canUseAttack = { _, c -> c.id == 2L },
            ),
            selectTarget = { selected = it.id; true },
        )

        assertThat(found).isTrue()
        assertThat(selected).isEqualTo(2L)
    }

    @Test
    fun `search skips the current unreachable target unless it follows a path`() {
        val creatures = mapOf(
            1L to creature(1, x = 101),
            2L to creature(2, x = 103),
        )
        var selected: Long? = null

        val blocked = MonsterTargeting.searchTargetImmediate(
            request = searchRequest(TargetSearchType.Nearest, listOf(1L, 2L), creatures, currentAttackedId = 1L),
            selectTarget = { selected = it.id; true },
        )

        assertThat(blocked).isTrue()
        assertThat(selected).isEqualTo(2L)

        val reachable = MonsterTargeting.searchTargetImmediate(
            request = searchRequest(TargetSearchType.Nearest, listOf(1L, 2L), creatures, currentAttackedId = 1L, hasFollowPath = true),
            selectTarget = { selected = it.id; true },
        )

        assertThat(reachable).isTrue()
        assertThat(selected).isEqualTo(1L)
    }

    @Test
    fun `health search picks the lowest health target`() {
        val creatures = mapOf(
            1L to creature(1, health = 50),
            2L to creature(2, x = 105, health = 10),
        )
        var selected: Long? = null

        val found = MonsterTargeting.searchTargetImmediate(
            request = searchRequest(TargetSearchType.Hp, listOf(1L, 2L), creatures),
            selectTarget = { selected = it.id; true },
        )

        assertThat(found).isTrue()
        assertThat(selected).isEqualTo(2L)
    }

    @Test
    fun `damage search picks the target with the most damage`() {
        val creatures = mapOf(
            1L to creature(1, x = 101),
            2L to creature(2, x = 105),
        )
        val damage = mapOf<Long, Int>(2L to 30)

        var selected: Long? = null
        val found = MonsterTargeting.searchTargetImmediate(
            request = searchRequest(TargetSearchType.Damage, listOf(1L, 2L), creatures, damageDealt = { damage[it] }),
            selectTarget = { selected = it.id; true },
        )

        assertThat(found).isTrue()
        assertThat(selected).isEqualTo(2L)
    }

    @Test
    fun `random search uses the injected index`() {
        val creatures = mapOf(
            1L to creature(1, x = 101),
            2L to creature(2, x = 103),
        )
        var selected: Long? = null

        val found = MonsterTargeting.searchTargetImmediate(
            request = searchRequest(TargetSearchType.Random, listOf(1L, 2L), creatures),
            selectTarget = { selected = it.id; true },
            nextRandomIndex = { it - 1 },
        )

        assertThat(found).isTrue()
        assertThat(selected).isEqualTo(2L)
    }

    @Test
    fun `default search rolls the strategy distribution before selecting`() {
        val creatures = mapOf(
            1L to creature(1, x = 101, health = 100),
            2L to creature(2, x = 105, health = 10),
        )
        var selected: Long? = null

        val found = MonsterTargeting.searchTargetImmediate(
            request = searchRequest(
                TargetSearchType.Default,
                listOf(1L, 2L),
                creatures,
                strategiesTargetNearest = 0,
                strategiesTargetHealth = 100,
            ),
            selectTarget = { selected = it.id; true },
            nextStrategyRoll = { 50 },
        )

        assertThat(found).isTrue()
        assertThat(selected).isEqualTo(2L)
    }

    @Test
    fun `search falls back to the first selectable when the ranked pick is rejected`() {
        val creatures = mapOf(
            1L to creature(1, x = 101),
            2L to creature(2, x = 103),
        )
        val attempts = mutableListOf<Long>()
        var selected: Long? = null

        val found = MonsterTargeting.searchTargetImmediate(
            request = searchRequest(TargetSearchType.Nearest, listOf(1L, 2L), creatures),
            selectTarget = { attempts.add(it.id); if (it.id == 2L) selected = it.id; it.id == 2L },
        )

        assertThat(found).isTrue()
        assertThat(selected).isEqualTo(2L)
        assertThat(attempts).containsExactly(1L, 1L, 2L)
    }

    @Test
    fun `search returns false when no target is eligible`() {
        var calls = 0

        val empty = MonsterTargeting.searchTargetImmediate(
            request = searchRequest(TargetSearchType.Nearest, emptyList(), emptyMap()),
            selectTarget = { calls++; true },
        )
        val allBlocked = MonsterTargeting.searchTargetImmediate(
            request = searchRequest(
                TargetSearchType.Nearest,
                listOf(1L),
                mapOf(1L to creature(1, zoneType = ZoneType.Protection)),
            ),
            selectTarget = { calls++; true },
        )

        assertThat(empty).isFalse()
        assertThat(allBlocked).isFalse()
        assertThat(calls).isZero()
    }
}
