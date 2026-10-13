package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.BlockType
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile

/**
 * Transcribes the remaining Canary `Monster` runtime behavior:
 * - spells: `Monster::canUseAttack`/`canUseSpell`/`commitCombatIntention`/`onThinkDefense`
 *   (src/creatures/monsters/monster.cpp:2095-2272)
 * - death/corpse: `Monster::death`, `Creature::dropCorpse` wrapper and `Monster::getCorpse`
 *   (src/creatures/monsters/monster.cpp:3227-3320, src/creatures/creature.cpp:732-843)
 * - defense/reflect: `Monster::getDefense`/`blockHit`/`isImmune`/`getReflectPercent`
 *   (src/creatures/monsters/monster.cpp:209-258, 1402-1428, 3535-3565)
 * - walking: `Monster::getRandomStep`/`getDanceStep`/`doRandomStep`/`doWalkBack`
 *   (src/creatures/monsters/monster.cpp:2494-2568, 2570-2630)
 * - despawn: `Monster::isInSpawnLocation`/`isInSpawnRange` (src/creatures/monsters/monster.cpp:1562-1567, 3322-3344)
 * - idle/walk-back decision: `Monster::updateIdleStatus`/`setIdle`
 *   (src/creatures/monsters/monster.cpp:1499-1556)
 */
class MonsterBehaviorTest {

    private val pos = Position(100, 100, 7)

    private fun monster(id: Long = 1, position: Position = pos) = Monster(id, "Rat", 100, 100, position = position)

    private fun player(id: Long = 2, position: Position = pos) = Player(id, "Hero", 100, 100, position = position)

    private fun ownerFacts(
        id: Long,
        playerId: Long? = null,
        masterId: Long? = null,
        masterPlayerId: Long? = null,
    ) = TargetCreatureFacts(
        id = id,
        position = pos,
        playerId = playerId,
        masterId = masterId,
        masterPlayerId = masterPlayerId,
    )

    // --- spell selection and casting ---

    @Test
    fun `canUseAttack ignores range zero spells and requires an in-range ranged spell with clear sight`() {
        val meleeOnly = MonsterSpellBlock(range = 0)
        val ranged = MonsterSpellBlock(range = 3)
        val origin = Position(10, 10, 7)

        assertThat(MonsterBehavior.canUseAttack(listOf(meleeOnly), isHostile = true, origin, Position(20, 20, 7), { _, _ -> true })).isFalse
        assertThat(MonsterBehavior.canUseAttack(listOf(ranged), isHostile = true, origin, Position(13, 13, 7), { _, _ -> true })).isTrue
        assertThat(MonsterBehavior.canUseAttack(listOf(ranged), isHostile = true, origin, Position(14, 10, 7), { _, _ -> true })).isFalse
        assertThat(MonsterBehavior.canUseAttack(listOf(ranged), isHostile = true, origin, Position(12, 10, 7), { _, _ -> false })).isFalse
        assertThat(MonsterBehavior.canUseAttack(emptyList(), isHostile = false, origin, Position(20, 20, 7), { _, _ -> false })).isTrue
    }

    @Test
    fun `canUseSpell rejects melee spells while fleeing and while the melee swing is cooling down`() {
        val state = MonsterBehaviorState()
        val melee = MonsterSpellBlock(spell = BaseSpell { _, _ -> }, isMelee = true, speed = 100, range = 1)
        val origin = Position(10, 10, 7)

        val fleeing = MonsterBehavior.canUseSpell(state, melee, origin, Position(10, 11, 7), interval = 100, isFleeing = true, resetTicks = true, now = 0)
        assertThat(fleeing.canUse).isFalse
        assertThat(fleeing.inRange).isTrue
        assertThat(fleeing.resetTicks).isTrue

        state.lastMeleeAttack = 1000
        val cooling = MonsterBehavior.canUseSpell(state, melee, origin, Position(10, 11, 7), interval = 100, isFleeing = false, resetTicks = true, now = 2000)
        assertThat(cooling.canUse).isFalse

        state.attackTicks = 100
        val ready = MonsterBehavior.canUseSpell(state, melee, origin, Position(10, 11, 7), interval = 100, isFleeing = false, resetTicks = true, now = 2500)
        assertThat(ready.canUse).isTrue
    }

    @Test
    fun `canUseSpell extra melee swing refreshes the swing clock and bypasses the speed gates`() {
        val state = MonsterBehaviorState().apply {
            extraMeleeAttack = true
            attackTicks = 0
        }
        val melee = MonsterSpellBlock(spell = BaseSpell { _, _ -> }, isMelee = true, speed = 2000, range = 1)

        val result = MonsterBehavior.canUseSpell(state, melee, Position(10, 10, 7), Position(10, 11, 7), interval = 100, isFleeing = false, resetTicks = true, now = 777)

        assertThat(result.canUse).isTrue
        assertThat(state.lastMeleeAttack).isEqualTo(777)
    }

