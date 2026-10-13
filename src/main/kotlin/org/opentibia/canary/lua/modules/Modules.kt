package org.opentibia.canary.lua.modules

import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.bindings.library.NetworkMessage
import org.opentibia.canary.lua.runtime.LuaEnvironment
import java.util.WeakHashMap

/**
 * `ModuleType_t` (`src/lua/lua_definitions.hpp:114`).
 */
enum class ModuleType {
    MODULE_TYPE_RECVBYTE,
    MODULE_TYPE_NONE,
}

/**
 * Kotlin port of Canary's `class Module` (`src/lua/modules/modules.hpp:21-56`) for
 * the recvbyte client-packet modules.
 *
 * The C++ class extends `Event`, so `scriptId`, `scriptInterface`, `scripted` and
 * `loaded` live on the base; the port keeps only the state the recvbyte path needs:
 * [scriptId], [callback] and [isLoaded]. `configureEvent(node)` reads pugixml
 * attributes (`type` as lower-case string, `byte` as `as_int`, `delay` as
 * `as_uint`); callers pass the already-parsed attribute values, using null for an
 * absent attribute.
 */
class Module {
    /** `Module::type`; `MODULE_TYPE_NONE` until a valid recvbyte configuration. */
    var type: ModuleType = ModuleType.MODULE_TYPE_NONE
        private set

    /** `Module::recvbyte` (uint8_t). */
    var recvbyte: Int = 0
        private set

    /** `Module::delay` (int16_t). */
    var delay: Int = 0
        private set

    /** `Module::loaded`; `Event::isLoaded()`. */
    var isLoaded: Boolean = false
        private set

    /** `Event::scriptId`; resolved by the script loader. */
    var scriptId: Int = 0

    /** The Lua callback named by [getScriptEventName]; set by the script loader. */
    var callback: LuaValue? = null

    /**
     * `Module::configureEvent` (modules.cpp:114-145). Returns false when the type
     * attribute is missing/invalid or the `byte` attribute is missing; otherwise
     * stores the recvbyte, defaults the delay to 0 and marks the module loaded.
     */
    fun configureEvent(typeName: String?, byte: Int?, delay: Int?): Boolean {
        this.delay = 0

        if (typeName == null) {
            return false
        }

        if (typeName.lowercase() == "recvbyte") {
            if (byte == null) {
                return false
            }
            this.recvbyte = byte and 0xFF
            type = ModuleType.MODULE_TYPE_RECVBYTE
        } else {
            return false
        }

        this.delay = delay ?: 0
        isLoaded = true
        return true
    }

    /** `Module::getEventType` (modules.hpp:27). */
    fun getEventType(): ModuleType = type

    /** `Module::getScriptEventName` (modules.cpp:147-154). */
    fun getScriptEventName(): String = when (type) {
        ModuleType.MODULE_TYPE_RECVBYTE -> "onRecvbyte"
        ModuleType.MODULE_TYPE_NONE -> ""
    }

    /** `Module::copyEvent` (modules.cpp:156-161): copies the script binding and load flag. */
    fun copyEvent(other: Module) {
        scriptId = other.scriptId
        callback = other.callback
        isLoaded = other.isLoaded
    }

    /** `Module::clearEvent` (modules.cpp:163-168). */
    fun clearEvent() {
        scriptId = 0
        callback = null
        isLoaded = false
    }

    /**
     * `Module::executeOnRecvbyte` (modules.cpp:170-189): pushes the player userdata,
     * the borrowed [NetworkMessage] userdata and the recvbyte, then calls the Lua
     * `onRecvbyte` callback. A module without a callback is a no-op.
     */
    fun executeOnRecvbyte(env: LuaEnvironment, player: Any, msg: NetworkMessage) {
        val fn = callback ?: return
        fn.invoke(
            env.userdata.wrap("Player", player),
            env.userdata.wrap("NetworkMessage", msg),
            LuaValue.valueOf(recvbyte),
        )
    }
}

