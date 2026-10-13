package org.opentibia.canary.lua.bindings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.lua.registry.LuaSpell
import org.opentibia.canary.lua.registry.SpellType
import org.opentibia.canary.lua.registry.Spells
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript-style tests for the Canary `Spell` binding group
 * (src/lua/functions/creatures/combat/spell_functions.cpp). Each test installs
 * [SpellFunctions] with a fake [SpellOperations] seam and drives the exact Canary
 * method names through real Lua chunks.
 */
class SpellFunctionsBindingsTest {

    private class FakeSpellOps : SpellOperations {
        val itemTypes = linkedMapOf<Int, ItemType>()
        private val vocations = mapOf("sorcerer" to 1, "druid" to 2, "knight" to 4)

        override fun getItemType(id: Int): ItemType = itemTypes.getOrPut(id) { ItemType(id, "") }

        override fun vocationIdByName(name: String): Int = vocations[name.lowercase()] ?: 0

        override fun vocationName(vocationId: Int): String? =
            vocations.entries.firstOrNull { it.value == vocationId }?.key
    }

    private fun installed(): Triple<LuaEnvironment, Spells, FakeSpellOps> {
        val environment = LuaEnvironment().apply { initState() }
        val operations = FakeSpellOps()
        val spells = Spells()
        SpellFunctions.install(environment, spells, operations)
        return Triple(environment, spells, operations)
    }

    private fun state(environment: LuaEnvironment, name: String): LuaSpell =
        environment.getGlobal(name).touserdata() as LuaSpell

    @Test
    fun `Spell constructor creates instant and rune spells and rejects unknown arguments`() {
        val (environment, _, _) = installed()

        environment.loadChunk(
            """
            instantByName = Spell("instant")
            runeByName = Spell("rune")
            instantByEnum = Spell(1)
            runeByEnum = Spell(2)
            unknownNumber = Spell(99)
            unknownName = Spell("bogus")
            noArgs = Spell()
            """.trimIndent()
        )

        assertThat(state(environment, "instantByName").spellType).isEqualTo(SpellType.INSTANT)
        assertThat(state(environment, "runeByName").spellType).isEqualTo(SpellType.RUNE)
        assertThat(state(environment, "instantByEnum").spellType).isEqualTo(SpellType.INSTANT)
        assertThat(state(environment, "runeByEnum").spellType).isEqualTo(SpellType.RUNE)
        assertThat(environment.getGlobal("unknownNumber").isnil()).isTrue()
        assertThat(environment.getGlobal("unknownName").isnil()).isTrue()
        assertThat(environment.getGlobal("noArgs").isnil()).isTrue()
    }

