package org.opentibia.canary.systems.concoction

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConcoctionTest {

    @Test
    fun `concoction item ids match canary`() {
        assertThat(ConcoctionType.KooldownAid.itemId).isEqualTo(36723)
        assertThat(ConcoctionType.StaminaExtension.itemId).isEqualTo(36725)
        assertThat(ConcoctionType.StrikeEnhancement.itemId).isEqualTo(36724)
        assertThat(ConcoctionType.PhysicalAmplification.itemId).isEqualTo(36742)
    }

    @Test
    fun `update concoction stores and erases the timer`() {
        val concoctions = PlayerConcoctions()

        concoctions.updateConcoction(ConcoctionType.FireResilience.itemId, 3600)
        assertThat(concoctions.getActiveConcoctions())
            .containsEntry(ConcoctionType.FireResilience.itemId, 3600)
        assertThat(concoctions.isConcoctionActive(ConcoctionType.FireResilience)).isTrue()

        concoctions.updateConcoction(ConcoctionType.FireResilience.itemId, 0)
        assertThat(concoctions.getActiveConcoctions()).doesNotContainKey(ConcoctionType.FireResilience.itemId)
        assertThat(concoctions.isConcoctionActive(ConcoctionType.FireResilience)).isFalse()
    }

    @Test
    fun `is concoction active is false for unknown concoctions`() {
        val concoctions = PlayerConcoctions()
        assertThat(concoctions.isConcoctionActive(ConcoctionType.HolyResilience)).isFalse()
    }
}
