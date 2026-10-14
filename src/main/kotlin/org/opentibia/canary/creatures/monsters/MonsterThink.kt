package org.opentibia.canary.creatures.monsters

import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.map.Position
import org.opentibia.canary.spells.SoundEffect

/**
 * `Monster::onThink` inputs (src/creatures/monsters/monster.cpp:1581-1632).
 * [runThinkEvent] replaces the Lua `thinkEvent` callback (returns true when the
 * script handled the tick and stopped the default flow), [canSpawn] is
 * `MonsterType::canSpawn(position)`, [removeCreature] is
 * `g_game().removeCreature`, [internalTeleport] is
 * `g_game().internalTeleport(monster, masterPos)`, [updateCreatureIcon] is
 * `g_game().updateCreatureIcon` and [setAsyncTaskFlag] is
 * `setAsyncTaskFlag(OnThink, true)`.
 */
class MonsterThinkRequest(
    val monster: Monster,
    val state: MonsterBehaviorState,
    val interval: Int,
    val thinkEvent: Int = -1,
    val infoTargetDistance: Int = 1,
    val runThinkEvent: () -> Boolean = { false },
    val canSpawn: (Position) -> Boolean = { true },
    val isInSpawnRange: (Position) -> Boolean = { true },
    val masterPos: Position = Position(0, 0, 0),
    val onUpdateCreatureIcon: () -> Unit = {},
    val removeCreature: () -> Unit = {},
    val internalTeleport: (Position) -> Unit = {},
    val setIdle: (Boolean) -> Unit = {},
    val updateIdleStatus: () -> Unit = {},
    val setAsyncTaskFlag: () -> Unit = {},
)

/**
 * `Monster::onThink_async` inputs (src/creatures/monsters/monster.cpp:1708-1752):
 * the live target/follow state is projected onto ids and booleans, and each
 * sub-rule is a seam. [searchTarget] takes the `TargetSearchType_t` of
 * `Monster::searchTarget`, `updateSummonTarget`/`updateTargetList` are the
 * deferred-epoch checked callbacks and `queuePostThinkAfterAsync` closes the
 * tick. [targetListPopulatedByRefresh] models `updateTargetList` finding
 * targets inside this tick.
 */
class MonsterThinkAsyncRequest(
    val state: MonsterBehaviorState,
    val position: Position = Position(0, 0, 0),
    val isIdle: Boolean,
    val isSummon: Boolean,
    val targetListEmpty: Boolean,
    val monsterPerfTestFriendlyFire: Boolean = false,
    val attackedCreatureId: Long? = null,
    val followCreatureId: Long? = null,
    val hasFollowPath: Boolean = false,
    val targetDistance: Int = 1,
    val targetListPopulatedByRefresh: Boolean = false,
    val canUseAttack: (Position) -> Boolean = { true },
    val isFleeing: Boolean = false,
    val addEventWalk: () -> Unit = {},
    val updateSummonTarget: () -> Unit = {},
    val updateTargetList: () -> Unit = {},
    val searchTarget: (TargetSearchType) -> Unit = {},
    val onThinkTarget: () -> Unit = {},
    val onThinkYell: () -> Unit = {},
    val onThinkDefense: () -> Unit = {},
    val onThinkSound: () -> Unit = {},
    val queuePostThinkAfterAsync: () -> Unit = {},
)

/**
 * `Monster::onThinkYell` inputs (src/creatures/monsters/monster.cpp:2274-2294).
 * [nextRandom] is `uniform_random(1, 100)` and [nextIndex] returns
 * `uniform_random(0, voiceVector.size - 1)`.
 */
class MonsterYellRequest(
    val state: MonsterBehaviorState,
    val interval: Int,
    val yellSpeedTicks: Int,
    val yellChance: Int,
    val voiceVector: List<VoiceBlock> = emptyList(),
    val nextRandom: (Int, Int) -> Int = { min, _ -> min },
    val nextIndex: (Int) -> Int = { 0 },
    val say: (String, Boolean) -> Unit = { _, _ -> },
)

/**
 * `Monster::onThinkSound` inputs (src/creatures/monsters/monster.cpp:2296-2310).
 * [nextRandom] is `uniform_random(1, 100)` and [nextIndex] returns
 * `uniform_random(0, soundVector.size - 1)`.
 */
class MonsterSoundRequest(
    val monster: Monster,
    val state: MonsterBehaviorState,
    val interval: Int,
    val soundSpeedTicks: Int,
    val soundChance: Int,
    val soundVector: List<SoundEffect> = emptyList(),
    val nextRandom: (Int, Int) -> Int = { min, _ -> min },
    val nextIndex: (Int) -> Int = { 0 },
    val sendSoundEffect: (Position, SoundEffect, Monster) -> Unit = { _, _, _ -> },
)

/**
 * Port of the `Monster` respawn/think rules:
 * - `Monster::getLostExperience` (src/creatures/monsters/monster.cpp:873-876)
 * - `Monster::onThink` (src/creatures/monsters/monster.cpp:1581-1632)
 * - `Monster::onThink_async` (src/creatures/monsters/monster.cpp:1708-1752)
 * - `Monster::onThinkYell` (src/creatures/monsters/monster.cpp:2274-2294)
 * - `Monster::onThinkSound` (src/creatures/monsters/monster.cpp:2296-2310)
 *
 * The post-think scheduling machinery (`trySchedulePostThink`,
 * `executePostThink`, `promotePostThinkToPlayerVisibleQueue`) and the deferred
 * `onThink_async` target/combat generations live in [MonsterAsyncPipelines].
 *
 * PARITY GAPS: `Creature::onThink`'s condition/event tail and the Lua
 * `thinkEvent` body stay behind the injected seams; `Monster::getLookCorpse` is
 * not ported.
 */
