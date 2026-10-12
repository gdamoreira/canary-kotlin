package org.opentibia.canary.systems.offlinetraining

import kotlin.math.round

/**
 * Mirror of Canary's `skills_t` enum (src/creatures/creatures_definitions.hpp:466).
 *
 * Only the offline-training relevant skills are exercised by this subsystem, but
 * every value is transcribed verbatim so the numbering matches Canary.
 */
enum class Skill(val value: Int) {
    SKILL_NONE(-1),
    SKILL_FIST(0),
    SKILL_CLUB(1),
    SKILL_SWORD(2),
    SKILL_AXE(3),
    SKILL_DISTANCE(4),
    SKILL_SHIELD(5),
    SKILL_FISHING(6),
    SKILL_CRITICAL_HIT_CHANCE(7),
    SKILL_CRITICAL_HIT_DAMAGE(8),
    SKILL_LIFE_LEECH_CHANCE(9),
    SKILL_LIFE_LEECH_AMOUNT(10),
    SKILL_MANA_LEECH_CHANCE(11),
    SKILL_MANA_LEECH_AMOUNT(12),
    SKILL_MAGLEVEL(13),
    SKILL_LEVEL(14),
    ;

    companion object {
        /** Mirrors Canary's `SKILL_LAST = SKILL_MANA_LEECH_AMOUNT`. */
        const val LAST: Int = 12
        /** Mirrors Canary's `SKILL_FIRST = SKILL_FIST`. */
        const val FIRST: Int = 0
    }
}

/**
 * Dependency-injection contract for the two `Vocation` methods used by
 * `Player::addOfflineTrainingTries`:
 *  - `Vocation::getReqSkillTries(uint8_t skill, uint16_t level)`
 *    (src/creatures/players/vocations/vocation.cpp:265)
 *  - `Vocation::getReqMana(uint32_t magLevel)`
 *    (src/creatures/players/vocations/vocation.cpp:295)
 *
 * Canary resolves these through the global `Vocations`/`g_configManager()`
 * singletons; per AGENTS.md 1.1 they are injected here instead.
 */
interface VocationRequirements {
    fun getReqSkillTries(skill: Skill, level: Int): Long
    fun getReqMana(magLevel: Int): Long
}

/**
 * Mirror of Canary `struct Skill` (src/creatures/creatures_definitions.hpp:1609).
 *
 * Canary declares `percent` as `double` but only ever assigns the truncated
 * `uint8_t` result of `Player::getPercentLevel`, so a whole-number [Int] keeps
 * the same observable behavior.
 */
data class SkillState(
    var level: Int = 10,
    var tries: Long = 0,
    var percent: Int = 0,
)

/**
 * C++-parity engine core of CipSoft's Offline Training, mirroring the offline
 * training state of Canary's `Player`:
 *
 *  - State fields: `offlineTrainingSkill` / `offlineTrainingTime`
 *    (src/creatures/players/player.hpp:1807-1808) and the skill/magic-level
 *    state touched by `Player::addOfflineTrainingTries`.
 *  - Methods: `addOfflineTrainingTries` (player.cpp:8130),
 *    `addOfflineTrainingTime` (player.cpp:8279),
 *    `removeOfflineTrainingTime` (player.cpp:8283),
 *    `getOfflineTrainingTime` (player.cpp:8287),
 *    `getOfflineTrainingSkill` (player.cpp:8291),
 *    `setOfflineTrainingSkill` (player.cpp:8295).
 *  - Accrual: `Player::onThink` calls `addOfflineTrainingTime(interval)`
 *    (player.cpp:8653).
 *
 * Login try conversion from offline time/vocation lives in
 * data/scripts/creaturescripts/player/offline_training.lua and stays Lua per
 * AGENTS.md 1.4. This class only provides the engine primitive that script calls.
 *
 * Not ported (requires the Player/protocol/event stack): sendSkills/sendStats,
 * sendTextMessage, sendTakeScreenshot, playerAdvance and the
 * playerOnGainSkillTries events/callbacks issued inside addOfflineTrainingTries.
 */