    @Test
    fun `canUseSpell applies the speed cooldown and the once per round attack ticks gate`() {
        val state = MonsterBehaviorState()
        val cooling = MonsterBehavior.canUseSpell(state, MonsterSpellBlock(spell = BaseSpell { _, _ -> }, speed = 2000), Position(10, 10, 7), Position(11, 10, 7), 100, false, true, 0)
        assertThat(cooling.canUse).isFalse
        assertThat(cooling.resetTicks).isFalse

        state.attackTicks = 1000
        val alreadyUsed = MonsterBehavior.canUseSpell(state, MonsterSpellBlock(spell = BaseSpell { _, _ -> }, speed = 400), Position(10, 10, 7), Position(11, 10, 7), 100, false, true, 0)
        assertThat(alreadyUsed.canUse).isFalse
        assertThat(alreadyUsed.resetTicks).isTrue
    }

    @Test
    fun `canUseSpell reports out of range without rejecting the spell shape`() {
        val state = MonsterBehaviorState().apply { attackTicks = 100 }
        val ranged = MonsterSpellBlock(spell = BaseSpell { _, _ -> }, speed = 100, range = 2)

        val result = MonsterBehavior.canUseSpell(state, ranged, Position(10, 10, 7), Position(13, 10, 7), 100, false, true, 0)

        assertThat(result.canUse).isFalse
        assertThat(result.inRange).isFalse
        assertThat(result.resetTicks).isTrue
    }

    @Test
    fun `commitCombatIntention casts a passing spell on the target and copies its combat values`() {
        val state = MonsterBehaviorState().apply { attackTicks = 100 }
        val caster = monster(position = Position(10, 10, 7))
        val target: Creature = player(position = Position(11, 10, 7))
        val casts = mutableListOf<Pair<Monster, Creature>>()
        val block = MonsterSpellBlock(
            spell = BaseSpell { creature, victim -> casts.add(creature to victim) },
            chance = 100,
            speed = 100,
            minCombatValue = 5,
            maxCombatValue = 9,
        )

        MonsterBehavior.commitCombatIntention(state, caster, target, listOf(block), interval = 100, isFleeing = false, nextRandom = { min, _ -> min })

        assertThat(casts).containsExactly(caster to target)
        assertThat(state.minCombatValue).isEqualTo(5)
        assertThat(state.maxCombatValue).isEqualTo(9)
        assertThat(state.attackTicks).isZero()
    }

    @Test
    fun `commitCombatIntention skips the cast when the chance roll misses`() {
        val state = MonsterBehaviorState().apply { attackTicks = 100 }
        val casts = mutableListOf<Pair<Monster, Creature>>()
        val block = MonsterSpellBlock(
            spell = BaseSpell { creature, victim -> casts.add(creature to victim) },
            chance = 99,
            speed = 100,
        )

        MonsterBehavior.commitCombatIntention(state, monster(), player(), listOf(block), interval = 100, isFleeing = false, nextRandom = { _, _ -> 100 })

        assertThat(casts).isEmpty()
        assertThat(state.attackTicks).isZero()
    }

    @Test
    fun `commitCombatIntention holds attack ticks while a spell is still cooling down`() {
        val state = MonsterBehaviorState().apply { attackTicks = 50 }
        val block = MonsterSpellBlock(spell = BaseSpell { _, _ -> }, speed = 2000)

        MonsterBehavior.commitCombatIntention(state, monster(), player(), listOf(block), interval = 100, isFleeing = false)

        assertThat(state.attackTicks).isEqualTo(50)
    }

    @Test
    fun `commitCombatIntention marks an extra melee swing when the target is out of melee reach`() {
        val state = MonsterBehaviorState().apply { attackTicks = 2000 }
        val melee = MonsterSpellBlock(spell = BaseSpell { _, _ -> }, speed = 100, range = 1, isMelee = true)

        MonsterBehavior.commitCombatIntention(state, monster(position = Position(10, 10, 7)), player(position = Position(15, 10, 7)), listOf(melee), interval = 100, isFleeing = false)

        assertThat(state.extraMeleeAttack).isTrue
    }

    @Test
    fun `commitCombatIntention clears the extra melee swing after a successful melee cast`() {
        val state = MonsterBehaviorState().apply {
            extraMeleeAttack = true
            attackTicks = 0
        }
        val casts = mutableListOf<Pair<Monster, Creature>>()
        val melee = MonsterSpellBlock(
            spell = BaseSpell { creature, victim -> casts.add(creature to victim) },
            speed = 2000,
            range = 1,
            isMelee = true,
        )

        MonsterBehavior.commitCombatIntention(state, monster(position = Position(10, 10, 7)), player(position = Position(10, 11, 7)), listOf(melee), interval = 100, isFleeing = false, now = { 5000 }, nextRandom = { min, _ -> min })

        assertThat(casts).hasSize(1)
        assertThat(state.extraMeleeAttack).isFalse
        assertThat(state.lastMeleeAttack).isEqualTo(5000)
    }