    @Test
    fun `Spell scalar setters round trip through the Canary getter forms`() {
        val (environment, _, _) = installed()

        environment.loadChunk(
            """
            s = Spell("instant")
            s:name("Exura Test")
            s:id(221)
            s:level(20)
            s:magicLevel(5)
            s:mana(80)
            s:manaPercent(3)
            s:soul(2)
            s:cooldown(2000)
            s:groupCooldown(1000, 2000)
            s:range(3)
            s:isPremium(true)
            s:isEnabled(false)
            s:needTarget(true)
            s:needWeapon(true)
            s:needLearn(true)
            s:allowOnSelf(false)
            s:setPzLocked(true)
            s:isSelfTarget(true)
            s:isAggressive(false)
            s:castSound(11)
            s:impactSound(12)
            s:monkSpellType(3)

            name = s:name()
            id = s:id()
            level = s:level()
            magicLevel = s:magicLevel()
            mana = s:mana()
            manaPercent = s:manaPercent()
            soul = s:soul()
            cooldown = s:cooldown()
            groupCd, secondaryGroupCd = s:groupCooldown()
            range = s:range()
            premium = s:isPremium()
            enabled = s:isEnabled()
            target = s:needTarget()
            weapon = s:needWeapon()
            learn = s:needLearn()
            onSelf = s:allowOnSelf()
            pz = s:setPzLocked()
            self = s:isSelfTarget()
            aggressive = s:isAggressive()
            cast = s:castSound()
            impact = s:impactSound()
            monk = s:monkSpellType()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Exura Test")
        assertThat(environment.getGlobal("id").toint()).isEqualTo(221)
        assertThat(environment.getGlobal("level").toint()).isEqualTo(20)
        assertThat(environment.getGlobal("magicLevel").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("mana").toint()).isEqualTo(80)
        assertThat(environment.getGlobal("manaPercent").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("soul").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("cooldown").toint()).isEqualTo(2000)
        assertThat(environment.getGlobal("groupCd").toint()).isEqualTo(1000)
        assertThat(environment.getGlobal("secondaryGroupCd").toint()).isEqualTo(2000)
        assertThat(environment.getGlobal("range").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("premium").toboolean()).isTrue()
        assertThat(environment.getGlobal("enabled").toboolean()).isFalse()
        assertThat(environment.getGlobal("target").toboolean()).isTrue()
        assertThat(environment.getGlobal("weapon").toboolean()).isTrue()
        assertThat(environment.getGlobal("learn").toboolean()).isTrue()
        assertThat(environment.getGlobal("onSelf").toboolean()).isFalse()
        assertThat(environment.getGlobal("pz").toboolean()).isTrue()
        assertThat(environment.getGlobal("self").toboolean()).isTrue()
        assertThat(environment.getGlobal("aggressive").toboolean()).isFalse()
        assertThat(environment.getGlobal("cast").toint()).isEqualTo(11)
        assertThat(environment.getGlobal("impact").toint()).isEqualTo(12)
        assertThat(environment.getGlobal("monk").toint()).isEqualTo(3)
    }

    @Test
    fun `Spell group accepts numbers strings and the Canary secondary group quirk`() {
        val (environment, _, _) = installed()

        environment.loadChunk(
            """
            s = Spell("instant")
            group, secondary = s:group()
            byString = s:group("attack")
            groupByName, secondaryByName = s:group()
            byNumbers = s:group(1, 2)
            groupByNumbers, secondaryByNumbers = s:group()
            byTwoStrings = s:group("attack", "healing")
            groupByTwoStrings, secondaryByTwoStrings = s:group()
            invalid = s:group("bogus")
            """.trimIndent()
        )

        assertThat(environment.getGlobal("group").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("secondary").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("byString").toboolean()).isTrue()
        assertThat(environment.getGlobal("groupByName").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("byNumbers").toboolean()).isTrue()
        assertThat(environment.getGlobal("groupByNumbers").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("secondaryByNumbers").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("byTwoStrings").toboolean()).isTrue()
        assertThat(environment.getGlobal("groupByTwoStrings").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("secondaryByTwoStrings").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("invalid").toboolean()).isFalse()
    }

    @Test
    fun `Spell isBlocking reads and writes both blocking flags`() {
        val (environment, _, _) = installed()

        environment.loadChunk(
            """
            s = Spell("instant")
            solid, creature = s:isBlocking()
            set = s:isBlocking(true, false)
            solidAfter, creatureAfter = s:isBlocking()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("solid").toboolean()).isFalse()
        assertThat(environment.getGlobal("creature").toboolean()).isFalse()
        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(environment.getGlobal("solidAfter").toboolean()).isTrue()
        assertThat(environment.getGlobal("creatureAfter").toboolean()).isFalse()
    }

