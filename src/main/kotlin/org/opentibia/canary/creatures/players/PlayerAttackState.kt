package org.opentibia.canary.creatures.players

import org.opentibia.canary.combat.BlockType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.skills_t

/**
 * Self-contained mirror of the Player attack/block bookkeeping state
 * (src/creatures/players/player.cpp:1135-1188, 3166-3172, 3486-3492,
 * 3843-3902).
 *
 * The class is intentionally *not* `Player.kt` (per task constraints). Canary
 * keeps these fields directly on `Player` (`lastAttack`, `lastAttacked`,
 * `lastAggressiveAction`, `lastAttackBlockType`, `bloodHitCount`,
 * `shieldBlockCount`, `addAttackSkillPoint`, `actionTaskEventPush`,
 * `inEventMovePush`; player.hpp:1740-1761); the state is exposed as an
 * injectable seam so the host player can delegate without a global
 * (AGENTS.md 1.1). Every external dependency is a constructor seam:
 * [now] replaces `OTSYS_TIME()`, [attackSpeed] replaces `Player::getAttackSpeed`
 * (player.cpp:7346), [hasShield] replaces `Player::hasShield` (player.cpp:3887),
 * [addSkillAdvance] replaces `Player::addSkillAdvance` (player.cpp:1190) and
 * [stopEvent] replaces `g_dispatcher().stopEvent` (player.cpp:3488).
 *
 * The methods keep the exact `Player::` names of the C++ symbols they
 * transcribe, so every behavior is auditable against the C++ line references.
 */
class PlayerAttackState(
    /** `OTSYS_TIME()` (`src/utils/time.hpp`); milliseconds. */
    private val now: () -> Long = { System.currentTimeMillis() },
    /** `Player::getAttackSpeed()` (player.cpp:7346). */
    private val attackSpeed: () -> Int = { 0 },
    /** `Player::hasShield()` (player.cpp:3887). */
    private val hasShield: () -> Boolean = { false },
    /** `Player::addSkillAdvance(skill, count)` (player.cpp:1190). */
    private val addSkillAdvance: (skills_t, Long) -> Unit = { _, _ -> },
    /** `g_dispatcher().stopEvent(eventId)` (player.cpp:3488). */
    private val stopEvent: (Long) -> Unit = {},
) {

    /** `Player::lastAttackBlockType` (player.hpp:1740). */
    private var lastAttackBlockType: BlockType = BlockType.NONE

    /** `Player::lastAttack` (player.hpp:1752). */
    private var lastAttack = 0L

    /** `Player::lastAggressiveAction` (player.hpp:1880). */
    private var lastAggressiveAction = 0L

    /** `Player::bloodHitCount` (player.hpp:1759). */
    private var bloodHitCount = 0

    /** `Player::shieldBlockCount` (player.hpp:1760). */
    private var shieldBlockCount = 0

    /** `Player::addAttackSkillPoint` (player.hpp:1761). */
    private var addAttackSkillPoint = false

    /**
     * `Player::actionTaskEventPush` (player.hpp:1756); the scheduler id of the
     * pending push event, `0` when none is scheduled.
     */
    var actionTaskEventPush: Long = 0

    /** `Player::inEventMovePush` (player.hpp:1739). */
    private var inEventMovePush = false

    /** `Player::getLastAttackBlockType` (player.cpp:1139-1141). */
    fun getLastAttackBlockType(): BlockType = lastAttackBlockType

    /** `Player::getAddAttackSkill` (player.cpp:1135-1137). */
    fun getAddAttackSkill(): Boolean = addAttackSkillPoint

    /** `Player::getLastAttack` (player.cpp:1162-1164). */
    fun getLastAttack(): Long = lastAttack

    /** `Player::checkLastAttackWithin` (player.cpp:1166-1168). */
    fun checkLastAttackWithin(interval: Int): Boolean = lastAttack > 0 && (now() - lastAttack) < interval

    /**
     * `Player::updateLastAttack` (player.cpp:1170-1174): the first attack is
     * backdated one tick before the cooldown so the next one is allowed.
     */
    fun updateLastAttack() {
        if (lastAttack == 0L) {
            lastAttack = now() - attackSpeed() - 1
            return
        }
        lastAttack = now()
    }

    /** `Player::getLastAggressiveAction` (player.cpp:1178-1180). */
    fun getLastAggressiveAction(): Long = lastAggressiveAction

    /** `Player::checkLastAggressiveActionWithin` (player.cpp:1182-1184). */
    fun checkLastAggressiveActionWithin(interval: Int): Boolean =
        lastAggressiveAction > 0 && (now() - lastAggressiveAction) < interval

    /** `Player::updateLastAggressiveAction` (player.cpp:1186-1188). */
    fun updateLastAggressiveAction() {
        lastAggressiveAction = now()
    }

    /** `Player::pushEvent` (player.cpp:3166-3168). */
    fun pushEvent(b: Boolean) {
        inEventMovePush = b
    }

    /** `Player::inPushEvent` (player.cpp:3170-3172). */
    fun inPushEvent(): Boolean = inEventMovePush

    /** `Player::cancelPush` (player.cpp:3486-3492). */
    fun cancelPush() {
        if (actionTaskEventPush != 0L) {
            stopEvent(actionTaskEventPush)
            actionTaskEventPush = 0
            inEventMovePush = false
        }
    }

    /** `Player::onBlockHit` (player.cpp:3843-3851). */
    fun onBlockHit() {
        if (shieldBlockCount > 0) {
            --shieldBlockCount

            if (hasShield()) {
                addSkillAdvance(skills_t.SKILL_SHIELD, 1)
            }
        }
    }

    /** `Player::onTakeDamage` (player.cpp:3853-3855): nothing here yet. */
    fun onTakeDamage(attacker: Creature?, damage: Int) {
        // Intentionally empty, mirroring the Canary stub.
    }

    /**
     * `Player::onAttackedCreatureBlockHit` (player.cpp:3857-3902): refills the
     * 30-hit blood/shield counters on a clean hit, consumes the blood counter on
     * defense/armor blocks and never adds attack skill on immunity/dodge.
     */
    fun onAttackedCreatureBlockHit(blockType: BlockType) {
        lastAttackBlockType = blockType

        when (blockType) {
            BlockType.NONE -> {
                addAttackSkillPoint = true
                bloodHitCount = 30
                shieldBlockCount = 30
            }

            BlockType.DEFENSE, BlockType.ARMOR -> {
                // need to draw blood every 30 hits
                if (bloodHitCount > 0) {
                    addAttackSkillPoint = true
                    --bloodHitCount
                } else {
                    addAttackSkillPoint = false
                }
            }

            else -> {
                addAttackSkillPoint = false
            }
        }
    }
}
