package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.BlockType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.map.Position

/**
 * Transcribes the Player attack/block bookkeeping fields
 * (src/creatures/players/player.cpp:1135-1188, 3166-3172, 3486-3492, 3843-3902):
 *
 * - `Player::getLastAttackBlockType` (player.cpp:1139-1141)
 * - `Player::getLastAttack` / `checkLastAttackWithin` / `updateLastAttack`
 *   (player.cpp:1162-1174)
 * - `Player::getLastAggressiveAction` / `checkLastAggressiveActionWithin` /
 *   `updateLastAggressiveAction` (player.cpp:1178-1188)
 * - `Player::getAddAttackSkill` (player.cpp:1135-1137)
 * - `Player::pushEvent` / `Player::inPushEvent` (player.cpp:3166-3172)
 * - `Player::cancelPush` (player.cpp:3486-3492)
 * - `Player::onBlockHit` (player.cpp:3843-3851)
 * - `Player::onTakeDamage` (player.cpp:3853-3855)
 * - `Player::onAttackedCreatureBlockHit` (player.cpp:3857-3902)
 *
 * Canary keeps these fields directly on `Player` (player.hpp:1740-1761); the
 * class is the injectable seam per AGENTS.md 1.1 because the port's `Player`
 * lives outside this package. [now] replaces `OTSYS_TIME()`, [attackSpeed]
 * replaces `Player::getAttackSpeed()` (player.cpp:7346), [hasShield] replaces
 * `Player::hasShield()` (player.cpp:3887), [addSkillAdvance] replaces
 * `Player::addSkillAdvance` (player.cpp:1190) and [stopEvent] replaces
 * `g_dispatcher().stopEvent(actionTaskEventPush)` (player.cpp:3488).
 */
class PlayerAttackStateTest {

    private var clock = 10_000L
    private var addSkillCalls = 0
    private var stoppedEvents = mutableListOf<Long>()

    private fun newState(
        attackSpeed: Int = 1500,
        hasShield: Boolean = true,
    ) = PlayerAttackState(
        now = { clock },
        attackSpeed = { attackSpeed },
        hasShield = { hasShield },
        addSkillAdvance = { skill, count ->
            if (skill == skills_t.SKILL_SHIELD && count == 1L) {
                addSkillCalls++
            }
        },
        stopEvent = { eventId -> stoppedEvents.add(eventId) },
    )

    // --- attack timestamps -------------------------------------------------------

    @Test
    fun `updateLastAttack seeds the first attack one tick before the cooldown`() {
        // Player::updateLastAttack (player.cpp:1170-1174): the first call
        // backdates to `OTSYS_TIME() - getAttackSpeed() - 1` so the next attack
        // is allowed immediately.
        val state = newState(attackSpeed = 1500)

        state.updateLastAttack()

        assertThat(state.getLastAttack()).isEqualTo(8_499)
        assertThat(state.checkLastAttackWithin(1500)).isFalse()
        assertThat(state.checkLastAttackWithin(2000)).isTrue()
    }

    @Test
    fun `updateLastAttack stamps the wall clock on later attacks`() {
        // player.cpp:1173: subsequent calls store `OTSYS_TIME()`.
        val state = newState()
        state.updateLastAttack()

        clock = 20_000
        state.updateLastAttack()

        assertThat(state.getLastAttack()).isEqualTo(20_000)
        assertThat(state.checkLastAttackWithin(1)).isTrue()

        clock = 20_001
        assertThat(state.checkLastAttackWithin(1)).isFalse()
    }

    @Test
    fun `checkLastAttackWithin is false before the first attack`() {
        // player.cpp:1167: `lastAttack > 0` gate.
        assertThat(newState().checkLastAttackWithin(Int.MAX_VALUE)).isFalse()
    }