    @Test
    fun `Spell instant-only words and targeting flags are nil for runes`() {
        val (environment, _, _) = installed()

        environment.loadChunk(
            """
            s = Spell("instant")
            set = s:words("exura test", ";")
            words, separator = s:words()
            s:needDirection(true)
            s:hasParams(true)
            s:hasPlayerNameParam(true)
            s:needCasterTargetOrDirection(true)
            s:isBlockingWalls(false)
            direction = s:needDirection()
            params = s:hasParams()
            nameParam = s:hasPlayerNameParam()
            casterTarget = s:needCasterTargetOrDirection()
            walls = s:isBlockingWalls()

            r = Spell("rune")
            runeWords = r:words()
            runeDirection = r:needDirection()
            runeParams = r:hasParams()
            runeNameParam = r:hasPlayerNameParam()
            runeCasterTarget = r:needCasterTargetOrDirection()
            runeWalls = r:isBlockingWalls()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(environment.getGlobal("words").tojstring()).isEqualTo("exura test")
        assertThat(environment.getGlobal("separator").tojstring()).isEqualTo(";")
        assertThat(environment.getGlobal("direction").toboolean()).isTrue()
        assertThat(environment.getGlobal("params").toboolean()).isTrue()
        assertThat(environment.getGlobal("nameParam").toboolean()).isTrue()
        assertThat(environment.getGlobal("casterTarget").toboolean()).isTrue()
        assertThat(environment.getGlobal("walls").toboolean()).isFalse()
        assertThat(environment.getGlobal("runeWords").isnil()).isTrue()
        assertThat(environment.getGlobal("runeDirection").isnil()).isTrue()
        assertThat(environment.getGlobal("runeParams").isnil()).isTrue()
        assertThat(environment.getGlobal("runeNameParam").isnil()).isTrue()
        assertThat(environment.getGlobal("runeCasterTarget").isnil()).isTrue()
        assertThat(environment.getGlobal("runeWalls").isnil()).isTrue()
    }

    @Test
    fun `Spell rune-only methods are nil for instant spells`() {
        val (environment, _, _) = installed()

        environment.loadChunk(
            """
            r = Spell("rune")
            r:id(18)
            r:runeId(3200)
            r:charges(5)
            r:allowFarUse(true)
            r:blockWalls(false)
            r:checkFloor(false)
            runeId = r:runeId()
            charges = r:charges()
            farUse = r:allowFarUse()
            blockWalls = r:blockWalls()
            checkFloor = r:checkFloor()

            s = Spell("instant")
            badRune = s:runeId()
            badCharges = s:charges()
            badFarUse = s:allowFarUse()
            badBlockWalls = s:blockWalls()
            badCheckFloor = s:checkFloor()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("runeId").toint()).isEqualTo(3200)
        assertThat(environment.getGlobal("charges").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("farUse").toboolean()).isTrue()
        assertThat(environment.getGlobal("blockWalls").toboolean()).isFalse()
        assertThat(environment.getGlobal("checkFloor").toboolean()).isFalse()
        assertThat(environment.getGlobal("badRune").isnil()).isTrue()
        assertThat(environment.getGlobal("badCharges").isnil()).isTrue()
        assertThat(environment.getGlobal("badFarUse").isnil()).isTrue()
        assertThat(environment.getGlobal("badBlockWalls").isnil()).isTrue()
        assertThat(environment.getGlobal("badCheckFloor").isnil()).isTrue()
    }

    @Test
    fun `Spell vocation stores the vocation map and resolves names through the seam`() {
        val (environment, _, _) = installed()

        environment.loadChunk(
            """
            s = Spell("instant")
            s:vocation("sorcerer")
            s:vocation("druid")
            vocations = s:vocation()
            first = vocations[1]
            second = vocations[2]
            """.trimIndent()
        )

        assertThat(environment.getGlobal("first").tojstring()).isEqualTo("sorcerer")
        assertThat(environment.getGlobal("second").tojstring()).isEqualTo("druid")
        assertThat(state(environment, "s").vocSpellMap[1]).isFalse()
        assertThat(state(environment, "s").vocSpellMap[2]).isFalse()

        environment.loadChunk("s:vocation('knight;true')")
        assertThat(state(environment, "s").vocSpellMap[4]).isTrue()
    }