    @Test
    fun `commitCombatIntention skips melee spells while fleeing`() {
        val state = MonsterBehaviorState().apply { attackTicks = 2000 }
        val casts = mutableListOf<Pair<Monster, Creature>>()
        val melee = MonsterSpellBlock(
            spell = BaseSpell { creature, victim -> casts.add(creature to victim) },
            speed = 100,
            range = 1,
            isMelee = true,
        )

        MonsterBehavior.commitCombatIntention(state, monster(position = Position(10, 10, 7)), player(position = Position(10, 11, 7)), listOf(melee), interval = 100, isFleeing = true)

        assertThat(casts).isEmpty()
        assertThat(state.extraMeleeAttack).isFalse
    }

    @Test
    fun `commitCombatIntention drops spells the geometry hint did not approve`() {
        val state = MonsterBehaviorState().apply { attackTicks = 100 }
        val casts = mutableListOf<Pair<Monster, Creature>>()
        val block = MonsterSpellBlock(
            spell = BaseSpell { creature, victim -> casts.add(creature to victim) },
            speed = 100,
            range = 0,
        )

        MonsterBehavior.commitCombatIntention(state, monster(), player(), listOf(block), interval = 100, isFleeing = false, geometrySuggested = { false })

        assertThat(casts).isEmpty()
    }

    @Test
    fun `onThinkDefense casts due defense spells on the monster itself and consumes its tick window`() {
        val state = MonsterBehaviorState()
        val self = monster()
        val casts = mutableListOf<Pair<Monster, Creature>>()
        val block = MonsterSpellBlock(
            spell = BaseSpell { creature, victim -> casts.add(creature to victim) },
            chance = 100,
            speed = 100,
            minCombatValue = 3,
            maxCombatValue = 7,
        )

        MonsterBehavior.onThinkDefense(state, self, listOf(block), interval = 100, nextRandom = { min, _ -> min })

        assertThat(casts).containsExactly(self to self)
        assertThat(state.minCombatValue).isEqualTo(3)
        assertThat(state.maxCombatValue).isEqualTo(7)
        assertThat(state.defenseTicks).isZero()
    }

    @Test
    fun `onThinkDefense accumulates ticks until the spell speed elapses`() {
        val state = MonsterBehaviorState()
        val block = MonsterSpellBlock(spell = BaseSpell { _, _ -> }, chance = 100, speed = 200)

        MonsterBehavior.onThinkDefense(state, monster(), listOf(block), interval = 100)

        assertThat(state.defenseTicks).isEqualTo(100)
    }

    @Test
    fun `onThinkDefense resets the accumulated ticks when only null or already used spells remain`() {
        val state = MonsterBehaviorState()
        val casts = mutableListOf<Pair<Monster, Creature>>()
        val nullSpell = MonsterSpellBlock(spell = null, chance = 100, speed = 100)

        MonsterBehavior.onThinkDefense(state, monster(), listOf(nullSpell), interval = 100)
        assertThat(state.defenseTicks).isZero()

        state.defenseTicks = 1000
        val used = MonsterSpellBlock(spell = BaseSpell { creature, victim -> casts.add(creature to victim) }, chance = 100, speed = 400)
        MonsterBehavior.onThinkDefense(state, monster(), listOf(used), interval = 100)
        assertThat(casts).isEmpty()
        assertThat(state.defenseTicks).isZero()
    }

    @Test
    fun `onThinkDefense skips the cast when the chance roll misses`() {
        val state = MonsterBehaviorState()
        val casts = mutableListOf<Pair<Monster, Creature>>()
        val block = MonsterSpellBlock(
            spell = BaseSpell { creature, victim -> casts.add(creature to victim) },
            chance = 99,
            speed = 100,
        )

        MonsterBehavior.onThinkDefense(state, monster(), listOf(block), interval = 100, nextRandom = { _, _ -> 100 })

        assertThat(casts).isEmpty()
        assertThat(state.defenseTicks).isZero()
    }

    // --- death and corpse ---

