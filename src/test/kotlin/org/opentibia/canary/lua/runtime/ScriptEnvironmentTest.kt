package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.database.DatabaseConnectionFactory.DBResult
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile

/**
 * Transcribes `ScriptEnvironment` (`src/lua/scripts/script_environment.cpp`): the
 * per-call script identity, the nested-callback guard, the thing/UID bookkeeping and
 * the static temporary-item and DB-result registries.
 */
class ScriptEnvironmentTest {

    private class FakeGame : ScriptEnvironmentGame {
        val creatures = mutableMapOf<Long, Creature>()
        val uniqueItems = mutableMapOf<Int, Item>()
        val removedUniqueIds = mutableListOf<Int>()

        override fun getCreatureByID(id: Long): Creature? = creatures[id]

        override fun getUniqueItem(uid: Int): Item? = uniqueItems[uid]

        override fun removeUniqueItem(uid: Int) {
            removedUniqueIds.add(uid)
            uniqueItems.remove(uid)
        }
    }

    private fun tileItem(id: Int): Item =
        Item(id).also { Tile(Position(100, 100, 7)).addThing(it) }

    private fun dbResult(id: String): DBResult =
        DBResult(mapOf("id" to 0), listOf(listOf(id)))

    @Test
    fun `resetEnv zeroes script identity and clears the local map`() {
        val environment = ScriptEnvironment()
        environment.setScriptId(7, null)
        environment.setTimerEvent()
        environment.insertItem(0x20000, tileItem(2160))

        environment.resetEnv()

        assertThat(environment.getScriptId()).isZero()
        assertThat(environment.callbackId).isZero()
        assertThat(environment.getScriptInterface()).isNull()
        assertThat(environment.getEventInfo().timerEvent).isFalse()
        assertThat(environment.getThingByUID(0x20000)).isNull()
    }

    @Test
    fun `setCallbackId accepts the first callback and rejects a nested one`() {
        val environment = ScriptEnvironment()
        val scriptInterface = object : LuaScriptInterface("Test Interface") {
            val reports = mutableListOf<String>()
            override fun reportError(function: String?, errorDesc: String, stackTrace: Boolean) {
                reports.add(errorDesc)
            }
        }
        environment.setScriptId(LuaScriptInterface.EVENT_ID_USER, scriptInterface)

        assertThat(environment.setCallbackId(1000, scriptInterface)).isTrue()
        assertThat(environment.callbackId).isEqualTo(1000)
        assertThat(environment.getScriptInterface()).isSameAs(scriptInterface)

        assertThat(environment.setCallbackId(2000, scriptInterface)).isFalse()
        assertThat(environment.callbackId).isEqualTo(1000)
        assertThat(scriptInterface.reports).containsExactly("Nested callbacks!")
    }

    @Test
    fun `getEventInfo reports the script identity tuple`() {
        val environment = ScriptEnvironment()
        val scriptInterface = LuaScriptInterface("Test Interface")
        environment.setScriptId(LuaScriptInterface.EVENT_ID_LOADING, scriptInterface)
        environment.setCallbackId(1000, scriptInterface)
        environment.setTimerEvent()

        val info = environment.getEventInfo()

        assertThat(info.scriptId).isEqualTo(LuaScriptInterface.EVENT_ID_LOADING)
        assertThat(info.scriptInterface).isSameAs(scriptInterface)
        assertThat(info.callbackId).isEqualTo(1000)
        assertThat(info.timerEvent).isTrue()
    }

    @Test
    fun `setNpc stores the current npc`() {
        val environment = ScriptEnvironment()
        val npc = Npc(1, "Guide", position = Position(100, 100, 7))

        environment.setNpc(npc)

        assertThat(environment.getNpc()).isSameAs(npc)
    }

    @Test
    fun `addThing returns zero for a null or removed thing`() {
        val environment = ScriptEnvironment()

        assertThat(environment.addThing(null)).isZero()

        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        player.setRemoved()
        assertThat(environment.addThing(player)).isZero()
    }

    @Test
    fun `addThing returns the creature id for a creature`() {
        val environment = ScriptEnvironment()
        val player = Player(42, "Hero", 100, 100, position = Position(100, 100, 7))

        assertThat(environment.addThing(player)).isEqualTo(42)
    }

