package org.opentibia.canary.spells

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

class SpellSystemTest {

    @Test
    fun `casts instant spell consuming mana and restoring health`() {
        val registry = SpellRegistry()
        val lightHealing = Spell(
            words = "exura",
            name = "Light Healing",
            manaCost = 20,
            levelRequired = 9
        ) { caster ->
            caster.addHealth(30)
            true
        }
        registry.register(lightHealing)

        val player = Player(
            id = 1,
            name = "Healer",
            maxHealth = 100,
            health = 50,
            maxMana = 100,
            mana = 50,
            level = 10,
            position = Position(100, 100, 7)
        )

        // Successful cast
        val success = registry.castSpell(player, "exura")
        assertThat(success).isTrue()
        assertThat(player.mana).isEqualTo(30) // 50 - 20
        assertThat(player.health).isEqualTo(80) // 50 + 30
    }

    @Test
    fun `rejects spell when caster mana is insufficient`() {
        val registry = SpellRegistry()
        val ultimateHealing = Spell(
            words = "exura vita",
            name = "Ultimate Healing",
            manaCost = 160,
            levelRequired = 20
        ) { caster ->
            caster.addHealth(250)
            true
        }
        registry.register(ultimateHealing)

        val player = Player(
            id = 1,
            name = "Mage",
            maxHealth = 100,
            health = 50,
            maxMana = 100,
            mana = 50, // needs 160
            level = 25,
            position = Position(100, 100, 7)
        )

        val success = registry.castSpell(player, "exura vita")
        assertThat(success).isFalse()
        assertThat(player.mana).isEqualTo(50) // unchanged
        assertThat(player.health).isEqualTo(50) // unchanged
    }

    @Test
    fun `rejects spell when caster level is lower than required`() {
        val registry = SpellRegistry()
        val fireWave = Spell(
            words = "exevo flam hur",
            name = "Fire Wave",
            manaCost = 25,
            levelRequired = 18
        ) { true }
        registry.register(fireWave)

        val lowLevelPlayer = Player(
            id = 1,
            name = "Novice",
            maxHealth = 100,
            health = 100,
            maxMana = 100,
            mana = 100,
            level = 12, // requires 18
            position = Position(100, 100, 7)
        )

        val success = registry.castSpell(lowLevelPlayer, "exevo flam hur")
        assertThat(success).isFalse()
    }
}