    @Test
    fun `death drains and releases every summon then marks the monster dead`() {
        val state = MonsterBehaviorState()
        val master = monster()
        val summon = Monster(9, "Skeleton", 30, 30, position = Position(101, 100, 7))
        summon.setMaster(master, false)
        var summonsCleared = false
        var targetsCleared = false
        var friendsCleared = false
        var idleStatus = false
        val sounds = mutableListOf<Pair<Position, Int>>()

        MonsterBehavior.death(
            MonsterDeathRequest(
                monster = master,
                state = state,
                hasMonsterType = true,
                deathSound = 49,
                clearSummons = { summonsCleared = true },
                clearTargetList = { targetsCleared = true },
                clearFriendList = { friendsCleared = true },
                onIdleStatus = { idleStatus = true },
                sendDeathSound = { position, sound -> sounds.add(position to sound) },
            )
        )

        assertThat(summon.health).isZero()
        assertThat(summon.isSummon()).isFalse
        assertThat(state.dead).isTrue
        assertThat(summonsCleared).isTrue
        assertThat(targetsCleared).isTrue
        assertThat(friendsCleared).isTrue
        assertThat(idleStatus).isTrue
        assertThat(sounds).containsExactly(master.position to 49)
    }

    @Test
    fun `death removes a forge monster and stays silent without a monster type`() {
        val state = MonsterBehaviorState()
        val forges = mutableListOf<Pair<Long, ForgeClassification>>()
        val sounds = mutableListOf<Pair<Position, Int>>()
        val boss = monster(id = 77)

        MonsterBehavior.death(
            MonsterDeathRequest(
                monster = boss,
                state = state,
                forgeClassification = ForgeClassification.Fiendish,
                hasMonsterType = false,
                deathSound = 49,
                removeForgeMonster = { id, classification -> forges.add(id to classification) },
                sendDeathSound = { position, sound -> sounds.add(position to sound) },
            )
        )

        assertThat(forges).containsExactly(77L to ForgeClassification.Fiendish)
        assertThat(state.dead).isTrue
        assertThat(sounds).isEmpty()
    }

    @Test
    fun `getCorpse returns no corpse without a look corpse id`() {
        assertThat(MonsterBehavior.getCorpse(0, { Container(it, 10) }, null)).isNull()
    }

    @Test
    fun `getCorpse stamps the player or master player as corpse owner`() {
        val corpse = MonsterBehavior.getCorpse(3058, { Container(it, 10) }, ownerFacts(7, playerId = 7))
        assertThat(corpse).isNotNull
        assertThat(corpse!!.id).isEqualTo(3058)
        assertThat(corpse.getAttribute(ItemAttribute.CORPSEOWNER)).isEqualTo(7L)

        val masterCorpse = MonsterBehavior.getCorpse(3058, { Container(it, 10) }, ownerFacts(8, masterId = 3, masterPlayerId = 3))
        assertThat(masterCorpse).isNotNull
        assertThat(masterCorpse!!.getAttribute(ItemAttribute.CORPSEOWNER)).isEqualTo(3L)

        val unowned = MonsterBehavior.getCorpse(3058, { Container(it, 10) }, ownerFacts(8))
        assertThat(unowned).isNotNull
        assertThat(unowned!!.hasAttribute(ItemAttribute.CORPSEOWNER)).isFalse
    }

    @Test
    fun `dropCorpse adds the race splash and a looted corpse then starts both decaying`() {
        val tile = Tile(pos)
        val decayed = mutableListOf<Item>()
        val result = MonsterBehavior.dropCorpse(
            MonsterCorpseDropRequest(
                monster = monster(),
                race = RaceType.RACE_BLOOD,
                lookCorpse = 3058,
                tile = tile,
                createCorpse = { Container(it, 10) },
                lootRequest = MonsterLootDropRequest(lootItems = listOf(LootBlock(id = 2148, chance = 100000))),
                nextRoll = { 0 },
                startDecaying = { decayed.add(it) },
            )
        )

        val splash = result.splash
        assertThat(splash).isNotNull
        assertThat(splash!!.id).isEqualTo(MonsterBehavior.ITEM_FULLSPLASH)
        assertThat(splash.count).isEqualTo(MonsterBehavior.FLUID_BLOOD)
        assertThat(result.corpse).isNotNull
        assertThat(result.corpse!!.id).isEqualTo(3058)
        assertThat(result.corpse!!.items).hasSize(1)
        assertThat(result.loot).containsExactly(LootedItem(2148, 1))
        assertThat(tile.items).containsExactly(splash, result.corpse)
        assertThat(decayed).containsExactly(splash, result.corpse)
    }

