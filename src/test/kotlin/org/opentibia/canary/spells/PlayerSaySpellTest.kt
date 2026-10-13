package org.opentibia.canary.spells

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountType
import org.opentibia.canary.combat.condition.ConditionGeneric
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.network.protocol.MessageClass

/**
 * Transcript of `Spells::playerSaySpell` (src/creatures/combat/spells.cpp:82-162).
 *
 * Covers the account/outside-PZ gate, the feared gate, the word trimming and
 * parameter extraction, the `Find Person` Exiva gate and the mutated `words`
 * echo returned on a successful cast.
 */
class PlayerSaySpellTest {

    private val world = FakeSpellWorld(learnSpells = false, rateSpellCooldown = 1.0)
    private val scripts = FakeSpellScriptRunner()
    private val registry = Spells()
    private val engine = SpellEngine(registry, world, scripts)

    private fun spell(
        words: String = "exura",
        name: String = "Light Healing",
        hasParam: Boolean = false,
        hasPlayerNameParam: Boolean = false,
    ): InstantSpell =
        InstantSpell().apply {
            spellType = SpellType.SPELL_INSTANT
            this.name = name
            this.words = words
            spellId = 1
            group = SpellGroup.SPELLGROUP_HEALING
            aggressive = false
            this.hasParam = hasParam
            this.hasPlayerNameParam = hasPlayerNameParam
        }

    private fun engineWith(spell: InstantSpell): SpellEngine {
        registry.registerInstantLuaEvent(spell)
        return engine
    }

    @Test
    fun `unknown words continue the talk pipeline`() {
        val caster = FakeSpellCaster()

        val result = engine.playerSaySpell(caster, "not a spell")

        assertThat(result.action).isEqualTo(TalkActionResult.TALKACTION_CONTINUE)
        assertThat(scripts.instantCalls).isEmpty()
    }

    @Test
    fun `feared players cannot cast and receive the failure text`() {
        val caster = FakeSpellCaster()
        caster.conditions.add(
            ConditionGeneric(
                ConditionId.CONDITIONID_COMBAT,
                ConditionType.CONDITION_FEARED,
                ticks = 1000,
            ),
        )
        engineWith(spell())

        val result = engine.playerSaySpell(caster, "exura")

        assertThat(result.action).isEqualTo(TalkActionResult.TALKACTION_FAILED)
        assertThat(caster.textMessages).containsExactly(MessageClass.MESSAGE_FAILURE to "You are feared.")
        assertThat(scripts.instantCalls).isEmpty()
    }

    @Test
    fun `successful casts return the spell words with trimmed input`() {
        val caster = FakeSpellCaster()
        engineWith(spell())

        val result = engine.playerSaySpell(caster, "exura   ")

        assertThat(result.action).isEqualTo(TalkActionResult.TALKACTION_BREAK)
        assertThat(result.words).isEqualTo("exura")
        assertThat(scripts.instantCalls).hasSize(1)
    }

    @Test
    fun `refused casts fail without echoing new words`() {
        val caster = FakeSpellCaster()
        engineWith(spell())
        scripts.instantResult = false

        val result = engine.playerSaySpell(caster, "exura")

        assertThat(result.action).isEqualTo(TalkActionResult.TALKACTION_FAILED)
        assertThat(result.words).isEqualTo("exura")
    }

    @Test
    fun `unquoted parameters are extracted and echoed quoted`() {
        val caster = FakeSpellCaster()
        engineWith(spell(words = "exiva", name = "Test Param", hasParam = true))

        val result = engine.playerSaySpell(caster, "exiva thomaz")

        assertThat(result.action).isEqualTo(TalkActionResult.TALKACTION_BREAK)
        assertThat(result.words).isEqualTo("exiva \"thomaz\"")
        assertThat(scripts.instantCalls.single().third.text).isEqualTo("thomaz")
    }

