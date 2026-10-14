package org.opentibia.canary.creatures.monsters

import org.opentibia.canary.combat.BlockType
import org.opentibia.canary.combat.Combat
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.spawns.SpawnsMonster
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.movement.getOffsetX
import org.opentibia.canary.game.movement.getOffsetY
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.spells.SoundEffect
import java.util.Random
import kotlin.math.abs
import kotlin.math.max


/**
 * `BaseSpell::castSpell(caster, target)` (src/creatures/combat/spells.hpp:91).
 *
 * The Lua/registry-backed spell implementations stay in their owning system
 * (AGENTS.md 1.4); the spell selection rules only need the cast call.
 */
fun interface BaseSpell {
    fun castSpell(caster: Monster, target: Creature)
}

/** `struct spellBlock_t` (src/creatures/monsters/monsters.hpp:35-47). */
class MonsterSpellBlock(
    val spell: BaseSpell? = null,
    val chance: Int = 100,
    val speed: Int = 2000,
    val range: Int = 0,
    val minCombatValue: Int = 0,
    val maxCombatValue: Int = 0,
    val combatSpell: Boolean = false,
    val isMelee: Boolean = false,
)

/** `bool &inRange` / `bool &resetTicks` out-parameters of `Monster::canUseSpell`. */
class CanUseSpellResult(
    val canUse: Boolean,
    val inRange: Boolean,
    val resetTicks: Boolean,
)

/**
 * Port of the mutable `Monster` fields the behavior rules mutate
 * (src/creatures/monsters/monster.hpp:429-469): attack/defense tick windows,
 * melee swing state, the per-monster defense/reflect overrides, the dead/immune
 * latches and the movement flags. The Kotlin [Monster] entity is intentionally
 * thin, so callers own one instance per monster.
 */
class MonsterBehaviorState {
    /** `Monster::attackTicks` (monster.hpp:429). */
    var attackTicks: Int = 0

    /** `Monster::defenseTicks` (monster.hpp:431). */
    var defenseTicks: Int = 0

    /** `Monster::lastMeleeAttack` (monster.hpp:420). */
    var lastMeleeAttack: Long = 0

    /** `Monster::extraMeleeAttack` (monster.hpp:456). */
    var extraMeleeAttack: Boolean = false

    /** `Monster::minCombatValue` / `maxCombatValue` (monster.hpp:435-436). */
    var minCombatValue: Int = 0
    var maxCombatValue: Int = 0

    /** `Monster::m_defense` (monster.hpp:444). */
    var defense: Int = 0

    /** `Monster::m_reflectElementMap` (monster.hpp:446). */
    val reflectElements = linkedMapOf<CombatType, Int>()

    /** `Monster::m_isDead` (monster.hpp:468). */
    var dead: Boolean = false

    /** `Monster::m_isImmune` (monster.hpp:469). */
    var immune: Boolean = false

    /** `Monster::isWalkingBack` (monster.hpp:454). */
    var isWalkingBack: Boolean = false

    /** `Monster::isIdle` (monster.hpp:456, default true). */
    var isIdle: Boolean = true

    /** `Monster::randomStepping` (monster.hpp:457). */
    var randomStepping: Boolean = false

    /** `Monster::ignoreFieldDamage` (monster.hpp:458). */
    var ignoreFieldDamage: Boolean = false

    /** `Monster::targetDistance` (monster.hpp:441, seeded from the monster type). */
    var targetDistance: Int = 1

    /** `Monster::targetChangeTicks` (monster.hpp:430). */
    var targetChangeTicks: Int = 0

    /** `Monster::m_targetChangeCooldown` (monster.hpp:437). */
    var targetChangeCooldown: Int = 0

    /** `Monster::challengeFocusDuration` (monster.hpp:439). */
    var challengeFocusDuration: Int = 0

    /** `Monster::fatalHoldDuration` (monster.hpp:438). */
    var fatalHoldDuration: Int = 0

    /** `Monster::challengeMeleeDuration` (monster.hpp:442). */
    var challengeMeleeDuration: Int = 0

    /** `Monster::stepDuration` (monster.hpp:440), the "target nearby" slowdown. */
    var stepDuration: Int = 0

    /** `Monster::yellTicks` (monster.hpp:433). */
    var yellTicks: Int = 0

    /** `Monster::soundTicks` (monster.hpp:434). */
    var soundTicks: Int = 0
}

/**
 * `Monster::death` inputs (src/creatures/monsters/monster.cpp:3227-3303).
 *
 * [lastHitCreature]/[attackedCreature] resolve the target player exactly like
 * the C++ chain (direct player, then the creature's master player).
 * [removeForgeMonster] is `g_game().removeForgeMonster`, the list clearances
 * replace `clearTargetList`/`clearFriendList`/`m_summons.clear()` and
 * [sendDeathSound] wraps `g_game().sendSingleSoundEffect`.
 *
 * PARITY GAP: the `targetPlayer` weapon-proficiency and bestiary charm tail
 * (monster.cpp:3280-3302) targets unported player-progression subsystems.
 */