object MonsterThink {

    /** `EVENT_CREATURE_THINK_INTERVAL` (src/creatures/creature.hpp:47). */
    const val EVENT_CREATURE_THINK_INTERVAL = 1000

    /**
     * `Monster::getLostExperience` (monster.cpp:873-876): a skill-loss monster
     * returns `experience * ((forgeStack + 10) / 10)` (integer tier) up to
     * stack 15, then `experience * 28`.
     */
    fun getLostExperience(forgeStack: Int, skillLoss: Boolean, experience: Long): Long {
        val extraExperience = if (forgeStack <= 15) {
            ((forgeStack + 10) / 10).toFloat()
        } else {
            28f
        }
        return if (skillLoss) {
            Math.round(experience * extraExperience.toDouble())
        } else {
            0L
        }
    }

    /**
     * `Monster::onThink` (monster.cpp:1581-1632): the Lua think event can stop
     * the tick; the melee challenge window ticks down and restores the type
     * target distance; a monster whose type can no longer spawn is removed; a
     * monster outside its spawn range teleports home and idles; otherwise the
     * idle status is refreshed and the async think task is flagged.
     */
    fun onThink(request: MonsterThinkRequest) {
        if (request.thinkEvent != -1 && request.runThinkEvent()) {
            return
        }

        if (request.state.challengeMeleeDuration != 0) {
            request.state.challengeMeleeDuration =
                MonsterTargeting.subtractElapsedTime(request.state.challengeMeleeDuration, request.interval)
            if (request.state.challengeMeleeDuration <= 0) {
                request.state.challengeMeleeDuration = 0
                request.state.targetDistance = request.infoTargetDistance
                request.onUpdateCreatureIcon()
            }
        }

        if (!request.canSpawn(request.monster.position)) {
            request.removeCreature()
        }

        if (!request.isInSpawnRange(request.monster.position)) {
            request.internalTeleport(request.masterPos)
            request.setIdle(true)
            return
        }

        request.updateIdleStatus()
        request.setAsyncTaskFlag()
    }

    /**
     * `Monster::onThink_async` (monster.cpp:1708-1752): an idle monster stops;
     * summons update their target, non-summons refresh the target list under
     * friendly fire and re-search when the current target is missing,
     * unattackable or unreachable (falling back to a default flee search), then
     * the target/yell/defense/sound sub-ticks run and the post-think is queued.
     */
    fun onThinkAsync(request: MonsterThinkAsyncRequest): Boolean {
        if (request.isIdle) {
            return false
        }

        request.addEventWalk()

        if (request.isSummon) {
            request.updateSummonTarget()
        } else {
            var targetListEmpty = request.targetListEmpty
            if (request.monsterPerfTestFriendlyFire && targetListEmpty) {
                request.updateTargetList()
                targetListEmpty = !request.targetListPopulatedByRefresh
            }

            if (!targetListEmpty) {
                val attackedCreatureIsUnattackable =
                    request.attackedCreatureId != null && !request.canUseAttack(request.position)
                val attackedCreatureIsUnreachable =
                    request.targetDistance <= 1 &&
                        request.attackedCreatureId != null &&
                        request.followCreatureId != null &&
                        !request.hasFollowPath

                if (request.attackedCreatureId == null || attackedCreatureIsUnattackable || attackedCreatureIsUnreachable) {
                    if (request.followCreatureId == null || !request.hasFollowPath) {
                        request.searchTarget(
                            if (request.monsterPerfTestFriendlyFire) TargetSearchType.Random else TargetSearchType.Nearest
                        )
                    } else if (request.attackedCreatureId != null && request.isFleeing && !request.canUseAttack(request.position)) {
                        request.searchTarget(TargetSearchType.Default)
                    }
                }
            }
        }

        request.onThinkTarget()
        request.onThinkYell()
        request.onThinkDefense()
        request.onThinkSound()
        request.queuePostThinkAfterAsync()
        return true
    }

    /**
     * `Monster::onThinkYell` (monster.cpp:2274-2294): the yell speed window
     * accumulates ticks; when it elapses, a chance roll picks a voice line and
     * speaks it (yell or say).
     */
    fun onThinkYell(request: MonsterYellRequest) {
        if (request.yellSpeedTicks == 0) {
            return
        }

        request.state.yellTicks += request.interval
        if (request.state.yellTicks >= request.yellSpeedTicks) {
            request.state.yellTicks = 0

            if (request.voiceVector.isNotEmpty() && request.yellChance >= request.nextRandom(1, 100)) {
                val voice = request.voiceVector[request.nextIndex(request.voiceVector.size)]
                request.say(voice.text, voice.yellText)
            }
        }
    }

    /**
     * `Monster::onThinkSound` (monster.cpp:2296-2310): the sound speed window
     * accumulates ticks; when it elapses, a chance roll picks and emits a sound
     * effect.
     */
    fun onThinkSound(request: MonsterSoundRequest) {
        if (request.soundSpeedTicks == 0) {
            return
        }

        request.state.soundTicks += request.interval
        if (request.state.soundTicks >= request.soundSpeedTicks) {
            request.state.soundTicks = 0

            if (request.soundVector.isNotEmpty() && request.soundChance >= request.nextRandom(1, 100)) {
                val sound = request.soundVector[request.nextIndex(request.soundVector.size)]
                request.sendSoundEffect(request.monster.position, sound, request.monster)
            }
        }
    }
}