    @Test
    fun `quoted parameters keep their inner text`() {
        val caster = FakeSpellCaster()
        engineWith(spell(words = "exiva", name = "Test Param", hasParam = true))

        val result = engine.playerSaySpell(caster, "exiva \"Thomaz\"")

        assertThat(result.action).isEqualTo(TalkActionResult.TALKACTION_BREAK)
        assertThat(result.words).isEqualTo("exiva \"Thomaz\"")
        assertThat(scripts.instantCalls.single().third.text).isEqualTo("Thomaz")
    }

    @Test
    fun `quoted parameters followed by junk continue the pipeline`() {
        val caster = FakeSpellCaster()
        engineWith(spell(words = "exiva", name = "Test Param", hasParam = true))

        val result = engine.playerSaySpell(caster, "exiva \"Thomaz\" junk")

        assertThat(result.action).isEqualTo(TalkActionResult.TALKACTION_CONTINUE)
        assertThat(scripts.instantCalls).isEmpty()
    }

    @Test
    fun `unquoted parameters with inner spaces continue the pipeline`() {
        val caster = FakeSpellCaster()
        engineWith(spell(words = "exiva", name = "Test Param", hasParam = true))

        val result = engine.playerSaySpell(caster, "exiva thom az")

        assertThat(result.action).isEqualTo(TalkActionResult.TALKACTION_CONTINUE)
        assertThat(scripts.instantCalls).isEmpty()
    }

    @Test
    fun `trailing spaces around the words are trimmed before the cast`() {
        val caster = FakeSpellCaster()
        engineWith(spell(words = "exiva", name = "Test Param", hasParam = true))

        val result = engine.playerSaySpell(caster, "exiva ")

        // Canary trims the whole input first (spells.cpp:110), so this casts.
        assertThat(result.action).isEqualTo(TalkActionResult.TALKACTION_BREAK)
        assertThat(result.words).isEqualTo("exiva")
    }

    @Test
    fun `double spaces before a parameter are trimmed to the parameter`() {
        val caster = FakeSpellCaster()
        engineWith(spell(words = "exiva", name = "Test Param", hasParam = true))

        val result = engine.playerSaySpell(caster, "exiva  thomaz")

        assertThat(result.action).isEqualTo(TalkActionResult.TALKACTION_BREAK)
        assertThat(result.words).isEqualTo("exiva \"thomaz\"")
    }

    @Test
    fun `player name parameters echo the resolved canonical name`() {
        val caster = FakeSpellCaster()
        engineWith(spell(words = "exiva", name = "Test Param", hasParam = true, hasPlayerNameParam = true))
        world.playerByName = { FakeSpellTarget(id = 9, name = "Thomaz") }

        val result = engine.playerSaySpell(caster, "exiva thomaz")

        assertThat(result.action).isEqualTo(TalkActionResult.TALKACTION_BREAK)
        assertThat(result.words).isEqualTo("exiva \"Thomaz\"")
    }

    @Test
    fun `find person is refused when the target rejects exiva`() {
        val caster = FakeSpellCaster()
        caster.exivaAllowed = false
        engineWith(spell(words = "exiva", name = "Find Person", hasParam = true, hasPlayerNameParam = true))

        val result = engine.playerSaySpell(caster, "exiva thomaz")

        assertThat(result.action).isEqualTo(TalkActionResult.TALKACTION_FAILED)
        assertThat(caster.textMessages).containsExactly(
            MessageClass.MESSAGE_TRADE to
                "The character you are trying to find with Exiva is currently protected from your spell.",
        )
        assertThat(scripts.instantCalls).isEmpty()
    }

    @Test
    fun `find person casts normally when the target allows exiva`() {
        val caster = FakeSpellCaster()
        engineWith(spell(words = "exiva", name = "Find Person", hasParam = true, hasPlayerNameParam = true))
        world.playerByName = { FakeSpellTarget(id = 9, name = "Thomaz") }

        val result = engine.playerSaySpell(caster, "exiva thomaz")

        assertThat(result.action).isEqualTo(TalkActionResult.TALKACTION_BREAK)
        assertThat(scripts.instantCalls.single().third.text).isEqualTo("Thomaz")
    }

