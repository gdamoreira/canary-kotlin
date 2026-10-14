package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionSpellCooldown
import org.opentibia.canary.combat.condition.ConditionSpellGroupCooldown
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.map.Position
import org.opentibia.canary.spells.InstantSpell
import org.opentibia.canary.spells.Spells

/**
 * Transcript tests for the `Player` learned-spell state and the spell/group
 * cooldown surface.
 *
 * Source of truth: ../.projects/canary
 *  - src/creatures/players/player.cpp:3122-3145 (`learnInstantSpell`,
 *    `forgetInstantSpell`, `hasLearnedInstantSpell`)
 *  - src/creatures/players/player.cpp:8997-9007 (`sendSpellCooldown`,
 *    `sendSpellGroupCooldown`)
 *  - src/creatures/players/player.cpp:13405-13437 (`sendSpellCooldowns`)
 *  - src/creatures/combat/spells.cpp:528 (cooldown checks through
 *    `hasCondition(type, subId)`)
 */
class PlayerSpellbookTest {

    private class RecordingCooldownClient : SpellCooldownClient {
        val spellCooldowns = mutableListOf<Pair<Int, Int>>()
        val groupCooldowns = mutableListOf<Pair<SpellGroup, Int>>()

        override fun sendSpellCooldown(spellId: Int, time: Int) {
            spellCooldowns.add(spellId to time)
        }

        override fun sendSpellGroupCooldown(groupId: SpellGroup, time: Int) {
            groupCooldowns.add(groupId to time)
        }
    }

    private var clock = 2_000_000L

