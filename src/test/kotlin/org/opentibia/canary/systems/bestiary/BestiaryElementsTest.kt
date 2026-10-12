package org.opentibia.canary.systems.bestiary

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test

/**
 * Parity tests for `IOBestiary::getMonsterElements` (src/io/iobestiary.cpp:576-612).
 *
 * Element maps mirror the real Canary monster data:
 *  - Dragon: data-otservbr-global/monster/dragons/dragon.lua:127-138
 *  - Demon:  data-otservbr-global/monster/demons/demon.lua
 * Keys are `CombatType_t` values (src/creatures/creatures_definitions.hpp:833-853).
 */
class BestiaryElementsTest {

    private val bestiary = IOBestiary()

    private val dragon = MonsterBestiaryInfo(
        raceid = 34,
        name = "Dragon",
        elementMap = mapOf(
            0 to 0, // COMBAT_PHYSICALDAMAGE
            1 to 100, // COMBAT_FIREDAMAGE
            2 to 80, // COMBAT_EARTHDAMAGE
            3 to 20, // COMBAT_ENERGYDAMAGE
            5 to 0, // COMBAT_LIFEDRAIN (ignored)
            6 to 0, // COMBAT_MANADRAIN (ignored)
            8 to 0, // COMBAT_DROWNDAMAGE (ignored)
            9 to -10, // COMBAT_ICEDAMAGE
            10 to 0, // COMBAT_HOLYDAMAGE
            11 to 0, // COMBAT_DEATHDAMAGE
        ),
    )

    private val demon = MonsterBestiaryInfo(
        raceid = 35,
        name = "Demon",
        elementMap = mapOf(
            0 to 25, // COMBAT_PHYSICALDAMAGE
            1 to 100, // COMBAT_FIREDAMAGE
            2 to 40, // COMBAT_EARTHDAMAGE
            3 to 50, // COMBAT_ENERGYDAMAGE
            5 to 100, // COMBAT_LIFEDRAIN (ignored)
            6 to 0, // COMBAT_MANADRAIN (ignored)
            8 to 100, // COMBAT_DROWNDAMAGE (ignored)
            9 to -12, // COMBAT_ICEDAMAGE
            10 to -12, // COMBAT_HOLYDAMAGE
            11 to 20, // COMBAT_DEATHDAMAGE
        ),
    )

    @Test
    fun `dragon elements subtract from the 100 baseline in protocol order`() {
        assertThat(bestiary.getMonsterElements(dragon)).containsExactly(
            entry(0, 100),
            entry(1, 0),
            entry(2, 20),
            entry(3, 80),
            entry(4, 110),
            entry(5, 100),
            entry(6, 100),
            entry(7, 100),
        )
    }

    @Test
    fun `demon elements subtract from the 100 baseline in protocol order`() {
        assertThat(bestiary.getMonsterElements(demon)).containsExactly(
            entry(0, 75),
            entry(1, 0),
            entry(2, 60),
            entry(3, 50),
            entry(4, 112),
            entry(5, 112),
            entry(6, 80),
            entry(7, 100),
        )
    }

    @Test
    fun `an empty element map leaves every slot at 100`() {
        val plain = MonsterBestiaryInfo(raceid = 21, name = "Rat")

        assertThat(bestiary.getMonsterElements(plain)).containsExactly(
            entry(0, 100),
            entry(1, 100),
            entry(2, 100),
            entry(3, 100),
            entry(4, 100),
            entry(5, 100),
            entry(6, 100),
            entry(7, 100),
        )
    }
}