    @Test
    fun `the per account gate refuses casters with too many characters outside protection zones`() {
        val caster = FakeSpellCaster()
        caster.tileValue = FakeSpellTile(protectionZone = false)
        val account = Account(id = 7, name = "acc")
        caster.accountValue = account

        val other = FakeSpellCaster(id = 2, name = "Other")
        other.tileValue = FakeSpellTile(protectionZone = false)

        world.maxPlayersPerAccount = 2
        world.maxPlayersOutsidePzPerAccount = 1
        world.playersByAccount = { listOf(caster, other) }
        engineWith(spell())

        val result = engine.playerSaySpell(caster, "exura")

        assertThat(result.action).isEqualTo(TalkActionResult.TALKACTION_FAILED)
        assertThat(caster.textMessages).containsExactly(
            MessageClass.MESSAGE_FAILURE to
                "You cannot cast spells while you have 1 character(s) outside of a protection zone.",
        )
        assertThat(scripts.instantCalls).isEmpty()
    }

    @Test
    fun `the per account gate skips game masters`() {
        val caster = FakeSpellCaster()
        caster.tileValue = FakeSpellTile(protectionZone = false)
        caster.accountTypeValue = AccountType.ACCOUNT_TYPE_GAMEMASTER
        caster.accountValue = Account(id = 7, name = "acc")

        val other = FakeSpellCaster(id = 2, name = "Other")
        other.tileValue = FakeSpellTile(protectionZone = false)

        world.maxPlayersPerAccount = 2
        world.maxPlayersOutsidePzPerAccount = 1
        world.playersByAccount = { listOf(caster, other) }
        engineWith(spell())

        assertThat(engine.playerSaySpell(caster, "exura").action).isEqualTo(TalkActionResult.TALKACTION_BREAK)
    }

    @Test
    fun `the per account gate skips casters inside a protection zone`() {
        val caster = FakeSpellCaster()
        caster.tileValue = FakeSpellTile(protectionZone = true)
        caster.accountValue = Account(id = 7, name = "acc")

        val other = FakeSpellCaster(id = 2, name = "Other")
        other.tileValue = FakeSpellTile(protectionZone = false)

        world.maxPlayersPerAccount = 2
        world.maxPlayersOutsidePzPerAccount = 1
        world.playersByAccount = { listOf(caster, other) }
        engineWith(spell())

        assertThat(engine.playerSaySpell(caster, "exura").action).isEqualTo(TalkActionResult.TALKACTION_BREAK)
    }

    @Test
    fun `the per account gate ignores offline and protected characters`() {
        val caster = FakeSpellCaster()
        caster.tileValue = FakeSpellTile(protectionZone = false)
        caster.accountValue = Account(id = 7, name = "acc")

        val offline = FakeSpellCaster(id = 2, name = "Offline")
        offline.offlineValue = true
        offline.tileValue = FakeSpellTile(protectionZone = false)

        val protected = FakeSpellCaster(id = 3, name = "Protected")
        protected.tileValue = FakeSpellTile(protectionZone = true)

        world.maxPlayersPerAccount = 2
        world.maxPlayersOutsidePzPerAccount = 1
        world.playersByAccount = { listOf(caster, offline, protected) }
        engineWith(spell())

        assertThat(engine.playerSaySpell(caster, "exura").action).isEqualTo(TalkActionResult.TALKACTION_BREAK)
    }

    @Test
    fun `the per account gate is inactive when only one character is allowed`() {
        val caster = FakeSpellCaster()
        caster.tileValue = FakeSpellTile(protectionZone = false)
        caster.accountValue = Account(id = 7, name = "acc")

        val other = FakeSpellCaster(id = 2, name = "Other")
        other.tileValue = FakeSpellTile(protectionZone = false)

        world.maxPlayersPerAccount = 1
        world.maxPlayersOutsidePzPerAccount = 1
        world.playersByAccount = { listOf(caster, other) }
        engineWith(spell())

        assertThat(engine.playerSaySpell(caster, "exura").action).isEqualTo(TalkActionResult.TALKACTION_BREAK)
    }
}
