package org.opentibia.canary.lua.bindings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.lua.bindings.core.luaToPosition

/**
 * Transcript-style tests for the Canary `Monster` binding group. Each test installs
 * [MonsterFunctions] with a fake [MonsterOperations] seam and drives it through real
 * Lua chunks using the exact method names from
 * src/lua/functions/creatures/monster/monster_functions.cpp.
 */
class MonsterBindingsTest {

    private class FakeMonster
    private class FakeCreature
    private class FakeMonsterType

    private class FakeMonsterOps : MonsterOperations {
        var type: Any? = null
        var name = "Rat"
        var spawnPosition: Position? = Position(100, 100, 7)
        var inSpawnRange = true
        val spawnRangeCalls = mutableListOf<Position?>()
        var idle = false
        var challenged = false
        var selectTargetResult = true
        var lastSelectTarget: Any? = null
        var targetPredicate = true
        var friendPredicate = true
        var opponentPredicate = true
        var lastTargetPredicate: Any? = null
        var lastFriendPredicate: Any? = null
        var lastOpponentPredicate: Any? = null
        val friends = mutableListOf<Any>()
        val targets = mutableListOf<Any>()
        var lastPushFront: Boolean? = null
        var attackedTarget: Any? = null
        val masters = mutableMapOf<Any, Any?>()

        override fun isMonster(monster: Any): Boolean = monster is FakeMonster

        override fun getType(monster: Any): Any? = type

        override fun getName(monster: Any): String? = name

        override fun getSpawnPosition(monster: Any): Position? = spawnPosition

        override fun isInSpawnRange(monster: Any, position: Position?): Boolean? {
            spawnRangeCalls += position
            return inSpawnRange
        }

        override fun isIdle(monster: Any): Boolean? = idle

        override fun setIdle(monster: Any, idle: Boolean): Boolean? {
            this.idle = idle
            return true
        }

        override fun isTarget(monster: Any, creature: Any?): Boolean? {
            lastTargetPredicate = creature
            return targetPredicate
        }

        override fun isOpponent(monster: Any, creature: Any?): Boolean? {
            lastOpponentPredicate = creature
            return opponentPredicate
        }

        override fun isFriend(monster: Any, creature: Any?): Boolean? {
            lastFriendPredicate = creature
            return friendPredicate
        }

        override fun addFriend(monster: Any, creature: Any?): Boolean? {
            if (creature != null) {
                friends += creature
            }
            return true
        }

        override fun removeFriend(monster: Any, creature: Any?): Boolean? {
            friends.remove(creature)
            return true
        }

        override fun getFriendList(monster: Any): List<Any>? = friends.toList()

        override fun addTarget(monster: Any, creature: Any?, pushFront: Boolean): Boolean? {
            lastPushFront = pushFront
            if (creature != null) {
                if (pushFront) targets.add(0, creature) else targets += creature
            }
            return true
        }

        override fun removeTarget(monster: Any, creature: Any?): Boolean? {
            targets.remove(creature)
            return true
        }

        override fun getTargetList(monster: Any): List<Any>? = targets.toList()

        override fun isChallenged(monster: Any): Boolean? = challenged

        override fun selectTarget(monster: Any, creature: Any?): Boolean? {
            lastSelectTarget = creature
            return selectTargetResult
        }

        override fun getTarget(monster: Any): Any? = attackedTarget

        override fun setTarget(monster: Any, target: Any?): Boolean? {
            attackedTarget = target
            return true
        }

        override fun getMaster(monster: Any): Any? = masters[monster]

        override fun setMaster(monster: Any, master: Any?): Boolean? {
            if (master == null) masters.remove(monster) else masters[monster] = master
            return true
        }
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun installed(fake: FakeMonsterOps = FakeMonsterOps()): Pair<LuaEnvironment, FakeMonsterOps> {
        val environment = env()
        MonsterFunctions.install(environment, fake)
        environment.globals.set("m", environment.userdata.wrap("Monster", FakeMonster()))
        return environment to fake
    }

    @Test
    fun `Monster isMonster and getName use the Canary names`() {
        val (environment, _) = installed()

        environment.loadChunk("isMonster = m:isMonster()")
        environment.loadChunk("name = m:getName()")

        assertThat(environment.getGlobal("isMonster").toboolean()).isTrue()
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Rat")
    }

    @Test
    fun `Monster getType wraps the type as MonsterType userdata`() {
        val fake = FakeMonsterOps()
        val type = FakeMonsterType()
        fake.type = type
        val (environment, _) = installed(fake)

        environment.loadChunk("t = m:getType()")

        assertThat(environment.getGlobal("t").touserdata()).isSameAs(type)
    }

    @Test
    fun `Monster getType returns nil without a type`() {
        val (environment, _) = installed()

        environment.loadChunk("t = m:getType()")

        assertThat(environment.getGlobal("t").isnil()).isTrue()
    }

    @Test
    fun `Monster getSpawnPosition and isInSpawnRange use the Canary names`() {
        val (environment, fake) = installed()

        environment.loadChunk("pos = m:getSpawnPosition()")
        environment.loadChunk("range = m:isInSpawnRange(pos)")
        environment.loadChunk("defaultRange = m:isInSpawnRange()")

        assertThat(luaToPosition(environment.getGlobal("pos"))).isEqualTo(Position(100, 100, 7))
        assertThat(environment.getGlobal("range").toboolean()).isTrue()
        assertThat(environment.getGlobal("defaultRange").toboolean()).isTrue()
        assertThat(fake.spawnRangeCalls).containsExactly(Position(100, 100, 7), null)
    }