class MonsterDeathRequest(
    val monster: Monster,
    val state: MonsterBehaviorState,
    val lastHitCreature: Creature? = null,
    val attackedCreature: Creature? = null,
    val forgeClassification: ForgeClassification = ForgeClassification.Normal,
    val hasMonsterType: Boolean = true,
    val deathSound: Int = 0,
    val removeForgeMonster: (Long, ForgeClassification) -> Unit = { _, _ -> },
    val clearSummons: () -> Unit = {},
    val clearTargetList: () -> Unit = {},
    val clearFriendList: () -> Unit = {},
    val onIdleStatus: () -> Unit = {},
    val sendDeathSound: (Position, Int) -> Unit = { _, _ -> },
)

/**
 * `Creature::dropCorpse` inputs (src/creatures/creature.cpp:732-828).
 *
 * The Lua death events (`executeOnDeath`), the protocol loot message and the
 * auto-loot path (creature.cpp:786-816, which read the last-hit creature and
 * `isRewardBoss`) are not ported; [addItemToTile] is
 * `g_game().internalAddItem` and [startDecaying] is `Item::startDecaying`.
 */
class MonsterCorpseDropRequest(
    val monster: Monster,
    val createCorpse: (Int) -> Container?,
    val lootDrop: Boolean = true,
    val race: RaceType = RaceType.RACE_BLOOD,
    val lookCorpse: Int = 0,
    val mostDamageCreature: TargetCreatureFacts? = null,
    val lootRequest: MonsterLootDropRequest = MonsterLootDropRequest(),
    val tile: Tile? = null,
    val addItemToTile: (Tile, Item) -> Unit = { tile, item -> tile.addItem(item) },
    val startDecaying: (Item) -> Unit = {},
    val addMagicEffect: (Position, Int) -> Unit = { _, _ -> },
    val nextRoll: () -> Int = { 0 },
    val nextSliverCount: (Int, Int) -> Int = { min, _ -> min },
)

/** Result of [MonsterBehavior.dropCorpse]: created items plus granted loot. */
class MonsterCorpseDropResult {
    var splash: Item? = null
    var corpse: Container? = null
    val loot = mutableListOf<LootedItem>()
}

/** `uint32_t &flags, Direction &nextDirection, bool &result` of `Monster::doWalkBack`. */
data class WalkBackResult(
    val flags: Int,
    val direction: Direction?,
    val result: Boolean,
)

/**
 * `Monster::updateIdleStatus` inputs (src/creatures/monsters/monster.cpp:1521-1556)
 * plus the `Monster::setIdle` side effects (monster.cpp:1499-1519). The async
 * `MONSTER_PERF_TEST_FORCE_ACTIVE`/barrier branch (monster.cpp:1522-1538) and
 * `g_game().addCreatureCheck`/`removeCreatureCheck` are injected seams.
 */
class MonsterIdleRequest(
    val state: MonsterBehaviorState,
    val position: Position,
    val masterPos: Position,
    val hasSpawnOwner: Boolean = false,
    val hasConditions: Boolean = false,
    val isSummon: Boolean = false,
    val hasMaster: Boolean = false,
    val targetListEmpty: Boolean = true,
    val totalPlayersOnScreen: Int = 0,
    val masterMonsterPlayersOnScreen: Int? = null,
    val factionIsDefault: Boolean = true,
    val isRemoved: Boolean = false,
    val health: Int = 1,
    val onIdleStatus: () -> Unit = {},
    val clearTargetList: () -> Unit = {},
    val clearFriendList: () -> Unit = {},
    val addCreatureCheck: () -> Unit = {},
    val removeCreatureCheck: () -> Unit = {},
)

/**
 * `Monster::doAttacking` inputs (src/creatures/monsters/monster.cpp:1754-1767).
 * The C++ `getAttackedCreature()` is passed as [attackedCreature] and its
 * `isLifeless`/player-login-protection checks are projected to flags.
 * [requestCombatIntention] is `Monster::requestCombatIntention`, the async
 * pipeline whose synchronous commit is [MonsterBehavior.commitCombatIntention].
 */
class MonsterAttackingRequest(
    val state: MonsterBehaviorState,
    val monsterId: Long,
    val isSummon: Boolean,
    val attackedCreature: Creature?,
    val interval: Int,
    val attackedIsLifeless: Boolean = false,
    val attackedIsLoginProtected: Boolean = false,
    val requestCombatIntention: (Int, Creature) -> Unit = { _, _ -> },
)