    @Test
    fun `Spell register follows the Canary callback and duplicate gates for instants`() {
        val (environment, spells, _) = installed()

        environment.loadChunk(
            """
            noCallback = Spell("instant")
            noCallback:words("exura nocallback")
            noCallbackResult = noCallback:register()

            noWords = Spell("instant")
            function noWords.onCastSpell(creature, variant) return true end
            noWordsResult = noWords:register()

            first = Spell("instant")
            first:name("First")
            first:words("exura first")
            function first.onCastSpell(creature, variant) return true end
            firstResult = first:register()

            second = Spell("instant")
            second:words("exura first")
            function second.onCastSpell(creature, variant) return true end
            secondResult = second:register()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("noCallbackResult").toboolean()).isFalse()
        assertThat(environment.getGlobal("noWordsResult").toboolean()).isFalse()
        assertThat(environment.getGlobal("firstResult").toboolean()).isTrue()
        assertThat(environment.getGlobal("secondResult").toboolean()).isFalse()
        assertThat(spells.getInstant("exura first")).isNotNull
    }

    @Test
    fun `Spell register writes the rune item info and rejects missing callbacks`() {
        val (environment, spells, operations) = installed()

        environment.loadChunk(
            """
            noCallback = Spell("rune")
            noCallback:runeId(3301)
            noCallbackResult = noCallback:register()

            rune = Spell("rune")
            rune:name("Test Rune")
            rune:runeId(3300)
            rune:level(20)
            rune:magicLevel(5)
            rune:charges(3)
            function rune.onCastSpell(creature, variant, isHotkey) return true end
            runeResult = rune:register()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("noCallbackResult").toboolean()).isFalse()
        assertThat(environment.getGlobal("runeResult").toboolean()).isTrue()
        assertThat(spells.getRune(3300)).isNotNull

        val itemType = operations.itemTypes.getValue(3300)
        assertThat(itemType.name).isEqualTo("Test Rune")
        assertThat(itemType.runeLevel).isEqualTo(20)
        assertThat(itemType.runeMagLevel).isEqualTo(5)
        assertThat(itemType.charges).isEqualTo(3)
    }

    @Test
    fun `Spell lookup resolves registered instants by name and runes by id`() {
        val (environment, spells, _) = installed()

        environment.loadChunk(
            """
            s = Spell("instant")
            s:name("Exura Lookup")
            s:words("exura lookup")
            function s.onCastSpell(creature, variant) return true end
            s:register()

            r = Spell("rune")
            r:name("Rune Lookup")
            r:runeId(3400)
            function r.onCastSpell(creature, variant) end
            r:register()

            byName = Spell("EXURA LOOKUP")
            byWords = Spell("exura lookup")
            byRuneName = Spell("Rune Lookup")
            byRuneId = Spell(3400)
            missing = Spell("nothing here")
            """.trimIndent()
        )

        assertThat(environment.getGlobal("byName").touserdata()).isSameAs(spells.getInstant("exura lookup"))
        assertThat(environment.getGlobal("byWords").touserdata()).isSameAs(spells.getInstant("exura lookup"))
        assertThat(environment.getGlobal("byRuneName").touserdata()).isSameAs(spells.getRune(3400))
        assertThat(environment.getGlobal("byRuneId").touserdata()).isSameAs(spells.getRune(3400))
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
    }

    @Test
    fun `Spell onCastSpell method stores the callback used by register`() {
        val (environment, spells, _) = installed()

        environment.loadChunk(
            """
            s = Spell("instant")
            s:name("Method Callback")
            s:words("exura method")
            stored = s:onCastSpell(function(creature, variant) return true end)
            registered = s:register()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("stored").toboolean()).isTrue()
        assertThat(environment.getGlobal("registered").toboolean()).isTrue()
        assertThat(spells.getInstant("exura method")!!.onCastSpell.isfunction()).isTrue()
    }
}