    @Test
    fun `dropCorpse maps the creature race to the Canary splash fluid`() {
        fun splashFor(race: RaceType): Item? = MonsterBehavior.dropCorpse(
            MonsterCorpseDropRequest(
                monster = monster(),
                race = race,
                lookCorpse = 0,
                tile = Tile(pos),
                createCorpse = { Container(it, 10) },
            )
        ).splash

        assertThat(splashFor(RaceType.RACE_VENOM)!!.count).isEqualTo(MonsterBehavior.FLUID_SLIME)
        assertThat(splashFor(RaceType.RACE_BLOOD)!!.count).isEqualTo(MonsterBehavior.FLUID_BLOOD)
        assertThat(splashFor(RaceType.RACE_INK)!!.count).isEqualTo(MonsterBehavior.FLUID_INK)
        assertThat(splashFor(RaceType.RACE_CHOCOLATE)!!.count).isEqualTo(MonsterBehavior.FLUID_CHOCOLATE)
        assertThat(splashFor(RaceType.RACE_CANDY)!!.count).isEqualTo(MonsterBehavior.FLUID_CANDY)
        assertThat(splashFor(RaceType.RACE_UNDEAD)).isNull()
        assertThat(splashFor(RaceType.RACE_FIRE)).isNull()
        assertThat(splashFor(RaceType.RACE_ENERGY)).isNull()
        assertThat(splashFor(RaceType.RACE_NONE)).isNull()
    }

    @Test
    fun `dropCorpse skips the corpse and emits a puff when loot drop is disabled`() {
        val tile = Tile(pos)
        val effects = mutableListOf<Pair<Position, Int>>()
        val result = MonsterBehavior.dropCorpse(
            MonsterCorpseDropRequest(
                monster = monster(),
                lootDrop = false,
                lookCorpse = 3058,
                tile = tile,
                createCorpse = { Container(it, 10) },
                addMagicEffect = { position, effect -> effects.add(position to effect) },
            )
        )

        assertThat(result.corpse).isNull()
        assertThat(result.splash).isNull()
        assertThat(tile.items).isEmpty()
        assertThat(effects).containsExactly(pos to MonsterBehavior.CONST_ME_POFF)
    }

    @Test
    fun `dropCorpse without a tile still resolves the corpse but never loots or adds items`() {
        val result = MonsterBehavior.dropCorpse(
            MonsterCorpseDropRequest(
                monster = monster(),
                race = RaceType.RACE_UNDEAD,
                lookCorpse = 3058,
                tile = null,
                createCorpse = { Container(it, 10) },
                lootRequest = MonsterLootDropRequest(lootItems = listOf(LootBlock(id = 2148, chance = 100000))),
                nextRoll = { 0 },
            )
        )

        assertThat(result.splash).isNull()
        assertThat(result.corpse).isNotNull
        assertThat(result.loot).isEmpty()
    }

    // --- defense and reflect ---

    @Test
    fun `getDefense adds the runtime defense then applies the multiplier and truncates`() {
        val state = MonsterBehaviorState()
        MonsterBehavior.addDefense(state, 5)

        assertThat(MonsterBehavior.getDefense(10, state)).isEqualTo(15)
        assertThat(MonsterBehavior.getDefense(10, state, defenseMultiplier = 1.5)).isEqualTo(22)
        assertThat(state.defense).isEqualTo(5)
    }

    @Test
    fun `getDefenseMultiplier applies the forge stack bonus to the base rate`() {
        assertThat(MonsterBehavior.getDefenseMultiplier(1.0, 0)).isEqualTo(1.0)
        assertThat(MonsterBehavior.getDefenseMultiplier(1.0, 2)).isCloseTo(1.2, within(1e-9))
        assertThat(MonsterBehavior.getDefenseMultiplier(2.0, 5)).isCloseTo(3.0, within(1e-9))
    }

    @Test
    fun `isImmune consults the override and the typed immunity sets`() {
        assertThat(MonsterBehavior.isImmune(CombatType.FIREDAMAGE, false, setOf(CombatType.FIREDAMAGE))).isTrue
        assertThat(MonsterBehavior.isImmune(CombatType.EARTHDAMAGE, false, setOf(CombatType.FIREDAMAGE))).isFalse
        assertThat(MonsterBehavior.isImmune(CombatType.EARTHDAMAGE, true, emptySet())).isTrue
        assertThat(MonsterBehavior.isImmune(ConditionType.CONDITION_FIRE, false, setOf(ConditionType.CONDITION_FIRE))).isTrue
        assertThat(MonsterBehavior.isImmune(ConditionType.CONDITION_POISON, false, setOf(ConditionType.CONDITION_FIRE))).isFalse
        assertThat(MonsterBehavior.isImmune(ConditionType.CONDITION_POISON, true, emptySet())).isTrue
    }

    @Test
    fun `getReflectPercent sums the creature, monster type and runtime reflect entries`() {
        val state = MonsterBehaviorState()
        MonsterBehavior.addReflectElement(state, CombatType.FIREDAMAGE, 5)
        MonsterBehavior.addReflectElement(state, CombatType.FIREDAMAGE, 5)

        assertThat(MonsterBehavior.getReflectPercent(CombatType.FIREDAMAGE, creatureReflect = 2, monsterTypeReflect = mapOf(CombatType.FIREDAMAGE to 20), state = state)).isEqualTo(32.0)
        assertThat(MonsterBehavior.getReflectPercent(CombatType.ICEDAMAGE, state = state)).isEqualTo(0.0)
    }