/**
 * Port of the remaining `Monster` runtime rules:
 * - spell selection/casting: `Monster::canUseAttack` (monster.cpp:2095-2107),
 *   `Monster::canUseSpell` (monster.cpp:2109-2139),
 *   `Monster::commitCombatIntention` core (monster.cpp:1935-2000),
 *   `Monster::onThinkDefense` (monster.cpp:2202-2272)
 * - death/corpse: `Monster::death` (monster.cpp:3227-3303), `Monster::getCorpse`
 *   (monster.cpp:3305-3320), the `Creature::dropCorpse` corpse/splash branch
 *   (src/creatures/creature.cpp:732-828)
 * - defense/reflect: `Monster::getDefense`/`addDefense` (monster.cpp:242-258),
 *   `Monster::blockHit` (monster.cpp:1402-1428), `Monster::isImmune`
 *   (monster.cpp:3535-3549), `Monster::getReflectPercent`/`addReflectElement`
 *   (monster.cpp:209-240), `Monster::getDefenseMultiplier` (monster.cpp:3559-3565)
 * - steps: `Monster::getRandomStep` (monster.cpp:2552-2568),
 *   `Monster::getDanceStep` (monster.cpp:2570-2630),
 *   `Monster::doRandomStep` (monster.cpp:2494-2499),
 *   `Monster::doWalkBack` (monster.cpp:2501-2528)
 * - despawn: `Monster::isInSpawnLocation` (monster.cpp:1562-1567),
 *   `Monster::isInSpawnRange` (monster.cpp:3322-3344)
 * - idle/walk-back: `Monster::updateIdleStatus`/`setIdle` (monster.cpp:1499-1556)
 *
 * The async combat-intention pipeline (`requestCombatIntention`,
 * `prepare`/`completeCombatIntention`, `commitCombatIntention`) lives in
 * [MonsterAsyncPipelines]; [commitCombatIntention] here is the synchronous
 * `isValid`/`geometrySuggested` projection of the same spell loop.
 *
 * PARITY GAPS: the defense summon roll (monster.cpp:2224-2267) depends on the
 * Lua-loaded monster type and `Game::placeCreature`; `Monster::getDistanceStep`
 * and `Monster::updateLookDirection` are not transcribed.
 */
object MonsterBehavior {
    /** `ITEM_FULLSPLASH` (src/utils/utils_definitions.hpp:629). */
    const val ITEM_FULLSPLASH = 2886

    /** `FLUID_BLOOD` (src/utils/utils_definitions.hpp:418). */
    const val FLUID_BLOOD = 5

    /** `FLUID_SLIME` (src/utils/utils_definitions.hpp:419). */
    const val FLUID_SLIME = 6

    /** `FLUID_INK` (src/utils/utils_definitions.hpp:431). */
    const val FLUID_INK = 18

    /** `FLUID_CANDY` (src/utils/utils_definitions.hpp:432). */
    const val FLUID_CANDY = 19

    /** `FLUID_CHOCOLATE` (src/utils/utils_definitions.hpp:433). */
    const val FLUID_CHOCOLATE = 20

    /** `CONST_ME_POFF` (src/utils/utils_definitions.hpp:63). */
    const val CONST_ME_POFF = 3

    /** `CONST_ME_TELEPORT` (src/utils/utils_definitions.hpp:71). */
    const val CONST_ME_TELEPORT = 11

    /** `CONST_ME_MAGIC_BLUE` (src/utils/utils_definitions.hpp:73). */
    const val CONST_ME_MAGIC_BLUE = 13

    /** `CONST_ME_BLOCKHIT` (src/utils/utils_definitions.hpp:64). */
    const val CONST_ME_BLOCKHIT = 4

    /**
     * `Monster::canUseAttack` (monster.cpp:2095-2107): a hostile monster needs a
     * ranged attack spell covering the diagonal distance plus clear sight;
     * passive monsters always report true.
     */
    fun canUseAttack(
        attackSpells: List<MonsterSpellBlock>,
        isHostile: Boolean,
        pos: Position,
        targetPos: Position,
        isSightClear: (Position, Position) -> Boolean,
    ): Boolean {
        if (!isHostile) {
            return true
        }

        val distance = max(abs(pos.x - targetPos.x), abs(pos.y - targetPos.y))
        for (spellBlock in attackSpells) {
            if (spellBlock.range != 0 && distance <= spellBlock.range) {
                return isSightClear(pos, targetPos)
            }
        }
        return false
    }

    /**
     * `Monster::canUseSpell` (monster.cpp:2109-2139). [resetTicks] is the
     * caller's `bool resetTicks` (true in `onThinkDefense`, `interval != 0` in
     * the attack commit) and is reported back through
     * [CanUseSpellResult.resetTicks]. The extra melee swing path refreshes
     * [MonsterBehaviorState.lastMeleeAttack] with [now] (`OTSYS_TIME`).
     */
    fun canUseSpell(
        state: MonsterBehaviorState,
        spellBlock: MonsterSpellBlock,
        pos: Position,
        targetPos: Position,
        interval: Int,
        isFleeing: Boolean,
        resetTicks: Boolean,
        now: Long,
    ): CanUseSpellResult {
        var reset = resetTicks

        if (spellBlock.isMelee && isFleeing) {
            return CanUseSpellResult(canUse = false, inRange = true, resetTicks = reset)
        }

        if (state.extraMeleeAttack) {
            state.lastMeleeAttack = now
        } else if (spellBlock.isMelee && (now - state.lastMeleeAttack) < 1500) {
            return CanUseSpellResult(canUse = false, inRange = true, resetTicks = reset)
        }

        if (!spellBlock.isMelee || !state.extraMeleeAttack) {
            if (spellBlock.speed > state.attackTicks) {
                reset = false
                return CanUseSpellResult(canUse = false, inRange = true, resetTicks = reset)
            }

            if (state.attackTicks % spellBlock.speed >= interval) {
                return CanUseSpellResult(canUse = false, inRange = true, resetTicks = reset)
            }
        }

        if (spellBlock.range != 0 && max(abs(pos.x - targetPos.x), abs(pos.y - targetPos.y)) > spellBlock.range) {
            return CanUseSpellResult(canUse = false, inRange = false, resetTicks = reset)
        }
        return CanUseSpellResult(canUse = true, inRange = true, resetTicks = reset)
    }

