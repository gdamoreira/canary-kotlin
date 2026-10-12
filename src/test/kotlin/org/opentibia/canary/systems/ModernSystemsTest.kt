package org.opentibia.canary.systems

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ModernSystemsTest {

    @Test
    fun `manages imbuement slot on equippable item`() {
        val imbuementSlot = ImbuementSlot(type = ImbuementType.CriticalHit, tier = 3, durationSeconds = 72000)

        assertThat(imbuementSlot.type).isEqualTo(ImbuementType.CriticalHit)
        assertThat(imbuementSlot.tier).isEqualTo(3)
        assertThat(imbuementSlot.isActive).isTrue()

        imbuementSlot.decay(72000)
        assertThat(imbuementSlot.durationSeconds).isEqualTo(0)
        assertThat(imbuementSlot.isActive).isFalse()
    }

    @Test
    fun `prey system tracks active monster and bonus multipliers`() {
        val preySlot = PreySlot(
            slotId = 0,
            monsterName = "Dragon Lord",
            bonusType = PreyBonusType.DamageBoost,
            bonusPercentage = 25,
            durationMinutes = 120
        )

        assertThat(preySlot.monsterName).isEqualTo("Dragon Lord")
        assertThat(preySlot.bonusType).isEqualTo(PreyBonusType.DamageBoost)
        assertThat(preySlot.bonusPercentage).isEqualTo(25)
        assertThat(preySlot.isActive).isTrue()
    }
}