    @Test
    fun `blockHit lets immunity win before any element adjustment`() {
        val (damage, blockType) = MonsterBehavior.blockHit(CombatType.FIREDAMAGE, 100, immune = true)

        assertThat(damage).isZero()
        assertThat(blockType).isEqualTo(BlockType.IMMUNITY)
    }

    @Test
    fun `blockHit scales damage by the monster element map and clamps zeroed damage to armor`() {
        val weak = MonsterBehavior.blockHit(CombatType.FIREDAMAGE, 100, elementMap = mapOf(CombatType.FIREDAMAGE to 20))
        assertThat(weak.first).isEqualTo(80)
        assertThat(weak.second).isEqualTo(BlockType.NONE)

        val resistant = MonsterBehavior.blockHit(CombatType.FIREDAMAGE, 100, elementMap = mapOf(CombatType.FIREDAMAGE to -20))
        assertThat(resistant.first).isEqualTo(120)

        val zeroed = MonsterBehavior.blockHit(CombatType.FIREDAMAGE, 1, elementMap = mapOf(CombatType.FIREDAMAGE to 200))
        assertThat(zeroed.first).isZero()
        assertThat(zeroed.second).isEqualTo(BlockType.ARMOR)
    }

    @Test
    fun `blockHit subtracts the projectile element sensitive reduction from the element modifier`() {
        val (damage, blockType) = MonsterBehavior.blockHit(
            combatType = CombatType.FIREDAMAGE,
            damage = 100,
            elementMap = mapOf(CombatType.FIREDAMAGE to 50),
            elementSensitiveReduction = 30,
        )

        assertThat(damage).isEqualTo(80)
        assertThat(blockType).isEqualTo(BlockType.NONE)
    }

    // --- random, dance and walk-back steps ---

    @Test
    fun `getRandomStep tries the shuffled north west east south order and returns the first walkable`() {
        val visited = mutableListOf<Direction>()
        val identity = { _: MutableList<Direction> -> }

        assertThat(MonsterBehavior.getRandomStep(pos, { _, direction -> visited.add(direction); false }, identity)).isNull()
        assertThat(visited).containsExactly(Direction.North, Direction.West, Direction.East, Direction.South)

        val eastOnly = MonsterBehavior.getRandomStep(pos, { _, direction -> direction == Direction.East }, identity)
        assertThat(eastOnly).isEqualTo(Direction.East)

        assertThat(MonsterBehavior.getRandomStep(pos, { _, _ -> false }, identity)).isNull()
    }

    @Test
    fun `getDanceStep refuses without a target or below the target distance`() {
        val walk = { _: Position, _: Direction -> true }
        val sight = { _: Position, _: Position -> true }

        assertThat(MonsterBehavior.getDanceStep(Position(10, 10, 7), null, 1, emptyList(), false, walk, sight)).isNull()
        assertThat(MonsterBehavior.getDanceStep(Position(10, 10, 7), Position(10, 11, 7), 2, emptyList(), false, walk, sight)).isNull()
    }

    @Test
    fun `getDanceStep keeps the target distance through lateral movement`() {
        val walk = { _: Position, _: Direction -> true }
        val sight = { _: Position, _: Position -> true }
        val identity = { _: MutableList<Direction> -> }
        val tried = mutableListOf<Direction>()

        val direction = MonsterBehavior.getDanceStep(
            creaturePos = Position(10, 10, 7),
            attackedCreaturePosition = Position(10, 12, 7),
            targetDistance = 1,
            attackSpells = emptyList(),
            isHostile = false,
            canWalkTo = { position, moveDirection -> tried.add(moveDirection); walk(position, moveDirection) },
            isSightClear = sight,
            shuffleDirections = identity,
            nextRandomIndex = { 1 },
        )

        assertThat(direction).isEqualTo(Direction.West)
        assertThat(tried).containsExactly(Direction.East, Direction.West)
    }

    @Test
    fun `getDanceStep drops candidates that would lose a currently usable attack`() {
        val walk = { _: Position, _: Direction -> true }
        val identity = { _: MutableList<Direction> -> }
        val origin = Position(10, 10, 7)
        val target = Position(13, 13, 7)
        val ranged = listOf(MonsterSpellBlock(spell = BaseSpell { _, _ -> }, range = 5))
        val sight = { from: Position, _: Position -> from == origin }

        val keepAttack = MonsterBehavior.getDanceStep(origin, target, 1, ranged, isHostile = true, walk, sight, shuffleDirections = identity)
        assertThat(keepAttack).isNull()

        val freeMove = MonsterBehavior.getDanceStep(origin, target, 1, ranged, isHostile = true, walk, sight, keepAttack = false, shuffleDirections = identity)
        assertThat(freeMove).isEqualTo(Direction.South)
    }

