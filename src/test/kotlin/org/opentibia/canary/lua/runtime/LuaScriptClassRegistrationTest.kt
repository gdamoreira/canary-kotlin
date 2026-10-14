package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform

/**
 * Transcribes the class/userdata registration flow of `Lua`
 * (`src/lua/functions/lua_functions_loader.cpp:747-916`): the class table + methods
 * metatable pair, the registry metatable fields (`__metatable`, `__index`, `h`, `p`,
 * `__name`, `baseclass`, `t`), typed shared classes, weak metatables and the
 * metatable-inheritance walk used to validate userdata.
 */
class LuaScriptClassRegistrationTest {

    private class Fixture {
        val state: Globals = JsePlatform.debugGlobals()
        val registry = LuaClassRegistry(state)
        val function: LuaValue = state.load("return function() return 1 end", "=(fn)").call()
    }

    @Test
    fun `LuaDataType ordinals match the Canary LuaData_t order`() {
        assertThat(LuaDataType.Unknown.ordinal).isZero()
        assertThat(LuaDataType.Item.ordinal).isEqualTo(1)
        assertThat(LuaDataType.Player.ordinal).isEqualTo(4)
        assertThat(LuaDataType.Action.ordinal).isEqualTo(27)
        assertThat(LuaDataType.ItemClassification.ordinal).isEqualTo(35)
    }

    @Test
    fun `registerClass builds the class table and registry metatable pair`() {
        val fixture = Fixture()

        fixture.registry.registerClass("Foo")

        val classTable = fixture.state.get("Foo")
        val metatable = fixture.registry.metatable("Foo")
        assertThat(classTable.istable()).isTrue()
        assertThat(metatable).isNotNull
        assertThat(metatable!!.get("__name").tojstring()).isEqualTo("Foo")
        assertThat(metatable.get("__metatable")).isSameAs(classTable)
        assertThat(metatable.get("__index")).isSameAs(classTable)
        assertThat(metatable.rawget('t'.code).toint()).isEqualTo(LuaDataType.Unknown.ordinal)
        assertThat(metatable.rawget('p'.code).toint()).isZero()
        assertThat(metatable.get("baseclass").isnil()).isTrue()
    }

    @Test
    fun `registerClass records the LuaData_t ordinal for a known class name`() {
        val fixture = Fixture()

        fixture.registry.registerClass("Player")
        fixture.registry.registerClass("ItemClassification")

        assertThat(fixture.registry.metatable("Player")!!.rawget('t'.code).toint()).isEqualTo(LuaDataType.Player.ordinal)
        assertThat(fixture.registry.metatable("ItemClassification")!!.rawget('t'.code).toint())
            .isEqualTo(LuaDataType.ItemClassification.ordinal)
    }

    @Test
    fun `registerClass wires the constructor through the methods metatable __call`() {
        val fixture = Fixture()
        val constructor = fixture.state.load("return function(self, name) return 'created:' .. name end", "=(new)").call()

        fixture.registry.registerClass("Foo", newFunction = constructor)

        val result = fixture.state.load("return Foo('x')", "=(call)").call()
        assertThat(result.tojstring()).isEqualTo("created:x")
    }

    @Test
    fun `registerClass with a base class links methods and counts parents`() {
        val fixture = Fixture()
        fixture.registry.registerClass("Creature")
        fixture.registry.registerMethod("Creature", "getHealth", fixture.function)

        fixture.registry.registerClass("Player", "Creature")

        val playerMetatable = fixture.registry.metatable("Player")!!
        assertThat(playerMetatable.get("baseclass").tojstring()).isEqualTo("Creature")
        assertThat(playerMetatable.rawget('p'.code).toint()).isEqualTo(1)
        assertThat(fixture.state.get("Player").get("getHealth").isfunction()).isTrue()

        fixture.registry.registerClass("Monster", "Player")
        assertThat(fixture.registry.metatable("Monster")!!.rawget('p'.code).toint()).isEqualTo(2)
    }

    @Test
    fun `registerMethod writes methods visible on the class table`() {
        val fixture = Fixture()
        fixture.registry.registerClass("Foo")

        fixture.registry.registerMethod("Foo", "getId", fixture.function)

        assertThat(fixture.state.get("Foo").get("getId").isfunction()).isTrue()
    }

    @Test
    fun `registerMetaMethod writes metamethods onto the registry metatable`() {
        val fixture = Fixture()
        fixture.registry.registerClass("Foo")

        fixture.registry.registerMetaMethod("Foo", "__eq", fixture.function)

        assertThat(fixture.registry.metatable("Foo")!!.get("__eq").isfunction()).isTrue()
    }

    @Test
    fun `registerTable and registerVariable fill a global table`() {
        val fixture = Fixture()

        fixture.registry.registerTable("MyTable")
        fixture.registry.registerVariable("MyTable", "speed", 200.0)

        assertThat(fixture.state.get("MyTable").istable()).isTrue()
        assertThat(fixture.state.get("MyTable").get("speed").toint()).isEqualTo(200)
    }