    /**
     * Core of `Monster::commitCombatIntention` (monster.cpp:1935-2000) for the
     * synchronous path: iterates the attack spell list in order, casts each due
     * spell whose chance roll passes and resets `attackTicks` when no spell held
     * the tick window. [isValid] replaces the generation/epoch/target checks and
     * [geometrySuggested] the async eligibility index list.
     */
    fun commitCombatIntention(
        state: MonsterBehaviorState,
        monster: Monster,
        target: Creature,
        attackSpells: List<MonsterSpellBlock>,
        interval: Int,
        isFleeing: Boolean,
        updateLookDirection: () -> Unit = {},
        isValid: () -> Boolean = { true },
        geometrySuggested: (Int) -> Boolean = { true },
        nextRandom: (Int, Int) -> Int = { min, _ -> min },
        now: () -> Long = System::currentTimeMillis,
    ) {
        var updateLook = true
        var resetTicks = interval != 0

        for (spellIndex in attackSpells.indices) {
            if (!isValid()) {
                break
            }

            val spellBlock = attackSpells[spellIndex]
            val spell = spellBlock.spell
            if (spell == null || (spellBlock.isMelee && isFleeing)) {
                continue
            }

            val useResult = canUseSpell(
                state = state,
                spellBlock = spellBlock,
                pos = monster.position,
                targetPos = target.position,
                interval = interval,
                isFleeing = isFleeing,
                resetTicks = resetTicks,
                now = now(),
            )
            resetTicks = useResult.resetTicks

            if (useResult.canUse && geometrySuggested(spellIndex)) {
                if (spellBlock.chance >= nextRandom(1, 100)) {
                    if (updateLook) {
                        updateLookDirection()
                        updateLook = false
                    }

                    state.minCombatValue = spellBlock.minCombatValue
                    state.maxCombatValue = spellBlock.maxCombatValue
                    spell.castSpell(monster, target)

                    if (spellBlock.isMelee) {
                        state.extraMeleeAttack = false
                    }
                }
            }

            if (!useResult.inRange && spellBlock.isMelee) {
                // melee swing out of reach
                state.extraMeleeAttack = true
            }
        }

        if (updateLook) {
            updateLookDirection()
        }

        if (resetTicks) {
            state.attackTicks = 0
        }
    }

    /**
     * `Monster::onThinkDefense` (monster.cpp:2202-2272), defense spells and the
     * summon roll in source order: spells whose `speed` window elapsed and whose
     * chance roll passes cast on the monster itself; then, for a non-summon with
     * `hasFollowPath` and room under [maxSummons], each [summonBlocks] entry
     * whose speed window elapsed, once-per-round gate, per-name [SummonBlock.count]
     * gate and chance roll pass creates a monster and places it. A successfully
     * placed summon is bound to [monster] with `reloadCreature = true`, announced
     * with the two magic effects and the summon sound, and counts toward the cap
     * immediately: [setMaster] is `Creature::setMaster`, which emplaces the
     * summon into the master's summon list (`creature.cpp:1344-1347`) that
     * [summons] mirrors, so it must append there.
     *
     * The tick counter folds to zero when no spell or summon block held it.
     * [soulPit]/[forgeStack] feed `Monster::setSoulPitStack`; [nextRandom] is
     * `uniform_random(1, 100)`.
     */
    fun onThinkDefense(
        state: MonsterBehaviorState,
        monster: Monster,
        defenseSpells: List<MonsterSpellBlock>,
        interval: Int,
        nextRandom: (Int, Int) -> Int = { min, _ -> min },
        isSummon: Boolean = false,
        summonBlocks: List<SummonBlock> = emptyList(),
        maxSummons: Int = 0,
        summons: MutableList<Monster> = mutableListOf(),
        hasFollowPath: Boolean = false,
        soulPit: Boolean = false,
        forgeStack: Int = 0,
        createMonster: (String) -> Monster? = { null },
        placeCreature: (Monster, Position, Boolean, Boolean) -> Boolean = { _, _, _, _ -> false },
        setMaster: (Monster, Monster, Boolean) -> Unit = { _, _, _ -> },
        setSoulPitStack: (Monster, Int, Boolean) -> Unit = { _, _, _ -> },
        addMagicEffect: (Position, Int) -> Unit = { _, _ -> },
        sendSoundEffect: (Position, SoundEffect, Monster) -> Unit = { _, _, _ -> },
    ) {
        var resetTicks = true
        state.defenseTicks += interval

        for (spellBlock in defenseSpells) {
            if (spellBlock.speed > state.defenseTicks) {
                resetTicks = false
                continue
            }

            if (spellBlock.spell == null || state.defenseTicks % spellBlock.speed >= interval) {
                // already used this spell for this round
                continue
            }

            if (spellBlock.chance >= nextRandom(1, 100)) {
                state.minCombatValue = spellBlock.minCombatValue
                state.maxCombatValue = spellBlock.maxCombatValue
                spellBlock.spell.castSpell(monster, monster)
            }
        }

        if (!isSummon && summons.size < maxSummons && hasFollowPath) {
            for (summonBlock in summonBlocks) {
                if (summonBlock.speed > state.defenseTicks) {
                    resetTicks = false
                    continue
                }

                if (summons.size >= maxSummons) {
                    continue
                }

                if (state.defenseTicks % summonBlock.speed >= interval) {
                    // already used this spell for this round
                    continue
                }

                var summonsCount = 0
                for (summon in summons) {
                    if (summon.name == summonBlock.name) {
                        summonsCount++
                    }
                }
                if (summonsCount >= summonBlock.count) {
                    continue
                }

                if (summonBlock.chance < nextRandom(1, 100)) {
                    continue
                }

                val summon = createMonster(summonBlock.name) ?: continue
                if (!placeCreature(summon, monster.position, false, summonBlock.force)) {
                    continue
                }

                if (soulPit) {
                    setSoulPitStack(summon, forgeStack, true)
                }
                setMaster(summon, monster, true)
                addMagicEffect(monster.position, CONST_ME_MAGIC_BLUE)
                addMagicEffect(summon.position, CONST_ME_TELEPORT)
                sendSoundEffect(summon.position, SoundEffect.MONSTER_SPELL_SUMMON, monster)
            }
        }

        if (resetTicks) {
            state.defenseTicks = 0
        }
    }

