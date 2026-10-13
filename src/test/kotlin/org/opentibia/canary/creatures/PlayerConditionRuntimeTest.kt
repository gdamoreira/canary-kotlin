package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionSuppression
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the `Player` condition integration.
 *
 * Source of truth: /Users/damoreira/.projects/canary
 *  - src/creatures/creature.cpp:1393-1404 (`addCombatCondition`), 1224-1226
 *    (`onAddCombatCondition` base)
 *  - src/creatures/players/player.cpp:356-394 (`isSuppress`,
 *    `addConditionSuppressions`, `removeConditionSuppressions`)
 *  - src/creatures/players/player.cpp:6291-6310 (`onAddCondition` override)
 *  - src/creatures/players/player.cpp:6342-6408 (`onAddCombatCondition`)
 *  - src/creatures/players/player.cpp:6411-6436 (`onEndCondition` override)
 *  - src/creatures/players/player.cpp:6528-6533 (`onIdleStatus`)
 */
class PlayerConditionRuntimeTest {

    private var clock = 1_000_000L

    private fun newPlayer() = Player(
        id = 11,
        name = "Condition Runtime",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    ).apply { now = { clock } }

    private fun condition(type: ConditionType, ticks: Int = 10_000, subId: Int = 0): Condition {
        val created = Condition.createCondition(
            ConditionId.CONDITIONID_DEFAULT,
            type,
            ticks,
            subId = subId,
            now = { clock },
        )!!
        created.setParam(org.opentibia.canary.combat.condition.ConditionParam.CONDITION_PARAM_MINVALUE, 1)
        created.setParam(org.opentibia.canary.combat.condition.ConditionParam.CONDITION_PARAM_MAXVALUE, 1)
        return created
    }

    @Test
    fun `addCombatCondition stores the condition and runs the player combat hook`() {
        val player = newPlayer()
        val messages = mutableListOf<String>()
        player.sendTextMessage = { messages.add(it) }

        assertThat(player.addCombatCondition(condition(ConditionType.CONDITION_POISON))).isTrue()

        assertThat(player.hasCondition(ConditionType.CONDITION_POISON)).isTrue()
        assertThat(messages).containsExactly("You are poisoned.")
        assertThat(player.getLastConditionTime(ConditionType.CONDITION_POISON)).isEqualTo(clock)
    }

    @Test
    fun `addCombatCondition rejects a null condition`() {
        val player = newPlayer()

        assertThat(player.addCombatCondition(null)).isFalse()
        assertThat(player.conditions).isEmpty()
    }

    @Test
    fun `addCombatCondition respects the player suppression gate`() {
        // Creature::addCondition -> Player::isSuppress (player.cpp:357-362).
        val player = newPlayer()
        player.addConditionSuppressions(arrayOf(ConditionType.CONDITION_FIRE))

        assertThat(player.addCombatCondition(condition(ConditionType.CONDITION_FIRE))).isFalse()
        assertThat(player.hasCondition(ConditionType.CONDITION_FIRE)).isFalse()
    }

    @Test
    fun `the min-delay window suppresses a re-applied suppressible type`() {
        val player = newPlayer()
        player.conditionSuppression = ConditionSuppression(
            minDelayBetweenConditions = 1_000,
            now = { clock },
            sendIcons = { player.sendIcons() },
        )

        assertThat(player.addCombatCondition(condition(ConditionType.CONDITION_PARALYZE))).isTrue()

        clock += 500
        assertThat(player.addCombatCondition(condition(ConditionType.CONDITION_PARALYZE))).isFalse()

        clock += 600
        assertThat(player.addCombatCondition(condition(ConditionType.CONDITION_PARALYZE))).isTrue()
    }

    @Test
    fun `suppression counts delegate to the player suppression state`() {
        val player = newPlayer()

        player.addConditionSuppressions(arrayOf(ConditionType.CONDITION_ENERGY))
        assertThat(player.isSuppress(ConditionType.CONDITION_ENERGY, false)).isTrue()
        assertThat(player.addCondition(condition(ConditionType.CONDITION_ENERGY))).isFalse()

        player.removeConditionSuppressions(listOf(ConditionType.CONDITION_ENERGY))
        assertThat(player.isSuppress(ConditionType.CONDITION_ENERGY, false)).isFalse()
        assertThat(player.addCondition(condition(ConditionType.CONDITION_ENERGY))).isTrue()
    }

    @Test
    fun `last condition time helpers delegate to the player suppression state`() {
        val player = newPlayer()

        assertThat(player.getLastConditionTime(ConditionType.CONDITION_CURSED)).isZero()
        player.updateLastConditionTime(ConditionType.CONDITION_CURSED)
        assertThat(player.getLastConditionTime(ConditionType.CONDITION_CURSED)).isEqualTo(clock)
        assertThat(player.checkLastConditionTimeWithin(ConditionType.CONDITION_CURSED, 1_000)).isTrue()
    }

    @Test
    fun `onAddCondition broadcasts the player icons`() {
        // player.cpp:6291-6310 always ends with sendIcons().
        val player = newPlayer()
        var icons = 0
        player.sendIcons = { icons++ }

        player.addCondition(condition(ConditionType.CONDITION_DRUNK))

        assertThat(icons).isEqualTo(1)
    }

