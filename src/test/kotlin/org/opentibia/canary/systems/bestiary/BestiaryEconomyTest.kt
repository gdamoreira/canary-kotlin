package org.opentibia.canary.systems.bestiary

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Deterministic charm-point economy parity tests.
 *
 * The arithmetic is extracted from `IOBestiary::sendBuyCharmRune`
 * (src/io/iobestiary.cpp:459-574): the per-tier point cost, the tier cap and
 * the minor-charm-echo reward `25*t*t + 25*t + 50` (iobestiary.cpp:479).
 */
class BestiaryEconomyTest {

    private val bestiary = IOBestiary()

    private val wound = Charm(
        name = "Wound",
        id = CharmRune.CHARM_WOUND,
        category = CharmCategory.CHARM_MAJOR,
        points = listOf(240, 360, 1200),
    )

    @Test
    fun `minor charm echo reward follows 25t squared plus 25t plus 50`() {
        assertThat(bestiary.getMinorCharmEchoesReward(0)).isEqualTo(50)
        assertThat(bestiary.getMinorCharmEchoesReward(1)).isEqualTo(100)
        assertThat(bestiary.getMinorCharmEchoesReward(2)).isEqualTo(200)
    }

    @Test
    fun `charm buy cost returns the per-tier point threshold`() {
        assertThat(bestiary.getCharmBuyCost(wound, 0)).isEqualTo(240)
        assertThat(bestiary.getCharmBuyCost(wound, 1)).isEqualTo(360)
        assertThat(bestiary.getCharmBuyCost(wound, 2)).isEqualTo(1200)
    }

    @Test
    fun `charm tier cap is two`() {
        assertThat(bestiary.isCharmTierMax(0)).isFalse()
        assertThat(bestiary.isCharmTierMax(2)).isFalse()
        assertThat(bestiary.isCharmTierMax(3)).isTrue()
    }
}