    /**
     * `Monster::doAttacking` (monster.cpp:1754-1767): a live, non-login-protected
     * attacked creature that is not the summon itself accumulates the interval
     * into `attackTicks` and starts the combat-intention pipeline.
     */
    fun doAttacking(request: MonsterAttackingRequest) {
        val attacked = request.attackedCreature ?: return
        if (request.attackedIsLifeless) {
            return
        }
        if (request.isSummon && attacked.id == request.monsterId) {
            return
        }
        if (request.attackedIsLoginProtected) {
            return
        }

        request.state.attackTicks += request.interval
        request.requestCombatIntention(request.interval, attacked)
    }

    /** `Monster::hasExtraSwing` (monster.cpp:2091-2093). */
    fun hasExtraSwing(state: MonsterBehaviorState): Boolean = state.extraMeleeAttack

    /**
     * `Monster::getCombatValues` (monster.cpp:3346-3355): `null` when both
     * bounds are zero, else the stored `(min, max)` pair.
     */
    fun getCombatValues(state: MonsterBehaviorState): Pair<Int, Int>? =
        if (state.minCombatValue == 0 && state.maxCombatValue == 0) {
            null
        } else {
            state.minCombatValue to state.maxCombatValue
        }

    /**
     * `Monster::updateLookDirection` (monster.cpp:3356-3413): faces the attacked
     * creature on the dominant axis, breaking exact diagonals from the current
     * facing (North turns East, West turns South on a full overlap). Returns
     * `null` without a target; the caller applies the turn.
     */
    fun updateLookDirection(
        currentDirection: Direction,
        pos: Position,
        attackedCreaturePos: Position?,
    ): Direction? {
        if (attackedCreaturePos == null) {
            return null
        }

        var newDir = currentDirection
        val offsetx = getOffsetX(attackedCreaturePos, pos)
        val offsety = getOffsetY(attackedCreaturePos, pos)
        val dx = abs(offsetx)
        val dy = abs(offsety)

        if (dx > dy) {
            // look EAST/WEST
            newDir = if (offsetx < 0) Direction.West else Direction.East
        } else if (dx < dy) {
            // look NORTH/SOUTH
            newDir = if (offsety < 0) Direction.North else Direction.South
        } else {
            if (offsetx < 0 && offsety < 0) {
                if (currentDirection == Direction.South) {
                    newDir = Direction.West
                } else if (currentDirection == Direction.East) {
                    newDir = Direction.North
                }
            } else if (offsetx < 0 && offsety > 0) {
                if (currentDirection == Direction.North) {
                    newDir = Direction.West
                } else if (currentDirection == Direction.East) {
                    newDir = Direction.South
                }
            } else if (offsetx > 0 && offsety < 0) {
                if (currentDirection == Direction.South) {
                    newDir = Direction.East
                } else if (currentDirection == Direction.West) {
                    newDir = Direction.North
                }
            } else {
                if (currentDirection == Direction.North) {
                    newDir = Direction.East
                } else if (currentDirection == Direction.West) {
                    newDir = Direction.South
                }
            }
        }

        return newDir
    }

    /**
     * `Monster::death` (monster.cpp:3227-3303) before the player-progression
     * tail: the forge registration is removed, every summon is drained and
     * released, the target/friend lists and idle damage state are cleared and
     * the dead latch plus death sound are applied.
     */
    fun death(request: MonsterDeathRequest) {
        if (request.forgeClassification != ForgeClassification.Normal) {
            request.removeForgeMonster(request.monster.id, request.forgeClassification)
        }

        var targetPlayer = request.lastHitCreature.playerOrMasterPlayer()
        if (targetPlayer == null) {
            targetPlayer = request.attackedCreature.playerOrMasterPlayer()
        }

        for (summon in request.monster.getSummons().toList()) {
            summon.changeHealth(-summon.health)
            summon.removeMaster()
        }
        request.clearSummons()

        request.clearTargetList()
        request.clearFriendList()
        request.onIdleStatus()

        request.state.dead = true

        if (!request.hasMonsterType) {
            return
        }

        request.sendDeathSound(request.monster.position, request.deathSound)

        if (targetPlayer == null) {
            return
        }

        // PARITY GAP: weaponProficiency().applyOn(...) and the bestiary charm /
        // weapon-proficiency tail (monster.cpp:3280-3302) stay with the unported
        // player-progression subsystems.
    }