    @Test
    fun `registerGlobal helpers install booleans, numbers, strings and methods`() {
        val fixture = Fixture()

        fixture.registry.registerGlobalBoolean("GLOBAL_FLAG", true)
        fixture.registry.registerGlobalVariable("GLOBAL_SPEED", 100.0)
        fixture.registry.registerGlobalString("GLOBAL_NAME", "canary")
        fixture.registry.registerGlobalMethod("GLOBAL_FN", fixture.function)

        assertThat(fixture.state.get("GLOBAL_FLAG").toboolean()).isTrue()
        assertThat(fixture.state.get("GLOBAL_SPEED").toint()).isEqualTo(100)
        assertThat(fixture.state.get("GLOBAL_NAME").tojstring()).isEqualTo("canary")
        assertThat(fixture.state.get("GLOBAL_FN").isfunction()).isTrue()
    }

    @Test
    fun `registerSharedClass installs the garbage-collection metamethod`() {
        val fixture = Fixture()

        fixture.registry.registerSharedClass("Foo")

        assertThat(fixture.state.get("Foo").istable()).isTrue()
        assertThat(fixture.registry.metatable("Foo")!!.get("__gc").isfunction()).isTrue()
    }

    @Test
    fun `registerSharedClass accepts a base class and constructor`() {
        val fixture = Fixture()
        fixture.registry.registerClass("Creature")
        val constructor = fixture.state.load("return function(self, name) return 'created:' .. name end", "=(new)").call()

        fixture.registry.registerSharedClass("Player", "Creature", constructor)

        val metatable = fixture.registry.metatable("Player")!!
        assertThat(metatable.get("baseclass").tojstring()).isEqualTo("Creature")
        assertThat(metatable.rawget('p'.code).toint()).isEqualTo(1)
        assertThat(metatable.get("__gc").isfunction()).isTrue()
        assertThat(fixture.state.load("return Player('x')", "=(call)").call().tojstring()).isEqualTo("created:x")
    }

    @Test
    fun `setMetatable attaches the registry metatable and protects the class table`() {
        val fixture = Fixture()
        fixture.registry.registerClass("Foo")
        val target = LuaTable()

        fixture.registry.setMetatable(target, "Foo")

        assertThat(target.getmetatable()).isSameAs(fixture.registry.metatable("Foo"))
        assertThat(fixture.state.load("return getmetatable(...)", "=(get)").invoke(target).arg1())
            .isSameAs(fixture.state.get("Foo"))
    }

    @Test
    fun `setWeakMetatable shares one weak metatable and copies the registry fields`() {
        val fixture = Fixture()
        fixture.registry.registerClass("Foo")
        val first = LuaTable()
        val second = LuaTable()

        fixture.registry.setWeakMetatable(first, "Foo")
        fixture.registry.setWeakMetatable(second, "Foo")

        assertThat(first.getmetatable()).isSameAs(second.getmetatable())
        val weak = fixture.registry.metatable("Foo_weak")
        assertThat(weak).isNotNull
        assertThat(first.getmetatable()).isSameAs(weak)
        assertThat(weak!!.get("__name").tojstring()).isEqualTo("Foo")
        assertThat(weak.get("__gc").isnil()).isTrue()
        assertThat(weak.get("__index")).isSameAs(fixture.state.get("Foo"))
    }

    @Test
    fun `checkMetatableInheritance follows the baseclass chain`() {
        val fixture = Fixture()
        fixture.registry.registerClass("Creature")
        fixture.registry.registerClass("Player", "Creature")
        val player = LuaValue.userdataOf("player", fixture.registry.metatable("Player"))
        val creature = LuaValue.userdataOf("creature", fixture.registry.metatable("Creature"))

        assertThat(fixture.registry.checkMetatableInheritance(player, "Player")).isTrue()
        assertThat(fixture.registry.checkMetatableInheritance(player, "Creature")).isTrue()
        assertThat(fixture.registry.checkMetatableInheritance(creature, "Player")).isFalse()
        assertThat(fixture.registry.checkMetatableInheritance(LuaValue.valueOf(1), "Creature")).isFalse()
    }

    @Test
    fun `checkMetatableInheritance follows the __index fallback`() {
        val fixture = Fixture()
        val indexTable = LuaTable().apply { set("__name", "Foo") }
        val metatable = LuaTable().apply { set("__index", indexTable) }
        val target = LuaTable().apply { setmetatable(metatable) }

        assertThat(fixture.registry.checkMetatableInheritance(target, "Foo")).isTrue()
        assertThat(fixture.registry.checkMetatableInheritance(target, "Bar")).isFalse()
    }

    @Test
    fun `getUserdataType reads the t field and names the enum`() {
        val fixture = Fixture()
        fixture.registry.registerClass("Player")
        val player = LuaValue.userdataOf("player", fixture.registry.metatable("Player"))

        assertThat(fixture.registry.getUserdataType(player)).isEqualTo(LuaDataType.Player)
        assertThat(fixture.registry.getUserdataType(LuaValue.valueOf(1))).isEqualTo(LuaDataType.Unknown)
        assertThat(fixture.registry.getUserdataTypeName(LuaDataType.ItemClassification))
            .isEqualTo("ItemClassification")
    }

    @Test
    fun `the environment exposes the class registry over its metatable table`() {
        val environment = LuaEnvironment().apply { initState() }

        environment.classes.registerClass("Foo")

        assertThat(environment.classes.metatable("Foo")).isNotNull
        assertThat(environment.getGlobal("Foo").istable()).isTrue()
    }
}
