package org.opentibia.canary.spells

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.PlayerFlags_t

class SpellBookTest {

    // `Player::learnInstantSpell` (player.cpp:3122-3126).
    @Test
    fun `learnInstantSpell appends a spell once`() {
        val book = SpellBook()

        book.learnInstantSpell("Light Healing")
        book.learnInstantSpell("Light Healing")

        assertThat(book.learnedInstantSpellList).containsExactly("Light Healing")
    }

    @Test
    fun `learnInstantSpell ignores a differently cased duplicate`() {
        val book = SpellBook()

        book.learnInstantSpell("Light Healing")
        book.learnInstantSpell("light healing")

        assertThat(book.learnedInstantSpellList).containsExactly("Light Healing")
    }

    // `Player::forgetInstantSpell` (player.cpp:3128-3130): `std::erase` is exact.
    @Test
    fun `forgetInstantSpell erases the exact stored name`() {
        val book = SpellBook()
        book.learnInstantSpell("Light Healing")
        book.learnInstantSpell("Find Person")

        book.forgetInstantSpell("Light Healing")

        assertThat(book.learnedInstantSpellList).containsExactly("Find Person")
    }

    @Test
    fun `forgetInstantSpell keeps entries with different casing`() {
        val book = SpellBook()
        book.learnInstantSpell("Light Healing")

        book.forgetInstantSpell("light healing")

        assertThat(book.learnedInstantSpellList).containsExactly("Light Healing")
    }

    // `Player::hasLearnedInstantSpell` (player.cpp:3132-3145).
    @Test
    fun `hasLearnedInstantSpell compares names case insensitively`() {
        val book = SpellBook()
        book.learnInstantSpell("Light Healing")

        assertThat(book.hasLearned("LIGHT HEALING")).isTrue()
        assertThat(book.hasLearned("Light Healing")).isTrue()
        assertThat(book.hasLearned("Ultimate Healing")).isFalse()
    }

    @Test
    fun `engine treats the ignore spell check flag as always learned`() {
        val engine = SpellEngine(Spells(), FakeSpellWorld(), FakeSpellScriptRunner())
        val caster = FakeSpellCaster()
        caster.setFlag(PlayerFlags_t.IgnoreSpellCheck)

        assertThat(engine.hasLearnedInstantSpell(caster, "Light Healing")).isTrue()
    }

    @Test
    fun `engine treats cannot use spells as never learned`() {
        val engine = SpellEngine(Spells(), FakeSpellWorld(), FakeSpellScriptRunner())
        val caster = FakeSpellCaster()
        caster.setFlag(PlayerFlags_t.CannotUseSpells)
        caster.book.learnInstantSpell("Light Healing")

        assertThat(engine.hasLearnedInstantSpell(caster, "Light Healing")).isFalse()
    }

    @Test
    fun `engine learn and forget route through the caster spell book`() {
        val engine = SpellEngine(Spells(), FakeSpellWorld(), FakeSpellScriptRunner())
        val caster = FakeSpellCaster()

        engine.learnInstantSpell(caster, "Light Healing")
        assertThat(caster.book.learnedInstantSpellList).containsExactly("Light Healing")

        engine.forgetInstantSpell(caster, "Light Healing")
        assertThat(caster.book.learnedInstantSpellList).isEmpty()
    }
}