    /**
     * `Monster::getCorpse` (monster.cpp:3305-3320) over `Creature::getCorpse`
     * (creature.cpp:838-843): no corpse without a `lookcorpse` id; otherwise the
     * item is created through [createCorpse] and owned by the most-damage
     * creature or its master player.
     */
    fun getCorpse(
        lookCorpse: Int,
        createCorpse: (Int) -> Container?,
        mostDamageCreature: TargetCreatureFacts?,
    ): Container? {
        if (lookCorpse == 0) {
            return null
        }

        val corpse = createCorpse(lookCorpse) ?: return null
        MonsterLoot.applyCorpseOwner(corpse, mostDamageCreature)
        return corpse
    }

    /**
     * Corpse/splash branch of `Creature::dropCorpse` (creature.cpp:732-828).
     * Without loot drop only the puff effect runs; otherwise the race splash is
     * placed and decayed, the corpse is created with loot and decayed. The Lua
     * death events, loot message and auto-loot are not ported (PARITY GAP).
     */
    fun dropCorpse(request: MonsterCorpseDropRequest): MonsterCorpseDropResult {
        val result = MonsterCorpseDropResult()

        if (!request.lootDrop) {
            request.addMagicEffect(request.monster.position, CONST_ME_POFF)
            return result
        }

        val tile = request.tile
        val splashFluid = splashFluidForRace(request.race)
        if (tile != null && splashFluid != null) {
            val splash = Item.create(ITEM_FULLSPLASH, splashFluid)
            request.addItemToTile(tile, splash)
            request.startDecaying(splash)
            result.splash = splash
        }

        val corpse = getCorpse(request.lookCorpse, request.createCorpse, request.mostDamageCreature)
        result.corpse = corpse
        if (tile != null && corpse != null) {
            request.addItemToTile(tile, corpse)
            result.loot.addAll(
                MonsterLoot.dropLoot(corpse, request.lootRequest, request.nextRoll, request.nextSliverCount)
            )
            request.startDecaying(corpse)
        }

        return result
    }

    /** `Monster::getDefense` (monster.cpp:242-252): `(type + runtime) * multiplier`. */
    fun getDefense(mtypeDefense: Int, state: MonsterBehaviorState, defenseMultiplier: Double = 1.0): Int =
        ((mtypeDefense + state.defense) * defenseMultiplier).toInt()

    /** `Monster::addDefense` (monster.cpp:254-258). */
    fun addDefense(state: MonsterBehaviorState, defense: Int) {
        state.defense += defense
    }

    /** `Monster::getDefenseMultiplier` forge stacking (monster.cpp:3559-3565). */
    fun getDefenseMultiplier(baseMultiplier: Double, forgeStack: Int): Double =
        if (forgeStack > 0) {
            baseMultiplier * (1 + 0.1 * forgeStack)
        } else {
            baseMultiplier
        }

    /** `Monster::isImmune(CombatType_t)` (monster.cpp:3539-3541). */
    fun isImmune(combatType: CombatType, immuneOverride: Boolean, damageImmunities: Set<CombatType>): Boolean =
        immuneOverride || damageImmunities.contains(combatType)

    /** `Monster::isImmune(ConditionType_t)` (monster.cpp:3535-3537). */
    fun isImmune(conditionType: ConditionType, immuneOverride: Boolean, conditionImmunities: Set<ConditionType>): Boolean =
        immuneOverride || conditionImmunities.contains(conditionType)

    /**
     * `Monster::getReflectPercent` (monster.cpp:209-235): the creature base
     * (`Creature::reflectPercent`, unmodelled and passed as [creatureReflect])
     * plus the monster-type `reflectMap` and the runtime [MonsterBehaviorState.reflectElements].
     */
    fun getReflectPercent(
        combatType: CombatType,
        creatureReflect: Int = 0,
        monsterTypeReflect: Map<CombatType, Int> = emptyMap(),
        state: MonsterBehaviorState,
    ): Double = (
        creatureReflect +
            (monsterTypeReflect[combatType] ?: 0) +
            (state.reflectElements[combatType] ?: 0)
        ).toDouble()

    /** `Monster::addReflectElement` (monster.cpp:237-240). */
    fun addReflectElement(state: MonsterBehaviorState, combatType: CombatType, percent: Int) {
        state.reflectElements[combatType] = (state.reflectElements[combatType] ?: 0) + percent
    }