    @Test
    fun `onAddCombatCondition sends the exact Canary texts`() {
        // player.cpp:6350-6407 MESSAGE_FAILURE branches.
        val expected = listOf(
            ConditionType.CONDITION_POISON to "You are poisoned.",
            ConditionType.CONDITION_DROWN to "You are drowning.",
            ConditionType.CONDITION_PARALYZE to "You are paralyzed.",
            ConditionType.CONDITION_DRUNK to "You are drunk.",
            ConditionType.CONDITION_LESSERHEX to "You are hexed.",
            ConditionType.CONDITION_INTENSEHEX to "You are hexed.",
            ConditionType.CONDITION_GREATERHEX to "You are hexed.",
            ConditionType.CONDITION_ROOTED to "You are rooted.",
            ConditionType.CONDITION_FEARED to "You are feared.",
            ConditionType.CONDITION_CURSED to "You are cursed.",
            ConditionType.CONDITION_FREEZING to "You are freezing.",
            ConditionType.CONDITION_DAZZLED to "You are dazzled.",
            ConditionType.CONDITION_BLEEDING to "You are bleeding.",
        )

        for ((type, text) in expected) {
            val player = newPlayer()
            val messages = mutableListOf<String>()
            player.sendTextMessage = { messages.add(it) }

            assertThat(player.addCombatCondition(condition(type)))
                .`as`("addCombatCondition(%s)", type)
                .isTrue()
            assertThat(messages).`as`("message for %s", type).containsExactly(text)
        }
    }

    @Test
    fun `onAddCombatCondition sends the agony text for a direct agony condition`() {
        // `Condition::createCondition` returns null for CONDITION_AGONY
        // (condition.cpp:403-409), so the branch is reached by a directly
        // built condition.
        val player = newPlayer()
        val messages = mutableListOf<String>()
        player.sendTextMessage = { messages.add(it) }
        val agony = org.opentibia.canary.combat.condition.ConditionGeneric(
            ConditionId.CONDITIONID_DEFAULT,
            ConditionType.CONDITION_AGONY,
            10_000,
            now = { clock },
        )

        assertThat(player.addCombatCondition(agony)).isTrue()

        assertThat(messages).containsExactly("You are in agony.")
    }

    @Test
    fun `onAddCombatCondition records suppressible types but not others`() {
        val player = newPlayer()

        player.addCombatCondition(condition(ConditionType.CONDITION_FREEZING))
        assertThat(player.getLastConditionTime(ConditionType.CONDITION_FREEZING)).isEqualTo(clock)

        player.addCombatCondition(condition(ConditionType.CONDITION_DRUNK))
        assertThat(player.getLastConditionTime(ConditionType.CONDITION_DRUNK)).isZero()
    }

    @Test
    fun `ending the last in-fight condition clears the lock and resets a non red skull`() {
        // player.cpp:6411-6430.
        val player = newPlayer()
        player.pzLocked = true
        player.pzLockOnlyUntil = 0
        player.setSkull(Skulls_t.SKULL_WHITE)
        player.addCondition(condition(ConditionType.CONDITION_INFIGHT))

        player.removeCondition(ConditionType.CONDITION_INFIGHT)

        assertThat(player.isPzLocked()).isFalse()
        assertThat(player.getSkull()).isEqualTo(Skulls_t.SKULL_NONE)
    }

    @Test
    fun `ending in-fight keeps the lock and skull while the lock-only window is open`() {
        // player.cpp:6416-6428.
        val player = newPlayer()
        player.pzLocked = false
        player.pzLockOnlyUntil = clock + 5_000
        player.setSkull(Skulls_t.SKULL_WHITE)
        player.addCondition(condition(ConditionType.CONDITION_INFIGHT))

        player.removeCondition(ConditionType.CONDITION_INFIGHT)

        assertThat(player.isPzLocked()).isTrue()
        assertThat(player.getSkull()).isEqualTo(Skulls_t.SKULL_WHITE)
    }

    @Test
    fun `ending in-fight keeps a red or black skull`() {
        val player = newPlayer()
        player.pzLocked = true
        player.setSkull(Skulls_t.SKULL_RED)
        player.addCondition(condition(ConditionType.CONDITION_INFIGHT))

        player.removeCondition(ConditionType.CONDITION_INFIGHT)

        assertThat(player.isPzLocked()).isFalse()
        assertThat(player.getSkull()).isEqualTo(Skulls_t.SKULL_RED)
    }

    @Test
    fun `ending a non in-fight condition leaves the lock untouched`() {
        val player = newPlayer()
        player.pzLocked = true
        player.addCondition(condition(ConditionType.CONDITION_DRUNK))

        player.removeCondition(ConditionType.CONDITION_DRUNK)

        assertThat(player.isPzLocked()).isTrue()
    }

    @Test
    fun `onIdleStatus clears the party contribution points`() {
        // player.cpp:6528-6533 -> Party::clearPlayerPoints.
        val player = newPlayer()
        val mate = Player(id = 12, name = "Mate", maxHealth = 100, health = 100, position = Position(101, 100, 7))
        val party = org.opentibia.canary.creatures.grouping.Party(leader = player)
        party.invitePlayer(mate)
        party.joinParty(mate)
        party.updatePlayerTicks(player, 1)
        assertThat(party.getMemberSharedExperienceStatus(player))
            .isEqualTo(org.opentibia.canary.creatures.grouping.SharedExpStatus_t.SHAREDEXP_OK)

        player.onIdleStatus()

        assertThat(party.getMemberSharedExperienceStatus(player))
            .isEqualTo(org.opentibia.canary.creatures.grouping.SharedExpStatus_t.SHAREDEXP_MEMBERINACTIVE)
    }
}
