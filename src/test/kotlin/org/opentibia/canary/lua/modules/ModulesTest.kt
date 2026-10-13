package org.opentibia.canary.lua.modules

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.bindings.library.NetworkMessage
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript tests for the recvbyte module system
 * (`src/lua/modules/modules.cpp`): module configuration, recvbyte registry
 * replacement/reload semantics, delay gating and the Lua `onRecvbyte(player,
 * msg, byte)` execution pushed by `Module::executeOnRecvbyte`.
 */
class ModulesTest {

    private class FakePlayer

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun moduleConfigured(
        byte: Int,
        delay: Int = 0,
    ): Module {
        val module = Module()
        val configured = module.configureEvent("recvbyte", byte, delay)
        assertThat(configured).isTrue()
        return module
    }

    @Test
    fun `configureEvent mirrors the Canary attribute contract`() {
        val module = Module()
        assertThat(module.getEventType()).isEqualTo(ModuleType.MODULE_TYPE_NONE)
        assertThat(module.getScriptEventName()).isEmpty()

        assertThat(module.configureEvent(null, 15, null)).isFalse()
        assertThat(module.configureEvent("recvbyte", null, null)).isFalse()
        assertThat(module.configureEvent("magic", 15, null)).isFalse()
        assertThat(module.isLoaded).isFalse()

        assertThat(module.configureEvent("RecvByte", 15, null)).isTrue()
        assertThat(module.isLoaded).isTrue()
        assertThat(module.getEventType()).isEqualTo(ModuleType.MODULE_TYPE_RECVBYTE)
        assertThat(module.recvbyte).isEqualTo(15)
        assertThat(module.delay).isZero()
        assertThat(module.getScriptEventName()).isEqualTo("onRecvbyte")

        assertThat(module.configureEvent("recvbyte", 300, 2500)).isTrue()
        assertThat(module.recvbyte).isEqualTo(300 and 0xFF)
        assertThat(module.delay).isEqualTo(2500)
    }

    @Test
    fun `registerEvent refuses reloads until the previous module is cleared`() {
        val modules = Modules(env())
        val first = moduleConfigured(15)
        assertThat(modules.registerEvent(Module())).isFalse()
        assertThat(modules.registerEvent(first)).isTrue()
        assertThat(modules.getEventByRecvbyte(15, false)).isSameAs(first)

        val loadedReplacement = moduleConfigured(15)
        assertThat(modules.registerEvent(loadedReplacement)).isFalse()
        assertThat(modules.getEventByRecvbyte(15, false)).isSameAs(first)

        modules.clear()
        assertThat(first.isLoaded).isFalse()
        assertThat(modules.getEventByRecvbyte(15, true)).isNull()

        val reload = moduleConfigured(15)
        reload.scriptId = 42
        assertThat(modules.registerEvent(reload)).isTrue()
        assertThat(modules.getEventByRecvbyte(15, false)).isSameAs(first)
        assertThat(first.isLoaded).isTrue()
        assertThat(first.scriptId).isEqualTo(42)
    }

    @Test
    fun `executeOnRecvbyte invokes the Lua onRecvbyte callback through LuaEnvironment`() {
        val environment = env()
        environment.loadChunk(
            """
            function onRecvbyte(player, msg, byte)
                receivedPlayer = player
                receivedByte = byte
                receivedValue = msg:getByte()
            end
            """.trimIndent(),
        )

        val modules = Modules(environment)
        val player = FakePlayer()
        val module = moduleConfigured(15)
        module.callback = environment.globals.get("onRecvbyte")
        assertThat(modules.registerEvent(module)).isTrue()

        val message = NetworkMessage()
        message.addByte(0x2A)
        message.reset()
        message.setPayloadLength(1)

        modules.executeOnRecvbyte(player, message, 15)
        assertThat(environment.getGlobal("receivedPlayer").touserdata()).isSameAs(player)
        assertThat(environment.getGlobal("receivedByte").toint()).isEqualTo(15)
        assertThat(environment.getGlobal("receivedValue").toint()).isEqualTo(0x2A)
    }

    @Test
    fun `executeOnRecvbyte gates modules behind the Player module delay`() {
        var now = 0L
        val environment = env()
        environment.loadChunk("function onRecvbyte(player, msg, byte) runs = (runs or 0) + 1 end")
        val modules = Modules(environment, now = { now })
        val module = moduleConfigured(20, delay = 1000)
        module.callback = environment.globals.get("onRecvbyte")
        assertThat(modules.registerEvent(module)).isTrue()

        val player = FakePlayer()
        val message = NetworkMessage()
        message.addByte(1)
        now = 100

        assertThat(modules.executeOnRecvbyte(player, message, 20)).isTrue()
        assertThat(modules.canRunModule(player, 20)).isFalse()
        assertThat(modules.executeOnRecvbyte(player, message, 20)).isFalse()

        now = 1100
        assertThat(modules.canRunModule(player, 20)).isTrue()
        assertThat(modules.executeOnRecvbyte(player, message, 20)).isTrue()
        assertThat(environment.getGlobal("runs").toint()).isEqualTo(2)
    }

    @Test
    fun `executeOnRecvbyte rejects unknown bytes wrong players and id zero`() {
        val environment = env()
        val modules = Modules(environment)
        val message = NetworkMessage()

        assertThat(modules.executeOnRecvbyte(null, message, 15)).isFalse()
        assertThat(modules.executeOnRecvbyte(FakePlayer(), message, 15)).isFalse()

        val zero = moduleConfigured(0)
        zero.callback = environment.globals.get("print")
        assertThat(modules.registerEvent(zero)).isTrue()
        assertThat(modules.executeOnRecvbyte(FakePlayer(), message, 0)).isFalse()
    }

    @Test
    fun `executeOnRecvbyte by player id resolves the live player like g_game`() {
        var now = 0L
        val environment = env()
        environment.loadChunk("function onRecvbyte(player, msg, byte) resolved = player end")
        val modules = Modules(environment, now = { now })
        val module = moduleConfigured(33)
        module.callback = environment.globals.get("onRecvbyte")
        assertThat(modules.registerEvent(module)).isTrue()

        val player = FakePlayer()
        val message = NetworkMessage()
        assertThat(modules.executeOnRecvbyte(7L, message, 33) { id -> if (id == 7L) player else null }).isTrue()
        assertThat(environment.getGlobal("resolved").touserdata()).isSameAs(player)
        assertThat(modules.executeOnRecvbyte(8L, message, 33) { null }).isFalse()
    }
}
