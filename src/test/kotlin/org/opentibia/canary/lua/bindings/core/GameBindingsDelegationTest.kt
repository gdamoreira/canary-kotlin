package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.luaj.vm2.LuaValue
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Fake-seam transcript tests pinning the exact argument plumbing of the extended `Game`
 * binding and the Canary timer/time globals: Canary argument order, defaults, value
 * conversion and userdata wrapping are all observed through real Lua chunks.
 */
class GameBindingsDelegationTest {

    private data class SpectatorCall(
        val position: Position,
        val multifloor: Boolean,
        val onlyPlayers: Boolean,
        val minRangeX: Int,
        val maxRangeX: Int,
        val minRangeY: Int,
        val maxRangeY: Int,
    )

    private data class MonsterCall(
        val name: String,
        val position: Position,
        val extended: Boolean,
        val force: Boolean,
        val master: Any?,
    )

    private data class NpcCall(
        val name: String,
        val position: Position,
        val extended: Boolean,
        val force: Boolean,
    )

    private data class AddEventCall(
        val delay: Int,
        val callback: LuaValue,
        val parameters: List<LuaValue>,
    )

    private class FakeGameOps : GameOperations {
        var lastSpectators: SpectatorCall? = null
        val spectators = mutableListOf<Any>()
        val onlinePlayers = mutableListOf<Any>()
        var lastMonster: MonsterCall? = null
        var monster: Any? = null
        var lastNpc: NpcCall? = null
        var npc: Any? = null
        var lastOffline: Any? = null
        var offline: Any? = null
        var lastNormalized: Pair<String, Boolean>? = null
        var normalized: String? = null
        var time = 0
        var lastAddEvent: AddEventCall? = null
        var addEventId = 0
        var lastStopEvent: Int? = null
        var stopEventResult = false

        override fun itemIdByName(name: String): Int = 0
        override fun createItem(itemId: Int, count: Int): List<Any> = emptyList()

        override fun getSpectators(
            position: Position,
            multifloor: Boolean,
            onlyPlayers: Boolean,
            minRangeX: Int,
            maxRangeX: Int,
            minRangeY: Int,
            maxRangeY: Int,
        ): List<Any> {
            lastSpectators = SpectatorCall(position, multifloor, onlyPlayers, minRangeX, maxRangeX, minRangeY, maxRangeY)
            return spectators
        }

        override fun getPlayers(): List<Any> = onlinePlayers

        override fun getGameState(): Int = 2

        override fun getMonsterCount(): Int = 4

        override fun getPlayerCount(): Int = 2

        override fun getNpcCount(): Int = 3

        override fun getExperienceForLevel(level: Int): Long = 1234L

        override fun createMonster(
            name: String,
            position: Position,
            extended: Boolean,
            force: Boolean,
            master: Any?,
        ): Any? {
            lastMonster = MonsterCall(name, position, extended, force, master)
            return monster
        }

        override fun createNpc(name: String, position: Position, extended: Boolean, force: Boolean): Any? {
            lastNpc = NpcCall(name, position, extended, force)
            return npc
        }

        override fun getOfflinePlayer(nameOrId: Any): Any? {
            lastOffline = nameOrId
            return offline
        }

        override fun getNormalizedPlayerName(name: String, isNewName: Boolean): String? {
            lastNormalized = name to isNewName
            return normalized
        }

        override fun getWorldTime(): Int = time

        override fun addEvent(delay: Int, callback: LuaValue, parameters: List<LuaValue>): Int {
            lastAddEvent = AddEventCall(delay, callback, parameters)
            return addEventId
        }

        override fun stopEvent(eventId: Int): Boolean {
            lastStopEvent = eventId
            return stopEventResult
        }
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    @Test
    fun `Game getSpectators forwards every argument and picks the creature metatable`() {
        val environment = env()
        val operations = FakeGameOps()
        operations.spectators += Monster(id = 7, name = "Rat", maxHealth = 100, health = 30, position = Position(1, 1, 7))
        operations.spectators += Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(2, 2, 7))
        operations.spectators += Npc(id = 9, name = "Sam", position = Position(3, 3, 7))
        GameFunctions.install(environment, operations)

        environment.loadChunk("list = Game.getSpectators({ x = 100, y = 200, z = 7 }, true, true, 1, 2, 3, 4)")
        environment.loadChunk("monsterMt = getmetatable(list[1]) == rawgetmetatable('Monster')")
        environment.loadChunk("playerMt = getmetatable(list[2]) == rawgetmetatable('Player')")
        environment.loadChunk("npcMt = getmetatable(list[3]) == rawgetmetatable('Npc')")
        environment.loadChunk("count = #list")

        assertThat(operations.lastSpectators)
            .isEqualTo(SpectatorCall(Position(100, 200, 7), true, true, 1, 2, 3, 4))
        assertThat(environment.getGlobal("count").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("monsterMt").toboolean()).isTrue()
        assertThat(environment.getGlobal("playerMt").toboolean()).isTrue()
        assertThat(environment.getGlobal("npcMt").toboolean()).isTrue()
    }