/**
 * Kotlin port of Canary's `class Modules` (`src/lua/modules/modules.hpp:58-85`),
 * the recvbyte client-packet module registry and dispatcher.
 *
 * PARITY NOTES:
 *  - The C++ class is a `BaseEvents` singleton reached through `g_modules()`;
 *    per AGENTS.md 1.1 the port is an injectable instance. The [LuaEnvironment]
 *    replaces the owned `LuaScriptInterface` and [now] replaces `OTSYS_TIME`.
 *  - `Player::canRunModule`/`Player::setModuleDelay` read and write the
 *    `Player::moduleDelayMap` (`player.cpp:3073-3082`). The Kotlin `Player` has no
 *    such state, so the port keeps the per-player delay map here, weakly keyed by
 *    player identity in place of the C++ player pointer so a released player is
 *    not retained.
 */
class Modules(
    private val env: LuaEnvironment,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val recvbyteList = LinkedHashMap<Int, Module>()

    private val moduleDelays = WeakHashMap<Any, MutableMap<Int, Long>>()

    /**
     * `Modules::clear` (modules.cpp:20-32): clears every module's script binding
     * and load flag, keeping the map entries so a reload can `copyEvent` into them.
     */
    fun clear() {
        for (module in recvbyteList.values) {
            module.clearEvent()
        }
    }

    /**
     * `Modules::registerEvent` (modules.cpp:51-73). An existing unloaded module of
     * the same type is reloaded in place; otherwise a loaded duplicate is refused.
     */
    fun registerEvent(module: Module): Boolean {
        if (module.type == ModuleType.MODULE_TYPE_NONE) {
            return false
        }

        val oldModule = getEventByRecvbyte(module.recvbyte, false)
        if (oldModule != null) {
            if (!oldModule.isLoaded && oldModule.type == module.type) {
                oldModule.copyEvent(module)
                return true
            }
            return false
        }

        recvbyteList[module.recvbyte] = module
        return true
    }

    /** `Modules::getEventByRecvbyte` (modules.cpp:75-81). */
    fun getEventByRecvbyte(recvbyte: Int, force: Boolean): Module? {
        val module = recvbyteList[recvbyte and 0xFF] ?: return null
        return if (!force || module.isLoaded) module else null
    }

    /** `Player::canRunModule` (player.cpp:3077-3082) over the port's per-player delay map. */
    fun canRunModule(player: Any, byteortype: Int): Boolean {
        val delay = moduleDelays[player]?.get(byteortype) ?: 0L
        if (delay == 0L) {
            return true
        }
        return delay <= now()
    }

    /** `Player::setModuleDelay` (player.cpp:3073-3075). */
    fun setModuleDelay(player: Any, byteortype: Int, delay: Int) {
        moduleDelays.getOrPut(player) { mutableMapOf() }[byteortype] = now() + delay
    }

    /**
     * `Modules::executeOnRecvbyte` (modules.cpp:83-104): resolves the recvbyte
     * module, refuses id 0, enforces the player's module delay, arms it, and runs
     * the Lua callback. Returns false for a missing player/module or wrong type.
     */
    fun executeOnRecvbyte(player: Any?, msg: NetworkMessage, byte: Int): Boolean {
        if (player == null) {
            return false
        }

        val module = getEventByRecvbyte(byte, false)
        if (module != null && module.recvbyte == 0) {
            return false
        }

        if (module == null || module.type != ModuleType.MODULE_TYPE_RECVBYTE) {
            return false
        }
        if (!canRunModule(player, module.recvbyte)) {
            return false
        }

        setModuleDelay(player, module.recvbyte, module.delay)
        module.executeOnRecvbyte(env, player, msg)
        return true
    }

    /**
     * `Modules::executeOnRecvbyte(uint32_t playerId, ...)` (modules.cpp:106-109).
     * Canary resolves the player through `g_game().getPlayerByID`; the resolver is
     * injected here.
     */
    fun executeOnRecvbyte(playerId: Long, msg: NetworkMessage, byte: Int, resolvePlayer: (Long) -> Any?): Boolean =
        executeOnRecvbyte(resolvePlayer(playerId), msg, byte)
}