class OfflineTraining(
    private val vocation: VocationRequirements,
) {
    /** `Player::offlineTrainingSkill`, default `SKILL_NONE` (player.hpp:1807). */
    private var offlineTrainingSkill: Skill = Skill.SKILL_NONE

    /** `Player::offlineTrainingTime` in milliseconds, default 0 (player.hpp:1808). */
    private var offlineTrainingTime: Int = 0

    /** Mirrors `Player::skills[SKILL_LAST + 1]` (player.hpp:1711). */
    val skills: MutableMap<Skill, SkillState> = mutableMapOf()

    /** Mirrors `Player::magLevel` (player.hpp). */
    var magLevel: Int = 0

    /** Mirrors `Player::manaSpent` (player.hpp:1722). */
    var manaSpent: Long = 0

    /** Mirrors `Player::magLevelPercent` (player.hpp:1851). */
    var magLevelPercent: Int = 0

    fun getOfflineTrainingSkill(): Skill = offlineTrainingSkill

    fun setOfflineTrainingSkill(skill: Skill) {
        offlineTrainingSkill = skill
    }

    fun getOfflineTrainingTime(): Int = offlineTrainingTime

    /** Mirrors `Player::addOfflineTrainingTime` (player.cpp:8279). */
    fun addOfflineTrainingTime(addTime: Int) {
        offlineTrainingTime = minOf(OFFLINE_TRAINING_TIME_CAP, offlineTrainingTime + addTime)
    }

    /** Mirrors `Player::removeOfflineTrainingTime` (player.cpp:8283). */
    fun removeOfflineTrainingTime(removeTime: Int) {
        offlineTrainingTime = maxOf(0, offlineTrainingTime - removeTime)
    }

    /**
     * Offline training accrual performed from `Player::onThink`
     * (player.cpp:8653): `addOfflineTrainingTime(interval)`.
     */
    fun onThink(interval: Int) {
        addOfflineTrainingTime(interval)
    }

    /**
     * Mirrors `Player::addOfflineTrainingTries` (player.cpp:8130). Consumes skill
     * or magic-level tries and applies the level-advancement loop. Returns the
     * Canary `sendUpdate` flag (a level or progress change occurred).
     */
    fun addOfflineTrainingTries(skill: Skill, tries: Long): Boolean {
        if (tries == 0L || skill == Skill.SKILL_LEVEL) {
            return false
        }

        var remainingTries = tries
        var sendUpdate = false

        if (skill == Skill.SKILL_MAGLEVEL) {
            var currReqMana = vocation.getReqMana(magLevel)
            var nextReqMana = vocation.getReqMana(magLevel + 1)

            if (currReqMana >= nextReqMana) {
                return false
            }

            val currMagLevel = magLevel
            while (manaSpent + remainingTries >= nextReqMana) {
                remainingTries -= nextReqMana - manaSpent

                magLevel++
                manaSpent = 0

                sendUpdate = true
                currReqMana = nextReqMana
                nextReqMana = vocation.getReqMana(magLevel + 1)

                if (currReqMana >= nextReqMana) {
                    remainingTries = 0
                    break
                }
            }

            manaSpent += remainingTries

            if (magLevel != currMagLevel) {
                // Canary sends "You advanced to magic level N." + screenshot here.
            }

            val newPercent = if (nextReqMana > currReqMana) {
                getPercentLevel(manaSpent, nextReqMana).toInt()
            } else {
                0
            }

            if (newPercent != magLevelPercent) {
                magLevelPercent = newPercent
                sendUpdate = true
            }
        } else {
            val state = skills.getOrPut(skill) { SkillState() }
            var currReqTries = vocation.getReqSkillTries(skill, state.level)
            var nextReqTries = vocation.getReqSkillTries(skill, state.level + 1)

            if (currReqTries >= nextReqTries) {
                return false
            }

            val currSkillLevel = state.level
            while (state.tries + remainingTries >= nextReqTries) {
                remainingTries -= nextReqTries - state.tries

                state.level++
                state.tries = 0
                state.percent = 0

                sendUpdate = true
                currReqTries = nextReqTries
                nextReqTries = vocation.getReqSkillTries(skill, state.level + 1)

                if (currReqTries >= nextReqTries) {
                    remainingTries = 0
                    break
                }
            }

            state.tries += remainingTries

            if (currSkillLevel != state.level) {
                // Canary sends "You advanced to <skill> level N." + screenshot here.
            }

            val newPercent = if (nextReqTries > currReqTries) {
                getPercentLevel(state.tries, nextReqTries).toInt()
            } else {
                0
            }

            if (state.percent != newPercent) {
                state.percent = newPercent
                sendUpdate = true
            }
        }

        return sendUpdate
    }

    companion object {
        /**
         * `std::min<int32_t>(12 * 3600 * 1000, ...)` from
         * `Player::addOfflineTrainingTime` (player.cpp:8280).
         */
        const val OFFLINE_TRAINING_TIME_CAP: Int = 12 * 3600 * 1000

        /**
         * Mirrors `Player::getPercentLevel` (player.cpp:3831). Returns 0 when the
         * denominator is 0 or when the rounded percentage would exceed 100.
         */
        fun getPercentLevel(count: Long, nextLevelCount: Long): Double {
            if (nextLevelCount == 0L) {
                return 0.0
            }

            val result = round(((count * 100.0) / nextLevelCount) * 100.0) / 100.0
            if (result > 100) {
                return 0.0
            }
            return result
        }
    }
}
