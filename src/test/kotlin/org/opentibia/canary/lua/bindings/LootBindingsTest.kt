package org.opentibia.canary.lua.bindings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript-style tests for the Canary `Loot` binding group. Each test installs
 * [LootFunctions] with a fake [LootOperations] seam and drives it through real Lua
 * chunks using the exact method names from
 * src/lua/functions/creatures/monster/loot_functions.cpp.
 */
class LootBindingsTest {

    private class FakeLootOps(private val names: Map<String, Int> = emptyMap()) : LootOperations {
        val lookedUpNames = mutableListOf<String>()

        override fun itemIdByName(name: String): Int {
            lookedUpNames += name
            return names[name] ?: 0
        }
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun installed(names: Map<String, Int> = mapOf("gold coin" to 2148)): Pair<LuaEnvironment, FakeLootOps> {
        val environment = env()
        val operations = FakeLootOps(names)
        LootFunctions.install(environment, operations)
        return environment to operations
    }

    @Test
    fun `Loot constructor creates a Loot userdata with an optional monster name`() {
        val (environment, _) = installed()

        environment.loadChunk("plain = Loot()")
        environment.loadChunk("named = Loot('Rat')")

        assertThat(environment.getGlobal("plain").touserdata()).isInstanceOf(LootData::class.java)
        assertThat((environment.getGlobal("plain").touserdata() as LootData).monsterName).isNull()
        assertThat((environment.getGlobal("named").touserdata() as LootData).monsterName).isEqualTo("Rat")
    }

    @Test
    fun `Loot setters fill the loot block with the Canary names`() {
        val (environment, _) = installed()
        environment.loadChunk("l = Loot()")

        environment.loadChunk(
            """
            id = l:setId(2148)
            min = l:setMinCount(1)
            max = l:setMaxCount(5)
            subType = l:setSubType(3)
            chance = l:setChance(50000)
            actionId = l:setActionId(100)
            text = l:setText('hello')
            name = l:setNameItem('gold coin')
            article = l:setArticle('a')
            attack = l:setAttack(5)
            defense = l:setDefense(6)
            extraDefense = l:setExtraDefense(7)
            armor = l:setArmor(8)
            shootRange = l:setShootRange(9)
            hitChance = l:setHitChance(10)
            """.trimIndent(),
        )

        val loot = environment.getGlobal("l").touserdata() as LootData
        assertThat(loot.id).isEqualTo(2148)
        assertThat(loot.countMin).isEqualTo(1)
        assertThat(loot.countMax).isEqualTo(5)
        assertThat(loot.subType).isEqualTo(3)
        assertThat(loot.chance).isEqualTo(50_000)
        assertThat(loot.actionId).isEqualTo(100)
        assertThat(loot.text).isEqualTo("hello")
        assertThat(loot.name).isEqualTo("gold coin")
        assertThat(loot.article).isEqualTo("a")
        assertThat(loot.attack).isEqualTo(5)
        assertThat(loot.defense).isEqualTo(6)
        assertThat(loot.extraDefense).isEqualTo(7)
        assertThat(loot.armor).isEqualTo(8)
        assertThat(loot.shootRange).isEqualTo(9)
        assertThat(loot.hitChance).isEqualTo(10)
    }

    @Test
    fun `Loot setIdFromName resolves the item name through the catalog`() {
        val (environment, operations) = installed()
        environment.loadChunk("l = Loot()")

        environment.loadChunk("found = l:setIdFromName('gold coin')")
        environment.loadChunk("missing = l:setIdFromName('nothing')")

        val loot = environment.getGlobal("l").touserdata() as LootData
        assertThat(environment.getGlobal("found").toboolean()).isTrue()
        assertThat(loot.id).isEqualTo(2148)
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
        assertThat(operations.lookedUpNames).containsExactly("gold coin", "nothing")
    }

    @Test
    fun `Loot setUnique reads and writes the flag`() {
        val (environment, _) = installed()
        environment.loadChunk("l = Loot()")

        environment.loadChunk("before = l:setUnique()")
        environment.loadChunk("set = l:setUnique(true)")
        environment.loadChunk("after = l:setUnique()")

        assertThat(environment.getGlobal("before").toboolean()).isFalse()
        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(environment.getGlobal("after").toboolean()).isTrue()
    }

    @Test
    fun `Loot addChildLoot appends a copy of the child block`() {
        val (environment, _) = installed()
        environment.loadChunk("l = Loot()")
        environment.loadChunk("child = Loot()")
        environment.loadChunk("child:setId(2160)")

        environment.loadChunk("added = l:addChildLoot(child)")
        environment.loadChunk("invalid = l:addChildLoot(42)")
        environment.loadChunk("child:setId(9999)")

        val loot = environment.getGlobal("l").touserdata() as LootData
        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(environment.getGlobal("invalid").toboolean()).isFalse()
        assertThat(loot.childLoot).hasSize(1)
        assertThat(loot.childLoot.single().id).isEqualTo(2160)
    }
}