    @Test
    fun `doRandomStep only steps after a full second since the last move`() {
        val state = MonsterBehaviorState()
        val identity = { _: MutableList<Direction> -> }

        assertThat(MonsterBehavior.doRandomStep(state, pos, timeSinceLastMove = 999, { _, _ -> true }, identity)).isNull()
        assertThat(state.randomStepping).isFalse

        assertThat(MonsterBehavior.doRandomStep(state, pos, timeSinceLastMove = 1000, { _, _ -> true }, identity)).isEqualTo(Direction.North)
        assertThat(state.randomStepping).isTrue
    }

    @Test
    fun `doWalkBack aborts immediately when players are on screen`() {
        val state = MonsterBehaviorState().apply { isWalkingBack = true }
        var nextStepCalled = false

        val result = MonsterBehavior.doWalkBack(state, Position(10, 10, 7), Position(10, 20, 7), totalPlayersOnScreen = 1, getNextStep = { nextStepCalled = true; Direction.North })

        assertThat(result.result).isFalse
        assertThat(result.direction).isNull()
        assertThat(state.isWalkingBack).isFalse
        assertThat(nextStepCalled).isFalse
    }

    @Test
    fun `doWalkBack follows the creature next step and flags pathfinding`() {
        val state = MonsterBehaviorState().apply { isWalkingBack = true }

        val result = MonsterBehavior.doWalkBack(state, Position(10, 10, 7), Position(10, 20, 7), getNextStep = { Direction.East })

        assertThat(result.result).isTrue
        assertThat(result.direction).isEqualTo(Direction.East)
        assertThat(result.flags).isEqualTo(CylinderFlags.FLAG_PATHFINDING)
        assertThat(state.isWalkingBack).isTrue
    }

    @Test
    fun `doWalkBack pathfinds toward the master with the distance window and ignores field damage on fallback`() {
        val state = MonsterBehaviorState().apply {
            isWalkingBack = true
            ignoreFieldDamage = true
        }
        val pathArgs = mutableListOf<Any>()
        val walked = mutableListOf<List<Direction>>()

        val result = MonsterBehavior.doWalkBack(
            state = state,
            position = Position(10, 10, 7),
            masterPos = Position(10, 20, 7),
            getNextStep = { null },
            getPathTo = { target, minTargetDist, maxTargetDist, fullPathSearch, clearSight, maxSearchDist ->
                pathArgs.addAll(listOf(target, minTargetDist, maxTargetDist, fullPathSearch, clearSight, maxSearchDist))
                listOf(Direction.South)
            },
            startAutoWalk = { walked.add(it) },
        )

        assertThat(pathArgs).containsExactly(Position(10, 20, 7), 0, 5, true, true, 10)
        assertThat(walked).containsExactly(listOf(Direction.South))
        assertThat(result.result).isFalse
        assertThat(state.ignoreFieldDamage).isFalse
        assertThat(state.isWalkingBack).isTrue
    }

    @Test
    fun `doWalkBack stops walking back when at the master position or without a path`() {
        val atMaster = MonsterBehaviorState().apply { isWalkingBack = true }
        var pathCalled = false
        MonsterBehavior.doWalkBack(atMaster, Position(10, 10, 7), Position(10, 10, 7), getNextStep = { null }, getPathTo = { _, _, _, _, _, _ -> pathCalled = true; emptyList() })
        assertThat(atMaster.isWalkingBack).isFalse
        assertThat(pathCalled).isFalse

        val noPath = MonsterBehaviorState().apply { isWalkingBack = true }
        MonsterBehavior.doWalkBack(noPath, Position(10, 10, 7), Position(10, 20, 7), getNextStep = { null }, getPathTo = { _, _, _, _, _, _ -> emptyList() })
        assertThat(noPath.isWalkingBack).isFalse
    }

    // --- despawn bounds and idle status ---

    @Test
    fun `isInSpawnLocation treats spawnless monsters and the master position as home`() {
        assertThat(MonsterBehavior.isInSpawnLocation(Position(500, 500, 7), Position(10, 10, 7), hasSpawnOwner = false)).isTrue
        assertThat(MonsterBehavior.isInSpawnLocation(Position(10, 10, 7), Position(10, 10, 7), hasSpawnOwner = true)).isTrue
        assertThat(MonsterBehavior.isInSpawnLocation(Position(10, 10, 7), Position(0, 0, 0), hasSpawnOwner = true)).isTrue
        assertThat(MonsterBehavior.isInSpawnLocation(Position(11, 10, 7), Position(10, 10, 7), hasSpawnOwner = true)).isFalse
    }