    @Test
    fun `Game getSpectators defaults every optional argument`() {
        val environment = env()
        val operations = FakeGameOps()
        GameFunctions.install(environment, operations)

        environment.loadChunk("list = Game.getSpectators({ x = 1, y = 2, z = 7 })")

        assertThat(operations.lastSpectators)
            .isEqualTo(SpectatorCall(Position(1, 2, 7), false, false, 0, 0, 0, 0))
        assertThat(environment.getGlobal("list").istable()).isTrue()
    }

    @Test
    fun `Game createMonster forwards the Canary signature and wraps a Monster`() {
        val environment = env()
        val operations = FakeGameOps()
        operations.monster = Monster(id = 7, name = "Rat", maxHealth = 100, health = 30, position = Position(1, 2, 7))
        GameFunctions.install(environment, operations)
        val master = Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(0, 0, 7))
        environment.globals.set("master", environment.userdata.wrap("Player", master))

        environment.loadChunk("monster = Game.createMonster('Rat', { x = 1, y = 2, z = 7 }, true, true, master)")
        environment.loadChunk("monsterMt = getmetatable(monster) == rawgetmetatable('Monster')")

        assertThat(operations.lastMonster)
            .isEqualTo(MonsterCall("Rat", Position(1, 2, 7), true, true, master))
        assertThat(environment.getGlobal("monsterMt").toboolean()).isTrue()
    }

    @Test
    fun `Game createNpc forwards the Canary signature and wraps an Npc`() {
        val environment = env()
        val operations = FakeGameOps()
        operations.npc = Npc(id = 9, name = "Sam", position = Position(1, 2, 7))
        GameFunctions.install(environment, operations)

        environment.loadChunk("npc = Game.createNpc('Sam', { x = 1, y = 2, z = 7 }, true, true)")
        environment.loadChunk("npcMt = getmetatable(npc) == rawgetmetatable('Npc')")

        assertThat(operations.lastNpc).isEqualTo(NpcCall("Sam", Position(1, 2, 7), true, true))
        assertThat(environment.getGlobal("npcMt").toboolean()).isTrue()
    }

    @Test
    fun `Game getOfflinePlayer accepts a name or an id`() {
        val environment = env()
        val operations = FakeGameOps()
        operations.offline = Player(id = 42, name = "Hero", maxHealth = 100, health = 100, position = Position(0, 0, 7))
        GameFunctions.install(environment, operations)

        environment.loadChunk("byName = Game.getOfflinePlayer('Hero')")
        environment.loadChunk("byId = Game.getOfflinePlayer(42)")
        environment.loadChunk("playerMt = getmetatable(byName) == rawgetmetatable('Player')")

        assertThat(operations.lastOffline).isEqualTo(42)
        assertThat(environment.getGlobal("playerMt").toboolean()).isTrue()
        assertThat(environment.getGlobal("byId").isuserdata()).isTrue()
    }

    @Test
    fun `Game getNormalizedPlayerName forwards isNewName`() {
        val environment = env()
        val operations = FakeGameOps()
        operations.normalized = "Hero"
        GameFunctions.install(environment, operations)

        environment.loadChunk("name = Game.getNormalizedPlayerName('hero', true)")

        assertThat(operations.lastNormalized).isEqualTo("hero" to true)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Hero")
    }

    @Test
    fun `Game state count and experience queries forward to the seam`() {
        val environment = env()
        GameFunctions.install(environment, FakeGameOps())

        environment.loadChunk("state = Game.getGameState()")
        environment.loadChunk("monsters = Game.getMonsterCount()")
        environment.loadChunk("players = Game.getPlayerCount()")
        environment.loadChunk("npcs = Game.getNpcCount()")
        environment.loadChunk("experience = Game.getExperienceForLevel(9)")

        assertThat(environment.getGlobal("state").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("monsters").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("players").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("npcs").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("experience").toint()).isEqualTo(1234)
    }

    @Test
    fun `global getWorldTime addEvent and stopEvent forward to the seam`() {
        val environment = env()
        val operations = FakeGameOps()
        operations.time = 360
        operations.addEventId = 9
        operations.stopEventResult = true
        GameFunctions.install(environment, operations)

        environment.loadChunk("time = getWorldTime()")
        environment.loadChunk("eventId = addEvent(function() end, 50, 1, 'two')")
        environment.loadChunk("stopped = stopEvent(9)")

        assertThat(environment.getGlobal("time").toint()).isEqualTo(360)
        assertThat(environment.getGlobal("eventId").toint()).isEqualTo(9)
        assertThat(environment.getGlobal("stopped").toboolean()).isTrue()
        assertThat(operations.lastStopEvent).isEqualTo(9)
        val addEvent = operations.lastAddEvent
        assertThat(addEvent).isNotNull()
        assertThat(addEvent!!.delay).isEqualTo(100)
        assertThat(addEvent.callback.isfunction()).isTrue()
        assertThat(addEvent.parameters.map { it.tojstring() }).containsExactly("1", "two")
    }
}