    /**
     * `Monster::blockHit` (monster.cpp:1402-1428) over the base
     * `Creature::blockHit` armor/defense branch: after the base block, a
     * non-zero damage is scaled by the monster type's `elementMap` entry
     * (percentage, positive weakens the attacker's damage and negative amplifies
     * it) and clamped to `BLOCK_ARMOR` when it reaches zero.
     *
     * [elementSensitiveReduction] is
     * `player->wheel().checkElementSensitiveReduction(combatType)`
     * (monster.cpp:1412-1416), unported.
     */
    fun blockHit(
        combatType: CombatType,
        damage: Int,
        elementMap: Map<CombatType, Int> = emptyMap(),
        checkDefense: Boolean = false,
        checkArmor: Boolean = false,
        hasDefense: Boolean = true,
        canUseDefense: Boolean = true,
        defense: Int = 0,
        armor: Int = 0,
        immune: Boolean = false,
        elementSensitiveReduction: Int = 0,
        random: Random = Random(),
    ): Pair<Int, BlockType> {
        val (baseDamage, baseBlockType) = Combat.blockHit(
            damage = damage,
            random = random,
            checkDefense = checkDefense,
            checkArmor = checkArmor,
            hasDefense = hasDefense,
            canUseDefense = canUseDefense,
            defense = defense,
            armor = armor,
            immune = immune,
        )

        if (baseDamage == 0) {
            return baseDamage to baseBlockType
        }

        var elementMod = elementMap[combatType] ?: 0
        elementMod -= elementSensitiveReduction
        if (elementMod == 0) {
            return baseDamage to baseBlockType
        }

        var adjustedDamage = Math.round(baseDamage * ((100 - elementMod) / 100.0)).toInt()
        var blockType = baseBlockType
        if (adjustedDamage <= 0) {
            adjustedDamage = 0
            blockType = BlockType.ARMOR
        }
        return adjustedDamage to blockType
    }

    /**
     * `Monster::getRandomStep` (monster.cpp:2552-2568): shuffles the fixed
     * `{NORTH, WEST, EAST, SOUTH}` list and returns the first walkable direction.
     */
    fun getRandomStep(
        creaturePos: Position,
        canWalkTo: (Position, Direction) -> Boolean,
        shuffleDirections: (MutableList<Direction>) -> Unit = { it.shuffle() },
    ): Direction? {
        val dirList = mutableListOf(Direction.North, Direction.West, Direction.East, Direction.South)
        shuffleDirections(dirList)

        for (direction in dirList) {
            if (canWalkTo(creaturePos, direction)) {
                return direction
            }
        }
        return null
    }

    /**
     * `Monster::getDanceStep` (monster.cpp:2570-2630): keeps the diagonal
     * distance to the attacked creature by trying the four cardinal directions
     * (gated by the offset sign for [keepDistance]) and, when [keepAttack], only
     * accepting positions that keep the attack usable. The surviving list is
     * shuffled and an index drawn through [nextRandomIndex].
     */
    fun getDanceStep(
        creaturePos: Position,
        attackedCreaturePosition: Position?,
        targetDistance: Int,
        attackSpells: List<MonsterSpellBlock>,
        isHostile: Boolean,
        canWalkTo: (Position, Direction) -> Boolean,
        isSightClear: (Position, Position) -> Boolean,
        keepAttack: Boolean = true,
        keepDistance: Boolean = true,
        shuffleDirections: (MutableList<Direction>) -> Unit = { it.shuffle() },
        nextRandomIndex: (Int) -> Int = { 0 },
    ): Direction? {
        if (attackedCreaturePosition == null) {
            return null
        }

        val canDoAttackNow = canUseAttack(attackSpells, isHostile, creaturePos, attackedCreaturePosition, isSightClear)
        val centerPos = attackedCreaturePosition
        val offsetX = creaturePos.x - centerPos.x
        val offsetY = creaturePos.y - centerPos.y
        val distanceX = abs(offsetX)
        val distanceY = abs(offsetY)
        val centerToDist = max(distanceX, distanceY)

        // monsters not at targetDistance shouldn't dancestep
        if (centerToDist < targetDistance) {
            return null
        }

        val dirList = mutableListOf<Direction>()

        fun tryAddDirection(direction: Direction, newX: Int, newY: Int) {
            val tmpDist = max(abs(newX - centerPos.x), abs(newY - centerPos.y))
            if (tmpDist != centerToDist || !canWalkTo(creaturePos, direction)) {
                return
            }

            val keepsAttack = !keepAttack || !canDoAttackNow ||
                canUseAttack(attackSpells, isHostile, Position(newX, newY, creaturePos.z), centerPos, isSightClear)
            if (keepsAttack) {
                dirList.add(direction)
            }
        }

        if (!keepDistance || offsetY >= 0) {
            tryAddDirection(Direction.North, creaturePos.x, creaturePos.y - 1)
        }

        if (!keepDistance || offsetY <= 0) {
            tryAddDirection(Direction.South, creaturePos.x, creaturePos.y + 1)
        }

        if (!keepDistance || offsetX <= 0) {
            tryAddDirection(Direction.East, creaturePos.x + 1, creaturePos.y)
        }

        if (!keepDistance || offsetX >= 0) {
            tryAddDirection(Direction.West, creaturePos.x - 1, creaturePos.y)
        }

        if (dirList.isEmpty()) {
            return null
        }

        shuffleDirections(dirList)
        return dirList[nextRandomIndex(dirList.size)]
    }

    /**
     * `Monster::doRandomStep` (monster.cpp:2494-2499): at most one random step
     * per second, latched through [MonsterBehaviorState.randomStepping].
     */
    fun doRandomStep(
        state: MonsterBehaviorState,
        position: Position,
        timeSinceLastMove: Long,
        canWalkTo: (Position, Direction) -> Boolean,
        shuffleDirections: (MutableList<Direction>) -> Unit = { it.shuffle() },
    ): Direction? {
        if (timeSinceLastMove < 1000) {
            return null
        }

        state.randomStepping = true
        return getRandomStep(position, canWalkTo, shuffleDirections)
    }