    @Test
    fun `aggressive action timestamps mirror the attack timestamps`() {
        // Player::getLastAggressiveAction / check / update (player.cpp:1178-1188).
        val state = newState()
        assertThat(state.getLastAggressiveAction()).isZero()
        assertThat(state.checkLastAggressiveActionWithin(Int.MAX_VALUE)).isFalse()

        state.updateLastAggressiveAction()
        assertThat(state.getLastAggressiveAction()).isEqualTo(10_000)
        assertThat(state.checkLastAggressiveActionWithin(500)).isTrue()

        clock = 10_500
        assertThat(state.checkLastAggressiveActionWithin(500)).isFalse()
    }

    // --- block bookkeeping -------------------------------------------------------

    @Test
    fun `onAttackedCreatureBlockHit NONE refills the blood and shield counters`() {
        // Player::onAttackedCreatureBlockHit (player.cpp:3857-3870).
        val state = newState()
        assertThat(state.getLastAttackBlockType()).isEqualTo(BlockType.NONE)
        assertThat(state.getAddAttackSkill()).isFalse()

        state.onAttackedCreatureBlockHit(BlockType.NONE)

        assertThat(state.getLastAttackBlockType()).isEqualTo(BlockType.NONE)
        assertThat(state.getAddAttackSkill()).isTrue()

        repeat(30) { state.onBlockHit() }
        assertThat(addSkillCalls).isEqualTo(30)
        state.onBlockHit()
        assertThat(addSkillCalls).isEqualTo(30)
    }

    @Test
    fun `onAttackedCreatureBlockHit DEFENSE consumes the blood counter`() {
        // Player::onAttackedCreatureBlockHit (player.cpp:3872-3881).
        val state = newState()
        state.onAttackedCreatureBlockHit(BlockType.DEFENSE)
        assertThat(state.getAddAttackSkill()).isFalse()

        state.onAttackedCreatureBlockHit(BlockType.NONE)
        state.onAttackedCreatureBlockHit(BlockType.ARMOR)
        assertThat(state.getAddAttackSkill()).isTrue()
        assertThat(state.getLastAttackBlockType()).isEqualTo(BlockType.ARMOR)
    }

    @Test
    fun `onAttackedCreatureBlockHit immunity and dodge never add attack skill`() {
        // player.cpp:3883-3886: the default branch.
        val state = newState()
        state.onAttackedCreatureBlockHit(BlockType.IMMUNITY)
        assertThat(state.getAddAttackSkill()).isFalse()
        assertThat(state.getLastAttackBlockType()).isEqualTo(BlockType.IMMUNITY)
    }

    @Test
    fun `onBlockHit without a shield only consumes the counter`() {
        // Player::onBlockHit (player.cpp:3843-3851): the skill advance is
        // gated by `hasShield()`.
        val state = newState(hasShield = false)
        state.onAttackedCreatureBlockHit(BlockType.NONE)
        state.onBlockHit()

        assertThat(addSkillCalls).isZero()
    }

    @Test
    fun `onTakeDamage is a no-op like the Canary stub`() {
        // Player::onTakeDamage (player.cpp:3853-3855).
        val state = newState()
        state.onTakeDamage(Player(id = 9, name = "Attacker", maxHealth = 10, health = 10, position = Position(100, 100, 7)), 50)
        assertThat(state.getLastAttackBlockType()).isEqualTo(BlockType.NONE)
        assertThat(state.getLastAttack()).isZero()
    }

    // --- push event --------------------------------------------------------------

    @Test
    fun `pushEvent latches the move-push flag`() {
        // Player::pushEvent / inPushEvent (player.cpp:3166-3172).
        val state = newState()
        assertThat(state.inPushEvent()).isFalse()

        state.pushEvent(true)
        assertThat(state.inPushEvent()).isTrue()

        state.pushEvent(false)
        assertThat(state.inPushEvent()).isFalse()
    }

    @Test
    fun `cancelPush stops the scheduled event and clears the flag`() {
        // Player::cancelPush (player.cpp:3486-3492).
        val state = newState()
        state.pushEvent(true)
        state.actionTaskEventPush = 42

        state.cancelPush()

        assertThat(stoppedEvents).containsExactly(42L)
        assertThat(state.actionTaskEventPush).isZero()
        assertThat(state.inPushEvent()).isFalse()

        state.cancelPush()
        assertThat(stoppedEvents).containsExactly(42L)
    }
}