    @Test
    fun `isInSpawnRange applies the despawn radius and then the z range`() {
        val master = Position(100, 100, 7)

        assertThat(MonsterBehavior.isInSpawnRange(Position(500, 500, 7), master, hasSpawnOwner = false, 50, 2)).isTrue
        assertThat(MonsterBehavior.isInSpawnRange(Position(500, 500, 7), master, hasSpawnOwner = true, 0, 2)).isTrue
        assertThat(MonsterBehavior.isInSpawnRange(Position(100, 100, 7), master, true, 50, 2)).isTrue
        assertThat(MonsterBehavior.isInSpawnRange(Position(150, 120, 7), master, true, 50, 2)).isTrue
        assertThat(MonsterBehavior.isInSpawnRange(Position(151, 100, 7), master, true, 50, 2)).isFalse
        assertThat(MonsterBehavior.isInSpawnRange(Position(100, 100, 9), master, true, 50, 2)).isTrue
        assertThat(MonsterBehavior.isInSpawnRange(Position(100, 100, 10), master, true, 50, 2)).isFalse
        assertThat(MonsterBehavior.isInSpawnRange(Position(100, 100, 30), master, true, 50, 0)).isTrue
    }

    @Test
    fun `updateIdleStatus idles a targetless monster standing on its spawn location`() {
        val state = MonsterBehaviorState()
        val checks = mutableListOf<String>()

        MonsterBehavior.updateIdleStatus(
            MonsterIdleRequest(
                state = state,
                position = pos,
                masterPos = pos,
                hasSpawnOwner = true,
                onIdleStatus = { checks.add("idleStatus") },
                clearTargetList = { checks.add("targets") },
                clearFriendList = { checks.add("friends") },
                removeCreatureCheck = { checks.add("removeCheck") },
            )
        )

        assertThat(state.isIdle).isTrue
        assertThat(state.isWalkingBack).isFalse
        assertThat(checks).containsExactly("idleStatus", "targets", "friends", "removeCheck")
    }

    @Test
    fun `updateIdleStatus sends a targetless monster away from spawn walking back`() {
        val state = MonsterBehaviorState()
        var checks = 0

        MonsterBehavior.updateIdleStatus(
            MonsterIdleRequest(
                state = state,
                position = Position(10, 10, 7),
                masterPos = Position(20, 20, 7),
                hasSpawnOwner = true,
                addCreatureCheck = { checks++ },
            )
        )

        assertThat(state.isWalkingBack).isTrue
        assertThat(state.isIdle).isFalse
        assertThat(checks).isEqualTo(1)
    }

    @Test
    fun `updateIdleStatus keeps a condition carrying or engaged monster active`() {
        val conditioned = MonsterBehaviorState()
        MonsterBehavior.updateIdleStatus(MonsterIdleRequest(state = conditioned, position = pos, masterPos = pos, hasConditions = true))
        assertThat(conditioned.isIdle).isFalse

        val engaged = MonsterBehaviorState()
        MonsterBehavior.updateIdleStatus(MonsterIdleRequest(state = engaged, position = pos, masterPos = pos, targetListEmpty = false))
        assertThat(engaged.isIdle).isFalse
    }

    @Test
    fun `updateIdleStatus idles a faction master whose players are all off screen`() {
        val state = MonsterBehaviorState().apply { isIdle = false }
        var removeChecks = 0

        MonsterBehavior.updateIdleStatus(
            MonsterIdleRequest(
                state = state,
                position = pos,
                masterPos = pos,
                targetListEmpty = false,
                hasMaster = true,
                totalPlayersOnScreen = 0,
                factionIsDefault = false,
                removeCreatureCheck = { removeChecks++ },
            )
        )

        assertThat(state.isIdle).isTrue
        assertThat(removeChecks).isEqualTo(1)
    }

    @Test
    fun `updateIdleStatus follows the master monster players on screen for a summon`() {
        val hidden = MonsterBehaviorState().apply { isIdle = false }
        MonsterBehavior.updateIdleStatus(
            MonsterIdleRequest(
                state = hidden,
                position = pos,
                masterPos = pos,
                targetListEmpty = false,
                isSummon = true,
                hasMaster = true,
                masterMonsterPlayersOnScreen = 0,
                factionIsDefault = false,
            )
        )
        assertThat(hidden.isIdle).isTrue

        val watched = MonsterBehaviorState().apply { isIdle = false }
        MonsterBehavior.updateIdleStatus(
            MonsterIdleRequest(
                state = watched,
                position = pos,
                masterPos = pos,
                targetListEmpty = false,
                isSummon = true,
                hasMaster = true,
                masterMonsterPlayersOnScreen = 1,
                factionIsDefault = false,
            )
        )
        assertThat(watched.isIdle).isFalse
    }
}