    /**
     * `Monster::doWalkBack` (monster.cpp:2501-2528): players on screen cancel
     * the walk home; a creature step keeps walking with the pathfinding flag;
     * otherwise a path to `masterPos` is requested with the
     * `[0, max(0, distance - 5)]` window and auto-walked. An empty path or
     * reaching the master clears `isWalkingBack`.
     */
    fun doWalkBack(
        state: MonsterBehaviorState,
        position: Position,
        masterPos: Position,
        flags: Int = 0,
        totalPlayersOnScreen: Int = 0,
        getNextStep: () -> Direction? = { null },
        getPathTo: (Position, Int, Int, Boolean, Boolean, Int) -> List<Direction> = { _, _, _, _, _, _ -> emptyList() },
        startAutoWalk: (List<Direction>) -> Unit = {},
    ): WalkBackResult {
        if (totalPlayersOnScreen > 0) {
            state.isWalkingBack = false
            return WalkBackResult(flags, null, false)
        }

        val nextDirection = getNextStep()
        if (nextDirection != null) {
            return WalkBackResult(flags or CylinderFlags.FLAG_PATHFINDING, nextDirection, true)
        }

        if (state.ignoreFieldDamage) {
            state.ignoreFieldDamage = false
        }

        val distance = max(abs(position.x - masterPos.x), abs(position.y - masterPos.y))
        if (distance == 0) {
            state.isWalkingBack = false
            return WalkBackResult(flags, null, false)
        }

        val path = getPathTo(masterPos, 0, max(0, distance - 5), true, true, distance)
        if (path.isEmpty()) {
            state.isWalkingBack = false
            return WalkBackResult(flags, null, false)
        }

        startAutoWalk(path)
        return WalkBackResult(flags, null, false)
    }

    /**
     * `Monster::isInSpawnLocation` (monster.cpp:1562-1567): a monster without a
     * spawn owner is always "at home"; otherwise home is `masterPos` (or an
     * unset master position).
     */
    fun isInSpawnLocation(position: Position, masterPos: Position, hasSpawnOwner: Boolean): Boolean {
        if (!hasSpawnOwner) {
            return true
        }
        return position == masterPos || masterPos == Position(0, 0, 0)
    }

    /**
     * `Monster::isInSpawnRange` (monster.cpp:3322-3344): the despawn radius zone
     * is checked first, then the absolute z distance against the despawn range.
     */
    fun isInSpawnRange(
        pos: Position,
        masterPos: Position,
        hasSpawnOwner: Boolean,
        despawnRadius: Int,
        despawnRange: Int,
    ): Boolean {
        if (!hasSpawnOwner) {
            return true
        }

        if (despawnRadius == 0) {
            return true
        }

        if (!SpawnsMonster.isInZone(masterPos, despawnRadius, pos)) {
            return false
        }

        if (despawnRange == 0) {
            return true
        }

        return abs(pos.z - masterPos.z) <= despawnRange
    }

    /**
     * `Monster::updateIdleStatus` deterministic decision (monster.cpp:1540-1555)
     * followed by `Monster::setIdle` (monster.cpp:1499-1519): a targetless
     * non-summon either idles at its spawn location or walks back; a faction
     * master idles when its screen audience is empty.
     */
    fun updateIdleStatus(request: MonsterIdleRequest) {
        var idle = false

        if (!request.hasConditions) {
            if (!request.isSummon && request.targetListEmpty) {
                if (isInSpawnLocation(request.position, request.masterPos, request.hasSpawnOwner)) {
                    idle = true
                } else {
                    request.state.isWalkingBack = true
                }
            } else if (request.hasMaster) {
                val masterPlayersOnScreen = if (request.isSummon) {
                    request.masterMonsterPlayersOnScreen == 0
                } else {
                    request.totalPlayersOnScreen == 0
                }
                if (masterPlayersOnScreen && !request.factionIsDefault) {
                    idle = true
                }
            }
        }

        setIdle(request, idle)
    }

    private fun setIdle(request: MonsterIdleRequest, idle: Boolean) {
        if (request.isRemoved || request.health <= 0) {
            return
        }

        if (!idle && !request.state.isIdle) {
            request.addCreatureCheck()
            return
        }

        request.state.isIdle = idle

        if (!idle) {
            request.addCreatureCheck()
        } else {
            request.onIdleStatus()
            request.clearTargetList()
            request.clearFriendList()
            request.removeCreatureCheck()
        }
    }

    private fun splashFluidForRace(race: RaceType): Int? = when (race) {
        RaceType.RACE_VENOM -> FLUID_SLIME
        RaceType.RACE_BLOOD -> FLUID_BLOOD
        RaceType.RACE_INK -> FLUID_INK
        RaceType.RACE_CHOCOLATE -> FLUID_CHOCOLATE
        RaceType.RACE_CANDY -> FLUID_CANDY
        RaceType.RACE_NONE, RaceType.RACE_UNDEAD, RaceType.RACE_FIRE, RaceType.RACE_ENERGY -> null
    }

    private fun Creature?.playerOrMasterPlayer(): Player? {
        if (this is Player) {
            return this
        }
        val master = this?.getMaster()
        return master as? Player
    }
}
