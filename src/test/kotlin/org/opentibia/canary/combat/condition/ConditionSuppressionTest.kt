package org.opentibia.canary.combat.condition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * `Player::isSuppress` and the condition-suppression state it owns
 * (`src/creatures/players/player.cpp:356-392, 1143-1160`,
 * `player.hpp:687-688, 831, 1724, 1784`):
 *
 * - `IsConditionSuppressible` (`creatures_definitions.hpp:160-175`) plus the
 *   `MIN_DELAY_BETWEEN_CONDITIONS` re-application window.
 * - `m_conditionSuppressionCount` incremented by item abilities through
 *   `addConditionSuppressions` and decremented by
 *   `removeConditionSuppressions`.
 * - `lastConditionTime` updated by `Player::onAddCombatCondition`
 *   (player.cpp:6345-6349) for suppressible types.
 */
class ConditionSuppressionTest {

    private var clock = 5000L
    private val now: () -> Long = { clock }

    private fun suppression(
        minDelay: Int = 0,
        sendIcons: () -> Unit = {},
    ) = ConditionSuppression(minDelay, now, sendIcons)

    @Test
    fun `suppressible set mirrors IsConditionSuppressible`() {
        val suppressible = setOf(
            ConditionType.CONDITION_POISON,
            ConditionType.CONDITION_FIRE,
            ConditionType.CONDITION_ENERGY,
            ConditionType.CONDITION_BLEEDING,
            ConditionType.CONDITION_PARALYZE,
            ConditionType.CONDITION_DROWN,
            ConditionType.CONDITION_FREEZING,
            ConditionType.CONDITION_CURSED,
        )

        for (type in ConditionType.entries) {
            assertThat(type.isSuppressible).`as`("suppressible %s", type).isEqualTo(type in suppressible)
        }
    }

    @Test
    fun `suppression count blocks and releases the condition`() {
        val suppression = suppression()

        assertThat(suppression.isSuppress(ConditionType.CONDITION_POISON, false)).isFalse()

        suppression.addConditionSuppressions(arrayOf(ConditionType.CONDITION_POISON))
        assertThat(suppression.isSuppress(ConditionType.CONDITION_POISON, false)).isTrue()

        suppression.removeConditionSuppressions(listOf(ConditionType.CONDITION_POISON))
        assertThat(suppression.isSuppress(ConditionType.CONDITION_POISON, false)).isFalse()
    }

    @Test
    fun `multiple suppressions stack and removals floor at zero`() {
        val suppression = suppression()

        repeat(3) { suppression.addConditionSuppressions(arrayOf(ConditionType.CONDITION_FIRE)) }
        suppression.removeConditionSuppressions(listOf(ConditionType.CONDITION_FIRE))
        assertThat(suppression.isSuppress(ConditionType.CONDITION_FIRE, false)).isTrue()

        suppression.removeConditionSuppressions(listOf(ConditionType.CONDITION_FIRE))
        suppression.removeConditionSuppressions(listOf(ConditionType.CONDITION_FIRE))
        suppression.removeConditionSuppressions(listOf(ConditionType.CONDITION_FIRE))
        assertThat(suppression.isSuppress(ConditionType.CONDITION_FIRE, false)).isFalse()
    }

    @Test
    fun `count is capped at uint8 max`() {
        // player.cpp:372-374 caps at UINT8_MAX.
        val suppression = suppression()

        repeat(300) { suppression.addConditionSuppressions(arrayOf(ConditionType.CONDITION_ENERGY)) }
        repeat(255) { suppression.removeConditionSuppressions(listOf(ConditionType.CONDITION_ENERGY)) }

        assertThat(suppression.isSuppress(ConditionType.CONDITION_ENERGY, false)).isFalse()
    }

    @Test
    fun `removal only sends icons when the count reaches zero`() {
        var iconsSent = 0
        val suppression = suppression(sendIcons = { iconsSent++ })

        suppression.addConditionSuppressions(arrayOf(ConditionType.CONDITION_BLEEDING))
        suppression.removeConditionSuppressions(listOf(ConditionType.CONDITION_BLEEDING))
        suppression.removeConditionSuppressions(listOf(ConditionType.CONDITION_BLEEDING))

        assertThat(iconsSent).isEqualTo(1)
    }

    @Test
    fun `suppressible types respect the min delay window`() {
        // player.cpp:357-360 + 1154-1160.
        val suppression = suppression(minDelay = 1000)

        suppression.updateLastConditionTime(ConditionType.CONDITION_PARALYZE)
        assertThat(suppression.getLastConditionTime(ConditionType.CONDITION_PARALYZE)).isEqualTo(5000L)

        clock = 5500
        assertThat(suppression.checkLastConditionTimeWithin(ConditionType.CONDITION_PARALYZE, 1000)).isTrue()
        assertThat(suppression.isSuppress(ConditionType.CONDITION_PARALYZE, true)).isTrue()

        clock = 6001
        assertThat(suppression.checkLastConditionTimeWithin(ConditionType.CONDITION_PARALYZE, 1000)).isFalse()
        assertThat(suppression.isSuppress(ConditionType.CONDITION_PARALYZE, true)).isFalse()
    }

    @Test
    fun `non-suppressible types track time without being suppressed`() {
        val suppression = suppression(minDelay = 10000)

        suppression.updateLastConditionTime(ConditionType.CONDITION_DRUNK)

        assertThat(suppression.checkLastConditionTimeWithin(ConditionType.CONDITION_DRUNK, 10000)).isTrue()
        assertThat(suppression.isSuppress(ConditionType.CONDITION_DRUNK, false)).isFalse()

        suppression.addConditionSuppressions(arrayOf(ConditionType.CONDITION_DRUNK))
        assertThat(suppression.isSuppress(ConditionType.CONDITION_DRUNK, false)).isTrue()
    }

    @Test
    fun `unknown last condition time is zero`() {
        val suppression = suppression()

        assertThat(suppression.getLastConditionTime(ConditionType.CONDITION_CURSED)).isZero()
        assertThat(suppression.checkLastConditionTimeWithin(ConditionType.CONDITION_CURSED, 1000)).isFalse()
    }

    @Test
    fun `condition none is ignored when removing`() {
        var iconsSent = 0
        val suppression = suppression(sendIcons = { iconsSent++ })

        suppression.removeConditionSuppressions(listOf(ConditionType.CONDITION_NONE))

        assertThat(iconsSent).isZero()
    }

    @Test
    fun `out of range condition ids are ignored when adding`() {
        val suppression = suppression()

        suppression.addConditionSuppressions(arrayOf(ConditionType.CONDITION_COUNT))

        for (type in ConditionType.entries) {
            assertThat(suppression.isSuppress(type, false)).`as`("suppressed %s", type).isFalse()
        }
    }
}