    @Test
    fun `addThing returns the unique id for an item carrying one`() {
        val environment = ScriptEnvironment()
        val item = tileItem(2160)
        item.setUniqueId(3000)

        assertThat(environment.addThing(item)).isEqualTo(3000)
    }

    @Test
    fun `addThing assigns local uids starting above uint16 max and reuses them`() {
        val environment = ScriptEnvironment()
        val first = tileItem(2160)
        val second = tileItem(2152)

        assertThat(environment.addThing(first)).isEqualTo(0x10000)
        assertThat(environment.addThing(second)).isEqualTo(0x10001)
        assertThat(environment.addThing(first)).isEqualTo(0x10000)
    }

    @Test
    fun `getThingByUID resolves creatures above the local range`() {
        val game = FakeGame()
        val environment = ScriptEnvironment(game)
        val player = Player(0x10000000, "Hero", 100, 100, position = Position(100, 100, 7))
        game.creatures[0x10000000] = player

        assertThat(environment.getThingByUID(0x10000000)).isSameAs(player)
        assertThat(environment.getThingByUID(0x10000001)).isNull()
    }

    @Test
    fun `getThingByUID resolves unique items from the game while not removed`() {
        val game = FakeGame()
        val environment = ScriptEnvironment(game)
        val item = tileItem(2160)
        game.uniqueItems[2160] = item

        assertThat(environment.getThingByUID(2160)).isSameAs(item)

        item.setUniqueId(2160)
        item.setParent(null)
        assertThat(environment.getThingByUID(2160)).isNull()
    }

    @Test
    fun `getThingByUID resolves local map items`() {
        val environment = ScriptEnvironment()
        val item = tileItem(2160)
        environment.insertItem(0x10000, item)

        assertThat(environment.getThingByUID(0x10000)).isSameAs(item)
        assertThat(environment.getThingByUID(0x10001)).isNull()
    }

    @Test
    fun `insertItem keeps the first item registered for a uid`() {
        val environment = ScriptEnvironment()
        val first = tileItem(2160)
        val duplicate = tileItem(2152)

        environment.insertItem(0x10000, first)
        environment.insertItem(0x10000, duplicate)

        assertThat(environment.getThingByUID(0x10000)).isSameAs(first)
    }

    @Test
    fun `getItemByUID and getContainerByUID unwrap the thing`() {
        val environment = ScriptEnvironment()
        val container = Container(id = 1988, capacity = 20)
        Tile(Position(100, 100, 7)).addThing(container)
        environment.insertItem(0x10000, container)

        assertThat(environment.getItemByUID(0x10000)).isSameAs(container)
        assertThat(environment.getContainerByUID(0x10000)).isSameAs(container)
        assertThat(environment.getContainerByUID(0x10001)).isNull()
    }

    @Test
    fun `removeItemByUID routes unique uids to the game and local uids to the map`() {
        val game = FakeGame()
        val environment = ScriptEnvironment(game)
        val unique = tileItem(2160)
        game.uniqueItems[2160] = unique
        val local = tileItem(2152)
        environment.insertItem(0x10000, local)

        environment.removeItemByUID(2160)
        environment.removeItemByUID(0x10000)

        assertThat(game.removedUniqueIds).containsExactly(2160)
        assertThat(environment.getThingByUID(0x10000)).isNull()
    }

    @Test
    fun `addResult stores the result until removeResult drops it`() {
        val result = dbResult("7")

        val id = ScriptEnvironment.addResult(result)
        assertThat(ScriptEnvironment.getResultByID(id)).isSameAs(result)

        assertThat(ScriptEnvironment.removeResult(id)).isTrue()
        assertThat(ScriptEnvironment.getResultByID(id)).isNull()
        assertThat(ScriptEnvironment.removeResult(id)).isFalse()
    }

    @Test
    fun `temp items are tracked per environment and released on reset`() {
        val environment = ScriptEnvironment()
        val item = tileItem(2160)

        environment.addTempItem(item)
        ScriptEnvironment.removeTempItem(item)

        environment.addTempItem(item)
        environment.resetEnv()

        assertThat(environment.getThingByUID(0x10000)).isNull()
    }
}