    @Test
    fun `Monster idle state uses isIdle and setIdle`() {
        val (environment, fake) = installed()

        environment.loadChunk("before = m:isIdle()")
        environment.loadChunk("set = m:setIdle(true)")
        environment.loadChunk("after = m:isIdle()")

        assertThat(environment.getGlobal("before").toboolean()).isFalse()
        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(environment.getGlobal("after").toboolean()).isTrue()
        assertThat(fake.idle).isTrue()
    }

    @Test
    fun `Monster friend list uses addFriend removeFriend and counts`() {
        val (environment, _) = installed()
        val creature = FakeCreature()
        environment.globals.set("c", environment.userdata.wrap("Creature", creature))

        environment.loadChunk("before = m:getFriendCount()")
        environment.loadChunk("added = m:addFriend(c)")
        environment.loadChunk("after = m:getFriendCount()")
        environment.loadChunk("list = m:getFriendList()")
        environment.loadChunk("first = list[1]")
        environment.loadChunk("removed = m:removeFriend(c)")
        environment.loadChunk("empty = m:getFriendCount()")

        assertThat(environment.getGlobal("before").toint()).isZero()
        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(environment.getGlobal("after").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("first").touserdata()).isSameAs(creature)
        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(environment.getGlobal("empty").toint()).isZero()
    }

    @Test
    fun `Monster target list uses addTarget removeTarget and counts`() {
        val (environment, fake) = installed()
        val first = FakeCreature()
        val second = FakeCreature()
        fake.targets += first
        environment.globals.set("a", environment.userdata.wrap("Creature", first))
        environment.globals.set("b", environment.userdata.wrap("Creature", second))

        environment.loadChunk("before = m:getTargetCount()")
        environment.loadChunk("added = m:addTarget(b)")
        environment.loadChunk("front = m:addTarget(b, true)")
        environment.loadChunk("list = m:getTargetList()")
        environment.loadChunk("head = list[1]")
        environment.loadChunk("removed = m:removeTarget(b)")

        assertThat(environment.getGlobal("before").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(environment.getGlobal("front").toboolean()).isTrue()
        assertThat(fake.lastPushFront).isTrue()
        assertThat(environment.getGlobal("head").touserdata()).isSameAs(second)
        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
    }

    @Test
    fun `Monster empty target list renders an empty Lua table`() {
        val (environment, _) = installed()

        environment.loadChunk("list = m:getTargetList()")
        environment.loadChunk("count = #list")

        assertThat(environment.getGlobal("list").istable()).isTrue()
        assertThat(environment.getGlobal("count").toint()).isZero()
    }

    @Test
    fun `Monster isTarget isFriend and isOpponent pass the creature argument`() {
        val (environment, _) = installed()
        val creature = FakeCreature()
        environment.globals.set("c", environment.userdata.wrap("Creature", creature))

        environment.loadChunk("target = m:isTarget(c)")
        environment.loadChunk("friend = m:isFriend(c)")
        environment.loadChunk("opponent = m:isOpponent(c)")

        assertThat(environment.getGlobal("target").toboolean()).isTrue()
        assertThat(environment.getGlobal("friend").toboolean()).isTrue()
        assertThat(environment.getGlobal("opponent").toboolean()).isTrue()
    }

    @Test
    fun `Monster getTarget setTarget getMaster and setMaster use the Canary names`() {
        val (environment, _) = installed()
        val creature = FakeCreature()
        environment.globals.set("c", environment.userdata.wrap("Creature", creature))

        environment.loadChunk("before = m:getTarget()")
        environment.loadChunk("set = m:setTarget(c)")
        environment.loadChunk("after = m:getTarget()")
        environment.loadChunk("cleared = m:setTarget(nil)")
        environment.loadChunk("clearedTarget = m:getTarget()")
        environment.loadChunk("setMaster = m:setMaster(c)")
        environment.loadChunk("master = m:getMaster()")

        assertThat(environment.getGlobal("before").isnil()).isTrue()
        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(environment.getGlobal("after").touserdata()).isSameAs(creature)
        assertThat(environment.getGlobal("cleared").toboolean()).isTrue()
        assertThat(environment.getGlobal("clearedTarget").isnil()).isTrue()
        assertThat(environment.getGlobal("setMaster").toboolean()).isTrue()
        assertThat(environment.getGlobal("master").touserdata()).isSameAs(creature)
    }

    @Test
    fun `Monster isChallenged and selectTarget use the Canary names`() {
        val (environment, fake) = installed()
        val creature = FakeCreature()
        fake.challenged = true
        fake.selectTargetResult = false
        environment.globals.set("c", environment.userdata.wrap("Creature", creature))

        environment.loadChunk("challenged = m:isChallenged()")
        environment.loadChunk("selected = m:selectTarget(c)")

        assertThat(environment.getGlobal("challenged").toboolean()).isTrue()
        assertThat(environment.getGlobal("selected").toboolean()).isFalse()
        assertThat(fake.lastSelectTarget).isSameAs(creature)
    }
}