    private fun newPlayer() = Player(
        id = 21,
        name = "Spellbook Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    ).apply { now = { clock } }

    private fun registry(vararg spellIds: Int): Spells {
        val spells = Spells()
        for (spellId in spellIds) {
            spells.registerInstantLuaEvent(
                InstantSpell().apply {
                    this.spellId = spellId
                    name = "Spell $spellId"
                    words = "spell $spellId"
                },
            )
        }
        return spells
    }

    private fun spellCooldown(spellId: Int, ticks: Int) = ConditionSpellCooldown(
        ConditionId.CONDITIONID_DEFAULT,
        ConditionType.CONDITION_SPELLCOOLDOWN,
        ticks,
        subId = spellId,
        now = { clock },
    )

    private fun groupCooldown(group: SpellGroup, ticks: Int) = ConditionSpellGroupCooldown(
        ConditionId.CONDITIONID_DEFAULT,
        ConditionType.CONDITION_SPELLGROUPCOOLDOWN,
        ticks,
        subId = group.value,
        now = { clock },
    )

    // --- learned spells --------------------------------------------------------------

    @Test
    fun `learnInstantSpell appends a spell once`() {
        val player = newPlayer()

        player.learnInstantSpell("Light Healing")
        player.learnInstantSpell("Light Healing")

        assertThat(player.learnedInstantSpellList).containsExactly("Light Healing")
    }

    @Test
    fun `hasLearnedInstantSpell compares case insensitively`() {
        // player.cpp:3141-3143 uses strcasecmp.
        val player = newPlayer()
        player.learnInstantSpell("Light Healing")

        assertThat(player.hasLearnedInstantSpell("light healing")).isTrue()
        assertThat(player.hasLearnedInstantSpell("LIGHT HEALING")).isTrue()
        assertThat(player.hasLearnedInstantSpell("Great Light Healing")).isFalse()
    }

    @Test
    fun `forgetInstantSpell erases every matching entry`() {
        val player = newPlayer()
        player.learnInstantSpell("Light Healing")

        player.forgetInstantSpell("Light Healing")

        assertThat(player.learnedInstantSpellList).isEmpty()
        assertThat(player.hasLearnedInstantSpell("Light Healing")).isFalse()
    }

    @Test
    fun `CannotUseSpells makes every instant spell unlearned`() {
        val player = newPlayer()
        player.learnInstantSpell("Light Healing")
        player.setFlag(PlayerFlags_t.CannotUseSpells)

        assertThat(player.hasLearnedInstantSpell("Light Healing")).isFalse()
    }

    @Test
    fun `IgnoreSpellCheck makes every instant spell learned`() {
        val player = newPlayer()
        player.setFlag(PlayerFlags_t.IgnoreSpellCheck)

        assertThat(player.hasLearnedInstantSpell("Never Learned")).isTrue()
    }

    // --- cooldown tracking -----------------------------------------------------------

    @Test
    fun `forgetInstantSpell is case sensitive unlike the membership check`() {
        // player.cpp:3128-3130 uses std::erase (exact match), while
        // hasLearnedInstantSpell compares with strcasecmp.
        val player = newPlayer()
        player.learnInstantSpell("Light Healing")
        player.forgetInstantSpell("LIGHT HEALING")

        assertThat(player.learnedInstantSpellList).containsExactly("Light Healing")
        assertThat(player.hasLearnedInstantSpell("Light Healing")).isTrue()
    }

    @Test
    fun `cooldown conditions are keyed by type and subId`() {
        // spells.cpp:528: CONDITION_SPELLGROUPCOOLDOWN by group,
        // CONDITION_SPELLCOOLDOWN by spell id.
        val player = newPlayer()
        player.addCondition(spellCooldown(spellId = 42, ticks = 2_000))
        player.addCondition(groupCooldown(SpellGroup.SPELLGROUP_SUPPORT, 1_500))

        assertThat(player.hasCondition(ConditionType.CONDITION_SPELLCOOLDOWN, 42)).isTrue()
        assertThat(player.hasCondition(ConditionType.CONDITION_SPELLCOOLDOWN, 43)).isFalse()
        assertThat(
            player.hasCondition(ConditionType.CONDITION_SPELLGROUPCOOLDOWN, SpellGroup.SPELLGROUP_SUPPORT.value),
        ).isTrue()
        assertThat(
            player.hasCondition(ConditionType.CONDITION_SPELLGROUPCOOLDOWN, SpellGroup.SPELLGROUP_ATTACK.value),
        ).isFalse()
    }

    @Test
    fun `an expired cooldown condition stops blocking`() {
        val player = newPlayer()
        player.addCondition(spellCooldown(spellId = 42, ticks = 2_000))

        clock += 3_000

        assertThat(player.hasCondition(ConditionType.CONDITION_SPELLCOOLDOWN, 42)).isFalse()
    }

    @Test
    fun `sendSpellCooldowns resends live spell and group cooldowns`() {
        val player = newPlayer()
        player.spells = registry(42)
        val client = RecordingCooldownClient()
        player.spellCooldownClient = client
        player.addCondition(spellCooldown(spellId = 42, ticks = 2_000))
        player.addCondition(groupCooldown(SpellGroup.SPELLGROUP_SUPPORT, 1_500))

        player.sendSpellCooldowns()

        assertThat(client.spellCooldowns).containsExactly(42 to 2_000)
        assertThat(client.groupCooldowns).containsExactly(SpellGroup.SPELLGROUP_SUPPORT to 1_500)
    }

    @Test
    fun `sendSpellCooldowns skips unknown spell ids zero ticks and other conditions`() {
        val player = newPlayer()
        player.spells = registry(42)
        val client = RecordingCooldownClient()
        player.spellCooldownClient = client
        player.addCondition(spellCooldown(spellId = 99, ticks = 2_000))
        player.addCondition(spellCooldown(spellId = 7, ticks = 0))
        player.addCondition(
            org.opentibia.canary.combat.condition.ConditionGeneric(
                ConditionId.CONDITIONID_DEFAULT,
                ConditionType.CONDITION_INFIGHT,
                2_000,
                now = { clock },
            ),
        )

        player.sendSpellCooldowns()

        assertThat(client.spellCooldowns).isEmpty()
        assertThat(client.groupCooldowns).isEmpty()
    }

    @Test
    fun `sendSpellCooldowns without a client or registry is a no-op`() {
        val player = newPlayer()
        player.spells = registry(42)
        player.addCondition(spellCooldown(spellId = 42, ticks = 2_000))

        player.sendSpellCooldowns()
    }

    @Test
    fun `truncated subIds resolve to zero exactly like the uint16 cast`() {
        // player.cpp:13431-13436: subId > UINT16_MAX is sent as spell id 0,
        // which the registry does not know.
        val player = newPlayer()
        player.spells = registry(0)
        val client = RecordingCooldownClient()
        player.spellCooldownClient = client
        player.addCondition(spellCooldown(spellId = 0x1_0000, ticks = 2_000))

        player.sendSpellCooldowns()

        assertThat(client.spellCooldowns).containsExactly(0 to 2_000)
    }
}
