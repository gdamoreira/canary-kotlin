package org.opentibia.canary.spells

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpellRegistryTest {

    private fun instant(words: String, name: String, id: Int = 0, hasParam: Boolean = false): InstantSpell =
        InstantSpell().apply {
            this.words = words
            this.name = name
            this.spellId = id
            this.hasParam = hasParam
        }

    private fun rune(name: String, runeId: Int): RuneSpell =
        RuneSpell().apply {
            this.name = name
            this.runeId = runeId
        }

    // `Spells::registerInstantLuaEvent` (spells.cpp:181-204).
    @Test
    fun `registerInstantLuaEvent stores the spell by words`() {
        val spells = Spells()
        val spell = instant("exura", "Light Healing", 1)

        assertThat(spells.registerInstantLuaEvent(spell)).isTrue()
        assertThat(spells.hasInstantSpell("exura")).isTrue()
        assertThat(spells.getInstantSpell("exura")).isSameAs(spell)
    }

    @Test
    fun `registerInstantLuaEvent rejects spells without words`() {
        val spells = Spells()

        assertThat(spells.registerInstantLuaEvent(instant("", "Nameless"))).isFalse()
        assertThat(spells.getInstantSpells()).isEmpty()
    }

    @Test
    fun `registerInstantLuaEvent rejects duplicate words case sensitively`() {
        val spells = Spells()
        assertThat(spells.registerInstantLuaEvent(instant("exura", "Light Healing"))).isTrue()

        // `hasInstantSpell` is an exact `std::map` lookup (spells.cpp:169-175).
        assertThat(spells.registerInstantLuaEvent(instant("exura", "Other Spell"))).isFalse()
        // Different case is a different map key in Canary.
        assertThat(spells.registerInstantLuaEvent(instant("Exura", "Cased Spell"))).isTrue()
    }

    @Test
    fun `setInstantSpell does not overwrite an existing word`() {
        val spells = Spells()
        val first = instant("exura", "First")
        val second = instant("exura", "Second")

        spells.setInstantSpell("exura", first)
        spells.setInstantSpell("exura", second)

        // `std::map::try_emplace` (spells.cpp:177-179).
        assertThat(spells.getInstantSpellByName("First")).isSameAs(first)
        assertThat(spells.getInstantSpellByName("Second")).isNull()
    }

    // `Spells::getInstantSpell` (spells.cpp:285-317).
    @Test
    fun `getInstantSpell matches words case insensitively`() {
        val spells = Spells()
        val spell = instant("exura", "Light Healing")
        spells.registerInstantLuaEvent(spell)

        assertThat(spells.getInstantSpell("EXURA")).isSameAs(spell)
    }

    @Test
    fun `getInstantSpell prefers the longest prefix match`() {
        val spells = Spells()
        val short = instant("exura", "Light Healing")
        val long = instant("exura gran", "Intense Healing")
        spells.registerInstantLuaEvent(short)
        spells.registerInstantLuaEvent(long)

        assertThat(spells.getInstantSpell("exura gran")).isSameAs(long)
        assertThat(spells.getInstantSpell("exura")).isSameAs(short)
    }

    @Test
    fun `getInstantSpell rejects extra text when the spell takes no param`() {
        val spells = Spells()
        spells.registerInstantLuaEvent(instant("exura", "Light Healing"))

        // paramLen < 2 or the separator is not a space (spells.cpp:301-313).
        assertThat(spells.getInstantSpell("exurazz")).isNull()
        assertThat(spells.getInstantSpell("exura x")).isNull()
    }

    @Test
    fun `getInstantSpell accepts extra text when the spell takes a param`() {
        val spells = Spells()
        val spell = instant("exiva", "Find Person", hasParam = true)
        spells.registerInstantLuaEvent(spell)

        assertThat(spells.getInstantSpell("exiva thomaz")).isSameAs(spell)
        assertThat(spells.getInstantSpell("exiva ab")).isSameAs(spell)
        // A single separating space plus one parameter character is the minimum.
        assertThat(spells.getInstantSpell("exiva a")).isSameAs(spell)
        // No separator: `paramLen == 1` fails the `paramLen < 2` guard.
        assertThat(spells.getInstantSpell("exivaa")).isNull()
    }

    // `Spells::getInstantSpellByName` / `getInstantSpellById` (spells.cpp:319-335).
    @Test
    fun `instant spell lookup by name is case insensitive and by id is exact`() {
        val spells = Spells()
        val spell = instant("exura", "Light Healing", 10)
        spells.registerInstantLuaEvent(spell)

        assertThat(spells.getInstantSpellByName("light healing")).isSameAs(spell)
        assertThat(spells.getInstantSpellByName("Healing")).isNull()
        assertThat(spells.getInstantSpellById(10)).isSameAs(spell)
        assertThat(spells.getInstantSpellById(11)).isNull()
    }

    // `Spells::registerRuneLuaEvent` / `getRuneSpell` (spells.cpp:206-222, 263-283).
    @Test
    fun `registerRuneLuaEvent stores runes by item id and rejects duplicates`() {
        val spells = Spells()
        val rune = rune("Light Magic Missile Rune", 3174)

        assertThat(spells.registerRuneLuaEvent(rune)).isTrue()
        assertThat(spells.getRuneSpell(3174)).isSameAs(rune)
        assertThat(spells.getRuneSpellByName("light magic missile rune")).isSameAs(rune)
        assertThat(spells.getRuneSpell(9999)).isNull()
        assertThat(spells.registerRuneLuaEvent(rune("Duplicate", 3174))).isFalse()
    }

    // `Spells::getSpellByName` (spells.cpp:255-261).
    @Test
    fun `getSpellByName searches runes before instants`() {
        val spells = Spells()
        val instant = instant("exura", "Shared Name")
        val rune = rune("Shared Name", 3174)
        spells.registerInstantLuaEvent(instant)
        spells.registerRuneLuaEvent(rune)

        assertThat(spells.getSpellByName("shared name")).isSameAs(rune)
        assertThat(spells.getSpellByName("missing")).isNull()
    }

    // `Spells::getSpellsByVocation` (spells.cpp:229-245): `std::map` order.
    @Test
    fun `getSpellsByVocation returns enabled spell ids in word order`() {
        val spells = Spells()
        val exura = instant("exura", "Light Healing", 1)
        exura.addVocMap(2, true)
        val exevo = instant("exevo pan", "Food", 2)
        exevo.addVocMap(2, false)
        val adori = instant("adori min vis", "Light Magic Missile Rune", 3)
        adori.addVocMap(2, true)
        spells.registerInstantLuaEvent(exura)
        spells.registerInstantLuaEvent(exevo)
        spells.registerInstantLuaEvent(adori)

        assertThat(spells.getSpellsByVocation(2)).containsExactly(3, 1)
        assertThat(spells.getSpellsByVocation(1)).isEmpty()
    }

    // `Spells::isMonkShrineSpell` (spells.cpp:224-227).
    @Test
    fun `isMonkShrineSpell recognises the shrine exclusives`() {
        val spells = Spells()

        assertThat(spells.isMonkShrineSpell("Mystic Repulse")).isTrue()
        assertThat(spells.isMonkShrineSpell("Forceful Uppercut")).isTrue()
        assertThat(spells.isMonkShrineSpell("Focus Harmony")).isTrue()
        assertThat(spells.isMonkShrineSpell("Exura")).isFalse()
    }

    // `Spells::clear` (spells.cpp:164-167).
    @Test
    fun `clear drops instants and runes`() {
        val spells = Spells()
        spells.registerInstantLuaEvent(instant("exura", "Light Healing"))
        spells.registerRuneLuaEvent(rune("Light Magic Missile Rune", 3174))

        spells.clear()

        assertThat(spells.getInstantSpells()).isEmpty()
        assertThat(spells.getRuneSpell(3174)).isNull()
    }

    // `Spells::getCasterPosition` (spells.cpp:337-339).
    @Test
    fun `getCasterPosition offsets the caster in the given direction`() {
        val caster = FakeSpellCaster(position = org.opentibia.canary.map.Position(100, 100, 7))

        assertThat(Spells.getCasterPosition(caster, org.opentibia.canary.game.movement.Direction.East))
            .isEqualTo(org.opentibia.canary.map.Position(101, 100, 7))
        assertThat(Spells.getCasterPosition(caster, org.opentibia.canary.game.movement.Direction.North))
            .isEqualTo(org.opentibia.canary.map.Position(100, 99, 7))
    }
}
